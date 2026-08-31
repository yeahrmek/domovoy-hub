package ru.domovoy.panel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.domovoy.core.Bounds
import ru.domovoy.core.Device
import ru.domovoy.core.DeviceKind
import ru.domovoy.core.Mode
import ru.domovoy.core.Reading
import ru.domovoy.core.Toggle
import ru.domovoy.integrations.yandex.YandexClient
import java.time.Instant

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
    /** Room temperature measured by the AC, separate from its writable target. */
    val measuredTemperature: Double? = null,
    val measuredTemperatureLastUpdated: Reading = Reading.Never,
    /** Every mode and secondary toggle this particular unit advertised. */
    val modes: Map<String, Mode> = emptyMap(),
    val toggles: Map<String, Toggle> = emptyMap(),
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
    /** When the poll behind these tiles last succeeded; null until the first one lands. */
    val lastPolledAt: Instant? = null,
)

/**
 * Holds what the air conditioner tiles show.
 *
 * Nothing here fetches or schedules: [YandexPoll] reads the house once for the whole panel and
 * hands the devices to [show], so a test drives a poll directly instead of waiting for one.
 * [reread] is that same shared read, used after an action.
 *
 * The `mode` and `toggle` capabilities the flat's units carry are retained per device for the
 * detail sheet. Verified values are driven through [setMode] and [setToggle]; the tile itself keeps
 * only the fan shortcut that earns its limited space. See docs/yandex.md.
 */
class AcTiles(
    private val client: YandexClient,
    private val reread: suspend () -> Unit,
) {
    private val mutableState = MutableStateFlow(AcPanelState())
    val state: StateFlow<AcPanelState> = mutableState.asStateFlow()

    /**
     * What one poll read, and when it read it. Every device in the house arrives; the acs are
     * picked out here.
     */
    fun show(
        devices: List<Device>,
        polledAt: Instant,
    ) {
        mutableState.value =
            AcPanelState(
                tiles = devices.filter { it.kind == DeviceKind.AirConditioner }.map(Device::toTile),
                lastPolledAt = polledAt,
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

    /** Sets a mode only when this unit advertised both the instance and the requested value. */
    suspend fun setMode(
        id: String,
        instance: String,
        value: String,
    ) {
        val tile = mutableState.value.tiles.firstOrNull { it.id == id } ?: return
        val mode = tile.modes[instance]
        if (mode == null || value !in mode.available) {
            mutableState.value = mutableState.value.copy(error = IllegalArgumentException("unsupported mode").describe())
            return
        }
        client
            .setMode(id, instance, value)
            .onSuccess { reread() }
            .onFailure { failure -> mutableState.value = mutableState.value.copy(error = failure.describe()) }
    }

    /** Sets a secondary boolean only when this unit actually carries it. */
    suspend fun setToggle(
        id: String,
        instance: String,
        on: Boolean,
    ) {
        val tile = mutableState.value.tiles.firstOrNull { it.id == id } ?: return
        if (instance !in tile.toggles) {
            mutableState.value = mutableState.value.copy(error = IllegalArgumentException("unsupported toggle").describe())
            return
        }
        client
            .setToggle(id, instance, on)
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
        measuredTemperature = properties[TEMPERATURE]?.value,
        measuredTemperatureLastUpdated = properties[TEMPERATURE]?.lastUpdated ?: Reading.Never,
        modes = modes,
        toggles = toggles,
    )
}
