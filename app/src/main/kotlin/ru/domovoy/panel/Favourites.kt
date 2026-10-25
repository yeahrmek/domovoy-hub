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
 * Home keeps favourite-room devices and launchers, plus individual failures and silently stale polls.
 * Explicit provider failures have section banners, so they do not copy every affected room here.
 * Vendor reading age does not decide membership: an unchanged value may still be polled successfully.
 * Devices retain their room membership and appear at most once within this section.
 */
fun favourites(
    sections: List<RoomSection>,
    errors: GroupErrors,
    polls: GroupPolls,
    now: Instant,
    yandex: Duration,
    tuya: Duration,
): RoomSection {
    fun <T> pick(
        tiles: (RoomSection) -> List<T>,
        silentlyStale: Boolean,
        pulledIn: (T) -> Boolean = { false },
    ): List<T> = sections.flatMap { section ->
        val whole = section.room in FAVOURITE_ROOMS || silentlyStale
        tiles(section).filter { whole || pulledIn(it) }
    }
    return RoomSection(
        room = null,
        acs = pick({ it.acs }, errors.acs == null && isStale(polls.acs, now, yandex)),
        curtains = pick({ it.curtains }, errors.curtains == null && isStale(polls.curtains, now, yandex)),
        strips = pick({ it.strips }, errors.strips == null && isStale(polls.strips, now, yandex)),
        // The only group whose tiles fail one at a time: reading a recuperator is a call per
        // device, so the fifth one timing out is the fifth one's news and belongs here on its own.
        recuperators =
        pick({ it.recuperators }, errors.recuperators == null && isStale(polls.recuperators, now, tuya)) {
            it.error != null
        },
        bulbs = pick({ it.bulbs }, errors.bulbs == null && isStale(polls.bulbs, now, yandex)),
        launchers = sections.flatMap { it.launchers },
    )
}
