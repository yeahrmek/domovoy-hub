package ru.domovoy.panel

import java.time.Instant
import kotlin.time.Duration

/** The first tab, and the one the panel comes back to on its own. See [resetAfterIdle]. */
private const val HOME = "Главная"

/**
 * What the tab holding the roomless tiles is called. Not a room name and not pretending to be one:
 * under it are the devices the panel refuses to drop because no vendor placed them, and the
 * launcher tiles that belong to no room in the first place.
 */
internal const val UNPLACED = "Без комнаты"

/** One tab: what the strip calls it, what is under it, and whether it carries bad news. */
data class PanelTab(
    val title: String,
    val section: RoomSection,
    /**
     * Shown on the strip itself, so it is visible from Главная without opening the room. Without
     * it the tabs hide eleven rooms and Спальня can be dead for a day behind a panel that looks
     * fine from the hallway.
     */
    val marked: Boolean,
)

/**
 * Which polled groups' last read failed. Five nullable strings rather than a lookup by name so
 * that a group can only be asked about by the name it has in the code — the tab mark and Главная
 * both key off this, and a group silently missing from a map is a room that never says it is dead.
 *
 * A recuperator's *own* failure is not here: it is on [RecuperatorTileState.error], because
 * reading them costs one call per device and four working ones must not be labelled for the fifth.
 */
data class GroupErrors(
    val acs: String? = null,
    val curtains: String? = null,
    val strips: String? = null,
    val recuperators: String? = null,
    val bulbs: String? = null,
)

/**
 * The tab strip: Главная, then one tab per section exactly as [roomSections] ordered them, then the
 * roomless one last.
 *
 * Nothing here re-sorts or drops a section — room membership and room order are decided in
 * [roomSections] and are consumed whole. The one thing this adds at the ends is that both Главная
 * and Без комнаты are always present: the first because it is the panel's home, the second because
 * a device falling off the wall for want of a room is the bug that section exists to prevent, and
 * an empty panel is exactly when that would happen quietly.
 */
fun panelTabs(
    sections: List<RoomSection>,
    errors: GroupErrors,
    now: Instant,
    yandex: Duration,
    tuya: Duration,
): List<PanelTab> {
    val unplaced = sections.firstOrNull { it.room == null } ?: RoomSection(room = null)
    return buildList {
        // Главная carries no mark of its own: what a mark would point at is already on it.
        add(PanelTab(HOME, favourites(sections, errors, now, yandex, tuya), marked = false))
        sections.filter { it.room != null }.forEach { section ->
            add(PanelTab(checkNotNull(section.room), section, marked(section, errors, now, yandex, tuya)))
        }
        add(PanelTab(UNPLACED, unplaced, marked(unplaced, errors, now, yandex, tuya)))
    }
}

/**
 * When a room's tab carries bad news: its group's poll failed, or every reading in it is stale.
 *
 * Every reading rather than any one of them, because one bulb that stopped answering is that
 * bulb's news — it is pulled onto Главная by [favourites] and says so on its own tile. The mark is
 * for a room that has gone quiet as a whole.
 *
 * A room with nothing to age is never marked: the коридор holding only the интерком's launcher tile
 * would otherwise satisfy "every reading is stale" with no readings at all.
 */
private fun marked(
    section: RoomSection,
    errors: GroupErrors,
    now: Instant,
    yandex: Duration,
    tuya: Duration,
): Boolean {
    val failed =
        (section.acs.isNotEmpty() && errors.acs != null) ||
            (section.curtains.isNotEmpty() && errors.curtains != null) ||
            (section.strips.isNotEmpty() && errors.strips != null) ||
            (section.bulbs.isNotEmpty() && errors.bulbs != null) ||
            (section.recuperators.isNotEmpty() && errors.recuperators != null) ||
            section.recuperators.any { it.error != null }
    if (failed) return true
    val ages = readings(section, yandex, tuya)
    return ages.isNotEmpty() && ages.all { (reading, interval) -> isStale(reading, now, interval) }
}
