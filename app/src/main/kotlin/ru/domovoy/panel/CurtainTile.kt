package ru.domovoy.panel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import kotlin.math.roundToInt

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
    Card(modifier = modifier.fillMaxWidth().padding(4.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(tile.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = statusLine(tile, now, error),
                style = MaterialTheme.typography.bodySmall,
            )
            val bounds = tile.bounds
            if (bounds != null) {
                // The dragged position is local: the tile behind it only changes on the next poll,
                // so binding the slider straight to it would drag the handle back under the finger.
                var dragged by remember(tile.id) { mutableFloatStateOf(sliderStart(tile, bounds.min)) }
                Slider(
                    value = dragged,
                    onValueChange = { dragged = it },
                    valueRange = bounds.min.toFloat()..bounds.max.toFloat(),
                    onValueChangeFinished = { onSetOpen(tile.id, dragged.toDouble()) },
                )
            }
        }
    }
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
    val position = tile.openPercent?.let { "${it.roundToInt()}% open" } ?: "unknown"
    val age = ageLabel(tile.lastUpdated, now)
    return if (error == null) "$position · $age" else "$position · $age · not updating: $error"
}
