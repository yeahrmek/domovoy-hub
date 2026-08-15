package ru.domovoy.panel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.domovoy.core.Bounds
import ru.domovoy.core.Device
import ru.domovoy.core.DeviceKind
import ru.domovoy.core.Reading
import ru.domovoy.integrations.yandex.YandexClient

/** The instance of the air conditioner's `range` capability: the temperature it is set to. */
private const val TEMPERATURE = "temperature"

/** What one air conditioner tile renders. */
data class AcTileState(
    val id: String,
    val name: String,
    val room: String?,
    /** Null when the ac reported no on/off value at all — shown as unknown, never as "off". */
    val isOn: Boolean?,
    /** When the on/off was read. */
    val powerLastUpdated: Reading,
    /** Null when the ac reported no target at all — shown as unknown, never as 16 °C. */
    val targetTemperature: Double?,
    /** What the ac will accept, as it reported it; null when it named no bounds. */
    val bounds: Bounds?,
    /** As the vendor spells it, `unit.temperature.celsius`; null when it named none. */
    val unit: String?,
    /** When the target temperature was read. */
    val temperatureLastUpdated: Reading,
)

/**
 * The air conditioner half of the panel. As with the bulbs and the curtains, one
 * `/v1.0/user/info` call is the whole house, so [error] belongs to the group rather than to a
 * tile and the tiles keep their last values.
 */
data class AcPanelState(
    val tiles: List<AcTileState> = emptyList(),
    /** Non-null when the last poll, toggle or set failed. [tiles] then hold the last known values. */
    val error: String? = null,
)

/**
 * Holds what the air conditioner tiles show.
 *
 * Nothing here fetches or schedules: [YandexPoll] reads the house once for the whole panel and
 * hands the devices to [show], so a test drives a poll directly instead of waiting for one.
 * [reread] is that same shared read, used after an action.
 *
 * The `mode` and `toggle` capabilities the flat's three units carry — `thermostat`, `fan_speed`,
 * `swing`, `ionization`, `keep_warm`, `backlight` — are parsed into the device model but are not
 * on the tile and cannot be driven from here. See docs/yandex.md.
 */
class AcTiles(
    private val client: YandexClient,
    private val reread: suspend () -> Unit,
) {
    private val mutableState = MutableStateFlow(AcPanelState())
    val state: StateFlow<AcPanelState> = mutableState.asStateFlow()

    /** What one poll read. Every device in the house arrives; the acs are picked out here. */
    fun show(devices: List<Device>) {
        mutableState.value =
            AcPanelState(
                tiles = devices.filter { it.kind == DeviceKind.AirConditioner }.map(Device::toTile),
            )
    }

    /** The poll failed. The tiles keep their last known values and ages, plus an error. */
    fun showFailure(reason: String) {
        mutableState.value = mutableState.value.copy(error = reason)
    }

    /**
     * Flips one air conditioner, then re-reads. An ac whose on/off has never reported is turned
     * *on* by the first tap: `isOn != true` rather than `!isOn`, so unknown is not read as "on".
     */
    suspend fun toggle(id: String) {
        val tile = mutableState.value.tiles.firstOrNull { it.id == id } ?: return
        client
            .setOn(id, on = tile.isOn != true)
            .onSuccess { reread() }
            .onFailure { failure -> mutableState.value = mutableState.value.copy(error = failure.describe()) }
    }

    /**
     * Sets one air conditioner's target to [celsius], then re-reads. The value is snapped to what
     * the ac said it accepts — 16..32 on all three of the flat's units — because Yandex can only
     * reject what is off the grid, and a rejected action reaches the wall as "not updating" for a
     * reason that was ours. As with the bulbs, the tile is repainted from a fresh poll rather than
     * from the action result, which promises only that Yandex took the request.
     */
    suspend fun setTemperature(
        id: String,
        celsius: Double,
    ) {
        val tile = mutableState.value.tiles.firstOrNull { it.id == id } ?: return
        client
            .setRange(id, instance = TEMPERATURE, value = tile.bounds?.snap(celsius) ?: celsius)
            .onSuccess { reread() }
            .onFailure { failure -> mutableState.value = mutableState.value.copy(error = failure.describe()) }
    }
}

private fun Device.toTile(): AcTileState {
    val temperature = ranges[TEMPERATURE]
    return AcTileState(
        id = id,
        name = name,
        room = room,
        isOn = onOff?.isOn,
        powerLastUpdated = onOff?.lastUpdated ?: Reading.Never,
        targetTemperature = temperature?.value,
        bounds = temperature?.bounds,
        unit = temperature?.unit,
        // Two ages, not one: on ac-01 the on/off was read 81 days after the temperature, and a
        // single age would have to lie about one of them.
        temperatureLastUpdated = temperature?.lastUpdated ?: Reading.Never,
    )
}
