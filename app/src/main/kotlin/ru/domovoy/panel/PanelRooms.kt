package ru.domovoy.panel

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant

/**
 * What the section holding the unplaced devices is called. Not a room name and not pretending to
 * be one: whatever is under it is on the wall because the panel refuses to drop a device it cannot
 * place, and the heading says exactly that much.
 */
private const val UNPLACED = "Без комнаты"

/**
 * The whole panel, one section per room. See [roomSections] for what goes where and in what order.
 *
 * One [LazyColumn] for everything rather than a column of five lists: the flat has 29 tiles and a
 * wall tablet shows about a screenful, so the panel is what scrolls — the bulbs no longer being
 * squeezed into whatever height the four groups above them left over.
 *
 * The tiles themselves are the same composables as before and show the same things; their group's
 * error still reaches every one of them, because a poll that failed failed for all of them at once.
 */
@Composable
fun PanelRooms(
    acs: AcPanelState,
    curtains: CurtainPanelState,
    strips: LightStripPanelState,
    recuperators: RecuperatorPanelState,
    bulbs: BulbPanelState,
    now: Instant,
    modifier: Modifier = Modifier,
    onToggleAc: (String) -> Unit = {},
    onSetTemperature: (String, Double) -> Unit = { _, _ -> },
    onSetOpen: (String, Double) -> Unit = { _, _ -> },
    onToggleStrip: (String) -> Unit = {},
    onSetBrightness: (String, Double) -> Unit = { _, _ -> },
    onToggleRecuperator: (String) -> Unit = {},
    onToggleBulb: (String) -> Unit = {},
) {
    val sections = roomSections(acs.tiles, curtains.tiles, strips.tiles, recuperators.tiles, bulbs.tiles)
    LazyColumn(modifier = modifier) {
        // Above every room: the groups that failed before they ever had a tile, which have nothing
        // of their own on the wall to say it on.
        items(groupFailures(acs, curtains, strips, recuperators, bulbs), key = { it }) { failure ->
            Text(
                text = failure,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp),
            )
        }
        sections.forEach { section ->
            item(key = "room:${section.room}") {
                Text(
                    text = section.room ?: UNPLACED,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(section.acs, key = { "ac:${it.id}" }) { tile ->
                AcTile(
                    tile = tile,
                    now = now,
                    error = acs.error,
                    onToggle = onToggleAc,
                    onSetTemperature = onSetTemperature,
                )
            }
            items(section.curtains, key = { "curtain:${it.id}" }) { tile ->
                CurtainTile(tile = tile, now = now, error = curtains.error, onSetOpen = onSetOpen)
            }
            items(section.strips, key = { "strip:${it.id}" }) { tile ->
                LightStripTile(
                    tile = tile,
                    now = now,
                    error = strips.error,
                    onToggle = onToggleStrip,
                    onSetBrightness = onSetBrightness,
                )
            }
            items(section.recuperators, key = { "recuperator:${it.id}" }) { tile ->
                RecuperatorTile(
                    tile = tile,
                    now = now,
                    groupError = recuperators.error,
                    onToggle = onToggleRecuperator,
                )
            }
            items(section.bulbs, key = { "bulb:${it.id}" }) { tile ->
                BulbTile(tile = tile, now = now, error = bulbs.error, onToggle = onToggleBulb)
            }
        }
    }
}
