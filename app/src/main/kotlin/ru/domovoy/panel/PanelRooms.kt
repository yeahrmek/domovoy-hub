package ru.domovoy.panel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import kotlin.time.Duration

/**
 * How a tab says its room has bad news. A character rather than a colour, because a colour is the
 * one thing a tile cannot be trusted to have in both themes yet — see docs/ui.md, "Theme".
 */
private const val MARK = " •"

/**
 * The whole panel: a strip of tabs, and under it the one room the selected tab names. See
 * [panelTabs] for what the tabs are and [favourites] for what is on the first of them.
 *
 * One tab's worth of tiles rather than all twelve rooms' in one scroll — the flat has 29 tiles and
 * a wall tablet shows about a screenful, so what someone walking up to it sees was, until now,
 * whichever room the last person scrolled to. It comes back to Главная by itself; that is
 * [resetAfterIdle], driven from `MainActivity`, and [selected] is the tab it resets.
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
    launchers: List<LauncherTileState>,
    now: Instant,
    /** The interval the Yandex groups are polled on, which is what makes their readings stale. */
    yandexInterval: Duration,
    /** The recuperators', which is 24 times longer and would call every one of them stale. */
    tuyaInterval: Duration,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelectTab: (Int) -> Unit = {},
    onToggleAc: (String) -> Unit = {},
    onSetTemperature: (String, Double) -> Unit = { _, _ -> },
    onSetOpen: (String, Double) -> Unit = { _, _ -> },
    onToggleStrip: (String) -> Unit = {},
    onSetBrightness: (String, Double) -> Unit = { _, _ -> },
    onToggleRecuperator: (String) -> Unit = {},
    onToggleBulb: (String) -> Unit = {},
    onOpenApp: (String) -> Unit = {},
) {
    val sections =
        roomSections(acs.tiles, curtains.tiles, strips.tiles, recuperators.tiles, bulbs.tiles, launchers)
    val errors =
        GroupErrors(
            acs = acs.error,
            curtains = curtains.error,
            strips = strips.error,
            recuperators = recuperators.error,
            bulbs = bulbs.error,
        )
    val tabs = panelTabs(sections, errors, now, yandexInterval, tuyaInterval)
    // Rooms come and go with the polls — a vendor that answered with nothing takes its rooms with
    // it — so the index is clamped rather than trusted. Out of range lands on Главная, which is
    // where the panel was heading anyway.
    val index = selected.coerceIn(0, tabs.lastIndex)
    val section = tabs[index].section
    // Every tab shares the one grid, so its scroll position has to be dropped when what is in the
    // grid changes — twice over, and both were seen on the tablet. Switching rooms without this
    // opens Спальня wherever Главная happened to be left, halfway down. And a panel that booted
    // with only its launcher tiles on Главная has 20 tiles inserted *above* them when the first
    // poll lands; a keyed grid holds the launcher in view, so the wall comes up from a
    // reboot showing the last two tiles of the list. Counting the tiles catches both, and it only
    // fires again if a device appears or disappears — which is a poll's news, and worth the top.
    val scroll = remember(index, section.tiles()) { LazyGridState() }
    Column(modifier = modifier) {
        // No edge padding. The default is 52 dp at each end, which on a wall panel reads as a gap
        // in front of Главная and nothing after Без комнаты until the strip is scrolled to it —
        // lopsided, and 52 dp of a strip that already does not fit twelve rooms. The tabs keep
        // their own internal padding, so the first title is not flush against the bezel.
        PrimaryScrollableTabRow(selectedTabIndex = index, edgePadding = 0.dp) {
            // Twelve rooms plus Главная plus Без комнаты does not fit, and the ones that scroll off
            // are the ones the room order already puts last — Ванная, Балкон, Гардероб — which are
            // switched at their own door anyway.
            tabs.forEachIndexed { position, tab ->
                Tab(
                    selected = position == index,
                    onClick = { onSelectTab(position) },
                    text = { Text(text = if (tab.marked) tab.title + MARK else tab.title) },
                )
            }
        }
        // The mosaic. Six columns against the 753 dp the wall tablet measured in portrait, which is
        // the orientation it is mounted in; the number lives in one place, [COLUMNS].
        // The span of a tile is a property of its type and not of the room it is in: anything with
        // a slider takes half the panel, anything that is a name and one line takes a third, and
        // the recuperator is the only one that asks its own content — see [span]. Halves and thirds
        // rather than a spread of widths because both divide six: a row fills, and two tiles of the
        // same kind beside each other come out the same size instead of one wrapping and the other
        // not, which is what the first pass on the tablet looked like.
        LazyVerticalGrid(columns = GridCells.Fixed(COLUMNS), state = scroll) {
            // Above everything, and only on Главная: the groups that failed before they ever had a
            // tile, which have no tile of their own to say it on and no room to be marked in. Full
            // width, because it is a sentence and not a tile.
            if (index == 0) {
                items(
                    groupFailures(acs, curtains, strips, recuperators, bulbs),
                    key = { it },
                    span = { GridItemSpan(maxLineSpan) },
                ) { failure ->
                    Text(
                        text = failure,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            items(section.acs, key = { "ac:${it.id}" }, span = { GridItemSpan(HALF_SPAN) }) { tile ->
                AcTile(
                    tile = tile,
                    now = now,
                    error = acs.error,
                    onToggle = onToggleAc,
                    onSetTemperature = onSetTemperature,
                )
            }
            items(section.curtains, key = { "curtain:${it.id}" }, span = { GridItemSpan(HALF_SPAN) }) { tile ->
                CurtainTile(tile = tile, now = now, error = curtains.error, onSetOpen = onSetOpen)
            }
            items(section.strips, key = { "strip:${it.id}" }, span = { GridItemSpan(HALF_SPAN) }) { tile ->
                LightStripTile(
                    tile = tile,
                    now = now,
                    error = strips.error,
                    onToggle = onToggleStrip,
                    onSetBrightness = onSetBrightness,
                )
            }
            // The one span decided by content rather than by type: four columns when the device
            // reports climate and has a second line to put there, two when it does not.
            items(section.recuperators, key = { "recuperator:${it.id}" }, span = { GridItemSpan(span(it)) }) { tile ->
                RecuperatorTile(
                    tile = tile,
                    now = now,
                    groupError = recuperators.error,
                    onToggle = onToggleRecuperator,
                )
            }
            // A third of the panel each until the lights group lands and they become 72 dp circles —
            // see docs/ui.md, "The lights group", which is the commit after this one.
            items(section.bulbs, key = { "bulb:${it.id}" }, span = { GridItemSpan(THIRD_SPAN) }) { tile ->
                BulbTile(tile = tile, now = now, error = bulbs.error, onToggle = onToggleBulb)
            }
            // Last in the room, and the only tiles here taking no `now`: they open another app
            // rather than showing anything the panel read, so there is no age on them to keep.
            items(
                section.launchers,
                key = { "launcher:${it.packageName}" },
                span = { GridItemSpan(THIRD_SPAN) },
            ) { tile ->
                LauncherTile(tile = tile, onOpen = onOpenApp)
            }
        }
    }
}

/** How many tiles a tab is showing — every group, since any of them can be the one that arrives. */
private fun RoomSection.tiles(): Int = acs.size + curtains.size + strips.size + recuperators.size +
    bulbs.size + launchers.size
