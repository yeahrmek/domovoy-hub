package ru.domovoy.panel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.time.Instant

/**
 * One curtain: its name, how far open it is, and how old that reading is. When [error] is set the
 * poll behind the position failed — the tile keeps showing the last position it had, and says so,
 * rather than blanking out or spinning.
 */
@Composable
fun CurtainTile(
    tile: CurtainTileState,
    now: Instant,
    modifier: Modifier = Modifier,
    error: String? = null,
    onSetOpen: (String, Double) -> Unit = { _, _ -> },
) {
    // The curtain has no switch to read a mood off, so its position is the mood: open at all is on,
    // fully shut is off, and never reported is unknown — which is the same three answers the
    // status line above it already gives, in the same order.
    // The only tile with a slider and no switch, and the anatomy says so out loud rather than by
    // leaving a slot unfilled — see [controls]. Its glyph is the one on the wall that carries state
    // rather than labelling a type: the flat's curtain says what it is doing from across the room.
    TileCard(
        anatomy = anatomy(tile, now, error),
        hue = hue(tile),
        mood = mood(tile.openPercent?.let { it > 0 }, error),
        modifier = modifier,
        level = {
            val bounds = tile.bounds
            if (bounds != null) {
                // The dragged position is local: the tile behind it only changes on the next poll,
                // so binding the slider straight to it would drag the handle back under the finger.
                var dragged by remember(tile.id) { mutableFloatStateOf(sliderStart(tile, bounds.min)) }
                SlimSlider(
                    value = dragged,
                    onValueChange = { dragged = it },
                    valueRange = bounds.min.toFloat()..bounds.max.toFloat(),
                    onValueChangeFinished = { onSetOpen(tile.id, dragged.toDouble()) },
                    hue = hue(tile),
                )
            }
        },
    )
}

// A curtain that has never reported has no position to start the handle from; the bottom of its
// range is the one value that is certainly on the grid, and the line above says "unknown" anyway.
private fun sliderStart(
    tile: CurtainTileState,
    min: Double,
): Float = (tile.openPercent ?: min).toFloat()

/** The line under the name: how far open, how old the reading is, and the error if the poll failed. */
internal fun statusLine(
    tile: CurtainTileState,
    now: Instant,
    error: String?,
): String {
    // The same string the tile promotes, plus the word for a curtain that has never reported.
    val position = promoted(tile) ?: "unknown"
    val age = ageLabel(tile.lastUpdated, now)
    return if (error == null) "$position · $age" else "$position · $age · not updating: $error"
}
