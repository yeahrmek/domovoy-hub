package ru.domovoy.panel

import java.time.Instant
import kotlin.time.Duration

/**
 * The rooms Главная holds whole, and the reason there is no settings screen behind them: these are
 * the two rooms switched on the way in and on the way out of a flat whose panel hangs in the first
 * of them. Like [ROOM_ORDER] this is a list with a hallway behind it, not a preference.
 */
private val FAVOURITE_ROOMS = listOf("Коридор", "Зал")

/**
 * What the first tab shows: every tile of [FAVOURITE_ROOMS], every launcher tile, and every tile
 * anywhere whose group has stopped updating — see [notUpdating].
 *
 * The launchers are here because the intercom is why somebody walks up to this panel at all, and
 * the not-updating tiles because a mark on a room's tab has to lead somewhere — a tile the panel
 * has stopped reading appears on Главная itself, not only as a dot on a tab nobody opens.
 *
 * What is *not* pulled in is a tile whose own reading is old. That age is the vendor's
 * `last_updated`, which says when the device reported and not whether anyone is still reading it;
 * a lamp untouched for three weeks would otherwise live on Главная for ever. See docs/ui.md,
 * "Stale".
 *
 * The section comes back with `room = null`: Главная is not a room, and the tiles on it are still
 * in the rooms [roomSections] put them in. It is named by its tab, in [panelTabs].
 *
 * Nothing is duplicated by construction — a tile is in exactly one of [sections] — so the коридор's
 * launcher and the коридор's stale bulb each come through once, whichever rule caught them.
 */
fun favourites(
    sections: List<RoomSection>,
    errors: GroupErrors,
    polls: GroupPolls,
    now: Instant,
    yandex: Duration,
    tuya: Duration,
): RoomSection {
    // A room's whole contents — because it is a favourite, or because the group feeding it has
    // stopped updating and every tile of it is showing a value nobody is confirming — or, for the
    // recuperators, only the tiles that failed on their own.
    fun <T> pick(
        tiles: (RoomSection) -> List<T>,
        groupNotUpdating: Boolean,
        pulledIn: (T) -> Boolean = { false },
    ): List<T> = sections.flatMap { section ->
        val whole = section.room in FAVOURITE_ROOMS || groupNotUpdating
        tiles(section).filter { whole || pulledIn(it) }
    }
    return RoomSection(
        room = null,
        acs = pick({ it.acs }, notUpdating(errors.acs, polls.acs, now, yandex)),
        curtains = pick({ it.curtains }, notUpdating(errors.curtains, polls.curtains, now, yandex)),
        strips = pick({ it.strips }, notUpdating(errors.strips, polls.strips, now, yandex)),
        // The only group whose tiles fail one at a time: reading a recuperator is a call per
        // device, so the fifth one timing out is the fifth one's news and belongs here on its own.
        recuperators =
        pick({ it.recuperators }, notUpdating(errors.recuperators, polls.recuperators, now, tuya)) {
            it.error != null
        },
        bulbs = pick({ it.bulbs }, notUpdating(errors.bulbs, polls.bulbs, now, yandex)),
        launchers = sections.flatMap { it.launchers },
    )
}
