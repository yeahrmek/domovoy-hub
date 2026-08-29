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
    // The curtain has no switch to read a mood off, so its position is the mood — which is [paint]'s
    // answer now, out where a test reaches it, and [error] is its group's and outlines.
    // The only tile with a slider and no switch, and the anatomy says so out loud rather than by
    // leaving a slot unfilled — see [controls]. Its glyph is the one on the wall that carries state
    // rather than labelling a type: the flat's curtain says what it is doing from across the room.
    TileCard(
        anatomy = anatomy(tile, now, error),
        hue = hue(tile),
        paint = paint(tile, error),
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

/**
 * The line under the name: how far open, how old that reading is once it is old enough to be worth
 * saying, and the error if the poll failed.
 *
 * A fresh curtain says only "40% open" now — see [ageLine]. And a curtain that reported no position
 * says only "unknown": the age went with the value, because a timestamp on a reading that does not
 * exist ages nothing.
 */
internal fun statusLine(
    tile: CurtainTileState,
    now: Instant,
    error: String?,
): String = listOfNotNull(
    // The same string the tile promotes, plus the word for a curtain that has never reported.
    promoted(tile) ?: "unknown",
    ageLine(tile.lastUpdated.takeIf { tile.openPercent != null }, now),
    error?.let { "not updating: $it" },
).joinToString(" · ")
