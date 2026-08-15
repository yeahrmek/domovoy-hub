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
)

/** The device types the panel has a tile for. Everything else is dropped at the vendor client. */
enum class DeviceKind {
    Bulb,
    Curtain,
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
