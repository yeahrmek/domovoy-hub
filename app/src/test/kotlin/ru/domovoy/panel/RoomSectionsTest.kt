package ru.domovoy.panel

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.domovoy.core.Reading
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Group recorded devices by room, including duplicate and empty room edge cases. */
class RoomSectionsTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun stop() {
        server.close()
    }

    @Test
    fun `one section holds every tile of the room, whatever the type`() = runTest {
        val sections = sectionsFromFixture()

        // Зал: an air conditioner, a light strip and four bulbs — three tile types and one section.
        val hall = sections.single { it.room == "Зал" }
        assertEquals(listOf("ac-03"), hall.acs.map { it.id })
        assertEquals(listOf("light-strip-01"), hall.strips.map { it.id })
        assertEquals(listOf("light-01", "light-02", "light-03", "light-15"), hall.bulbs.map { it.id })

        // Спальня: the flat's one curtain, next to the bedroom's own ac and bulbs rather than in a
        // section of its own at the top of the panel.
        val bedroom = sections.single { it.room == "Спальня" }
        assertEquals(listOf("curtain-01"), bedroom.curtains.map { it.id })
        assertEquals(listOf("ac-02"), bedroom.acs.map { it.id })
        assertEquals(7, bedroom.bulbs.size)
    }

    @Test
    fun `a room nothing on the panel is in does not render`() = runTest {
        val rooms = sectionsFromFixture().map { it.room }

        // Rooms the recorded response lists with no device at all.
        assertTrue("Living room" !in rooms, "an empty room must not be a section: $rooms")
        assertTrue("Маленькая комната" !in rooms)
        assertTrue("entrance" !in rooms)
        // Гостиная has two devices, and the panel has a tile for neither — a vacuum and a tv. A
        // room whose every device was dropped is as empty as one with none.
        assertTrue("Гостиная" !in rooms)
    }

    @Test
    fun `the rooms come out in the panel's order, the hallway it hangs in first`() = runTest {
        assertEquals(
            listOf("Коридор", "Зал", "Спальня", "Детская", "Кабинет", "Гардероб", "Ванная", "Детская ванная"),
            sectionsFromFixture().map { it.room },
        )
    }

    @Test
    fun `a room the order does not name still renders, after the ones it does`() = runTest {
        server.enqueue(MockResponse(body = yandexFixture()))
        val poll = YandexPoll(server.yandexClient())
        poll.refresh()

        val sections =
            roomSections(
                acs = emptyList(),
                curtains = emptyList(),
                strips = emptyList(),
                recuperators = listOf(recuperator("xfj-09", room = "Сауна")),
                bulbs = poll.bulbs.state.value.tiles,
                launchers = emptyList(),
            )

        assertEquals("Сауна", sections.map { it.room }.last())
        assertEquals(listOf("xfj-09"), sections.last().recuperators.map { it.id })
    }

    @Test
    fun `a room only a recuperator is in is placed in the order like any other`() = runTest {
        // Locally assigned Tuya rooms belong in the same room ordering as Yandex rooms.
        server.enqueue(MockResponse(body = yandexFixture()))
        val poll = YandexPoll(server.yandexClient())
        poll.refresh()

        val sections =
            roomSections(
                acs = poll.acs.state.value.tiles,
                curtains = poll.curtains.state.value.tiles,
                strips = poll.strips.state.value.tiles,
                recuperators = listOf(recuperator("xfj-01", room = "Маленькая детская")),
                bulbs = poll.bulbs.state.value.tiles,
                launchers = emptyList(),
            )

        assertEquals(
            listOf(
                "Коридор",
                "Зал",
                "Спальня",
                "Детская",
                "Маленькая детская",
                "Кабинет",
                "Гардероб",
                "Ванная",
                "Детская ванная",
            ),
            sections.map { it.room },
        )
    }

    @Test
    fun `a device no vendor placed is still on the wall, in a section of its own and last`() = runTest {
        // Missing local room mappings put recuperators in the unplaced section; never drop them.
        server.enqueue(MockResponse(body = yandexFixture()))
        val poll = YandexPoll(server.yandexClient())
        poll.refresh()

        val sections =
            roomSections(
                acs = poll.acs.state.value.tiles,
                curtains = poll.curtains.state.value.tiles,
                strips = poll.strips.state.value.tiles,
                recuperators = listOf(recuperator("xfj-01"), recuperator("xfj-05")),
                bulbs = poll.bulbs.state.value.tiles,
                launchers = emptyList(),
            )

        val unplaced = sections.last()
        assertEquals(null, unplaced.room)
        assertEquals(listOf("xfj-01", "xfj-05"), unplaced.recuperators.map { it.id })
        // Every tile that was on the panel before is still on it: nothing is lost to grouping.
        val placed =
            sections.sumOf { section ->
                with(section) { acs.size + curtains.size + strips.size + recuperators.size + bulbs.size }
            }
        assertEquals(26, placed, "24 Yandex tiles and 2 recuperators went in; all of them come out")
    }

    @Test
    fun `a recuperator the flat placed joins that room instead of the unplaced section`() = runTest {
        server.enqueue(MockResponse(body = yandexFixture()))
        val poll = YandexPoll(server.yandexClient())
        poll.refresh()

        val sections =
            roomSections(
                acs = poll.acs.state.value.tiles,
                curtains = emptyList(),
                strips = emptyList(),
                recuperators = listOf(recuperator("xfj-05", room = "Зал")),
                bulbs = emptyList(),
                launchers = emptyList(),
            )

        // One section, not two: the recuperator sits with the air conditioner of the same room.
        val hall = sections.single { it.room == "Зал" }
        assertEquals(listOf("xfj-05"), hall.recuperators.map { it.id })
        assertEquals(listOf("ac-03"), hall.acs.map { it.id })
        assertTrue(sections.none { it.room == null }, "nothing is unplaced any more: ${sections.map { it.room }}")
    }

    @Test
    fun `a launcher tile is grouped by room like any other`() = runTest {
        server.enqueue(MockResponse(body = yandexFixture()))
        val poll = YandexPoll(server.yandexClient())
        poll.refresh()

        val sections =
            roomSections(
                acs = poll.acs.state.value.tiles,
                curtains = poll.curtains.state.value.tiles,
                strips = poll.strips.state.value.tiles,
                recuperators = emptyList(),
                bulbs = poll.bulbs.state.value.tiles,
                launchers = launcherTiles(canOpen = { true }),
            )

        // The intercom and docked vacuum belong with the hallway's devices.
        val hallway = sections.single { it.room == "Коридор" }
        assertEquals(
            listOf("com.domonap.app", "com.xiaomi.smarthome"),
            hallway.launchers.map { it.packageName },
        )
        assertTrue(hallway.bulbs.isNotEmpty(), "the коридор's own tiles are still there")

        // No launcher goes unplaced any more. The unplaced section is still a rule of this file —
        // it is what the recuperators land in — and it has its own test above.
        assertTrue(
            sections.none { it.room == null },
            "no launcher is unplaced any more: ${sections.map { it.room }}",
        )
    }

    @Test
    fun `a launcher tile whose app is missing is still on the wall, saying so`() = runTest {
        // The tile is what tells someone the app is gone; dropping it would hide exactly the fact
        // it exists to report, and the panel would silently lose the intercom shortcut.
        server.enqueue(MockResponse(body = yandexFixture()))
        val poll = YandexPoll(server.yandexClient())
        poll.refresh()

        val sections =
            roomSections(
                acs = emptyList(),
                curtains = emptyList(),
                strips = emptyList(),
                recuperators = emptyList(),
                bulbs = poll.bulbs.state.value.tiles,
                launchers = launcherTiles(canOpen = { false }),
            )

        val hallway = sections.single { it.room == "Коридор" }
        assertEquals(listOf("Домофон", "Пылесос"), hallway.launchers.map { it.name })
        assertEquals(listOf(false, false), hallway.launchers.map { it.openable })
    }

    @Test
    fun `a group that failed before it ever had a tile still says so`() {
        // With no tile anywhere on the panel there is nothing to hang the reason on, and a group
        // that stayed quiet would just be missing from the wall with no reason given.
        val failures =
            groupFailures(
                acs = AcPanelState(error = "HTTP 500"),
                curtains = CurtainPanelState(error = "HTTP 500"),
                strips = LightStripPanelState(error = "HTTP 500"),
                recuperators = RecuperatorPanelState(error = "timeout"),
                bulbs = BulbPanelState(error = "HTTP 500"),
            )

        assertEquals(
            listOf(
                "Кондиционеры: нет обновления — HTTP 500",
                "Шторы: нет обновления — HTTP 500",
                "Подсветка: нет обновления — HTTP 500",
                "Бризеры: нет обновления — timeout",
                "Лампы: нет обновления — HTTP 500",
            ),
            failures,
        )
    }

    @Test
    fun `one shared outage is described once in a populated section`() {
        val section = RoomSection(
            room = "Зал",
            acs = Flat.acs.tiles,
            strips = Flat.strips.tiles,
            recuperators = Flat.recuperators.tiles,
        )
        val failures = sectionFailures(
            section,
            GroupErrors(acs = "Нет сети", strips = "Нет сети", recuperators = "Подписка истекла"),
        )
        assertEquals(
            listOf(
                "Кондиционеры, Подсветка: нет обновления — Нет сети",
                "Бризеры: нет обновления — Подписка истекла",
            ),
            failures,
        )
    }

    @Test
    fun `a group that failed with tiles on the wall says it on the tiles, not twice`() = runTest {
        server.enqueue(MockResponse(body = yandexFixture()))
        val poll = YandexPoll(server.yandexClient())
        poll.refresh()
        val bulbs = BulbPanelState(tiles = poll.bulbs.state.value.tiles, error = "HTTP 500")

        val failures =
            groupFailures(
                acs = AcPanelState(),
                curtains = CurtainPanelState(),
                strips = LightStripPanelState(),
                recuperators = RecuperatorPanelState(),
                bulbs = bulbs,
            )

        assertEquals(emptyList(), failures)
    }

    private suspend fun sectionsFromFixture(): List<RoomSection> {
        server.enqueue(MockResponse(body = yandexFixture()))
        val poll = YandexPoll(server.yandexClient())
        poll.refresh()
        return roomSections(
            acs = poll.acs.state.value.tiles,
            curtains = poll.curtains.state.value.tiles,
            strips = poll.strips.state.value.tiles,
            recuperators = emptyList(),
            bulbs = poll.bulbs.state.value.tiles,
            launchers = emptyList(),
        )
    }

    /** A recuperator tile as the Tuya poll builds one: no room, unless the flat named it. */
    private fun recuperator(
        id: String,
        room: String? = null,
    ) = RecuperatorTileState(
        id = id,
        name = id,
        room = room,
        isOn = null,
        powerLastUpdated = Reading.Never,
        speeds = emptyList(),
        speedLastUpdated = Reading.Never,
        temperature = null,
        temperatureLastUpdated = Reading.Never,
        humidity = null,
        humidityLastUpdated = Reading.Never,
        online = null,
    )
}
