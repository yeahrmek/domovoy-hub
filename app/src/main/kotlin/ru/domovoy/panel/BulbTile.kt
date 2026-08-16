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
import java.time.Instant

/**
 * One bulb: its name, whether it is on, and how old that reading is. When [error] is set the poll
 * behind these values failed — the tile keeps showing the last value it had, and says so, rather
 * than blanking out or spinning.
 */
@Composable
fun BulbTile(
    tile: BulbTileState,
    now: Instant,
    modifier: Modifier = Modifier,
    error: String? = null,
    onToggle: (String) -> Unit = {},
) {
    TileCard(mood = mood(tile.isOn, error), span = THIRD_SPAN, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tile.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = statusLine(tile, now, error),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = tile.isOn == true,
                onCheckedChange = { onToggle(tile.id) },
                modifier = Modifier.touchable(),
            )
        }
    }
}

/** The line under the name: on/off, how old the reading is, and the error if the poll failed. */
internal fun statusLine(
    tile: BulbTileState,
    now: Instant,
    error: String?,
): String {
    val power =
        when (tile.isOn) {
            true -> "on"
            false -> "off"
            null -> "unknown"
        }
    val age = ageLabel(tile.lastUpdated, now)
    return if (error == null) "$power · $age" else "$power · $age · not updating: $error"
}
