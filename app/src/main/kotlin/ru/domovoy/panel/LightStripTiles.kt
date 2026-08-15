package ru.domovoy.panel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.domovoy.core.Bounds
import ru.domovoy.core.ColorSetting
import ru.domovoy.core.Device
import ru.domovoy.core.DeviceKind
import ru.domovoy.core.Reading
import ru.domovoy.integrations.yandex.YandexClient

/** The instance of the strip's `range` capability: how bright it is, in percent. */
private const val BRIGHTNESS = "brightness"

/** What one light strip tile renders. */
data class LightStripTileState(
    val id: String,
    val name: String,
    val room: String?,
    /** Null when the strip reported no on/off value at all — shown as unknown, never as "off". */
    val isOn: Boolean?,
    /** When the on/off was read. */
    val powerLastUpdated: Reading,
    /** Null when the strip reported no brightness at all — shown as unknown, never as dimmest. */
    val brightnessPercent: Double?,
    /** What the strip will accept, as it reported it; `1..100` on both of the flat's. */
    val bounds: Bounds?,
    /** As the vendor spells it, `unit.percent`; null when it named none. */
    val unit: String?,
    /** When the brightness was read. */
    val brightnessLastUpdated: Reading,
    /**
     * The colour the strip reports, shown but not driveable: the panel has no colour action to
     * send, so the tile says so rather than offering a control that does nothing. Null when the
     * strip has no `color_setting` capability at all — which is not the same as one that has never
     * reported, and the flat has one of each.
     */
    val color: ColorSetting?,
)

/**
 * The light strip half of the panel. As with the other groups, one `/v1.0/user/info` call is the
 * whole house, so [error] belongs to the group rather than to a tile and the tiles keep their
 * last values.
 */
data class LightStripPanelState(
    val tiles: List<LightStripTileState> = emptyList(),
    /** Non-null when the last poll, toggle or set failed. [tiles] then hold the last known values. */
    val error: String? = null,
)

/**
 * Holds what the light strip tiles show.
 *
 * Nothing here fetches or schedules: [YandexPoll] reads the house once for the whole panel and
 * hands the devices to [show], so a test drives a poll directly instead of waiting for one.
 * [reread] is that same shared read, used after an action.
 *
 * The `color_setting` both strips carry is parsed into the device model and printed on the tile,
 * but cannot be driven from here: there is no `setColor` on the client, and writing one means first
 * knowing what a colour action body looks like for this device. See docs/yandex.md.
 */
class LightStripTiles(
    private val client: YandexClient,
    private val reread: suspend () -> Unit,
) {
    private val mutableState = MutableStateFlow(LightStripPanelState())
    val state: StateFlow<LightStripPanelState> = mutableState.asStateFlow()

    /** What one poll read. Every device in the house arrives; the strips are picked out here. */
    fun show(devices: List<Device>) {
        mutableState.value =
            LightStripPanelState(
                tiles = devices.filter { it.kind == DeviceKind.LightStrip }.map(Device::toTile),
            )
    }

    /** The poll failed. The tiles keep their last known values and ages, plus an error. */
    fun showFailure(reason: String) {
        mutableState.value = mutableState.value.copy(error = reason)
    }

    /**
     * Flips one strip, then re-reads. As on the air conditioner, a strip whose on/off has never
     * reported is turned *on* by the first tap: `isOn != true` rather than `!isOn`, so unknown is
     * not read as "on".
     */
    suspend fun toggle(id: String) {
        val tile = mutableState.value.tiles.firstOrNull { it.id == id } ?: return
        client
            .setOn(id, on = tile.isOn != true)
            .onSuccess { reread() }
            .onFailure { failure -> mutableState.value = mutableState.value.copy(error = failure.describe()) }
    }

    /**
     * Sets one strip's brightness to [percent], then re-reads. The value is snapped to what the
     * strip said it accepts — `1..100`, so a slider dragged to the bottom hands over a 0 the device
     * never offered — for the same reason the curtain's position is snapped: Yandex can only reject
     * what is off the grid, and a rejected action reaches the wall as "not updating" for a reason
     * that was ours. The tile is repainted from a fresh poll, not from the action result.
     */
    suspend fun setBrightness(
        id: String,
        percent: Double,
    ) {
        val tile = mutableState.value.tiles.firstOrNull { it.id == id } ?: return
        client
            .setRange(id, instance = BRIGHTNESS, value = tile.bounds?.snap(percent) ?: percent)
            .onSuccess { reread() }
            .onFailure { failure -> mutableState.value = mutableState.value.copy(error = failure.describe()) }
    }
}

private fun Device.toTile(): LightStripTileState {
    val brightness = ranges[BRIGHTNESS]
    return LightStripTileState(
        id = id,
        name = name,
        room = room,
        isOn = onOff?.isOn,
        powerLastUpdated = onOff?.lastUpdated ?: Reading.Never,
        brightnessPercent = brightness?.value,
        bounds = brightness?.bounds,
        unit = brightness?.unit,
        // Two ages, as on the air conditioner: the on/off and the brightness are separate readings
        // and one age for both would have to lie about one of them. On the flat's two strips every
        // capability carries last_updated 0.0, so both come out "never read".
        brightnessLastUpdated = brightness?.lastUpdated ?: Reading.Never,
        color = color,
    )
}
