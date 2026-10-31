package ru.domovoy.integrations.yandex

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
import ru.domovoy.core.Bounds
import ru.domovoy.core.ColorSetting
import ru.domovoy.core.Device
import ru.domovoy.core.DeviceKind
import ru.domovoy.core.EventProperty
import ru.domovoy.core.FloatProperty
import ru.domovoy.core.Mode
import ru.domovoy.core.OnOff
import ru.domovoy.core.Range
import ru.domovoy.core.Reading
import ru.domovoy.core.Toggle
import java.io.IOException
import kotlin.coroutines.resumeWithException
import kotlin.math.floor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/** Exact vendor types supported by panel tiles; light strips are a separate type. */
private val KINDS =
    mapOf(
        "devices.types.light" to DeviceKind.Bulb,
        "devices.types.light.strip" to DeviceKind.LightStrip,
        "devices.types.openable.curtain" to DeviceKind.Curtain,
        "devices.types.thermostat.ac" to DeviceKind.AirConditioner,
    )

private const val ON_OFF = "devices.capabilities.on_off"
private const val RANGE = "devices.capabilities.range"
private const val MODE = "devices.capabilities.mode"
private const val TOGGLE = "devices.capabilities.toggle"
private const val COLOR_SETTING = "devices.capabilities.color_setting"
private const val FLOAT_PROPERTY = "devices.properties.float"

val YANDEX_BASE_URL: HttpUrl = "https://api.iot.yandex.net/".toHttpUrl()

/** Missing-credential details go to the log; tiles display a short failure label. */
internal const val NO_TOKEN = "no Yandex token stored — set yandex.oauth.token in local.properties and reinstall"

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

private val json = Json { ignoreUnknownKeys = true }

/**
 * Discovers every device in one household. Other households are discarded.
 * Credentials are read per request so updates take effect without restarting the panel.
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
     * devices of [householdId] — every kind together, told apart by
     * [Device.kind]; failures come back as a failed [Result] for the caller to show.
     */
    suspend fun devices(): Result<List<Device>> = runCatching {
        val body = get("v1.0/user/info")
        val info = json.decodeFromString<UserInfoDto>(body)
        if (info.status != null && info.status != "ok") {
            error("GET /v1.0/user/info returned status=${info.status} request_id=${info.requestId}")
        }
        val roomNames = info.rooms.associate { it.id to it.name }
        info.devices
            .filter { it.householdId == householdId }
            .map { device -> device.toDevice(KINDS[device.type] ?: DeviceKind.Other, roomNames) }
    }.onFailure { if (it is CancellationException) throw it }

    /** Success means accepted by Yandex; callers re-read to confirm device state. */
    suspend fun setOn(
        deviceId: String,
        on: Boolean,
    ): Result<Unit> = action(deviceId, ON_OFF, ActionStateDto("on", JsonPrimitive(on)))

    /** Sets a numeric capability; callers snap values to device bounds and re-read. */
    suspend fun setRange(
        deviceId: String,
        instance: String,
        value: Double,
    ): Result<Unit> = action(deviceId, RANGE, ActionStateDto(instance, value.asJson()))

    /** Drives one enumerated capability using a value the device itself advertised. */
    suspend fun setMode(
        deviceId: String,
        instance: String,
        value: String,
    ): Result<Unit> = action(deviceId, MODE, ActionStateDto(instance, JsonPrimitive(value)))

    /** Drives one secondary boolean capability such as ionization. */
    suspend fun setToggle(
        deviceId: String,
        instance: String,
        on: Boolean,
    ): Result<Unit> = action(deviceId, TOGGLE, ActionStateDto(instance, JsonPrimitive(on)))

    /** Drives RGB or Kelvin color values, both integer-shaped in Yandex's control API. */
    suspend fun setColor(
        deviceId: String,
        instance: String,
        value: Int,
    ): Result<Unit> = action(deviceId, COLOR_SETTING, ActionStateDto(instance, JsonPrimitive(value)))

    /** Drives one of the scene ids advertised by the device. */
    suspend fun setColor(
        deviceId: String,
        instance: String,
        value: String,
    ): Result<Unit> = action(deviceId, COLOR_SETTING, ActionStateDto(instance, JsonPrimitive(value)))

    private suspend fun action(
        deviceId: String,
        type: String,
        state: ActionStateDto,
    ): Result<Unit> = runCatching {
        val payload =
            ActionsRequestDto(
                devices =
                listOf(
                    ActionDeviceDto(
                        id = deviceId,
                        actions = listOf(ActionDto(type = type, state = state)),
                    ),
                ),
            )
        val body = post("v1.0/devices/actions", json.encodeToString(payload))
        val response = json.decodeFromString<ActionsResponseDto>(body)
        if (response.status != null && response.status != "ok") {
            error("POST /v1.0/devices/actions returned status=${response.status} request_id=${response.requestId}")
        }
    }.onFailure { if (it is CancellationException) throw it }

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

    // Credential decryption runs off Main. awaitBody reads the socket on OkHttp's dispatcher
    // and keeps cancellation connected until the body has been consumed.
    private suspend fun send(build: () -> Request): String = withContext(Dispatchers.IO) {
        val request = build()
        http.newCall(request).awaitBody()
    }
}

private fun DeviceDto.toDevice(
    kind: DeviceKind,
    roomNames: Map<String, String>,
): Device = Device(
    id = id,
    name = name,
    kind = kind,
    room = room?.let(roomNames::get),
    onOff =
    capabilities.firstOrNull { it.type == ON_OFF }?.let { capability ->
        val value = (capability.state?.value as? JsonPrimitive)?.booleanOrNull
        value?.let {
            OnOff(
                isOn = it,
                lastUpdated = Reading.ofEpochSeconds(capability.lastUpdated),
            )
        }
    },
    ranges = capabilities.filter { it.type == RANGE }.mapNotNull(CapabilityDto::toRange).toMap(),
    modes = capabilities.filter { it.type == MODE }.mapNotNull(CapabilityDto::toMode).toMap(),
    toggles = capabilities.filter { it.type == TOGGLE }.mapNotNull(CapabilityDto::toToggle).toMap(),
    color = capabilities.firstOrNull { it.type == COLOR_SETTING }?.toColorSetting(),
    properties = properties.filter { it.type == FLOAT_PROPERTY }.mapNotNull(PropertyDto::toFloatProperty).toMap(),
    vendorType = type,
    events = properties.filter { it.type == "devices.properties.event" }.mapNotNull(PropertyDto::toEventProperty).toMap(),
)

// A range with no state at all is kept, not dropped: its bounds are still what the device accepts,
// and "never reported" is a different thing from "no such capability" on the tile. The same holds
// for the mode and toggle below — every one of ac-01's eight capabilities is in exactly that state.
private fun CapabilityDto.toRange(): Pair<String, Range>? {
    val instance = parameters?.instance ?: state?.instance ?: return null
    return instance to
        Range(
            value = (state?.value as? JsonPrimitive)?.doubleOrNull,
            bounds = parameters?.range?.let { Bounds(min = it.min, max = it.max, precision = it.precision) },
            // The TV's volume range carries "" rather than omitting the unit; both mean the same.
            unit = parameters?.unit?.takeIf { it.isNotBlank() },
            lastUpdated = Reading.ofEpochSeconds(lastUpdated),
        )
}

private fun PropertyDto.toEventProperty(): Pair<String, EventProperty>? {
    val instance = parameters?.instance ?: state?.instance ?: return null
    return instance to EventProperty(
        value = (state?.value as? JsonPrimitive)?.takeIf { it.isString }?.content,
        lastUpdated = Reading.ofEpochSeconds(lastUpdated),
    )
}

private fun CapabilityDto.toMode(): Pair<String, Mode>? {
    val instance = parameters?.instance ?: state?.instance ?: return null
    return instance to
        Mode(
            // A mode's value is a string; isString keeps a number from being read as a mode name.
            current = (state?.value as? JsonPrimitive)?.takeIf { it.isString }?.content,
            available = parameters?.modes.orEmpty().map(ModeParameterDto::value),
            lastUpdated = Reading.ofEpochSeconds(lastUpdated),
        )
}

private fun CapabilityDto.toToggle(): Pair<String, Toggle>? {
    val instance = parameters?.instance ?: state?.instance ?: return null
    return instance to
        Toggle(
            isOn = (state?.value as? JsonPrimitive)?.booleanOrNull,
            lastUpdated = Reading.ofEpochSeconds(lastUpdated),
        )
}

// Kept whatever it reported, which is where this differs from the three above: they key themselves
// by instance and drop a capability that names none, but a color_setting names its instance only
// inside `state` — so light-strip-02, whose state is null, has neither instance nor value. Dropping
// it would tell the tile the strip has no colour, when what it has is a colour never reported.
private fun CapabilityDto.toColorSetting(): ColorSetting = ColorSetting(
    instance = state?.instance,
    value = (state?.value as? JsonPrimitive)?.doubleOrNull,
    scenes = parameters?.colorScene?.scenes.orEmpty().map(ColorSceneDto::id),
    lastUpdated = Reading.ofEpochSeconds(lastUpdated),
    scene = (state?.value as? JsonPrimitive)?.takeIf { state?.instance == "scene" && it.isString }?.content,
)

private fun PropertyDto.toFloatProperty(): Pair<String, FloatProperty>? {
    val instance = parameters?.instance ?: state?.instance ?: return null
    return instance to
        FloatProperty(
            value = (state?.value as? JsonPrimitive)?.doubleOrNull,
            lastUpdated = Reading.ofEpochSeconds(lastUpdated),
            unit = parameters?.unit?.takeIf { it.isNotBlank() },
        )
}

// Yandex reports a percentage as 70 and a temperature as 24, and every range recorded so far has
// precision 1 — so sending 70.0 back is a difference from the vendor's own spelling for no gain.
private fun Double.asJson(): JsonPrimitive = if (isFinite() && this == floor(this)) {
    JsonPrimitive(toLong())
} else {
    JsonPrimitive(this)
}

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
