package ru.domovoy.panel

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.domovoy.core.Reading
import java.time.Instant
import java.util.Locale

/**
 * One recuperator: its name, whether it is on, what fan speed it reported, and how old each of
 * those two readings is.
 *
 * The error lives on the tile rather than on the group, because state costs one call per device —
 * a tile that says "not updating" here means *this* recuperator, and the one next to it may be
 * perfectly current.
 *
 * This is the only tile with both kinds of bad news at once, and the two are drawn differently on
 * purpose: [RecuperatorTileState.error] is one device's and adds the red offline mark, while
 * [groupError] is the inventory call and outlines all five. Outlining the one that timed out would
 * bury it among four that are fine. Every other kind follows the same rule — see [TilePaint].
 *
 * Its width is the one span in the panel decided by content — see [span].
 */
@Composable
fun RecuperatorTile(
    tile: RecuperatorTileState,
    now: Instant,
    modifier: Modifier = Modifier,
    groupError: String? = null,
    showRoom: Boolean = false,
    pending: Boolean = false,
    /** What a tap on the card does: open this device's sheet. Null when there is none — see [AcTile]. */
    onOpen: (() -> Unit)? = null,
    onToggle: (String) -> Unit = {},
    onSetSpeed: (String, FanSpeed) -> Unit = { _, _ -> },
) {
    // The card no longer needs the span — one radius, one anatomy, one height — and the grid still
    // asks [span] for the width, which is the one width in the panel decided by content.
    // Worked out once and read twice: the card takes it, and so does the power button. A recuperator
    // that timed out keeps its last-reported power direction, but the button goes grey because the
    // panel has stopped being able to confirm it.
    val paint = paint(tile, groupError)
    TileCard(
        anatomy = anatomy(tile, now, groupError),
        paint = paint,
        modifier = modifier,
        format = TileFormat.Reading,
        showRoom = showRoom,
        pending = pending,
        onClick = onOpen,
        onAction = { onSetSpeed(tile.id, nextFanSpeed(tile)) },
        toggle = {
            TilePowerButton(
                deviceName = tile.name,
                room = tile.room,
                isOn = tile.isOn == true,
                mood = paint.mood,
                onToggle = { onToggle(tile.id) },
                enabled = !pending,
            )
        },
    )
}

/**
 * The line under the name: on/off, the fan speed and **one age for the whole tile**. The reason it
 * stopped updating is the tile's second line now — see [TileAnatomy].
 *
 * This is the tile the one-age rule was written for. It printed four of them — two here and two on
 * the climate line — and on the recorded response three were the same number: `on · 3 min ago · low
 * + medium + high · 3 min ago` over `26.4 °C · 3 min ago · 41.0 % · 3 min ago`. The four datapoints
 * really are timestamped separately, so the one printed is the **oldest** of them, the climate
 * included: the tile under-claims how fresh it is rather than quoting the humidity's 26 seconds over
 * a switch that has not moved in three days.
 *
 * **"offline" replaces the power word rather than leading a queue of echoes**, and this is the line
 * on the wall that most needed it. `offline · unknown · low + medium + high · not updating: timeout`
 * is 62 characters on a 251 dp tile that holds about twenty-four of them; it wrapped onto three
 * lines and was the longest thing on the panel. What is dropped is the echo: a device Tuya says is
 * offline is not confirming its switch or its speeds, and the panel's rule everywhere else is that
 * it does not assert what it has not read. What survives is the state, its age, and — on the line
 * below — why the panel is not reading it. The values themselves are still on
 * [RecuperatorTileState]; nothing about the device is forgotten, it is only not claimed.
 */
internal fun statusLine(
    tile: RecuperatorTileState,
    now: Instant,
): String {
    val offline = tile.online == false
    return listOfNotNull(
        if (offline) "Нет связи" else power(tile.isOn),
        speedLabel(tile).takeUnless {
            offline || (tile.isOn == null && tile.speeds.isEmpty() && tile.speedLastUpdated == Reading.Never)
        },
        ageLine(oldest(tile.readings()), now),
    ).joinToString(" · ")
}

/**
 * The readings behind everything this tile shows, on both of its lines — and only the ones it has a
 * value for, on [AcTileState]'s rule. A speed that reported nothing at all reads as "unknown" and
 * brings no age with it; three booleans that all came back false are a reading like any other and
 * bring theirs.
 */
private fun RecuperatorTileState.readings(): List<Reading> = listOfNotNull(
    powerLastUpdated.takeIf { isOn != null },
    speedLastUpdated.takeIf { it != Reading.Never || speeds.isNotEmpty() },
    temperatureLastUpdated.takeIf { temperature != null },
    humidityLastUpdated.takeIf { humidity != null },
)

// "no speed" and "unknown" are different answers: the first is three booleans that all came back
// false, the second is a device that reported no speed datapoint at all. Shared with the tile's
// sheet, which prints the same speeds with an age of their own — see [TileSheet].
internal fun speedLabel(tile: RecuperatorTileState): String = when {
    tile.speeds.isNotEmpty() -> tile.speeds.joinToString(" + ") { capabilityLabel(it.label) }
    tile.speedLastUpdated == Reading.Never -> "Нет данных"
    else -> "Скорость не задана"
}

/** Humidity supports the promoted temperature without repeating it. */
internal fun climateLine(tile: RecuperatorTileState): String? {
    if (tile.temperature == null && tile.humidity == null) return null
    return tile.humidity?.let { "Влажность ${measured(it, PERCENT_SIGN)}" }
}

/**
 * The units are hardcoded because the vendor does not send them: `typeSpec.unit` is `""` for both
 * `temper` and `huimi`. They are not invented, though — docs/tuya.md records them as verified
 * against the Smart Life app showing the same device, which is where `330 = 33.0 %RH` and
 * `279 = 27.9 °C` come from. That check is the only source, and it is a weaker one than the
 * `scale` a test can assert against the recorded thing model.
 *
 * Reachable from [promoted], which promotes the temperature this line ages — one formatter for the
 * pair, so the two cannot come out rounded differently.
 */
internal const val DEGREES = "°C"

internal const val PERCENT_SIGN = "%"

// Locale.ROOT, not the tablet's: the panel prints one spelling of a number, and a tablet set to
// ru-RU would otherwise render 29.3 as "29,3" on a line whose every other word is English.
internal fun measured(
    value: Double?,
    unit: String,
): String = value?.let { String.format(Locale.ROOT, "%.1f %s", it, unit) } ?: "Нет данных"
