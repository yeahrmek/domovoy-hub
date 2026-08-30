package ru.domovoy.panel

import org.junit.jupiter.api.Test
import ru.domovoy.core.Reading
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Who is behind a room's group tile and who is a tile of their own, and what that group tile says.
 *
 * The split is on whether the panel has any state for the bulb at all — `isOn` null — and on
 * nothing else. Staleness deliberately does not decide it: one `/v1.0/user/info` call feeds every
 * bulb in the flat, so either all of them are stale or none are, and a rule that fires on all 28 at
 * once is not a split. See docs/ui.md, "The lights group".
 */
class BulbGroupTest {
    private val now = Instant.ofEpochSecond(1_786_000_000)

    @Test
    fun `a bulb the panel has no state for stays out of the group`() {
        // The group tile is a claim that the panel knows whether each of those lamps is on. For a
        // bulb that reported nothing it does not, so it says "unknown" on a named tile instead.
        val group = bulbGroup(listOf(bulb("light-01", isOn = true), bulb("light-02", isOn = null)))

        assertEquals(listOf("light-01"), group.lamps.map { it.id })
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

        assertEquals(listOf("light-01", "light-02"), group.lamps.map { it.id })
        assertTrue(group.brokenOut.isEmpty())
    }

    @Test
    fun `one lamp is one lamp, and the tile says so in the singular`() {
        // The smallest group there is. It still draws as a group tile rather than as a named lamp:
        // who is behind the tile is the isOn split and not a count, so a room with one reporting
        // bulb reads like every other room and gains a lamp without changing shape.
        val group = bulbGroup(listOf(bulb("light-01", isOn = true)))

        assertEquals("1 lamp", lampCount(group))
        assertEquals("1 on", promoted(group))
        assertEquals("1 on", bulbGroupLine(group, now))
    }

    @Test
    fun `seven lamps are one tile that says how many and how many are on`() {
        // The row this replaces was seven identical amber discs: to know there were seven you
        // counted them, and to know which was which you could not. Both numbers are words now.
        val lamps = (1..7).map { bulb("light-0$it", isOn = it <= 5) }

        val group = bulbGroup(lamps)

        assertEquals(7, group.lamps.size)
        assertEquals("7 lamps", lampCount(group))
        assertEquals("5 on", promoted(group))
        assertEquals("5 on", bulbGroupLine(group, now))
    }

    @Test
    fun `a group part-stale quotes its oldest lamp and not the six fresh ones`() {
        // The whole reason the tile can carry one age: quoting the freshest would hide a lamp that
        // stopped answering a week ago behind six that reported a minute ago.
        val lamps =
            (1..6).map { bulb("light-0$it", isOn = true) } +
                bulb("light-07", isOn = true, lastUpdated = secondsAgo(7 * 86_400))

        val group = bulbGroup(lamps)

        assertEquals(Reading.At(now.minusSeconds(7 * 86_400)), group.oldest)
        assertEquals("7 on · 7 d ago", bulbGroupLine(group, now))
    }

    @Test
    fun `the group quotes the oldest reading of those that stayed, not the freshest`() {
        val bulbs =
            listOf(
                bulb("light-01", isOn = true, lastUpdated = secondsAgo(60)),
                bulb("light-02", isOn = true, lastUpdated = secondsAgo(7 * 86_400)),
                bulb("light-03", isOn = false, lastUpdated = secondsAgo(3600)),
            )

        val group = bulbGroup(bulbs)

        assertEquals(Reading.At(now.minusSeconds(7 * 86_400)), group.oldest)
        assertEquals("2 on · 7 d ago", bulbGroupLine(group, now))
    }

    @Test
    fun `a bulb that never reported a time stays in the group and is the oldest of all`() {
        // `isOn` and `last_updated` are different fields of the same capability: three of Коридор's
        // four bulbs report a value with a `last_updated` of 0.0. They are in the group, and the
        // line has to be able to quote the Never they carry.
        val bulbs =
            listOf(
                bulb("light-04", isOn = true, lastUpdated = Reading.Never),
                bulb("light-21", isOn = true, lastUpdated = secondsAgo(7 * 86_400)),
            )

        val group = bulbGroup(bulbs)

        assertEquals(listOf("light-04", "light-21"), group.lamps.map { it.id })
        assertEquals(Reading.Never, group.oldest)
        assertEquals("2 on · never read", bulbGroupLine(group, now))
    }

    @Test
    fun `how many are on counts the group's lamps and not the ones that broke out`() {
        // A bulb the panel has no state for is not "off" and is certainly not "on"; it is not in
        // the group being counted at all.
        val bulbs =
            listOf(
                bulb("light-01", isOn = true),
                bulb("light-02", isOn = false),
                bulb("light-03", isOn = null),
            )

        val group = bulbGroup(bulbs)

        assertEquals(1, group.on)
        assertEquals("1 on", bulbGroupLine(group, now))
    }

    @Test
    fun `a room with no bulbs has no group rather than an empty tile`() {
        val group = bulbGroup(emptyList())

        assertTrue(group.lamps.isEmpty())
        assertTrue(group.brokenOut.isEmpty())
        assertEquals(0, group.on)
        // No reading to quote, because there is nothing to quote it under.
        assertNull(group.oldest)
    }

    @Test
    fun `a room whose every bulb broke out has no group tile either`() {
        val group = bulbGroup(listOf(bulb("light-01", isOn = null), bulb("light-02", isOn = null)))

        assertTrue(group.lamps.isEmpty())
        assertEquals(2, group.brokenOut.size)
        assertNull(group.oldest)
    }

    @Test
    fun `the tile says the group stopped updating once for the whole group`() {
        // 28 bulbs behind one call: the group tile says it once instead of 28 tiles saying it each.
        // It says it on the *second* line, where every tile on this wall now says its bad news —
        // the first one is 188 dp wide and holds about sixteen characters of it.
        val group = bulbGroup(listOf(bulb("light-01", isOn = true), bulb("light-02", isOn = false)))

        assertEquals(
            "timed out",
            anatomy(group, now, "timed out", notUpdating = true, open = false).detail,
        )
        // Stale with nothing to name: the poll simply stopped landing, and there is no reason to
        // quote — see notUpdating.
        assertEquals(
            "not updating",
            anatomy(group, now, error = null, notUpdating = true, open = false).detail,
        )
        // Either way the lamps and their age are untouched on the line above.
        assertEquals("1 on", bulbGroupLine(group, now))
    }

    @Test
    fun `the group's status line does not repeat the name printed above it`() {
        // "7 lamps · 5 on · 20 d ago" wrapped onto two lines of a quarter-width tile whose *name*
        // already said "7 lamps". The count is the name because it does not change; how many are
        // on is the value, and the status line ages the value it names.
        val group = bulbGroup((1..7).map { bulb("light-0$it", isOn = it <= 5) })
        val tile = anatomy(group, now, error = null, notUpdating = false, open = false)

        assertEquals("7 lamps", tile.name)
        assertFalse(tile.status.contains("lamp"), "the count is the name: ${tile.status}")
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
