package ru.domovoy.core

import kotlin.math.roundToLong

/**
 * A device as the panel sees it, whichever vendor it came from.
 *
 * There is deliberately no `online` / `offline` here: `/v1.0/user/info` carries no such field for
 * any of the 41 devices it returned, so from a poll the panel can only say how old a reading is.
 * See docs/yandex.md.
 */
data class Device(
    val id: String,
    val name: String,
    /** Which tile group the device belongs to; the panel has no tile for anything else. */
    val kind: DeviceKind,
    /** Room name, not id; null when the vendor puts the device in no room. */
    val room: String?,
    /** Null when the device has no on/off capability at all. */
    val onOff: OnOff?,
    /**
     * Every numeric capability the device reported, keyed by instance — `open` on the curtain,
     * `brightness` on a bulb, `temperature` on the air conditioner. A map rather than a field per
     * device: one device carries several, and one poll is the whole house.
     */
    val ranges: Map<String, Range> = emptyMap(),
    /**
     * Every one-of-several capability the device reported, keyed by instance — `thermostat`,
     * `fan_speed` and `swing` on the air conditioner. Keyed the same way and for the same reason
     * as [ranges].
     */
    val modes: Map<String, Mode> = emptyMap(),
    /** Every on/off capability that is not *the* on/off — `ionization`, `keep_warm`, `backlight`. */
    val toggles: Map<String, Toggle> = emptyMap(),
)

/** The device types the panel has a tile for. Everything else is dropped at the vendor client. */
enum class DeviceKind {
    Bulb,
    Curtain,
    AirConditioner,
}

/**
 * An on/off capability, with both timestamps the vendor reports for it: [lastUpdated] is when the
 * value was last read, [stateChangedAt] when it last actually changed. Both are kept — which of
 * the two a tile should show is still an open question in docs/yandex.md.
 */
data class OnOff(
    val isOn: Boolean,
    val lastUpdated: Reading,
    val stateChangedAt: Reading,
)

/**
 * A numeric capability the panel can read and set: how far open the curtain is, how bright a bulb
 * is. Carries the same two timestamps as [OnOff], for the same reason.
 */
data class Range(
    /**
     * Null when the capability reported no value at all. That is *unknown*, not the bottom of the
     * range: a curtain at 0% is shut, and one that never reported is not.
     */
    val value: Double?,
    /** Null when the vendor named no bounds — the recorded response does that on a TV channel. */
    val bounds: Bounds?,
    /** As the vendor spells it, `unit.percent` or `unit.temperature.celsius`; null when blank. */
    val unit: String?,
    val lastUpdated: Reading,
    val stateChangedAt: Reading,
)

/**
 * A capability that is one of a listed set of values: which way the air conditioner is swinging,
 * how hard it is blowing. Carries the same two timestamps as [OnOff], for the same reason.
 */
data class Mode(
    /**
     * Null when the capability reported no value at all. That is *unknown*, and emphatically not
     * the first of [available]: the recorded response lists every mode ac-01 accepts while saying
     * nothing about which one is running, and "fan_only" printed on the wall would be an invention.
     */
    val current: String?,
    /** The values the device said it accepts, in the order it listed them; never hardcoded. */
    val available: List<String>,
    val lastUpdated: Reading,
    val stateChangedAt: Reading,
)

/**
 * A boolean capability that is not the device's main power: ionization, keep-warm, the backlight.
 * Carries the same two timestamps as [OnOff], for the same reason.
 */
data class Toggle(
    /** Null when the capability reported no value at all — unknown, not off. */
    val isOn: Boolean?,
    val lastUpdated: Reading,
    val stateChangedAt: Reading,
)

/** What a [Range] will accept, as the vendor reports it. */
data class Bounds(
    val min: Double,
    val max: Double,
    /** The step between two values the device accepts; `1` on every range recorded so far. */
    val precision: Double,
) {
    /**
     * The nearest value this range will accept. A slider can hand over anything, and Yandex can
     * only reject what is off the grid — which reaches the wall as a tile saying "not updating"
     * for a reason that was ours, not the vendor's.
     */
    fun snap(value: Double): Double {
        val clamped = value.coerceIn(min, max)
        if (precision <= 0.0) return clamped
        return min + ((clamped - min) / precision).roundToLong() * precision
    }
}
