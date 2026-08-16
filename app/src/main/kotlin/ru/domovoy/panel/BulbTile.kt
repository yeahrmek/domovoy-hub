package ru.domovoy.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

/**
 * A bulb circle: 72 dp, full round, and nothing on it but its colour. Round rather than a rounded
 * square because it is not a card — it holds no name and no line, and the only thing it says is the
 * one thing a bulb has to say. Its colour is [mood]'s answer through the same four roles every
 * other tile uses, so a failing poll paints the circles the same red it paints the tiles.
 *
 * 72 dp clears the panel's 64 dp floor on its own, so there is no [touchable] inside it.
 */
private val BULB_CIRCLE = 72.dp

/** The gutter between circles, and the grid's own 8 dp. */
private val CIRCLE_GAP = 8.dp

/**
 * The lights group: one wrapping row of circles and one line under it.
 *
 * This is what 28 third-width tiles became. The bulbs are on/off only and they are the many, so a
 * card each is what made Главная fourteen rows of lamps; who is in this row is [bulbGroup]'s answer
 * and the line is [bulbGroupLine]'s, out where a test reaches both.
 *
 * The row says "not updating" once for all of them rather than 28 times, because one
 * `/v1.0/user/info` call is behind every circle in it — see [notUpdating].
 */
@Composable
fun BulbCircles(
    group: BulbGroup,
    now: Instant,
    modifier: Modifier = Modifier,
    /** The bulb group's error, which colours every circle: the poll behind all of them failed. */
    error: String? = null,
    /** Whether that poll has stopped landing at all — said once, on the line under the row. */
    notUpdating: Boolean = false,
    onToggle: (String) -> Unit = {},
) {
    Column(modifier = modifier.padding(CIRCLE_GAP / 2)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(CIRCLE_GAP),
            verticalArrangement = Arrangement.spacedBy(CIRCLE_GAP),
        ) {
            group.circles.forEach { tile -> BulbCircle(tile = tile, error = error, onToggle = onToggle) }
        }
        Text(
            text = bulbGroupLine(group, now, notUpdating, error),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = CIRCLE_GAP),
        )
    }
}

/**
 * One circle. Its name and its state are on it as a content description and nowhere else: there is
 * no room for either at 72 dp, and the row is one thing to read rather than 28.
 */
@Composable
private fun BulbCircle(
    tile: BulbTileState,
    error: String?,
    onToggle: (String) -> Unit,
) {
    Card(
        onClick = { onToggle(tile.id) },
        modifier =
        Modifier.size(BULB_CIRCLE).semantics {
            contentDescription = "${tile.name} · ${power(tile.isOn)}"
        },
        shape = CircleShape,
        colors = tileColors(mood(tile.isOn, error)),
    ) {}
}

/** The line under the name: on/off, how old the reading is, and the error if the poll failed. */
internal fun statusLine(
    tile: BulbTileState,
    now: Instant,
    error: String?,
): String {
    val power = power(tile.isOn)
    val age = ageLabel(tile.lastUpdated, now)
    return if (error == null) "$power · $age" else "$power · $age · not updating: $error"
}

/**
 * The three words a bulb's state comes in. Shared with the circle's content description so that the
 * one place a circle can be read out says the same thing the tile's line has always said —
 * "unknown" and never "off" for a bulb that reported nothing.
 */
private fun power(isOn: Boolean?): String = when (isOn) {
    true -> "on"
    false -> "off"
    null -> "unknown"
}
