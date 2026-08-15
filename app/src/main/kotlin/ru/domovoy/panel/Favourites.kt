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
 * What the first tab shows: every tile of [FAVOURITE_ROOMS], every launcher tile, and any tile in
 * any room that is failing or stale.
 *
 * The launchers are here because the intercom is why somebody walks up to this panel at all, and
 * the failing tiles because a mark on a room's tab has to lead somewhere — a tile that stopped
 * answering appears on Главная itself, not only as a dot on a tab nobody opens.
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
    now: Instant,
    yandex: Duration,
    tuya: Duration,
): RoomSection {
    // A room's whole contents, or only what is failing or stale in it.
    fun <T> pick(
        tiles: (RoomSection) -> List<T>,
        groupError: String?,
        pulledIn: (T) -> Boolean,
    ): List<T> = sections.flatMap { section ->
        val whole = section.room in FAVOURITE_ROOMS || groupError != null
        tiles(section).filter { whole || pulledIn(it) }
    }
    return RoomSection(
        room = null,
        acs = pick({ it.acs }, errors.acs) { isStale(readings(it), now, yandex) },
        curtains = pick({ it.curtains }, errors.curtains) { isStale(readings(it), now, yandex) },
        strips = pick({ it.strips }, errors.strips) { isStale(readings(it), now, yandex) },
        // The only group whose tiles fail one at a time: reading a recuperator is a call per
        // device, so the fifth one timing out is the fifth one's news and belongs here on its own.
        recuperators =
        pick({ it.recuperators }, errors.recuperators) {
            it.error != null || isStale(readings(it), now, tuya)
        },
        bulbs = pick({ it.bulbs }, errors.bulbs) { isStale(readings(it), now, yandex) },
        launchers = sections.flatMap { it.launchers },
    )
}
