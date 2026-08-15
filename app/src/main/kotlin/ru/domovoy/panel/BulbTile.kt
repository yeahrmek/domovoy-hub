package ru.domovoy.panel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
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
    Card(modifier = modifier.fillMaxWidth().padding(4.dp)) {
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

/** The bulbs of the flat, newest state first painted by whoever owns the poll timer. */
@Composable
fun BulbTileList(
    state: BulbPanelState,
    now: Instant,
    modifier: Modifier = Modifier,
    onToggle: (String) -> Unit = {},
) {
    LazyColumn(modifier = modifier) {
        // Nothing has ever been read: there is no tile to hang the error on, so it gets its own
        // line. Without this the panel would be blank after a first poll that failed.
        if (state.tiles.isEmpty() && state.error != null) {
            item {
                Text(
                    text = "Лампы: not updating: ${state.error}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        items(state.tiles, key = { it.id }) { tile ->
            BulbTile(tile = tile, now = now, error = state.error, onToggle = onToggle)
        }
    }
}
