package ru.domovoy.panel

import ru.domovoy.core.Reading
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/**
 * How many of its own poll intervals a group may go unread before the panel calls it stale.
 *
 * Eight is a guess and is written down as one. The number that matters is how long a device can be
 * quiet before somebody would want to know, and nobody has measured that on this wall — see
 * docs/ui.md, "Open". What is *not* a guess is that it is a multiple rather than a duration:
 * a flat threshold short enough to catch a dead Yandex poll (every 15 s) marks the recuperators
 * (Tuya, every 6 minutes) permanently, and the panel would hang a warning on five tiles that are
 * working exactly as designed.
 */
private const val INTERVALS_BEFORE_STALE = 8

/**
 * Whether a group has stopped being read: no refresh has succeeded within eight of its own poll
 * intervals — 2 minutes for the Yandex groups, 48 minutes for the recuperators.
 *
 * **Stale is about the poll, not about the value.** What goes in is when the group's refresh last
 * got through, stamped by [YandexPoll] or [TuyaPoll]. It is deliberately not the vendor's
 * `last_updated` that every tile prints: that says when the *device* last reported, so a bulb
 * switched on three weeks ago and untouched since carries a three-week-old timestamp while every
 * poll has read it perfectly well — and 33 of the 116 recorded capabilities have never reported at
 * all. Judging health on those calls a steady flat broken. See docs/ui.md, "Stale".
 *
 * A null [lastPolledAt] is stale at every interval: the panel has read nothing yet, which is
 * exactly the state a tile must not paint as current.
 *
 * Tied to the interval rather than to a constant of its own so that retuning either cadence carries
 * staleness with it instead of quietly falling out of step.
 */
fun isStale(
    lastPolledAt: Instant?,
    now: Instant,
    interval: Duration,
): Boolean {
    if (lastPolledAt == null) return true
    // Milliseconds, not nanoseconds: this is a wall clock read a few times a minute, and this way a
    // stamp dated in the future — a tablet whose clock jumped back — is fresh rather than absurd.
    return now.toEpochMilli() - lastPolledAt.toEpochMilli() > (interval * INTERVALS_BEFORE_STALE).inWholeMilliseconds
}

/**
 * A polled group's bad news, and the whole of it: its last refresh failed, or none has succeeded
 * in eight intervals. Both mean the same thing to whoever is standing at the wall — the tiles in
 * front of them are not being updated — and both are facts about the call, not about any one tile.
 *
 * The recuperators' per-device failures are not here. Reading them costs one call each, so a
 * single device's timeout lands on [RecuperatorTileState.error] and four working units are not
 * labelled for the fifth.
 */
internal fun notUpdating(
    error: String?,
    lastPolledAt: Instant?,
    now: Instant,
    interval: Duration,
): Boolean = error != null || isStale(lastPolledAt, now, interval)

/**
 * **How old a reading has to be before a tile bothers to print it.** Under this, the tile says
 * nothing about its age at all.
 *
 * The wall printed an age per field and the fields shared them: `on · 3 min ago · low + medium +
 * high · 3 min ago` over `26.4 °C · 3 min ago · 41.0 % · 3 min ago` — four timestamps on one tile,
 * three of them the same number — while the app this panel is judged against prints one short grey
 * line under a name, or nothing. CLAUDE.md requires a tile to say how old its state is. It does not
 * require it to say so once per value, and it does not require it to say so when there is nothing
 * worth saying.
 *
 * **An hour is a guess and is written down as one**, the same way [INTERVALS_BEFORE_STALE] is. It is
 * the line between a value that is what the flat is doing and one that is history: Yandex is read
 * every 15 s and Tuya every 6 minutes, so a reading younger than an hour has been confirmed by
 * dozens of polls, and "3 min ago" beside it is a line nobody standing in the hallway acts on. Past
 * it the tile speaks, in hours and then in days — which is why [ageLine] has no wording below an
 * hour to give.
 *
 * _It is deliberately not [isStale]'s threshold._ That one is about whether the panel is still
 * reading, is counted in poll intervals, and is said on the room's heading. This one is about the
 * value on the card: the vendor's own `last_updated`, which says when the *device* reported. The two
 * numbers answer different questions and must not become one — see docs/ui.md, "Stale".
 */
private val WORTH_SAYING = 1.hours

/**
 * **Whether a reading has crossed [WORTH_SAYING] into history** — the same line [ageLine] speaks at,
 * asked as a question instead of answered as a string, so a tile can decide what to *do* about an
 * old value and not only how to describe it. The two must stay one line: a tile that promoted a
 * value it was simultaneously printing an age for would be arguing with itself on its own card.
 *
 * [Reading.Never] is past the line by definition. A null reading is not: that is a tile with no
 * value to age rather than one holding an old one — see [oldest].
 *
 * _It is not [isStale]._ That one is about whether the panel is still reading and is counted in poll
 * intervals; this one is about how old the vendor says the value itself is. See docs/ui.md, "Stale".
 */
internal fun isHistory(
    reading: Reading?,
    now: Instant,
): Boolean = when (reading) {
    null -> false
    Reading.Never -> true
    // Milliseconds and not Duration.between, for the reason above: a stamp dated in the future —
    // a tablet whose clock jumped back — comes out fresh rather than absurd.
    is Reading.At -> (now.toEpochMilli() - reading.instant.toEpochMilli()).milliseconds >= WORTH_SAYING
}

/**
 * Whether [reading] is strictly newer than [than], with [Reading.Never] older than any instant and
 * not newer than itself: a capability that has never reported cannot have overtaken one that has.
 *
 * Ordering two readings against each other rather than against the clock, which is what the curtain
 * needs — see [confirmedPosition] — and why this takes no `now`.
 */
internal fun isNewer(
    reading: Reading,
    than: Reading,
): Boolean = when {
    reading !is Reading.At -> false
    than !is Reading.At -> true
    else -> reading.instant.isAfter(than.instant)
}

/**
 * The oldest of a set of readings, with [Reading.Never] older than any instant: a capability that
 * has never reported is the least fresh thing a tile can be holding, and 33 of the 116 recorded
 * capabilities are exactly that.
 *
 * **Null when the set is empty**, which is a tile with no reading to age rather than a tile that is
 * fresh — a bulb the panel has no value for at all, whose status line says "unknown" and has said so
 * since the first commit. Nothing is lost by not ageing it: "unknown · never read" is the same fact
 * twice, and this is the file that decides such a line is not worth its width.
 *
 * Shared rather than one tile's, because **one age per tile is the rule now** and every kind has to
 * take the oldest of what it is showing: the air conditioner over its two capabilities, the strip
 * over three, the recuperator over four, the lights group over a room's worth of lamps. It was the
 * group's private helper until this commit, which is why it reads as though it were about bulbs.
 */
internal fun oldest(readings: List<Reading>): Reading? = when {
    readings.isEmpty() -> null
    readings.any { it == Reading.Never } -> Reading.Never
    else -> Reading.At(readings.filterIsInstance<Reading.At>().minOf { it.instant })
}

/**
 * **The one age a tile prints**, in the words it prints it in — or null when it has nothing to say:
 * no reading at all, or one young enough that [WORTH_SAYING] holds its tongue.
 *
 * A reading that never reported comes back as "never read" whatever the clock says; formatting its
 * `0.0` as a date would put *1 Jan 1970* on the wall.
 *
 * Milliseconds and not `Duration.between`, for [isStale]'s reason: this is a wall clock read a few
 * times a minute, and a stamp dated in the future — a tablet whose clock jumped back — comes out
 * fresh and silent rather than absurd.
 */
internal fun ageLine(
    reading: Reading?,
    now: Instant,
): String? = when (reading) {
    null -> null
    Reading.Never -> "never read"
    is Reading.At -> {
        val age = (now.toEpochMilli() - reading.instant.toEpochMilli()).milliseconds
        when {
            age < WORTH_SAYING -> null
            age < 1.days -> "${age.inWholeHours} h ago"
            else -> "${age.inWholeDays} d ago"
        }
    }
}
