package ru.domovoy.panel

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.domovoy.core.Reading
import ru.domovoy.integrations.yandex.YandexClient
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The panel is a wall, not a stack: what is on it is grouped by the room the device is in, whatever
 * the device type or the vendor it came from. Everything here is driven off the recorded
 * `/v1.0/user/info` — a fixture whose rooms are the flat's real ones, duplicates, empty ones and
 * all — because the awkward cases are the recorded ones, not invented ones.
 */
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
        server.enqueue(MockResponse(body = fixture()))
        val poll = YandexPoll(client())
        poll.refresh()

        val sections =
            roomSections(
                acs = emptyList(),
                curtains = emptyList(),
                strips = emptyList(),
                recuperators = listOf(recuperator("xfj-09", room = "Сауна")),
                bulbs = poll.bulbs.state.value.tiles,
            )

        assertEquals("Сауна", sections.map { it.room }.last())
        assertEquals(listOf("xfj-09"), sections.last().recuperators.map { it.id })
    }

    @Test
    fun `a device no vendor placed is still on the wall, in a section of its own and last`() = runTest {
        // Tuya's API names no room, so every recuperator arrives with room = null unless the flat's
        // own answer was written into local.properties. Unplaced is not dropped: a device that
        // vanishes because no vendor gave it a room is the bug this section exists to prevent.
        server.enqueue(MockResponse(body = fixture()))
        val poll = YandexPoll(client())
        poll.refresh()

        val sections =
            roomSections(
                acs = poll.acs.state.value.tiles,
                curtains = poll.curtains.state.value.tiles,
                strips = poll.strips.state.value.tiles,
                recuperators = listOf(recuperator("xfj-01"), recuperator("xfj-05")),
                bulbs = poll.bulbs.state.value.tiles,
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
        server.enqueue(MockResponse(body = fixture()))
        val poll = YandexPoll(client())
        poll.refresh()

        val sections =
            roomSections(
                acs = poll.acs.state.value.tiles,
                curtains = emptyList(),
                strips = emptyList(),
                recuperators = listOf(recuperator("xfj-05", room = "Зал")),
                bulbs = emptyList(),
            )

        // One section, not two: the recuperator sits with the air conditioner of the same room.
        val hall = sections.single { it.room == "Зал" }
        assertEquals(listOf("xfj-05"), hall.recuperators.map { it.id })
        assertEquals(listOf("ac-03"), hall.acs.map { it.id })
        assertTrue(sections.none { it.room == null }, "nothing is unplaced any more: ${sections.map { it.room }}")
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
                "Кондиционеры: not updating: HTTP 500",
                "Шторы: not updating: HTTP 500",
                "Подсветка: not updating: HTTP 500",
                "Бризеры: not updating: timeout",
                "Лампы: not updating: HTTP 500",
            ),
            failures,
        )
    }

    @Test
    fun `a group that failed with tiles on the wall says it on the tiles, not twice`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        val poll = YandexPoll(client())
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
        server.enqueue(MockResponse(body = fixture()))
        val poll = YandexPoll(client())
        poll.refresh()
        return roomSections(
            acs = poll.acs.state.value.tiles,
            curtains = poll.curtains.state.value.tiles,
            strips = poll.strips.state.value.tiles,
            recuperators = emptyList(),
            bulbs = poll.bulbs.state.value.tiles,
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

    private fun client() = YandexClient(
        http = OkHttpClient(),
        token = { "test-token" },
        householdId = "household-flat",
        baseUrl = server.url("/"),
    )

    private fun fixture(): String = checkNotNull(javaClass.getResourceAsStream("/yandex/user_info.json")) {
        "missing fixture app/src/test/resources/yandex/user_info.json"
    }.use { it.readBytes().decodeToString() }
}
