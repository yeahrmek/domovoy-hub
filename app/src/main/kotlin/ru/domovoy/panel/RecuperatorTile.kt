package ru.domovoy.panel

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.domovoy.core.Reading
import java.time.Instant
import java.util.Locale

/**
 * One recuperator: its name, power control, fan speed and climate readings.
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

/** Speed and unavailable state; power is conveyed by the switch. */
internal fun statusLine(
    tile: RecuperatorTileState,
    now: Instant,
): String {
    val offline = tile.online == false
    return listOfNotNull(
        if (offline) "Нет связи" else "Нет данных".takeIf { tile.isOn == null },
        speedLabel(tile).takeUnless {
            offline || (tile.isOn == null && tile.speeds.isEmpty() && tile.speedLastUpdated == Reading.Never)
        },
    ).joinToString(" · ")
}

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
