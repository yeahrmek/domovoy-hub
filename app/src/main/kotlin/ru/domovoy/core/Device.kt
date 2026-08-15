package ru.domovoy.core

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
    /** Room name, not id; null when the vendor puts the device in no room. */
    val room: String?,
    /** Null when the device has no on/off capability at all. */
    val onOff: OnOff?,
)

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
