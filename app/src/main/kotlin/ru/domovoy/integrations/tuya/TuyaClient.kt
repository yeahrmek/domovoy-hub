package ru.domovoy.integrations.tuya

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import ru.domovoy.core.Device
import ru.domovoy.core.DeviceKind
import ru.domovoy.core.OnOff
import ru.domovoy.core.Range
import ru.domovoy.core.Reading
import ru.domovoy.core.Toggle
import java.io.IOException
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/** Central Europe, which is the data centre this account's cloud project was created in. */
val TUYA_BASE_URL: HttpUrl = "https://openapi.tuyaeu.com/".toHttpUrl()

/** The recuperators. The same account also holds `dj` lamps, `mjj` towel rails, a `wg2` and a `kg`. */
private const val RECUPERATOR_CATEGORY = "xfj"

/** The one datapoint that is the device's power; every other bool is a [Toggle]. */
private const val SWITCH = "switch"

private const val TYPE_BOOL = "bool"
private const val TYPE_VALUE = "value"

/**
 * `huimi` and `temper` are tenths: the thing model says `scale: 1`, so 322 is 32.2 %RH and 293 is
 * 29.3 °C. Held here rather than re-read per poll — the model is a property of the product, and a
 * refresh already costs five calls against a metered allowance. TuyaClientTest asserts the
 * recorded model still says this.
 */
private const val VALUE_SCALE = 10.0

private val SPEEDS = setOf("speed_one", "speed_two", "speed_three")

/**
 * Taken off the expiry so a token is replaced before it lapses rather than in the middle of a
 * call — a poll that fails on a token that expired in flight is a tile saying "not updating" for
 * a reason the panel could have avoided.
 */
private const val TOKEN_MARGIN_SECONDS = 60L

/**
 * Names both what is missing and the one way a credential gets in today. Said in `Log` rather than
 * on the tile, for [ru.domovoy.integrations.yandex.NO_TOKEN]'s reason and with its consequences.
 */
internal const val NO_CREDENTIALS =
    "no Tuya credentials stored — set tuya.client.id, tuya.client.secret and tuya.uid in local.properties and reinstall"

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

private val json = Json { ignoreUnknownKeys = true }

/** What a signed Tuya call needs. [uid] is the linked Smart Life account, not the cloud project. */
data class TuyaCredentials(
    val clientId: String,
    val clientSecret: String,
    val uid: String,
)

/**
 * Reads the flat's recuperators, and switches one on or off.
 *
 * Two things shape this and neither is a preference:
 *
 * **A refresh is five calls.** `/v1.0/users/{uid}/devices` is one call for the inventory, but the
 * `status` it carries is filtered to Tuya's standard instruction set and holds only `switch` — the
 * real 13 datapoints come from `/v2.0/cloud/thing/{id}/shadow/properties`, one call per
 * recuperator, because the batch route answers `40001900 No space permission`. So the panel polls
 * slowly; see docs/tuya.md for what the allowance actually buys.
 *
 * **The token is cached.** It costs a call like anything else, and the account hands out the one it
 * already issued — `expire_time` is the seconds *left* on it, not a fresh 7200 — so it is held
 * until that runs out rather than fetched per request.
 *
 * [credentials] is asked for per call rather than held, for the reason the Yandex client reads its
 * token per call: the panel runs for weeks on a wall, and credentials written to the store while it
 * runs have to be used by the next poll. It may throw — a store that will not open does — and that
 * lands on the tile like any other failure, because every entry point below runs inside
 * `runCatching`.
 *
 * Shares nothing with `integrations/yandex/` by design, per AGENTS.md.
 */
class TuyaClient(
    http: OkHttpClient,
    private val credentials: () -> TuyaCredentials,
    private val baseUrl: HttpUrl = TUYA_BASE_URL,
    timeout: Duration = 10.seconds,
    private val now: () -> Instant = Instant::now,
) {
    // Every request goes through this one, so every request carries a call timeout — no tile can
    // be left with a spinner that spins forever. newBuilder() shares the connection pool.
    private val http = http.newBuilder().callTimeout(timeout.toJavaDuration()).build()

    // A tap and a poll can want a token at the same moment; without this they would each spend a
    // call to fetch one, which on this allowance is not free.
    private val tokenLock = Mutex()
    private var token: CachedToken? = null

    /**
     * The inventory: which recuperators exist, what they are called, and whether Tuya says they
     * are reachable. Deliberately carries no state — see [read].
     */
    suspend fun devices(): Result<List<Device>> = runCatching {
        val credentials = credentials().checked()
        val body =
            request(
                credentials = credentials,
                method = "GET",
                path = "v1.0/users/${credentials.uid}/devices",
                accessToken = accessToken(credentials),
            )
        resultOf<List<TuyaDeviceDto>>(body, "GET /v1.0/users/{uid}/devices")
            .filter { it.category == RECUPERATOR_CATEGORY }
            .map { it.toDevice() }
    }

    /**
     * The real state of one recuperator, on top of what [devices] already said about it. One call,
     * and the only place the panel's values come from.
     */
    suspend fun read(device: Device): Result<Device> = runCatching {
        val credentials = credentials().checked()
        val call = "GET /v2.0/cloud/thing/{id}/shadow/properties"
        val body =
            request(
                credentials = credentials,
                method = "GET",
                path = "v2.0/cloud/thing/${device.id}/shadow/properties",
                accessToken = accessToken(credentials),
            )
        resultOf<TuyaShadowPropertiesDto>(body, call).properties.applyTo(device)
    }

    /**
     * Switches one recuperator on or off.
     *
     * The thing-model issue route and this body shape were verified on the physical device on
     * 2026-08-30. The v1.0 command route is not an option: the standard instruction set for this
     * product is `switch` alone. See docs/tuya.md.
     *
     * A success here means the host took the request, nothing more, so the caller re-reads rather
     * than painting the new value from this result — the same rule the Yandex toggle follows.
     */
    suspend fun setOn(
        deviceId: String,
        on: Boolean,
    ): Result<Unit> = issue(deviceId, mapOf(SWITCH to on))

    /** Selects one of the three speed datapoints verified on the physical recuperator. */
    suspend fun setSpeed(
        deviceId: String,
        code: String,
    ): Result<Unit> = runCatching {
        require(code in SPEEDS) { "unsupported recuperator speed: $code" }
        issue(deviceId, mapOf(code to true)).getOrThrow()
    }

    private suspend fun issue(
        deviceId: String,
        properties: Map<String, Boolean>,
    ): Result<Unit> = runCatching {
        val credentials = credentials().checked()
        val payload = json.encodeToString(TuyaIssueDto(properties = json.encodeToString(properties)))
        val body =
            request(
                credentials = credentials,
                method = "POST",
                path = "v2.0/cloud/thing/$deviceId/shadow/properties/issue",
                body = payload,
                accessToken = accessToken(credentials),
            )
        checkSuccess(body, "POST /v2.0/cloud/thing/{id}/shadow/properties/issue")
    }

    // Held until it lapses rather than fetched per call. The lock is not for correctness of the
    // token — a second one would work — but for the allowance.
    private suspend fun accessToken(credentials: TuyaCredentials): String = tokenLock.withLock {
        val held = token
        if (held != null && now().isBefore(held.expiresAt)) return@withLock held.value
        val body =
            request(
                credentials = credentials,
                method = "GET",
                path = "v1.0/token",
                query = mapOf("grant_type" to "1"),
                // There is no token yet, and the signature says so with an empty string.
                accessToken = "",
            )
        val fresh = resultOf<TuyaTokenDto>(body, "GET /v1.0/token")
        token =
            CachedToken(
                value = fresh.accessToken,
                expiresAt = now().plusSeconds(fresh.expireTime - TOKEN_MARGIN_SECONDS),
            )
        fresh.accessToken
    }

    private suspend fun request(
        credentials: TuyaCredentials,
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        body: String? = null,
        accessToken: String,
    ): String = withContext(Dispatchers.IO) {
        val url =
            baseUrl
                .newBuilder()
                .addPathSegments(path)
                // Tuya signs the query with its parameters sorted. Only `grant_type` is ever sent
                // today, so this is cheap insurance rather than something being relied on.
                .apply { query.toSortedMap().forEach { (name, value) -> addQueryParameter(name, value) } }
                .build()
        val timestamp = now().toEpochMilli()
        // Signed over exactly what goes on the wire, taken back off the built URL rather than
        // rebuilt from the arguments — the two drifting apart is what `1004 sign invalid` is.
        val pathWithQuery = url.encodedPath + url.encodedQuery?.let { "?$it" }.orEmpty()
        val request =
            Request
                .Builder()
                .url(url)
                .header("client_id", credentials.clientId)
                .header(
                    "sign",
                    tuyaSignature(
                        clientId = credentials.clientId,
                        clientSecret = credentials.clientSecret,
                        accessToken = accessToken,
                        timestampMillis = timestamp,
                        method = method,
                        pathWithQuery = pathWithQuery,
                        body = body.orEmpty(),
                    ),
                ).header("t", timestamp.toString())
                .header("sign_method", "HMAC-SHA256")
                .apply { if (accessToken.isNotEmpty()) header("access_token", accessToken) }
                .apply { if (body != null) post(body.toRequestBody(JSON_MEDIA_TYPE)) }
                .build()

        http.newCall(request).await().use { response ->
            val payload = response.body.string()
            if (!response.isSuccessful) {
                error("$method ${url.encodedPath} failed: HTTP ${response.code} ${payload.take(200)}")
            }
            payload
        }
    }

    private fun TuyaCredentials.checked(): TuyaCredentials {
        if (clientId.isBlank() || clientSecret.isBlank() || uid.isBlank()) error(NO_CREDENTIALS)
        return this
    }
}

private data class CachedToken(
    val value: String,
    val expiresAt: Instant,
)

private fun TuyaDeviceDto.toDevice(): Device = Device(
    id = id,
    name = name,
    kind = DeviceKind.Recuperator,
    // Tuya's device list names no room; the Smart Life app groups them, the API does not say so.
    room = null,
    // Not from the `status` this response carries: it is filtered to the standard instruction set,
    // and the only timestamp on it is the device-level `update_time`, which belongs to no one
    // datapoint. State comes from the thing model, in [TuyaClient.read].
    onOff = null,
    online = online,
)

/**
 * The 13 datapoints, laid over what the inventory already said about the device.
 *
 * Every capability below carries the same instant in both of the model's timestamps, and that is
 * the honest reading rather than a shortcut: Tuya reports one `time` per datapoint and does not say
 * whether it is when the value was last read or when it last changed. Splitting them would invent
 * a distinction the vendor does not make. See docs/tuya.md.
 */
private fun List<TuyaPropertyDto>.applyTo(device: Device): Device = device.copy(
    onOff =
    firstOrNull { it.code == SWITCH }?.let { property ->
        property.boolean()?.let {
            OnOff(isOn = it, lastUpdated = property.reading(), stateChangedAt = property.reading())
        }
    },
    toggles =
    filter { it.type == TYPE_BOOL && it.code != SWITCH }.associate {
        it.code to Toggle(isOn = it.boolean(), lastUpdated = it.reading(), stateChangedAt = it.reading())
    },
    ranges =
    filter { it.type == TYPE_VALUE }.associate {
        it.code to
            Range(
                value = it.number()?.div(VALUE_SCALE),
                // The shadow response carries neither; the thing model's `min: 0 / max: 10000` is
                // a nominal range rather than what the sensor can read, and its unit is "".
                bounds = null,
                unit = null,
                lastUpdated = it.reading(),
                stateChangedAt = it.reading(),
            )
    },
)

private fun TuyaPropertyDto.reading(): Reading = Reading.ofEpochMillis(time)

private fun TuyaPropertyDto.boolean(): Boolean? = (value as? JsonPrimitive)?.booleanOrNull

private fun TuyaPropertyDto.number(): Double? = (value as? JsonPrimitive)?.doubleOrNull

private inline fun <reified T> resultOf(
    body: String,
    call: String,
): T {
    val response = json.decodeFromString<TuyaResponseDto<T>>(body)
    if (!response.success) error(response.failure(call))
    return response.result ?: error("$call answered success with no result")
}

// The issue call's result carries no state worth trusting, so only `success` is read from it.
private fun checkSuccess(
    body: String,
    call: String,
) {
    val response = json.decodeFromString<TuyaResponseDto<JsonElement>>(body)
    if (!response.success) error(response.failure(call))
}

// The code is the useful half — 1004 is the signature, 1108 a wrong method, 40001900 a project
// that is not authorised for the route — and the message alone does not say which.
private fun TuyaResponseDto<*>.failure(call: String): String = "$call failed: code=$code msg=$msg"

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onResponse(
                call: Call,
                response: Response,
            ) {
                continuation.resume(response)
            }

            override fun onFailure(
                call: Call,
                e: IOException,
            ) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }
        },
    )
}
