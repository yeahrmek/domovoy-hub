package ru.domovoy.integrations.yandex

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
import ru.domovoy.core.FloatProperty
import ru.domovoy.core.Mode
import ru.domovoy.core.OnOff
import ru.domovoy.core.Range
import ru.domovoy.core.Reading
import ru.domovoy.core.Toggle
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.floor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * The device types the panel has a tile for, matched exactly. `devices.types.light.strip` is its
 * own type rather than a sub-type Yandex would fold into `devices.types.light`, so it needs its own
 * entry here — without one the flat's two GLEDOPTO strips render as nothing at all.
 */
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

/**
 * Names both what is wrong and the one way a token gets in today. Sending an empty `Bearer` instead
 * would come back `403` and the panel would blame the OAuth scopes — see docs/yandex.md.
 *
 * **It was said on the tile and is now said in `Log`.** Every throwable that reaches a tile goes
 * through `reason` first, which has four words in it and none of them is a sentence — see
 * `docs/ui.md`, "Why a poll failed". Seventy-six characters were never going to fit a 188 dp card
 * whatever the rule was; where they *do* fit is the 753 dp group failure line at the top of Главная,
 * and giving them that line is `docs/design/panel-redesign.md` item 8.
 */
internal const val NO_TOKEN = "no Yandex token stored — set yandex.oauth.token in local.properties and reinstall"

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

private val json = Json { ignoreUnknownKeys = true }

/**
 * Reads and drives the devices of one Yandex household that the panel has a tile for.
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
     * devices of [householdId] the panel has a tile for — every kind together, told apart by
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
            .mapNotNull { device -> KINDS[device.type]?.let { device.toDevice(it, roomNames) } }
    }

    /**
     * Turns one device on or off. Success means Yandex accepted the request — the docs do not say
     * whether `DONE` waits for the device to physically change, so the caller re-reads rather than
     * painting the new value from this result alone.
     */
    suspend fun setOn(
        deviceId: String,
        on: Boolean,
    ): Result<Unit> = action(deviceId, ON_OFF, ActionStateDto("on", JsonPrimitive(on)))

    /**
     * Drives one numeric capability — the curtain's `open`, later the air conditioner's
     * `temperature` — to [value]. Nothing is clamped here: what the device accepts is on the
     * device, and the caller holds the bounds the poll reported. Re-read as for [setOn].
     */
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
                stateChangedAt = Reading.ofEpochSeconds(capability.stateChangedAt),
            )
        }
    },
    ranges = capabilities.filter { it.type == RANGE }.mapNotNull(CapabilityDto::toRange).toMap(),
    modes = capabilities.filter { it.type == MODE }.mapNotNull(CapabilityDto::toMode).toMap(),
    toggles = capabilities.filter { it.type == TOGGLE }.mapNotNull(CapabilityDto::toToggle).toMap(),
    color = capabilities.firstOrNull { it.type == COLOR_SETTING }?.toColorSetting(),
    properties = properties.filter { it.type == FLOAT_PROPERTY }.mapNotNull(PropertyDto::toFloatProperty).toMap(),
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
            stateChangedAt = Reading.ofEpochSeconds(stateChangedAt),
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
            stateChangedAt = Reading.ofEpochSeconds(stateChangedAt),
        )
}

private fun CapabilityDto.toToggle(): Pair<String, Toggle>? {
    val instance = parameters?.instance ?: state?.instance ?: return null
    return instance to
        Toggle(
            isOn = (state?.value as? JsonPrimitive)?.booleanOrNull,
            lastUpdated = Reading.ofEpochSeconds(lastUpdated),
            stateChangedAt = Reading.ofEpochSeconds(stateChangedAt),
        )
}

// Kept whatever it reported, which is where this differs from the three above: they key themselves
// by instance and drop a capability that names none, but a color_setting names its instance only
// inside `state` — so light-strip-02, whose state is null, has neither instance nor value. Dropping
// it would tell the tile the strip has no colour, when what it has is a colour never reported.
private fun CapabilityDto.toColorSetting(): ColorSetting = ColorSetting(
    instance = state?.instance,
    value = (state?.value as? JsonPrimitive)?.doubleOrNull,
    temperatureBounds = parameters?.temperatureK?.let { Bounds(it.min, it.max, precision = 1.0) },
    scenes = parameters?.colorScene?.scenes.orEmpty().map(ColorSceneDto::id),
    lastUpdated = Reading.ofEpochSeconds(lastUpdated),
    stateChangedAt = Reading.ofEpochSeconds(stateChangedAt),
)

private fun PropertyDto.toFloatProperty(): Pair<String, FloatProperty>? {
    val instance = parameters?.instance ?: state?.instance ?: return null
    return instance to
        FloatProperty(
            value = (state?.value as? JsonPrimitive)?.doubleOrNull,
            unit = parameters?.unit?.takeIf { it.isNotBlank() },
            lastUpdated = Reading.ofEpochSeconds(lastUpdated),
            stateChangedAt = Reading.ofEpochSeconds(stateChangedAt),
        )
}

// Yandex reports a percentage as 70 and a temperature as 24, and every range recorded so far has
// precision 1 — so sending 70.0 back is a difference from the vendor's own spelling for no gain.
private fun Double.asJson(): JsonPrimitive = if (isFinite() && this == floor(this)) {
    JsonPrimitive(toLong())
} else {
    JsonPrimitive(this)
}

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
