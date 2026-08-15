package ru.domovoy.integrations.yandex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
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
import ru.domovoy.core.OnOff
import ru.domovoy.core.Reading
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/** `devices.types.light.strip` is a different type and deliberately not in scope. */
private const val LIGHT = "devices.types.light"
private const val ON_OFF = "devices.capabilities.on_off"

val YANDEX_BASE_URL: HttpUrl = "https://api.iot.yandex.net/".toHttpUrl()

/**
 * Said on the tile itself when the store holds no token, so it names both what is wrong and the
 * one way a token gets in today. Sending an empty `Bearer` instead would come back `403` and the
 * panel would blame the OAuth scopes — see docs/yandex.md.
 */
internal const val NO_TOKEN = "no Yandex token stored — set yandex.oauth.token in local.properties and reinstall"

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

private val json = Json { ignoreUnknownKeys = true }

/**
 * Reads and drives the bulbs of one Yandex household.
 *
 * The account behind the recorded response holds four households — the flat, two other homes and
 * a dacha. [householdId] is the only thing that says which of them the panel hangs in, so every
 * device from the other three is dropped here rather than anywhere downstream.
 *
 * [token] is asked for once per request rather than held: the panel runs for weeks on a wall, and
 * a token written to the store while it runs has to be used by the next poll, not after a restart.
 * It may fail — a store that will not open throws out of it — and that failure lands on the tile
 * like any other, because the call sites below already run inside `runCatching`.
 */
class YandexClient(
    http: OkHttpClient,
    private val token: () -> String,
    private val householdId: String,
    private val baseUrl: HttpUrl = YANDEX_BASE_URL,
    timeout: Duration = 10.seconds,
) {
    // Every request the client sends goes through this one, so every request carries a call
    // timeout — no tile can be left with a spinner that spins forever. newBuilder() shares the
    // connection pool and dispatcher, so this costs nothing.
    private val http = http.newBuilder().callTimeout(timeout.toJavaDuration()).build()

    /**
     * One `/v1.0/user/info` call is the whole house, so this is the panel's poll. Returns the
     * bulbs of [householdId] only; failures come back as a failed [Result] for the caller to show.
     */
    suspend fun devices(): Result<List<Device>> = runCatching {
        val body = get("v1.0/user/info")
        val info = json.decodeFromString<UserInfoDto>(body)
        if (info.status != null && info.status != "ok") {
            error("GET /v1.0/user/info returned status=${info.status} request_id=${info.requestId}")
        }
        val roomNames = info.rooms.associate { it.id to it.name }
        info.devices
            .filter { it.householdId == householdId && it.type == LIGHT }
            .map { it.toDevice(roomNames) }
    }

    /**
     * Turns one bulb on or off. Success means Yandex accepted the request — the docs do not say
     * whether `DONE` waits for the bulb to physically change, so the caller re-reads rather than
     * painting the new value from this result alone.
     */
    suspend fun setOn(
        deviceId: String,
        on: Boolean,
    ): Result<Unit> = runCatching {
        val payload =
            ActionsRequestDto(
                devices =
                listOf(
                    ActionDeviceDto(
                        id = deviceId,
                        actions = listOf(ActionDto(type = ON_OFF, state = ActionStateDto("on", on))),
                    ),
                ),
            )
        val body = post("v1.0/devices/actions", json.encodeToString(payload))
        val response = json.decodeFromString<ActionsResponseDto>(body)
        if (response.status != null && response.status != "ok") {
            error("POST /v1.0/devices/actions returned status=${response.status} request_id=${response.requestId}")
        }
    }

    private suspend fun get(path: String): String = send { request(path).build() }

    private suspend fun post(
        path: String,
        body: String,
    ): String = send { request(path).post(body.toRequestBody(JSON_MEDIA_TYPE)).build() }

    // Building the request is where the token is fetched, so a missing one fails the call before
    // a socket is opened — an unauthenticated request to Yandex is worse than no request at all.
    private fun request(path: String): Request.Builder {
        val token = token().trim()
        if (token.isEmpty()) error(NO_TOKEN)
        return Request
            .Builder()
            .url(baseUrl.newBuilder().addPathSegments(path).build())
            .header("Authorization", "Bearer $token")
    }

    // enqueue() hands the response back as soon as the headers land, but reading the body is a
    // blocking socket read — and it happens on whatever dispatcher called in. The panel polls from
    // Dispatchers.Main, so without this the tablet answers NetworkOnMainThreadException and no
    // tile ever gets a value. The request is built in here too, so reading the token out of
    // encrypted storage — a decrypt per call — happens off the main thread as well.
    private suspend fun send(build: () -> Request): String = withContext(Dispatchers.IO) {
        val request = build()
        http.newCall(request).await().use { response ->
            // A wrong OAuth scope answers with a bare non-JSON "Forbidden", so the body is read
            // before it is trusted to be JSON and quoted back in the failure.
            val body = response.body.string()
            if (!response.isSuccessful) {
                error("${request.method} ${request.url.encodedPath} failed: HTTP ${response.code} ${body.take(200)}")
            }
            body
        }
    }
}

private fun DeviceDto.toDevice(roomNames: Map<String, String>): Device = Device(
    id = id,
    name = name,
    room = room?.let(roomNames::get),
    onOff =
    capabilities.firstOrNull { it.type == ON_OFF }?.let { capability ->
        val value = (capability.state?.value as? JsonPrimitive)?.booleanOrNull
        value?.let {
            OnOff(
                isOn = it,
                lastUpdated = Reading.ofEpochSeconds(capability.lastUpdated),
                stateChangedAt = Reading.ofEpochSeconds(capability.stateChangedAt),
            )
        }
    },
)

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
