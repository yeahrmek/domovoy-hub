package ru.domovoy.core

import kotlin.math.roundToLong

/** Vendor-independent device state used by the panel. */
data class Device(
    val id: String,
    val name: String,
    val kind: DeviceKind,
    /** Room name; null when unplaced. */
    val room: String?,
    val onOff: OnOff?,
    /** Numeric capabilities keyed by vendor instance, such as brightness or temperature. */
    val ranges: Map<String, Range> = emptyMap(),
    val modes: Map<String, Mode> = emptyMap(),
    /** Secondary booleans such as ionization; main power belongs in [onOff]. */
    val toggles: Map<String, Toggle> = emptyMap(),
    val color: ColorSetting? = null,
    /** Read-only numeric measurements, separate from writable ranges. */
    val properties: Map<String, FloatProperty> = emptyMap(),
    /** Null means the vendor did not report reachability, rather than offline. */
    val online: Boolean? = null,
    /** Original type for newly discovered devices without dedicated controls. */
    val vendorType: String? = null,
    val events: Map<String, EventProperty> = emptyMap(),
)

/** Dedicated controls plus a read-only card for other discovered devices. */
enum class DeviceKind {
    Bulb,
    LightStrip,
    Curtain,
    AirConditioner,
    Recuperator,
    Other,
}

data class OnOff(
    val isOn: Boolean,
    val lastUpdated: Reading,
)

data class Range(
    /** Null means unknown, not the bottom of the range. */
    val value: Double?,
    val bounds: Bounds?,
    /** Vendor unit, such as `unit.percent`; null when absent. */
    val unit: String?,
    val lastUpdated: Reading,
)

data class Mode(
    /** Null means unknown; never substitute the first available option. */
    val current: String?,
    /** Values advertised by the device, in vendor order. */
    val available: List<String>,
    val lastUpdated: Reading,
)

data class Toggle(
    val isOn: Boolean?,
    val lastUpdated: Reading,
)

/** Reported light colour. Kelvin remains read-only until a physical write is verified. */
data class ColorSetting(
    /** `temperature_k`, `rgb` or a scene; null when no state was reported. */
    val instance: String?,
    /** Kelvin or packed 0xRRGGBB; null means unknown rather than black. */
    val value: Double?,
    val scenes: List<String> = emptyList(),
    val lastUpdated: Reading,
    /** Reported scene id; numeric colour values remain separate. */
    val scene: String? = null,
)

data class FloatProperty(
    val value: Double?,
    val lastUpdated: Reading,
    val unit: String? = null,
)

data class EventProperty(
    val value: String?,
    val lastUpdated: Reading,
)

/** Bounds advertised by a writable range. */
data class Bounds(
    val min: Double,
    val max: Double,
    val precision: Double,
) {
    fun snap(value: Double): Double {
        val clamped = value.coerceIn(min, max)
        if (precision <= 0.0) return clamped
        return min + ((clamped - min) / precision).roundToLong() * precision
    }
}
