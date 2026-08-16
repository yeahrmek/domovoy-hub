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
 */
class PanelTabsTest {
    private val yandex = 15.seconds
    private val tuya = 6.minutes
    private val now = Instant.ofEpochSecond(1_786_000_000)

    @Test
    fun `главная is the first tab`() {
        val tabs = panelTabs(sections(bulb("light-01", "Спальня")), GroupErrors(), now, yandex, tuya)

        assertEquals("Главная", tabs.first().title)
    }

    @Test
    fun `the rooms come in the order roomSections returns them`() {
        val sections =
            sections(
                bulb("light-01", "Ванная"),
                bulb("light-02", "Коридор"),
                bulb("light-03", "Спальня"),
                bulb("light-04", "Зал"),
            )

        val tabs = panelTabs(sections, GroupErrors(), now, yandex, tuya)

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
        val tabs = panelTabs(emptyList(), GroupErrors(), now, yandex, tuya)

        assertEquals(listOf("Главная", "Без комнаты"), tabs.map { it.title })
        assertEquals(null, tabs.last().section.room)
    }

    @Test
    fun `a section carries its own tiles onto its tab`() {
        val tabs = panelTabs(sections(bulb("light-03", "Спальня")), GroupErrors(), now, yandex, tuya)

        val bedroom = tabs.single { it.title == "Спальня" }
        assertEquals(listOf("light-03"), bedroom.section.bulbs.map { it.id })
    }

    @Test
    fun `a room is marked when its group's poll failed`() {
        val sections = sections(bulb("light-03", "Спальня"))

        val tabs = panelTabs(sections, GroupErrors(bulbs = "HTTP 500"), now, yandex, tuya)

        assertTrue(tabs.single { it.title == "Спальня" }.marked)
    }

    @Test
    fun `a room is marked when every reading in it is stale`() {
        val sections = sections(bulb("light-03", "Спальня", secondsAgo(180)))

        val tabs = panelTabs(sections, GroupErrors(), now, yandex, tuya)

        assertTrue(tabs.single { it.title == "Спальня" }.marked)
    }

    @Test
    fun `a room with a fresh reading in it is not marked`() {
        val sections =
            sections(
                bulb("light-03", "Спальня", secondsAgo(180)),
                bulb("light-04", "Спальня", secondsAgo(30)),
            )

        val tabs = panelTabs(sections, GroupErrors(), now, yandex, tuya)

        assertFalse(
            tabs.single { it.title == "Спальня" }.marked,
            "one bulb behind is the bulb's own news, not the room's",
        )
    }

    @Test
    fun `a room holding only launcher tiles is never marked`() {
        // A launcher has no reading, so "every reading in it is stale" is true of nothing. Marking
        // the коридор because its интерком tile has no age would put a warning on every panel.
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
                bulb("light-02", "Коридор"),
                bulb("light-03", "Спальня", secondsAgo(180)),
                bulb("light-04", "Спальня"),
            )

        val home = panelTabs(sections, GroupErrors(), now, yandex, tuya).first().section

        assertEquals(listOf("light-02", "light-03"), home.bulbs.map { it.id })
    }

    private fun sections(vararg bulbs: BulbTileState) = roomSections(
        acs = emptyList(),
        curtains = emptyList(),
        strips = emptyList(),
        recuperators = emptyList(),
        bulbs = bulbs.toList(),
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

    private fun secondsAgo(seconds: Long) = Reading.At(now.minusSeconds(seconds))
}
