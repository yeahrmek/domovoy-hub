package ru.domovoy.panel

import org.junit.jupiter.api.Test
import ru.domovoy.core.Reading
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Who is a circle and who is a tile of their own, and what the one line under the circles says.
 *
 * The split is on whether the panel has any state for the bulb at all — `isOn` null — and on
 * nothing else. Staleness deliberately does not decide it: one `/v1.0/user/info` call feeds every
 * bulb in the flat, so either all of them are stale or none are, and a rule that fires on all 28 at
 * once is not a split. See docs/ui.md, "The lights group".
 */
class BulbGroupTest {
    private val now = Instant.ofEpochSecond(1_786_000_000)

    @Test
    fun `a bulb the panel has no state for leaves the group`() {
        // A circle is a claim that the panel knows whether that lamp is on. For a bulb that
        // reported nothing it does not, so it says "unknown" on a named tile instead.
        val group = bulbGroup(listOf(bulb("light-01", isOn = true), bulb("light-02", isOn = null)))

        assertEquals(listOf("light-01"), group.circles.map { it.id })
        assertEquals(listOf("light-02"), group.brokenOut.map { it.id })
    }

    @Test
    fun `a bulb that is on and one that is off both stay, however old their readings are`() {
        // Three weeks old and three minutes old are both readings; the vendor's `last_updated` says
        // when the lamp last reported, not whether anyone is still reading it.
        val bulbs =
            listOf(
                bulb("light-01", isOn = true, lastUpdated = secondsAgo(20 * 86_400)),
                bulb("light-02", isOn = false, lastUpdated = secondsAgo(180)),
            )

        val group = bulbGroup(bulbs)

        assertEquals(listOf("light-01", "light-02"), group.circles.map { it.id })
        assertTrue(group.brokenOut.isEmpty())
    }

    @Test
    fun `the group quotes the oldest reading of those that stayed, not the freshest`() {
        // The whole reason the row can carry one age: quoting the freshest would hide a bulb that
        // stopped answering a week ago behind one that reported a minute ago.
        val bulbs =
            listOf(
                bulb("light-01", isOn = true, lastUpdated = secondsAgo(60)),
                bulb("light-02", isOn = true, lastUpdated = secondsAgo(7 * 86_400)),
                bulb("light-03", isOn = false, lastUpdated = secondsAgo(3600)),
            )

        val group = bulbGroup(bulbs)

        assertEquals(Reading.At(now.minusSeconds(7 * 86_400)), group.oldest)
        assertEquals("3 lamps · 2 on · 7 d ago", bulbGroupLine(group, now, notUpdating = false, error = null))
    }

    @Test
    fun `a bulb that never reported a time stays a circle and is the oldest of all`() {
        // `isOn` and `last_updated` are different fields of the same capability: three of Коридор's
        // four bulbs report a value with a `last_updated` of 0.0. They are circles, and the line
        // has to be able to quote the Never they carry.
        val bulbs =
            listOf(
                bulb("light-04", isOn = true, lastUpdated = Reading.Never),
                bulb("light-21", isOn = true, lastUpdated = secondsAgo(7 * 86_400)),
            )

        val group = bulbGroup(bulbs)

        assertEquals(listOf("light-04", "light-21"), group.circles.map { it.id })
        assertEquals(Reading.Never, group.oldest)
        assertEquals("2 lamps · 2 on · never read", bulbGroupLine(group, now, notUpdating = false, error = null))
    }

    @Test
    fun `how many are on counts the circles and not the ones that broke out`() {
        // A bulb the panel has no state for is not "off" and is certainly not "on"; it is not in
        // the row being counted at all.
        val bulbs =
            listOf(
                bulb("light-01", isOn = true),
                bulb("light-02", isOn = false),
                bulb("light-03", isOn = null),
            )

        val group = bulbGroup(bulbs)

        assertEquals(1, group.on)
        assertEquals("2 lamps · 1 on · just now", bulbGroupLine(group, now, notUpdating = false, error = null))
    }

    @Test
    fun `a room with no bulbs has no group rather than an empty row`() {
        val group = bulbGroup(emptyList())

        assertTrue(group.circles.isEmpty())
        assertTrue(group.brokenOut.isEmpty())
        assertEquals(0, group.on)
        // No reading to quote, because there is nothing to quote it under.
        assertNull(group.oldest)
    }

    @Test
    fun `a room whose every bulb broke out has no row either`() {
        val group = bulbGroup(listOf(bulb("light-01", isOn = null), bulb("light-02", isOn = null)))

        assertTrue(group.circles.isEmpty())
        assertEquals(2, group.brokenOut.size)
        assertNull(group.oldest)
    }

    @Test
    fun `the line says the group stopped updating once for the whole row`() {
        // 28 bulbs behind one call: the row says it once instead of 28 tiles saying it each.
        val group = bulbGroup(listOf(bulb("light-01", isOn = true), bulb("light-02", isOn = false)))

        assertEquals(
            "2 lamps · 1 on · just now · not updating: HTTP 500",
            bulbGroupLine(group, now, notUpdating = true, error = "HTTP 500"),
        )
        // Stale with nothing to name: the poll simply stopped landing, and there is no error to
        // quote — see notUpdating.
        assertEquals(
            "2 lamps · 1 on · just now · not updating",
            bulbGroupLine(group, now, notUpdating = true, error = null),
        )
    }

    private fun secondsAgo(seconds: Long): Reading = Reading.At(now.minusSeconds(seconds))

    private fun bulb(
        id: String,
        isOn: Boolean?,
        lastUpdated: Reading = Reading.At(now),
    ) = BulbTileState(
        id = id,
        name = id,
        room = "Коридор",
        isOn = isOn,
        lastUpdated = lastUpdated,
        stateChangedAt = Reading.Never,
    )
}
