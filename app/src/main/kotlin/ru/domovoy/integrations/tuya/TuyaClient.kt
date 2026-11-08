package ru.domovoy.integrations.tuya

import kotlinx.coroutines.CancellationException
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

/** Verified sensor scale for `huimi` and `temper`: 322 means 32.2. See docs/tuya.md. */
private const val VALUE_SCALE = 10.0

private val SPEEDS = setOf("speed_one", "speed_two", "speed_three")

/** Refresh tokens before expiry to allow for requests in flight. */
private const val TOKEN_MARGIN_SECONDS = 60L

/** Missing-credential details go to the log; tiles display a short failure label. */
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

/** Tuya business errors arrive as HTTP 200; preserve the code for presentation decisions. */
class TuyaApiException(
    val code: Long?,
    call: String,
    apiMessage: String?,
) : IllegalStateException("$call failed: code=$code msg=$apiMessage")

/**
 * Reads and controls recuperators independently of the Yandex integration.
 * A refresh costs one inventory request plus one shadow request per device; poll slowly.
 * Credentials are read per call; access tokens are cached for their remaining lifetime.
 */
class TuyaClient(
    http: OkHttpClient,
    private val credentials: () -> TuyaCredentials,
    private val baseUrl: HttpUrl = TUYA_BASE_URL,
    timeout: Duration = 10.seconds,
    private val now: () -> Instant = Instant::now,
) : TuyaDevices {
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
    override suspend fun devices(): Result<List<Device>> = runCatching {
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
    }.onFailure { if (it is CancellationException) throw it }

    /**
     * The real state of one recuperator, on top of what [devices] already said about it. One call,
     * and the only place the panel's values come from.
     */
    override suspend fun read(device: Device): Result<Device> = runCatching {
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
    }.onFailure { if (it is CancellationException) throw it }

    /**
     * Uses the issue route verified on a physical device; standard commands expose only power.
     * Success means accepted by Tuya. Callers re-read until the shadow reflects the command.
     */
    override suspend fun setOn(
        deviceId: String,
        on: Boolean,
    ): Result<Unit> = issue(deviceId, mapOf(SWITCH to on))

    /** Selects one of the three speed datapoints verified on the physical recuperator. */
    override suspend fun setSpeed(
        deviceId: String,
        code: String,
    ): Result<Unit> = runCatching {
        require(code in SPEEDS) { "unsupported recuperator speed: $code" }
        issue(deviceId, mapOf(code to true)).getOrThrow()
    }.onFailure { if (it is CancellationException) throw it }

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
    }.onFailure { if (it is CancellationException) throw it }

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

        http.newCall(request).awaitBody()
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

/** Maps the verified shadow datapoints, retaining their per-property timestamps. */
private fun List<TuyaPropertyDto>.applyTo(device: Device): Device = device.copy(
    onOff =
    firstOrNull { it.code == SWITCH }?.let { property ->
        property.boolean()?.let {
            OnOff(isOn = it, lastUpdated = property.reading())
        }
    },
    toggles =
    filter { it.type == TYPE_BOOL && it.code != SWITCH }.associate {
        it.code to Toggle(isOn = it.boolean(), lastUpdated = it.reading())
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
    if (!response.success) throw response.failure(call)
    return response.result ?: error("$call answered success with no result")
}

// The issue call's result carries no state worth trusting, so only `success` is read from it.
private fun checkSuccess(
    body: String,
    call: String,
) {
    val response = json.decodeFromString<TuyaResponseDto<JsonElement>>(body)
    if (!response.success) throw response.failure(call)
}

private fun TuyaResponseDto<*>.failure(call: String): TuyaApiException = TuyaApiException(code, call, msg)

/** Keeps HTTP cancellation connected until the entire response body is consumed. */
private suspend fun Call.awaitBody(): String = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onResponse(
                call: Call,
                response: Response,
            ) {
                val result = runCatching {
                    response.use {
                        val body = it.body.string()
                        if (!it.isSuccessful) {
                            val request = call.request()
                            error("${request.method} ${request.url.encodedPath} failed: HTTP ${it.code} ${body.take(200)}")
                        }
                        body
                    }
                }
                continuation.resumeWith(result)
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
