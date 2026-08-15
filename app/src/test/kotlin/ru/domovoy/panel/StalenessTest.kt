package ru.domovoy.panel

import org.junit.jupiter.api.Test
import ru.domovoy.core.Reading
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Stale is relative to the interval of the poll that produced the reading, not a flat duration.
 * The two intervals here are the two the panel actually runs on — `POLL_INTERVAL` and
 * `TUYA_POLL_INTERVAL` in `MainActivity` — because the whole point of the rule is that a group
 * polled every 6 minutes is not called stale for being 6 minutes old.
 */
class StalenessTest {
    private val yandex = 15.seconds
    private val tuya = 6.minutes
    private val now = Instant.ofEpochSecond(1_786_000_000)

    @Test
    fun `a yandex reading a minute and a half old is fresh`() {
        // Six polls behind. Yandex is read every 15 s, so this is a bulb that missed a few reads
        // over a wi-fi that came back — not one that stopped answering.
        assertFalse(isStale(secondsAgo(90), now, yandex))
    }

    @Test
    fun `a yandex reading three minutes old is stale`() {
        assertTrue(isStale(secondsAgo(180), now, yandex))
    }

    @Test
    fun `a tuya reading seven minutes old is fresh`() {
        // One poll behind, and the reason the rule is not a flat 2 minutes: at 6 minutes a
        // recuperator is *never* newer than this for long, and marking it would hang a warning on
        // five tiles that are working exactly as designed.
        assertFalse(isStale(secondsAgo(7 * 60), now, tuya))
    }

    @Test
    fun `a tuya reading an hour old is stale`() {
        assertTrue(isStale(secondsAgo(3600), now, tuya))
    }

    @Test
    fun `a reading exactly eight intervals old is still fresh`() {
        // Older *than* eight intervals, so the boundary itself is not stale — on either group.
        assertFalse(isStale(secondsAgo(8 * 15), now, yandex))
        assertFalse(isStale(secondsAgo(8 * 6 * 60), now, tuya))
        assertTrue(isStale(secondsAgo(8 * 15 + 1), now, yandex))
        assertTrue(isStale(secondsAgo(8 * 6 * 60 + 1), now, tuya))
    }

    @Test
    fun `never read is stale at every interval`() {
        // 33 of the 116 recorded capabilities have never reported. A tile showing a value nobody
        // ever confirmed is the case this rule exists for, whatever the cadence behind it.
        assertTrue(isStale(Reading.Never, now, yandex))
        assertTrue(isStale(Reading.Never, now, tuya))
        assertTrue(isStale(Reading.Never, now, 1.minutes))
    }

    private fun secondsAgo(seconds: Long) = Reading.At(now.minusSeconds(seconds))
}
