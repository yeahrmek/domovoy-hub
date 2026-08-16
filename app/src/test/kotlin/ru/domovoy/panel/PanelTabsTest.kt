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
 * The tab strip consumes exactly what [roomSections] returns — one tab per section, in that order,
 * with Главная in front and the roomless section last. The mark is the other half of it: with
 * twelve rooms behind twelve tabs, a room that stopped answering has to say so from the strip, or
 * Спальня can be dead for a day behind a Главная that looks fine.
 *
 * What marks a room is its group's poll — the call failed, or none has succeeded in eight
 * intervals. Not the age of the values on its tiles: those are the vendor's `last_updated`, which
 * says when the *device* last reported, and a lamp nobody has touched in three weeks is not a
 * broken lamp.
 */
class PanelTabsTest {
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
    fun `главная is the first tab`() {
        val tabs = panelTabs(sections(bulbs = listOf(bulb("light-01", "Спальня"))), GroupErrors(), polling, now, yandex, tuya)

        assertEquals("Главная", tabs.first().title)
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
                ),
            )

        val tabs = panelTabs(sections, GroupErrors(), polling, now, yandex, tuya)

        assertEquals(
            listOf("Главная", "Коридор", "Зал", "Спальня", "Ванная", "Без комнаты"),
            tabs.map { it.title },
        )
        // Not a re-sort of its own: the tabs are the sections, in the order that function produced.
        assertEquals(sections.map { it.room }, tabs.drop(1).dropLast(1).map { it.section.room })
    }

    @Test
    fun `без комнаты is last and is there even when every other section is empty`() {
        // Nothing on the wall at all: the panel still offers the section that holds whatever no
        // vendor placed, rather than a strip with one tab on it.
        val tabs = panelTabs(emptyList(), GroupErrors(), polling, now, yandex, tuya)

        assertEquals(listOf("Главная", "Без комнаты"), tabs.map { it.title })
        assertEquals(null, tabs.last().section.room)
    }

    @Test
    fun `a section carries its own tiles onto its tab`() {
        val tabs = panelTabs(sections(bulbs = listOf(bulb("light-03", "Спальня"))), GroupErrors(), polling, now, yandex, tuya)

        val bedroom = tabs.single { it.title == "Спальня" }
        assertEquals(listOf("light-03"), bedroom.section.bulbs.map { it.id })
    }

    @Test
    fun `a room is marked when its group's poll failed`() {
        val sections = sections(bulbs = listOf(bulb("light-03", "Спальня")))

        val tabs = panelTabs(sections, GroupErrors(bulbs = "HTTP 500"), polling, now, yandex, tuya)

        assertTrue(tabs.single { it.title == "Спальня" }.marked)
    }

    @Test
    fun `a room is marked when its group has stopped being polled`() {
        // The bulb's own reading is 30 s old and looks fine. What is wrong is that nothing has
        // read it for three minutes, which is twelve missed polls — and only the poll knows that.
        val sections = sections(bulbs = listOf(bulb("light-03", "Спальня")))

        val tabs =
            panelTabs(
                sections,
                GroupErrors(),
                polling.copy(bulbs = polledSecondsAgo(180)),
                now,
                yandex,
                tuya,
            )

        assertTrue(tabs.single { it.title == "Спальня" }.marked)
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

        val tabs = panelTabs(sections, GroupErrors(), polling, now, yandex, tuya)

        assertFalse(
            tabs.single { it.title == "Спальня" }.marked,
            "a bulb Yandex has never reported is still being read; the poll is what says otherwise",
        )
    }

    @Test
    fun `a room whose bulb was last touched three weeks ago is not marked`() {
        // The other half of the same mistake: a bulb switched on three weeks ago and untouched
        // since carries a three-week-old `last_updated` while every poll has read it fine.
        val sections = sections(bulbs = listOf(bulb("light-03", "Спальня", secondsAgo(21 * 86_400))))

        val tabs = panelTabs(sections, GroupErrors(), polling, now, yandex, tuya)

        assertFalse(
            tabs.single { it.title == "Спальня" }.marked,
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

        val tabs = panelTabs(sections, GroupErrors(), polling, now, yandex, tuya)

        assertEquals(
            listOf("Балкон"),
            tabs.filter { it.marked }.map { it.title },
            "one recuperator's own timeout is one room's news",
        )
    }

    @Test
    fun `a room holding only launcher tiles is never marked`() {
        // A launcher is polled by nothing, so no group's news is its news. Marking the коридор
        // because its интерком tile has no poll behind it would put a warning on every panel —
        // including before the first refresh has landed anywhere.
        val tabs =
            panelTabs(
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

        assertFalse(tabs.single { it.title == "Коридор" }.marked)
        assertFalse(tabs.single { it.title == "Без комнаты" }.marked)
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

        val home = panelTabs(sections, GroupErrors(), polling, now, yandex, tuya).first().section

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
