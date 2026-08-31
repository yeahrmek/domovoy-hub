package ru.domovoy.panel

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.domovoy.core.Device
import ru.domovoy.core.KnownRecuperators
import ru.domovoy.core.Reading
import ru.domovoy.integrations.tuya.TuyaClient
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

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
     * A live write confirmed the device normally makes the three booleans mutually exclusive. The
     * parser still prints every true value it receives rather than hiding an inconsistent shadow.
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
 *
 * It starts with the recuperators [remembered] from the last run rather than with nothing. Their
 * tiles carry no values — every age is "never read" — and the first successful refresh replaces
 * them. See [KnownRecuperators] for why this group, alone of the five, needs a memory.
 */
class RecuperatorTiles(
    private val client: TuyaClient,
    private val remembered: KnownRecuperators = KnownRecuperators(null),
) {
    private val mutableState = MutableStateFlow(RecuperatorPanelState())
    val state: StateFlow<RecuperatorPanelState> = mutableState.asStateFlow()

    // The last inventory, kept so a tap knows which device it is commanding and re-reading without
    // the tile state having to carry a vendor model around. Seeded from the memory, so a tile that
    // is on the wall before the first poll is one a finger can still switch.
    private var known: Map<String, Device> = emptyMap()

    init {
        val last = remembered.remembered()
        known = last.associateBy { it.id }
        // No stamp: nothing has been read. The group is stale until a refresh lands, which is what
        // marks the tab and pulls these tiles onto Главная — see docs/ui.md, "Stale".
        mutableState.value = RecuperatorPanelState(tiles = last.map { it.toTile(error = null) })
    }

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
        // Kept for the next cold start, so the wall is not empty while the first refresh of the day
        // is still failing on a Wi-Fi that has not come up.
        remembered.remember(devices)
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
     * Switches one recuperator, then re-reads that one device until the shadow says so.
     *
     * The command path is verified, but its answer promises only that the host took the request,
     * so the tile is repainted from the re-read and not from it. A
     * recuperator whose `switch` has never reported is turned *on* by the first tap: `isOn != true`
     * rather than `!isOn`, so unknown is not read as "on".
     */
    suspend fun toggle(id: String) {
        val tile = mutableState.value.tiles.firstOrNull { it.id == id } ?: return
        val device = known[id] ?: return
        val wanted = tile.isOn != true
        write(tile, device, issue = { client.setOn(id, on = wanted) }, reflected = { it.isOn == wanted })
    }

    /**
     * Selects a verified speed only while the unit is already on. The physical device ignores a
     * speed written while off, so the UI disables these buttons until a re-read confirms power.
     */
    suspend fun setSpeed(
        id: String,
        speed: FanSpeed,
    ) {
        val tile = mutableState.value.tiles.firstOrNull { it.id == id } ?: return
        val device = known[id] ?: return
        if (tile.isOn != true || tile.online == false) {
            replace(tile.copy(error = IllegalStateException("recuperator must be on").describe()))
            return
        }
        write(tile, device, issue = { client.setSpeed(id, speed.code) }, reflected = { speed in it.speeds })
    }

    /**
     * Sends one command and then reads the device it touched **until that device reports what was
     * written**, or until [CONFIRM_DELAYS] runs out.
     *
     * The single read this used to do was the bug the wall showed: **the recuperator is
     * asynchronous** — docs/tuya.md, "Live write verification", records that the unit takes a
     * command and reports it seconds later, which is why the live checks there waited 20 s between
     * a write and its read. The read fired immediately after the write therefore returns the *old*
     * shadow, and the tile was repainted with it: the fan audibly came on and the panel said "off"
     * until the next 6-minute poll, with the speed buttons disabled for that whole time because
     * [setSpeed] refuses while power is unconfirmed.
     *
     * What it does *not* do is paint what it asked for. Every repaint here is a read of the device,
     * so an unconfirmed write leaves a tile telling the truth — the device has not reported the
     * change — rather than one showing a state nothing has confirmed. The tile is repainted from
     * each read as it lands, so it flips the moment the device does rather than at the end of the
     * window.
     */
    private suspend fun write(
        tile: RecuperatorTileState,
        device: Device,
        issue: suspend () -> Result<Unit>,
        reflected: (RecuperatorTileState) -> Boolean,
    ) {
        issue().onFailure { failure ->
            replace(tile.copy(error = failure.describe()))
            return
        }
        var waits = CONFIRM_DELAYS
        while (true) {
            val read =
                client
                    .read(device)
                    .getOrElse { failure ->
                        replace(tile.copy(error = failure.describe()))
                        return
                    }.toTile(error = null)
            replace(read)
            if (reflected(read) || waits.isEmpty()) return
            delay(waits.first())
            waits = waits.drop(1)
        }
    }

    private fun replace(tile: RecuperatorTileState) {
        mutableState.value =
            mutableState.value.copy(
                tiles = mutableState.value.tiles.map { if (it.id == tile.id) tile else it },
            )
    }
}

/**
 * **How long the panel waits for a write to turn up in Tuya's shadow**: it reads at once, and then
 * again after each of these until the device reports what was asked of it.
 *
 * The window is ~30 s and is spent only when a device is slow to report. Widening between reads
 * rather than reading at a fixed cadence, because the allowance is money: a unit that reports
 * quickly — most of them, most of the time — costs the one re-read a tap has always cost, and only
 * a slow one runs to the five [CONFIRM_READS] worth. Five calls a handful of times a day is
 * nothing against a refresh's five every six minutes.
 *
 * 30 s is chosen from the vendor evidence rather than measured on this wall: docs/tuya.md's live
 * write checks used 20-second waits because six-second loops saw commands land out of order. It
 * has not been timed against a real tap, and docs/tuya.md's open questions say so.
 */
private val CONFIRM_DELAYS = listOf(2.seconds, 4.seconds, 8.seconds, 16.seconds)

/** How many reads one write can cost at most, which is what a test counts against the allowance. */
internal val CONFIRM_READS = CONFIRM_DELAYS.size + 1

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
