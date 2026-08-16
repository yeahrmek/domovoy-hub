package ru.domovoy.panel

import java.time.Instant
import kotlin.time.Duration

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
