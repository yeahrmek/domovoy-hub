package ru.domovoy.panel

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertFalse
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

    private fun secondsAgo(seconds: Long): Instant = now.minusSeconds(seconds)
}
