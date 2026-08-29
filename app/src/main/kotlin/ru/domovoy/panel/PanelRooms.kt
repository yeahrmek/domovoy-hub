package ru.domovoy.panel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.LocalContentColor
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
 * How a tab says its room has bad news: a `•` after the title, and the title in the error colour.
 *
 * Commit 1 wrote this as a character *rather than* a colour, because there was no palette to trust
 * in both themes — true then, and untrue since commit 5 wrote both schemes out. So the colour is
 * here now. The dot stays, and the two together are the point rather than a leftover:
 *
 * - Colour alone is not a signal everyone can perceive, and this dot is the panel's only word that
 *   a room has gone quiet — the room itself is behind a tab nobody has opened.
 * - Samsung's blue light filter is on permanently on this tablet and tints the whole screen warm,
 *   which is exactly what erodes a red against a neutral. That has already bitten once here: the
 *   bulbs' lit and unlit lamps composited to two browns told apart by lightness (docs/ui.md,
 *   "Icons"). The answer there was the same — let the shape carry the state and the colour
 *   reinforce it.
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
    // When each group was last read, which is what says whether it is still being read at all. The
    // four Yandex groups carry the same instant — one call feeds them — and the recuperators their
    // own; they are passed separately anyway, because a group that stopped polling on its own is
    // exactly what a shared "last read" would hide.
    val polls =
        GroupPolls(
            acs = acs.lastPolledAt,
            curtains = curtains.lastPolledAt,
            strips = strips.lastPolledAt,
            recuperators = recuperators.lastPolledAt,
            bulbs = bulbs.lastPolledAt,
        )
    val tabs = panelTabs(sections, errors, polls, now, yandexInterval, tuyaInterval)
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
    // Who is a circle and who is a tile of their own. Decided out here rather than in the grid so
    // that the row and the tiles it did not take are drawn from one answer to the question.
    val lights = bulbGroup(section.bulbs)
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
                // A marked room's title is the error colour whether or not its tab is the open one,
                // and that costs the selection nothing — which is the reason it is safe to do.
                // Material's `Tab` defaults *both* content colours to the strip's own, so on this
                // panel "which tab is open" has only ever been said by the indicator under it, never
                // by the label. The indicator is untouched. A tab that is selected and marked
                // therefore says both things at once, in two places, rather than one of them
                // quietly winning: an error-coloured title with the selection bar still under it.
                // [LocalContentColor] rather than naming a role for the unmarked case, so that an
                // ordinary tab is exactly what the strip would have drawn on its own.
                val label = if (tab.marked) MaterialTheme.colorScheme.error else LocalContentColor.current
                Tab(
                    selected = position == index,
                    onClick = { onSelectTab(position) },
                    text = { Text(text = if (tab.marked) tab.title + MARK else tab.title) },
                    selectedContentColor = label,
                    unselectedContentColor = label,
                    // [MIN_TOUCH], because a tab is the one thing on this wall a finger can miss:
                    // Material sizes a text-only tab at 48 dp and the row takes its height from
                    // its tallest tab, so raising the tab raises the strip and the indicator
                    // stays on the row's bottom edge with it. A floor rather than a fixed height,
                    // the same shape as [touchable] — 64 dp is the smallest a tab may be, not the
                    // only size it may have.
                    modifier = Modifier.heightIn(min = MIN_TOUCH),
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
            // The lights group. The bulbs the panel has a value for are the many and are on/off
            // only, so they are one wrapping row of 72 dp circles under one line instead of 28
            // cards — see docs/ui.md, "The lights group". The few it has no value for come first,
            // as named third-width tiles: those are the ones worth reading.
            items(lights.brokenOut, key = { "bulb:${it.id}" }, span = { GridItemSpan(THIRD_SPAN) }) { tile ->
                BulbTile(tile = tile, now = now, error = bulbs.error, onToggle = onToggleBulb)
            }
            // A room whose bulbs all broke out has no row, and neither has a room with no bulbs.
            if (lights.circles.isNotEmpty()) {
                item(key = "bulbs", span = { GridItemSpan(maxLineSpan) }) {
                    BulbCircles(
                        group = lights,
                        now = now,
                        error = bulbs.error,
                        // Said once for the row rather than 28 times: one call is behind all of
                        // them, so a poll that stopped landing stopped for the whole group.
                        notUpdating = notUpdating(bulbs.error, bulbs.lastPolledAt, now, yandexInterval),
                        onToggle = onToggleBulb,
                    )
                }
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
