package ru.domovoy.panel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    onToggle: (String) -> Unit = {},
    onSetTemperature: (String, Double) -> Unit = { _, _ -> },
) {
    TileCard(hue = hue(tile), mood = mood(tile.isOn, error), span = HALF_SPAN, modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    TileHeading(glyph = glyph(tile), name = tile.name, span = HALF_SPAN)
                    // The target is what somebody walking past reads without stopping, so it is set
                    // at display size rather than buried in the status line, which keeps saying how
                    // old it is. Which value that is is [promoted]'s answer, and it is null — no
                    // line at all rather than "unknown" at display size — for an ac that has never
                    // reported a target.
                    PromotedValue(promoted(tile))
                    Text(
                        text = statusLine(tile, now, error),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = tile.isOn == true,
                    onCheckedChange = { onToggle(tile.id) },
                    modifier = Modifier.touchable(),
                )
            }
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
                    hue = hue(tile),
                )
            }
        }
    }
}

// An ac that has never reported a target has none to start the handle from; the bottom of its
// range is the one value that is certainly on the grid, and the line above says "unknown" anyway.
private fun sliderStart(
    tile: AcTileState,
    min: Double,
): Float = (tile.targetTemperature ?: min).toFloat()

/**
 * The line under the name: on/off and how old that is, then the target and how old *that* is.
 * Two ages rather than one because the two capabilities are read separately — on ac-01 they are
 * 81 days apart — and the error if the poll failed.
 */
internal fun statusLine(
    tile: AcTileState,
    now: Instant,
    error: String?,
): String {
    val power =
        when (tile.isOn) {
            true -> "on"
            false -> "off"
            null -> "unknown"
        }
    // The same string the tile promotes, plus the word for a target nobody has reported. One
    // formatter for both, so the value at the top of the tile and the value being aged underneath
    // it cannot disagree.
    val line =
        "$power · ${ageLabel(tile.powerLastUpdated, now)} · " +
            "${promoted(tile) ?: "unknown"} · ${ageLabel(tile.temperatureLastUpdated, now)}"
    return if (error == null) line else "$line · not updating: $error"
}
