package ru.domovoy.panel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
 * This is the only tile with both kinds of bad news, and the two are drawn differently on purpose:
 * [RecuperatorTileState.error] is one device's and *fills* that tile, [groupError] is the inventory
 * call and *outlines* all five. Filling all five for a group failure would say five recuperators
 * broke; outlining the one that timed out would bury it among four that are fine.
 *
 * Its width is the one span in the panel decided by content — see [span].
 */
@Composable
fun RecuperatorTile(
    tile: RecuperatorTileState,
    now: Instant,
    modifier: Modifier = Modifier,
    groupError: String? = null,
    onToggle: (String) -> Unit = {},
) {
    TileCard(
        hue = hue(tile),
        mood = mood(tile.isOn, tile.error),
        span = span(tile),
        modifier = modifier,
        border = groupFailureBorder(groupError),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tile.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = statusLine(tile, now, groupError),
                    style = MaterialTheme.typography.bodySmall,
                )
                // Absent when the device reported neither reading, rather than a second line
                // saying "unknown" twice over.
                climateLine(tile, now)?.let { climate ->
                    Text(text = climate, style = MaterialTheme.typography.bodySmall)
                }
            }
            Switch(
                checked = tile.isOn == true,
                onCheckedChange = { onToggle(tile.id) },
                modifier = Modifier.touchable(),
            )
        }
    }
}

/**
 * The line under the name: on/off and how old that is, then the fan speed and how old *that* is.
 * Two ages rather than one for the same reason the air conditioner prints two — the datapoints are
 * timestamped separately, and on the recorded response the humidity was minutes old while the
 * switch had not moved in days.
 *
 * "offline" leads, when Tuya says so: everything after it is what the device last reported before
 * it went away, and reading it as current would be the tile's worst lie.
 */
internal fun statusLine(
    tile: RecuperatorTileState,
    now: Instant,
    /** The group's failure — the inventory call — which stops every tile from updating at once. */
    groupError: String? = null,
): String {
    val power =
        when (tile.isOn) {
            true -> "on"
            false -> "off"
            null -> "unknown"
        }
    val line =
        "$power · ${ageLabel(tile.powerLastUpdated, now)} · " +
            "${speedLabel(tile)} · ${ageLabel(tile.speedLastUpdated, now)}"
    // This tile's own failure first: it is the more specific of the two.
    val reason = tile.error ?: groupError
    val reported = if (reason == null) line else "$line · not updating: $reason"
    return if (tile.online == false) "offline · $reported" else reported
}

// "no speed" and "unknown" are different answers: the first is three booleans that all came back
// false, the second is a device that reported no speed datapoint at all.
private fun speedLabel(tile: RecuperatorTileState): String = when {
    tile.speeds.isNotEmpty() -> tile.speeds.joinToString(" + ") { it.label }
    tile.speedLastUpdated == Reading.Never -> "unknown"
    else -> "no speed"
}

/**
 * The second line: what the recuperator measures, and how old each of those two readings is. They
 * get a line of their own because they are the only values here that move on their own — the
 * humidity was 26 s old on the recorded read while the switch had not changed in three days.
 *
 * Null when the device reported neither, and the tile then has no second line at all: a row of
 * "unknown · never read · unknown · never read" says nothing the first line has not already said.
 */
internal fun climateLine(
    tile: RecuperatorTileState,
    now: Instant,
): String? {
    if (tile.temperature == null && tile.humidity == null) return null
    return "${measured(tile.temperature, CELSIUS)} · ${ageLabel(tile.temperatureLastUpdated, now)} · " +
        "${measured(tile.humidity, PERCENT)} · ${ageLabel(tile.humidityLastUpdated, now)}"
}

/**
 * The units are hardcoded because the vendor does not send them: `typeSpec.unit` is `""` for both
 * `temper` and `huimi`. They are not invented, though — docs/tuya.md records them as verified
 * against the Smart Life app showing the same device, which is where `330 = 33.0 %RH` and
 * `279 = 27.9 °C` come from. That check is the only source, and it is a weaker one than the
 * `scale` a test can assert against the recorded thing model.
 */
private const val CELSIUS = "°C"
private const val PERCENT = "%"

// Locale.ROOT, not the tablet's: the panel prints one spelling of a number, and a tablet set to
// ru-RU would otherwise render 29.3 as "29,3" on a line whose every other word is English.
private fun measured(
    value: Double?,
    unit: String,
): String = value?.let { String.format(Locale.ROOT, "%.1f %s", it, unit) } ?: "unknown"
