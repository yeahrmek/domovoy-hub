package ru.domovoy.panel

import org.junit.jupiter.api.Test
import ru.domovoy.core.Reading
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Stale is a fact about the *poll*, not about the value it read: it means the panel has stopped
 * reading, not that the flat has stopped changing. What goes in is when a group's refresh last
 * succeeded, which is the only thing that answers "are we still reading this".
 *
 * It is relative to the interval of that poll rather than a flat duration. The two intervals here
 * are the two the panel actually runs on — `POLL_INTERVAL` and `TUYA_POLL_INTERVAL` in
 * `MainActivity` — because the whole point of the rule is that a group polled every 6 minutes is
 * not called stale for being 6 minutes old.
 *
 * The second half of the file is the other side of that distinction: [ageLine], which is what a
 * *tile* prints under its name. That one takes the vendor's `last_updated` — when the device
 * reported — and answers in words, or in nothing at all when the reading is too fresh to be worth a
 * line. The two must not become one number; the file they live in is the same because the question
 * "is this worth saying" is.
 */
class StalenessTest {
    private val yandex = 15.seconds
    private val tuya = 6.minutes
    private val now = Instant.ofEpochSecond(1_786_000_000)

    @Test
    fun `a yandex group polled a minute and a half ago is fresh`() {
        // Six polls behind. Yandex is read every 15 s, so this is a panel that missed a few reads
        // over a wi-fi that came back — not one that stopped reading.
        assertFalse(isStale(secondsAgo(90), now, yandex))
    }

    @Test
    fun `a yandex group polled three minutes ago is stale`() {
        assertTrue(isStale(secondsAgo(180), now, yandex))
    }

    @Test
    fun `a tuya group polled seven minutes ago is fresh`() {
        // One poll behind, and the reason the rule is not a flat 2 minutes: at 6 minutes the
        // recuperators are *never* newer than this for long, and marking them would hang a warning
        // on five tiles that are working exactly as designed.
        assertFalse(isStale(secondsAgo(7 * 60), now, tuya))
    }

    @Test
    fun `a tuya group polled an hour ago is stale`() {
        assertTrue(isStale(secondsAgo(3600), now, tuya))
    }

    @Test
    fun `a group polled exactly eight intervals ago is still fresh`() {
        // Older *than* eight intervals, so the boundary itself is not stale — on either poll.
        assertFalse(isStale(secondsAgo(8 * 15), now, yandex))
        assertFalse(isStale(secondsAgo(8 * 6 * 60), now, tuya))
        assertTrue(isStale(secondsAgo(8 * 15 + 1), now, yandex))
        assertTrue(isStale(secondsAgo(8 * 6 * 60 + 1), now, tuya))
    }

    @Test
    fun `a group that has never been polled is stale at every interval`() {
        // Null until the first refresh lands: the panel has read nothing at all, which is exactly
        // the state a tile must not paint as current.
        assertTrue(isStale(null, now, yandex))
        assertTrue(isStale(null, now, tuya))
        assertTrue(isStale(null, now, 1.minutes))
    }

    // --- The age a tile prints ------------------------------------------------------------------
    //
    // The other half of the same distinction, and the half that reaches the words: what a *reading*
    // is worth saying about. Stale above is about the poll and is said on a room's heading; these
    // are about the vendor's own `last_updated`, which is what a tile prints under its name.

    @Test
    fun `a reading younger than an hour is not worth a line`() {
        // The whole point of the threshold. The panel reads Yandex every 15 s and Tuya every 6
        // minutes, so "3 min ago" is a value dozens of polls have confirmed — a line that says
        // nothing anyone standing in the hallway would act on, printed once per value.
        assertNull(ageLine(readingAgo(0), now))
        assertNull(ageLine(readingAgo(180), now))
        assertNull(ageLine(readingAgo(3599), now))
    }

    @Test
    fun `an hour old and past it, the tile says so once`() {
        assertEquals("1 h ago", ageLine(readingAgo(3600), now))
        assertEquals("2 h ago", ageLine(readingAgo(2 * 3600), now))
        assertEquals("23 h ago", ageLine(readingAgo(86_399), now))
        assertEquals("1 d ago", ageLine(readingAgo(86_400), now))
        assertEquals("81 d ago", ageLine(readingAgo(81 * 86_400), now))
    }

    @Test
    fun `a capability that never reported says so, whatever the clock says`() {
        // 33 of the 116 recorded capabilities are `last_updated: 0.0`. There is no age to be under
        // the threshold with, and "never read" is the thing the tile has to be able to say.
        assertEquals("never read", ageLine(Reading.Never, now))
    }

    @Test
    fun `a tile with no reading at all prints no age`() {
        // Not the same as fresh: this is a value the panel does not have, and its status line says
        // "unknown" in words. "unknown · never read" was that fact twice.
        assertNull(ageLine(reading = null, now))
        assertNull(ageLine(oldest(emptyList()), now))
    }

    @Test
    fun `the age a tile prints is the oldest of the readings behind it`() {
        // One age per tile, and the oldest of them: a tile that quoted its freshest reading would
        // hide the 81-day-old temperature behind the on/off read 90 seconds ago.
        assertEquals("81 d ago", ageLine(oldest(listOf(readingAgo(90), readingAgo(81 * 86_400))), now))
        // And it under-claims rather than over-claims: three fresh readings and one Never comes out
        // "never read", not silence.
        assertEquals("never read", ageLine(oldest(listOf(readingAgo(20), Reading.Never, readingAgo(60))), now))
        // Every one of them fresh is the case that says nothing at all.
        assertNull(ageLine(oldest(listOf(readingAgo(20), readingAgo(600))), now))
    }

    @Test
    fun `a reading stamped in the future is fresh rather than absurd`() {
        // A tablet whose clock jumped back. Same tolerance isStale takes, for the same reason.
        assertNull(ageLine(Reading.At(now.plusSeconds(3600)), now))
    }

    private fun secondsAgo(seconds: Long): Instant = now.minusSeconds(seconds)

    private fun readingAgo(seconds: Long): Reading = Reading.At(secondsAgo(seconds))
}
