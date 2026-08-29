package ru.domovoy.panel

import org.junit.jupiter.api.Test
import ru.domovoy.core.Reading
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The scroll consumes exactly what [roomSections] returns — one heading per section, in that order,
 * with Главная in front and the roomless section last. The mark is the other half of it: fourteen
 * rooms is more than a screenful, so a room that stopped answering has to say so on its heading, or
 * Спальня can be dead for a day behind a Главная that looks fine.
 *
 * What marks a room is its group's poll — the call failed, or none has succeeded in eight
 * intervals. Not the age of the values on its tiles: those are the vendor's `last_updated`, which
 * says when the *device* last reported, and a lamp nobody has touched in three weeks is not a
 * broken lamp.
 */
class PanelHeadingsTest {
    private val yandex = 15.seconds
    private val tuya = 6.minutes
    private val now = Instant.ofEpochSecond(1_786_000_000)

    /** Every group read a moment ago: the panel is working, whatever the tiles' own ages say. */
    private val polling =
        GroupPolls(
            acs = polledSecondsAgo(5),
            curtains = polledSecondsAgo(5),
            strips = polledSecondsAgo(5),
            recuperators = polledSecondsAgo(5),
            bulbs = polledSecondsAgo(5),
        )

    @Test
    fun `главная is the first heading`() {
        val headings = panelHeadings(sections(bulbs = listOf(bulb("light-02", "Коридор"))), GroupErrors(), polling, now, yandex, tuya)

        assertEquals("Главная", headings.first().title)
    }

    @Test
    fun `the rooms come in the order roomSections returns them`() {
        val sections =
            sections(
                bulbs =
                listOf(
                    bulb("light-01", "Ванная"),
                    bulb("light-02", "Коридор"),
                    bulb("light-03", "Спальня"),
                    bulb("light-04", "Зал"),
                    bulb("light-09", room = null),
                ),
            )

        val headings = panelHeadings(sections, GroupErrors(), polling, now, yandex, tuya)

        assertEquals(
            listOf("Главная", "Коридор", "Зал", "Спальня", "Ванная", "Без комнаты"),
            headings.map { it.title },
        )
        // Not a re-sort of its own: the headings are the sections, in the order that function
        // produced. The roomless one is dropped from both sides — it is the last heading rather
        // than one of the rooms, and the test above is where it is checked.
        assertEquals(
            sections.filter { it.room != null }.map { it.room },
            headings.drop(1).dropLast(1).map { it.section.room },
        )
    }

    @Test
    fun `без комнаты is last, and holds what no vendor placed`() {
        val headings =
            panelHeadings(
                sections(
                    bulbs =
                    listOf(
                        bulb("light-02", "Коридор"),
                        bulb("light-01", "Спальня"),
                        bulb("light-09", room = null),
                    ),
                ),
                GroupErrors(),
                polling,
                now,
                yandex,
                tuya,
            )

        assertEquals(listOf("Главная", "Коридор", "Спальня", "Без комнаты"), headings.map { it.title })
        assertEquals(null, headings.last().section.room)
        assertEquals(listOf("light-09"), headings.last().section.bulbs.map { it.id })
    }

    @Test
    fun `a section with nothing under it gets no heading`() {
        // The strip always carried Без комнаты, empty or not, so that a device no vendor placed
        // could never fall off the wall — a tab that is not there is a room that cannot be opened.
        // Stacked, that argument does not hold: a section's tiles are on the same scroll as its
        // heading, so the only thing an empty heading can do is claim a room that has nothing in
        // it. Whatever *is* unplaced still brings its own heading with it, which the test above is.
        val headings = panelHeadings(emptyList(), GroupErrors(), polling, now, yandex, tuya)

        assertEquals(emptyList(), headings.map { it.title })
    }

    @Test
    fun `главная is dropped when the panel has nothing to put on it`() {
        // Not a special case — Главная is a section like the rest and goes when it is empty. The
        // panel that produces this is one where every group failed before it ever had a tile, and
        // what stands on it then is `groupFailures`, which is not a section and does not come
        // through here.
        val headings =
            panelHeadings(
                sections(bulbs = listOf(bulb("light-03", "Спальня"))),
                GroupErrors(),
                polling,
                now,
                yandex,
                tuya,
            )

        // The bedroom is neither a favourite room nor failing, so Главная has nothing to pull in.
        assertEquals(listOf("Спальня"), headings.map { it.title })
    }

    @Test
    fun `a section carries its own tiles under its heading`() {
        val headings = panelHeadings(sections(bulbs = listOf(bulb("light-03", "Спальня"))), GroupErrors(), polling, now, yandex, tuya)

        val bedroom = headings.single { it.title == "Спальня" }
        assertEquals(listOf("light-03"), bedroom.section.bulbs.map { it.id })
    }

    @Test
    fun `a room is marked when its group's poll failed`() {
        val sections = sections(bulbs = listOf(bulb("light-03", "Спальня")))

        val headings = panelHeadings(sections, GroupErrors(bulbs = "HTTP 500"), polling, now, yandex, tuya)

        assertTrue(headings.single { it.title == "Спальня" }.marked)
    }

    @Test
    fun `a room is marked when its group has stopped being polled`() {
        // The bulb's own reading is 30 s old and looks fine. What is wrong is that nothing has
        // read it for three minutes, which is twelve missed polls — and only the poll knows that.
        val sections = sections(bulbs = listOf(bulb("light-03", "Спальня")))

        val headings =
            panelHeadings(
                sections,
                GroupErrors(),
                polling.copy(bulbs = polledSecondsAgo(180)),
                now,
                yandex,
                tuya,
            )

        assertTrue(headings.single { it.title == "Спальня" }.marked)
    }

    @Test
    fun `a room of bulbs that have never reported is not marked while its group is polling`() {
        // The regression this rule exists for. 33 of the 116 recorded capabilities are `0.0`, which
        // is `Never`, and a room of them was marked while every poll read it perfectly well.
        val sections =
            sections(
                bulbs =
                listOf(
                    bulb("light-03", "Спальня", Reading.Never),
                    bulb("light-04", "Спальня", Reading.Never),
                ),
            )

        val headings = panelHeadings(sections, GroupErrors(), polling, now, yandex, tuya)

        assertFalse(
            headings.single { it.title == "Спальня" }.marked,
            "a bulb Yandex has never reported is still being read; the poll is what says otherwise",
        )
    }

    @Test
    fun `a room whose bulb was last touched three weeks ago is not marked`() {
        // The other half of the same mistake: a bulb switched on three weeks ago and untouched
        // since carries a three-week-old `last_updated` while every poll has read it fine.
        val sections = sections(bulbs = listOf(bulb("light-03", "Спальня", secondsAgo(21 * 86_400))))

        val headings = panelHeadings(sections, GroupErrors(), polling, now, yandex, tuya)

        assertFalse(
            headings.single { it.title == "Спальня" }.marked,
            "a steady lamp is not a broken one",
        )
    }

    @Test
    fun `a recuperator with its own error marks its room while the other four do not`() {
        // Tuya state is one call per device, so this failure is that device's and stays that
        // device's: the four rooms that read fine are not marked for the fifth.
        val sections =
            sections(
                recuperators =
                listOf(
                    recuperator("xfj-01", "Спальня"),
                    recuperator("xfj-02", "Детская"),
                    recuperator("xfj-03", "Кабинет"),
                    recuperator("xfj-04", "Зал"),
                    recuperator("xfj-05", "Балкон", error = "timeout"),
                ),
            )

        val headings = panelHeadings(sections, GroupErrors(), polling, now, yandex, tuya)

        assertEquals(
            listOf("Балкон"),
            headings.filter { it.marked }.map { it.title },
            "one recuperator's own timeout is one room's news",
        )
    }

    @Test
    fun `a room holding only launcher tiles is never marked`() {
        // A launcher is polled by nothing, so no group's news is its news. Marking the коридор
        // because its интерком tile has no poll behind it would put a warning on every panel —
        // including before the first refresh has landed anywhere.
        val headings =
            panelHeadings(
                roomSections(
                    acs = emptyList(),
                    curtains = emptyList(),
                    strips = emptyList(),
                    recuperators = emptyList(),
                    bulbs = emptyList(),
                    launchers = launcherTiles(canOpen = { true }),
                ),
                GroupErrors(),
                GroupPolls(acs = null, curtains = null, strips = null, recuperators = null, bulbs = null),
                now,
                yandex,
                tuya,
            )

        assertFalse(headings.single { it.title == "Коридор" }.marked)
        assertFalse(headings.single { it.title == "Без комнаты" }.marked)
    }

    @Test
    fun `главная holds what главная holds`() {
        val sections =
            sections(
                bulbs =
                listOf(
                    bulb("light-02", "Коридор"),
                    bulb("light-03", "Спальня", Reading.Never),
                    bulb("light-04", "Спальня"),
                ),
            )

        val home = panelHeadings(sections, GroupErrors(), polling, now, yandex, tuya).first().section

        assertEquals(listOf("light-02"), home.bulbs.map { it.id })
    }

    private fun sections(
        bulbs: List<BulbTileState> = emptyList(),
        recuperators: List<RecuperatorTileState> = emptyList(),
    ) = roomSections(
        acs = emptyList(),
        curtains = emptyList(),
        strips = emptyList(),
        recuperators = recuperators,
        bulbs = bulbs,
        launchers = emptyList(),
    )

    private fun bulb(
        id: String,
        room: String?,
        lastUpdated: Reading = secondsAgo(30),
    ) = BulbTileState(
        id = id,
        name = id,
        room = room,
        isOn = true,
        lastUpdated = lastUpdated,
        stateChangedAt = Reading.Never,
    )

    private fun recuperator(
        id: String,
        room: String?,
        error: String? = null,
    ) = RecuperatorTileState(
        id = id,
        name = id,
        room = room,
        isOn = true,
        powerLastUpdated = secondsAgo(3 * 86_400),
        speeds = listOf(FanSpeed.Low),
        speedLastUpdated = secondsAgo(3 * 86_400),
        temperature = 29.3,
        temperatureLastUpdated = secondsAgo(180),
        humidity = 32.2,
        humidityLastUpdated = secondsAgo(26),
        online = true,
        error = error,
    )

    private fun secondsAgo(seconds: Long) = Reading.At(now.minusSeconds(seconds))

    private fun polledSecondsAgo(seconds: Long): Instant = now.minusSeconds(seconds)
}
