package ru.domovoy.integrations.yandex

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Only the parts of `/v1.0/user/info` the bulb tile needs. The response also carries `groups`,
 * `scenarios`, `households`, `properties`, `device_info` and `quasar_info`; the parser ignores
 * unknown keys so those stay out of here until something actually reads them.
 */
@Serializable
internal data class UserInfoDto(
    val status: String? = null,
    // Yandex tells you to log this: it is the only thing their support can act on.
    @SerialName("request_id") val requestId: String? = null,
    val rooms: List<RoomDto> = emptyList(),
    val devices: List<DeviceDto> = emptyList(),
)

@Serializable
internal data class RoomDto(
    val id: String,
    val name: String,
)

@Serializable
internal data class DeviceDto(
    val id: String,
    val name: String,
    val type: String,
    @SerialName("household_id") val householdId: String? = null,
    /** Room *id*, resolved against `rooms` by the client. */
    val room: String? = null,
    val capabilities: List<CapabilityDto> = emptyList(),
)

@Serializable
internal data class CapabilityDto(
    val type: String,
    /** Absent on group capabilities, and `0.0` for a capability that has never reported. */
    @SerialName("last_updated") val lastUpdated: Double = 0.0,
    @SerialName("state_changed_at") val stateChangedAt: Double = 0.0,
    val parameters: CapabilityParametersDto? = null,
    val state: CapabilityStateDto? = null,
)

/**
 * Only the parameters a `range` needs. Every capability type puts something different in here —
 * `split` on `on_off`, `modes` on `mode`, `temperature_k` on `color_setting` — and unknown keys
 * are ignored, so one class covers them all without pretending to describe them.
 */
@Serializable
internal data class CapabilityParametersDto(
    /** The instance is here as well as in `state`, and it is the only one a stateless range has. */
    val instance: String? = null,
    val unit: String? = null,
    val range: RangeParametersDto? = null,
)

@Serializable
internal data class RangeParametersDto(
    val min: Double,
    val max: Double,
    /** `1` on every range in the recorded response; kept optional so a missing one is not fatal. */
    val precision: Double = 1.0,
)

@Serializable
internal data class CapabilityStateDto(
    val instance: String? = null,
    /** Boolean for `on_off`, a number for `range`, an object for `zigbee_node` — so it stays untyped. */
    val value: JsonElement? = null,
)

@Serializable
internal data class ActionsRequestDto(
    val devices: List<ActionDeviceDto>,
)

@Serializable
internal data class ActionDeviceDto(
    val id: String,
    val actions: List<ActionDto>,
)

@Serializable
internal data class ActionDto(
    val type: String,
    val state: ActionStateDto,
)

@Serializable
internal data class ActionStateDto(
    val instance: String,
    /** `true` for `on_off`, a number for `range` — the same field, two shapes, as Yandex has it. */
    val value: JsonPrimitive,
)

@Serializable
internal data class ActionsResponseDto(
    val status: String? = null,
    @SerialName("request_id") val requestId: String? = null,
)
