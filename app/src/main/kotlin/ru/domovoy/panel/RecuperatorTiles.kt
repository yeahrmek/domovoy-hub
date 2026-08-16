package ru.domovoy.panel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.domovoy.core.Device
import ru.domovoy.core.Reading
import ru.domovoy.integrations.tuya.TuyaClient
import java.time.Instant

/**
 * A fan speed as the recuperator reports it — three separate booleans, `speed_one` / `speed_two` /
 * `speed_three`, rather than one enumerated datapoint.
 */
enum class FanSpeed(
    internal val code: String,
    internal val label: String,
) {
    Low("speed_one", "low"),
    Medium("speed_two", "medium"),
    High("speed_three", "high"),
}

/** What one recuperator tile renders. */
data class RecuperatorTileState(
    val id: String,
    val name: String,
    /**
     * Which room the tile is shown in. Null whenever the flat has not said — Tuya's API names no
     * room for any device, so this is null for every recuperator unless [TuyaPoll] was given the
     * mapping out of `local.properties`. Null is not a hidden tile: it lands in the panel's
     * unplaced section instead. See [roomSections] and [recuperatorRooms].
     */
    val room: String?,
    /** Null when the recuperator reported no `switch` at all — shown as unknown, never as "off". */
    val isOn: Boolean?,
    /** When the `switch` was read. */
    val powerLastUpdated: Reading,
    /**
     * The speeds the device reported as *on*. Empty is a real answer — all three reported false,
     * which is a recuperator running no fan — and is told apart from "never reported" by
     * [speedLastUpdated] being [Reading.Never].
     *
     * More than one at a time is possible on the wire: the three booleans are independent and
     * whether the device enforces mutual exclusion is unverified (docs/tuya.md). The tile prints
     * what it got rather than choosing which half of the reading to believe.
     */
    val speeds: List<FanSpeed>,
    /** The newest of the three speed datapoints; [Reading.Never] when none was reported. */
    val speedLastUpdated: Reading,
    /** Degrees Celsius, to a tenth. Null when the recuperator reported none — unknown, not 0 °C. */
    val temperature: Double?,
    val temperatureLastUpdated: Reading,
    /** Relative humidity in percent, to a tenth. Null when none was reported. */
    val humidity: Double?,
    val humidityLastUpdated: Reading,
    /** What Tuya says about reachability. Null only if a device ever comes back without it. */
    val online: Boolean?,
    /**
     * Non-null when *this* recuperator's own read or command failed. Per tile, not per group,
     * because state costs one call per device: four working recuperators must not be labelled
     * "not updating" because the fifth timed out.
     */
    val error: String? = null,
)

/**
 * The recuperator half of the panel. [error] here is the group's, and covers only what fails for
 * every tile at once — the inventory call, or a missing credential. A single device's failure
 * lands on [RecuperatorTileState.error] instead.
 */
data class RecuperatorPanelState(
    val tiles: List<RecuperatorTileState> = emptyList(),
    /** Non-null when the inventory call failed. [tiles] then hold the last known values. */
    val error: String? = null,
    /**
     * When the refresh behind these tiles last got through, which is what makes the group stale.
     * Null until the first one lands. A single device's read failing does not move it — that is
     * [RecuperatorTileState.error]'s news, and the poll itself ran. See docs/ui.md, "Stale".
     */
    val lastPolledAt: Instant? = null,
)

/**
 * Holds what the recuperator tiles show.
 *
 * Nothing here schedules: [TuyaPoll] does the five-call refresh and hands the results to [show].
 * A tap re-reads only the device it touched — a refresh is five calls against a metered allowance,
 * and repainting one tile has no business spending all five.
 */
class RecuperatorTiles(
    private val client: TuyaClient,
) {
    private val mutableState = MutableStateFlow(RecuperatorPanelState())
    val state: StateFlow<RecuperatorPanelState> = mutableState.asStateFlow()

    // The last inventory, kept so a tap knows which device it is commanding and re-reading without
    // the tile state having to carry a vendor model around.
    private var known: Map<String, Device> = emptyMap()

    /**
     * What one refresh read, and when it read it. [failures] holds the devices whose own read
     * failed, by id: those tiles keep the values they last had and say why they are not moving.
     */
    fun show(
        devices: List<Device>,
        failures: Map<String, String>,
        polledAt: Instant,
    ) {
        known = devices.associateBy { it.id }
        val previous = mutableState.value.tiles.associateBy { it.id }
        mutableState.value =
            RecuperatorPanelState(
                tiles =
                devices.map { device ->
                    val failure = failures[device.id]
                    // A device that failed to read has nothing new to show, so its last tile is
                    // kept whole — values, ages and all — with the reason hung on it.
                    if (failure != null) {
                        previous[device.id]?.copy(online = device.online, error = failure)
                            ?: device.toTile(failure)
                    } else {
                        device.toTile(error = null)
                    }
                },
                lastPolledAt = polledAt,
            )
    }

    /** The inventory call failed, so nothing was read. Every tile keeps its last known values. */
    fun showFailure(reason: String) {
        mutableState.value = mutableState.value.copy(error = reason)
    }

    /**
     * Switches one recuperator, then re-reads that one device.
     *
     * The command path is unverified — see [TuyaClient.setOn] — and its answer promises only that
     * the host took the request, so the tile is repainted from the re-read and not from it. A
     * recuperator whose `switch` has never reported is turned *on* by the first tap: `isOn != true`
     * rather than `!isOn`, so unknown is not read as "on".
     */
    suspend fun toggle(id: String) {
        val tile = mutableState.value.tiles.firstOrNull { it.id == id } ?: return
        val device = known[id] ?: return
        client
            .setOn(id, on = tile.isOn != true)
            .mapCatching { client.read(device).getOrThrow() }
            .onSuccess { read -> replace(read.toTile(error = null)) }
            .onFailure { failure -> replace(tile.copy(error = failure.describe())) }
    }

    private fun replace(tile: RecuperatorTileState) {
        mutableState.value =
            mutableState.value.copy(
                tiles = mutableState.value.tiles.map { if (it.id == tile.id) tile else it },
            )
    }
}

/** The vendor's own codes for the two datapoints it only reports, and never accepts. */
private const val TEMPERATURE = "temper"
private const val HUMIDITY = "huimi"

private fun Device.toTile(error: String?): RecuperatorTileState {
    val speeds = FanSpeed.entries.filter { toggles[it.code]?.isOn == true }
    val speedReadings = FanSpeed.entries.mapNotNull { toggles[it.code]?.lastUpdated }
    val temperature = ranges[TEMPERATURE]
    val humidity = ranges[HUMIDITY]
    return RecuperatorTileState(
        id = id,
        name = name,
        room = room,
        isOn = onOff?.isOn,
        powerLastUpdated = onOff?.lastUpdated ?: Reading.Never,
        speeds = speeds,
        // The newest of the three: the speed the device is running was last confirmed then, and
        // the older two say nothing newer about it.
        speedLastUpdated = speedReadings.filterIsInstance<Reading.At>().maxByOrNull { it.instant } ?: Reading.Never,
        // Four ages on one tile, because these two move on their own and the switch does not: on
        // the recorded read the humidity was 26 s old and the switch three days.
        temperature = temperature?.value,
        temperatureLastUpdated = temperature?.lastUpdated ?: Reading.Never,
        humidity = humidity?.value,
        humidityLastUpdated = humidity?.lastUpdated ?: Reading.Never,
        online = online,
        error = error,
    )
}
