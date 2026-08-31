package ru.domovoy.panel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ru.domovoy.core.Reading
import java.time.Instant

/**
 * One air conditioner: its name, whether it is on, what it is set to, and how old each of those
 * two readings is. When [error] is set the poll behind them failed — the tile keeps showing the
 * last values it had, and says so, rather than blanking out or spinning.
 */
@Composable
fun AcTile(
    tile: AcTileState,
    now: Instant,
    modifier: Modifier = Modifier,
    error: String? = null,
    /**
     * What a tap on the card does: open this device's sheet. Null when nothing is behind the tap —
     * the tile is then not clickable at all, rather than clickable and dead. See [DeviceSheet].
     */
    onOpen: (() -> Unit)? = null,
    onToggle: (String) -> Unit = {},
    onSetTemperature: (String, Double) -> Unit = { _, _ -> },
    onSetMode: (String, String, String) -> Unit = { _, _, _ -> },
) {
    // The words, the art and which controls this tile offers, all from one pure function — see
    // [TileAnatomy]. What is left here is the two things a data class cannot hold: the power button and
    // the slider, which carry callbacks and a drag of their own.
    // [error] is the *group's* — one Yandex call feeds every ac in the flat — so it outlines this
    // tile rather than filling it. See [TilePaint]. Worked out once and read twice: the card takes
    // it, and so does the power button, whose colour is the tile's second on-mark.
    val paint = paint(tile, error)
    TileCard(
        anatomy = anatomy(tile, now, error),
        paint = paint,
        modifier = modifier,
        onClick = onOpen,
        onAction = {
            nextFanMode(tile)?.let { next -> onSetMode(tile.id, "fan_speed", next) }
        },
        toggle = {
            TilePowerButton(
                isOn = tile.isOn == true,
                mood = paint.mood,
                onToggle = { onToggle(tile.id) },
            )
        },
        level = {
            val bounds = tile.bounds
            if (bounds != null) {
                // As on the curtain, the dragged value is local: the tile behind it only changes
                // on the next poll, so binding the slider straight to it drags the handle back
                // under the finger.
                var dragged by remember(tile.id) { mutableFloatStateOf(sliderStart(tile, bounds.min)) }
                SlimSlider(
                    value = dragged,
                    onValueChange = { dragged = it },
                    valueRange = bounds.min.toFloat()..bounds.max.toFloat(),
                    onValueChangeFinished = { onSetTemperature(tile.id, dragged.toDouble()) },
                )
            }
        },
    )
}

// An ac that has never reported a target has none to start the handle from; the bottom of its
// range is the one value that is certainly on the grid, and the line above says "unknown" anyway.
private fun sliderStart(
    tile: AcTileState,
    min: Double,
): Float = (tile.targetTemperature ?: min).toFloat()

/**
 * The line under the name: on/off, the target and **one age**. It no longer carries the reason a
 * poll failed — that is the tile's second line now, for every kind at once; see [TileAnatomy].
 *
 * It printed two ages — one per capability — because on `ac-01` they are 81 days apart and neither
 * could speak for the other. They still cannot, so the one printed is the **older**: the tile
 * under-claims its freshness rather than over-claiming it, which is the direction every other
 * refusal on this wall already goes. What is gone is the second timestamp and the pair of dots
 * around it; what "81 d ago" means has not changed.
 */
internal fun statusLine(
    tile: AcTileState,
    now: Instant,
): String {
    val power =
        when (tile.isOn) {
            true -> "on"
            false -> "off"
            null -> "unknown"
        }
    return listOfNotNull(
        power,
        // The same string the tile promotes, plus the word for a target nobody has reported. One
        // formatter for both, so the value at the top of the tile and the value being aged
        // underneath it cannot disagree.
        promoted(tile) ?: "unknown",
        ageLine(oldest(tile.readings()), now),
    ).joinToString(" · ")
}

/**
 * The readings behind the two values this tile shows — **and only the ones it has a value for**. A
 * capability that reported nothing is printed as "unknown", which already says everything the panel
 * knows about it; ageing it as well would spend the tile's one age on the thing it does not have,
 * and on `ac-01` that is an 81-day timestamp hung on a temperature nobody sent.
 */
private fun AcTileState.readings(): List<Reading> = listOfNotNull(
    powerLastUpdated.takeIf { isOn != null },
    temperatureLastUpdated.takeIf { targetTemperature != null },
)
