package ru.domovoy.panel

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.time.Instant
import kotlin.math.roundToInt

/** The two `color_setting` instances the recorded response carries, on the strips and on light-21. */
private const val TEMPERATURE_K = "temperature_k"
private const val RGB = "rgb"

/**
 * One light strip: its name, whether it is on, how bright it is, how old each of those readings is,
 * and the colour it reports — which is shown and cannot be changed. When [error] is set the poll
 * behind the values failed; the tile keeps showing the last ones it had, and says so, rather than
 * blanking out or spinning.
 */
@Composable
fun LightStripTile(
    tile: LightStripTileState,
    now: Instant,
    modifier: Modifier = Modifier,
    error: String? = null,
    onToggle: (String) -> Unit = {},
    onSetBrightness: (String, Double) -> Unit = { _, _ -> },
) {
    // The colour it reports is the second line of the status slot rather than more dots on the
    // first — it carries an age of its own, and all six values in one run is a line nobody reads.
    // Which is [anatomy]'s answer now; see [colorLine].
    TileCard(
        anatomy = anatomy(tile, now, error),
        hue = hue(tile),
        mood = mood(tile.isOn, error),
        modifier = modifier,
        toggle = {
            Switch(checked = tile.isOn == true, onCheckedChange = { onToggle(tile.id) })
        },
        level = {
            val bounds = tile.bounds
            if (bounds != null) {
                // As on the curtain and the ac, the dragged value is local: the tile behind it only
                // changes on the next poll, so binding the slider straight to it drags the handle
                // back under the finger.
                var dragged by remember(tile.id) { mutableFloatStateOf(sliderStart(tile, bounds.min)) }
                SlimSlider(
                    value = dragged,
                    onValueChange = { dragged = it },
                    valueRange = bounds.min.toFloat()..bounds.max.toFloat(),
                    onValueChangeFinished = { onSetBrightness(tile.id, dragged.toDouble()) },
                    hue = hue(tile),
                )
            }
        },
    )
}

// A strip that has never reported a brightness has none to start the handle from; the bottom of its
// range is the one value that is certainly on the grid, and the line above says "unknown" anyway.
private fun sliderStart(
    tile: LightStripTileState,
    min: Double,
): Float = (tile.brightnessPercent ?: min).toFloat()

/**
 * The line under the name: on/off and how old that is, then the brightness and how old *that* is.
 * Two ages rather than one because the two capabilities are read separately — the same reason the
 * ac tile prints two — and the error if the poll failed.
 */
internal fun statusLine(
    tile: LightStripTileState,
    now: Instant,
    error: String?,
): String {
    val power =
        when (tile.isOn) {
            true -> "on"
            false -> "off"
            null -> "unknown"
        }
    // The same string the tile promotes, plus the word for a brightness nobody has reported.
    val line =
        "$power · ${ageLabel(tile.powerLastUpdated, now)} · " +
            "${promoted(tile) ?: "unknown"} · ${ageLabel(tile.brightnessLastUpdated, now)}"
    return if (error == null) line else "$line · not updating: $error"
}

/**
 * The colour line: what the strip reports, how old that reading is, and that it cannot be driven.
 * Null when the strip has no `color_setting` at all — a strip that never reported one still gets
 * the line, saying "unknown", because that is a value the panel is failing to read rather than a
 * capability the device lacks.
 *
 * "not controllable" is said on the tile on purpose. The panel has no colour action to send, and a
 * value shown next to an on/off and a slider otherwise reads as one more thing to tap.
 */
internal fun colorLine(
    tile: LightStripTileState,
    now: Instant,
): String? {
    val color = tile.color ?: return null
    return "${colorLabel(color.instance, color.value)} · ${ageLabel(color.lastUpdated, now)} · not controllable"
}

// Both shapes are from the recorded response: temperature_k on the strips, rgb on light-21. A
// packed 0xRRGGBB printed as 16777200 says nothing to anyone standing in the hallway.
private fun colorLabel(
    instance: String?,
    value: Double?,
): String {
    val reported = value ?: return "unknown"
    return when (instance) {
        TEMPERATURE_K -> "${reported.roundToInt()} K"
        RGB -> "#%06X".format(reported.roundToInt())
        else -> "unknown"
    }
}
