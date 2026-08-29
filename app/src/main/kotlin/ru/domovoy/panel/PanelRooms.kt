package ru.domovoy.panel

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import kotlin.time.Duration

/**
 * How a heading says its room has bad news: a `•` after the title, and the title in the error
 * colour.
 *
 * Commit 1 wrote this as a character *rather than* a colour, because there was no palette to trust
 * in both themes — true then, and untrue since commit 5 wrote both schemes out. So the colour is
 * here now. The dot stays, and the two together are the point rather than a leftover:
 *
 * - Colour alone is not a signal everyone can perceive, and this dot is the panel's only word that
 *   a room has gone quiet.
 * - Samsung's blue light filter tints the whole screen warm when it is on, which is exactly what
 *   erodes a red against a neutral. That has already bitten once here: the bulbs' lit and unlit
 *   lamps composited to two browns told apart by lightness (docs/ui.md, "Icons"). The answer there
 *   was the same — let the shape carry the state and the colour reinforce it.
 */
private const val MARK = " •"

/**
 * Above a heading, which is the gap that says a new room has started. Larger than the one under it:
 * a heading belongs to the tiles after it, and equal gaps would leave it floating between two rooms.
 */
private val HEADING_TOP = 24.dp

/** Under a heading, before the first row of its tiles. */
private val HEADING_BOTTOM = 4.dp

/**
 * The heading's own indent, matching where a tile's text starts: a tile pads itself by 4 dp against
 * the gutter and its contents by 12 more, so 16 dp puts the room name on the same left edge as the
 * names of the tiles under it.
 */
private val HEADING_INDENT = 16.dp

/**
 * The whole panel: every room down one scroll, each behind its own heading. See [panelHeadings] for
 * what the sections are and [favourites] for what is on the first of them.
 *
 * **This was a tab strip until now, and the strip was the wrong shape twice over.** It held fourteen
 * rooms across 753 dp and could not: `Гардеробная` was clipped mid-word at the right edge and
 * Ванная, Балкон and Гардероб were off the end entirely, so the mark rule — "a room says its own bad
 * news from Главная" — was false for the last third of the flat. And measured off the same capture,
 * content stopped at 563 dp of a 1205 dp screen: every tile crammed into the top half with 53 % of
 * the wall empty under it. A horizontal strip was the wrong answer to a vertical problem, and this
 * is `docs/design/panel-redesign.md` item 9 answered rather than patched.
 *
 * **Главная keeps its job.** It is the first section rather than the first tab: pulling the failing
 * and the stale to the top of a fourteen-room scroll is what makes the scroll bearable, and is what
 * the tab marks stood in for. Its tiles appear twice — once here and once in the room they are in —
 * which was true of the strip too and is the point rather than a duplication.
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
    modifier: Modifier = Modifier,
    /**
     * Where the wall is scrolled to. Hoisted because the idle reset drives it: after two minutes
     * with no touch the panel goes back to the top, which is where Главная is. See [resetAfterIdle].
     */
    scroll: LazyGridState = rememberLazyGridState(),
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
    val headings = panelHeadings(sections, errors, polls, now, yandexInterval, tuyaInterval)
    // **Puts the wall back at the top when the first poll lands, and leaves it alone otherwise.**
    //
    // The bug this replaces: the grid's state used to be keyed on the tile count, so *any* device
    // appearing or disappearing threw the scroll position away — under the finger of whoever was
    // reading Спальня at the time. Only one of the two things that key was doing is worth keeping.
    // A tablet that rebooted into a Wi-Fi that was not up yet comes up holding nothing but its
    // launcher tiles; when the first refresh lands, twenty tiles are inserted *above* them, and a
    // keyed grid holds the launcher in view — so the wall would come up from a reboot showing the
    // last two tiles of the list, which is what a wall panel must not do.
    //
    // So: the transition from nothing polled to something polled, once, and nothing else. A device
    // arriving on a wall that already has tiles on it is somebody's poll landing while they are
    // reading, and it leaves them where they are. The other event that returns the panel to the top
    // is the idle reset — two minutes of nobody touching it, driven from `MainActivity`; see
    // [resetAfterIdle]. Neither of the two can fire under a hand.
    //
    // The launcher tiles are left out of the count on purpose: nothing polls them, they are on the
    // wall from the first frame, and counting them would mean a rebooted panel never sees this
    // transition at all.
    val polled = sections.sumOf { it.tileCount() - it.launchers.size } > 0
    var wasPolled by remember { mutableStateOf(polled) }
    LaunchedEffect(polled) {
        if (polled && !wasPolled) scroll.scrollToItem(0)
        wasPolled = polled
    }
    // Which rooms have their lamps open, by heading — the one piece of state the wall holds that is
    // nobody's reading. A set rather than a single room, because opening Спальня's lamps is not a
    // reason to close the ones somebody left open in Коридор.
    //
    // `remember` and not `rememberSaveable`: this is the panel's only state that a person put there
    // with a finger, and a tablet that rebooted at 04:00 should come up closed, showing the counts,
    // like a panel nobody has touched. The idle reset does not close them either — it scrolls to the
    // top, and an open group eleven sections down is out of sight rather than in the way.
    var openLamps by remember { mutableStateOf(emptySet<String>()) }
    // The mosaic. Twelve columns against the 753 dp the wall tablet measured in portrait, which is
    // the orientation it is mounted in; the number lives in one place, [COLUMNS].
    // The span of a tile is a property of its type and not of the room it is in: anything with a
    // slider takes a third of the panel, anything that is a name and a status line takes a quarter,
    // and the recuperator is the only one that asks its own content — see [span]. Thirds and
    // quarters rather than a spread of widths because both divide twelve: a row fills instead of
    // trailing dead cells. It was halves and thirds until the tile anatomy landed, and half a wall
    // for one air conditioner was a phone's two-column proportion drawn at wall size.
    LazyVerticalGrid(columns = GridCells.Fixed(COLUMNS), state = scroll, modifier = modifier) {
        // Above everything, including the first heading: the groups that failed before they ever
        // had a tile, which have no tile of their own to say it on and no room to be marked in.
        // Full width, because it is a sentence and not a tile.
        items(
            groupFailures(acs, curtains, strips, recuperators, bulbs),
            key = { "failure:$it" },
            span = { GridItemSpan(maxLineSpan) },
        ) { failure ->
            Text(
                text = failure,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp),
            )
        }
        headings.forEach { heading ->
            val section = heading.section
            // Every key is prefixed with the section it is in, because a tile can genuinely be in
            // two places at once — Главная holds the коридор's tiles and so does Коридор — and a
            // `LazyVerticalGrid` given the same key twice throws.
            val room = heading.title
            item(key = "heading:$room", span = { GridItemSpan(maxLineSpan) }) {
                SectionHeading(title = room, marked = heading.marked)
            }
            items(section.acs, key = { "$room/ac:${it.id}" }, span = { GridItemSpan(WIDE_SPAN) }) { tile ->
                AcTile(
                    tile = tile,
                    now = now,
                    error = acs.error,
                    onToggle = onToggleAc,
                    onSetTemperature = onSetTemperature,
                )
            }
            items(section.curtains, key = { "$room/curtain:${it.id}" }, span = { GridItemSpan(WIDE_SPAN) }) { tile ->
                CurtainTile(tile = tile, now = now, error = curtains.error, onSetOpen = onSetOpen)
            }
            items(section.strips, key = { "$room/strip:${it.id}" }, span = { GridItemSpan(WIDE_SPAN) }) { tile ->
                LightStripTile(
                    tile = tile,
                    now = now,
                    error = strips.error,
                    onToggle = onToggleStrip,
                    onSetBrightness = onSetBrightness,
                )
            }
            // The one span decided by content rather than by type: the wider column when the device
            // has a second line to put there — a climate reading, or the reason it stopped
            // updating — and the narrow one when it has neither. See [span].
            items(
                section.recuperators,
                key = { "$room/recuperator:${it.id}" },
                span = { GridItemSpan(span(it)) },
            ) { tile ->
                RecuperatorTile(
                    tile = tile,
                    now = now,
                    groupError = recuperators.error,
                    onToggle = onToggleRecuperator,
                )
            }
            // The lights group. The bulbs the panel has a value for are the many and are on/off
            // only, so they are one tile saying how many there are and how many are lit rather than
            // 28 cards — see docs/ui.md, "The lights group". The few it has no value for come
            // first, as named quarter-width tiles: those are the ones worth reading. Asked once per
            // section, so that a room's group tile and the tiles it did not take come from one
            // answer.
            val group = bulbGroup(section.bulbs)
            items(group.brokenOut, key = { "$room/bulb:${it.id}" }, span = { GridItemSpan(NARROW_SPAN) }) { tile ->
                BulbTile(tile = tile, now = now, error = bulbs.error, onToggle = onToggleBulb)
            }
            // A room whose bulbs all broke out has no group tile, and neither has a room with no
            // bulbs at all.
            if (group.lamps.isNotEmpty()) {
                val open = room in openLamps
                item(key = "$room/lamps", span = { GridItemSpan(NARROW_SPAN) }) {
                    BulbGroupTile(
                        group = group,
                        now = now,
                        error = bulbs.error,
                        // Said once for the group rather than 28 times: one call is behind all of
                        // them, so a poll that stopped landing stopped for the whole group.
                        notUpdating = notUpdating(bulbs.error, bulbs.lastPolledAt, now, yandexInterval),
                        open = open,
                        onOpen = { openLamps = if (open) openLamps - room else openLamps + room },
                    )
                }
                // What the tap opens: the room's lamps as ordinary tiles, each with its name, its
                // own age and its own switch — the thing the row of discs could never say. They
                // follow the group tile in the grid rather than replacing it, so the count and the
                // one age stay on the wall while the seven are open.
                if (open) {
                    items(
                        group.lamps,
                        key = { "$room/lamp:${it.id}" },
                        span = { GridItemSpan(NARROW_SPAN) },
                    ) { tile ->
                        BulbTile(tile = tile, now = now, error = bulbs.error, onToggle = onToggleBulb)
                    }
                }
            }
            // Last in the room, and the only tiles here taking no `now`: they open another app
            // rather than showing anything the panel read, so there is no age on them to keep.
            items(
                section.launchers,
                key = { "$room/launcher:${it.packageName}" },
                span = { GridItemSpan(NARROW_SPAN) },
            ) { tile ->
                LauncherTile(tile = tile, onOpen = onOpenApp)
            }
        }
    }
}

/**
 * A room's name, and the largest thing on the wall.
 *
 * `displayMedium` — 52sp on this panel's scale — puts it a step above the promoted value a tile
 * carries, which is what makes the scroll navigable from across the hallway: the thing you are
 * looking for when you walk up to a fourteen-room panel is the room, and only then the reading. The
 * reference app does the same and for the phone's version of the same reason.
 *
 * The mark, when the room has bad news, is [MARK] and the error colour together — the rule the tab
 * strip carried, moved to where it cannot scroll out of sight. An unmarked heading takes
 * [LocalContentColor] rather than naming a role, so it is exactly the colour the surface under it
 * would have given it.
 */
@Composable
internal fun SectionHeading(
    title: String,
    marked: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = if (marked) title + MARK else title,
        style = MaterialTheme.typography.displayMedium,
        // Bold rather than the scale's own weight: this is the one line on the wall that is read
        // before anything else on it, and 52sp of regular weight next to a 44sp promoted value in
        // a tile reads as the same emphasis at two different sizes.
        fontWeight = FontWeight.Bold,
        color = if (marked) MaterialTheme.colorScheme.error else LocalContentColor.current,
        modifier =
        modifier.padding(
            start = HEADING_INDENT,
            end = HEADING_INDENT,
            top = HEADING_TOP,
            bottom = HEADING_BOTTOM,
        ),
    )
}
