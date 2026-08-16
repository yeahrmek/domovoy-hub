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
 * When each polled group's refresh last succeeded, `null` until the first one lands.
 *
 * Named per group for the same reason [GroupErrors] is, and it is the same five groups: what a
 * lookup by name would allow is a group quietly missing from the map, which reads as "polled just
 * now" and is a room that never says it has gone quiet.
 *
 * The four Yandex groups are fed by one `/v1.0/user/info` call and so carry the same instant; the
 * recuperators are their own call on their own timer and do not.
 */
data class GroupPolls(
    val acs: Instant?,
    val curtains: Instant?,
    val strips: Instant?,
    val recuperators: Instant?,
    val bulbs: Instant?,
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
    polls: GroupPolls,
    now: Instant,
    yandex: Duration,
    tuya: Duration,
): List<PanelTab> {
    val unplaced = sections.firstOrNull { it.room == null } ?: RoomSection(room = null)
    return buildList {
        // Главная carries no mark of its own: what a mark would point at is already on it.
        add(PanelTab(HOME, favourites(sections, errors, polls, now, yandex, tuya), marked = false))
        sections.filter { it.room != null }.forEach { section ->
            add(PanelTab(checkNotNull(section.room), section, marked(section, errors, polls, now, yandex, tuya)))
        }
        add(PanelTab(UNPLACED, unplaced, marked(unplaced, errors, polls, now, yandex, tuya)))
    }
}

/**
 * When a room's tab carries bad news: a group it holds tiles of has stopped updating — the call
 * failed, or nothing has read it in eight intervals — or one of its recuperators failed on its own.
 *
 * Any of its groups rather than all of them, because a room holding a bulb and a recuperator is
 * fed by two different calls on two different timers: either one going quiet is a room showing
 * values nobody is confirming, and the tab is the only place that says so from Главная.
 *
 * A room with nothing polled in it is never marked, and the check is which tiles it holds: the
 * коридор with only the интерком's launcher tile has no group behind it to have stopped, and
 * marking it would put a warning on every panel — including before the first refresh lands.
 */
private fun marked(
    section: RoomSection,
    errors: GroupErrors,
    polls: GroupPolls,
    now: Instant,
    yandex: Duration,
    tuya: Duration,
): Boolean = (section.acs.isNotEmpty() && notUpdating(errors.acs, polls.acs, now, yandex)) ||
    (section.curtains.isNotEmpty() && notUpdating(errors.curtains, polls.curtains, now, yandex)) ||
    (section.strips.isNotEmpty() && notUpdating(errors.strips, polls.strips, now, yandex)) ||
    (section.bulbs.isNotEmpty() && notUpdating(errors.bulbs, polls.bulbs, now, yandex)) ||
    (section.recuperators.isNotEmpty() && notUpdating(errors.recuperators, polls.recuperators, now, tuya)) ||
    section.recuperators.any { it.error != null }
