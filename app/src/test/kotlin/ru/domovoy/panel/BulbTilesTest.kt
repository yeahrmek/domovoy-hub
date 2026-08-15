package ru.domovoy.panel

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.domovoy.core.Reading
import ru.domovoy.integrations.yandex.YandexClient
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** What the wall actually shows, driven through the client against the recorded response. */
class BulbTilesTest {
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
    fun `a tile shows the bulb's name, whether it is on, and how old the reading is`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        val poll = YandexPoll(client())
        val tiles = poll.bulbs

        poll.refresh()

        val tile = tiles.state.value.tiles.single { it.id == "light-01" }
        assertEquals("Лампа 4", tile.name)
        assertEquals(true, tile.isOn)
        // last_updated 1784883564 read two hours later.
        val now = Instant.ofEpochSecond(1_784_883_564 + 2 * 3600)
        assertEquals("on · 2 h ago", statusLine(tile, now, error = null))
    }

    @Test
    fun `a bulb that never reported reads as never, not as 1 Jan 1970`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        val poll = YandexPoll(client())
        val tiles = poll.bulbs

        poll.refresh()

        val tile = tiles.state.value.tiles.single { it.id == "light-04" }
        assertEquals(Reading.Never, tile.lastUpdated)
        assertEquals("on · never read", statusLine(tile, Instant.ofEpochSecond(1_786_790_000), error = null))
    }

    @Test
    fun `a failed poll keeps the last known value and age, and says it is not updating`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        server.enqueue(MockResponse(code = 500, body = "boom"))
        val poll = YandexPoll(client())
        val tiles = poll.bulbs

        poll.refresh()
        val before = tiles.state.value.tiles.single { it.id == "light-01" }
        poll.refresh()
        val after = tiles.state.value

        assertNotNull(after.error)
        val kept = after.tiles.single { it.id == "light-01" }
        assertEquals(before, kept)
        val now = Instant.ofEpochSecond(1_784_883_564 + 2 * 3600)
        assertTrue(statusLine(kept, now, after.error).startsWith("on · 2 h ago · not updating"))
    }

    @Test
    fun `a poll that times out is an error, not a spinner`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        server.enqueue(
            MockResponse
                .Builder()
                .body(fixture())
                .bodyDelay(10, TimeUnit.SECONDS)
                .build(),
        )
        val poll = YandexPoll(client(timeout = 200.milliseconds))
        val tiles = poll.bulbs

        poll.refresh()
        poll.refresh()

        val state = tiles.state.value
        assertNotNull(state.error)
        assertEquals(18, state.tiles.size)
    }

    @Test
    fun `a panel with no token stored says so instead of standing there empty`() = runTest {
        // No tile has ever been read, so there is nothing to hang the message on but the group
        // error — and without it the wall would show a blank panel and no reason for it.
        val poll = YandexPoll(client(token = { "" }))
        val tiles = poll.bulbs

        poll.refresh()

        val state = tiles.state.value
        assertTrue(state.tiles.isEmpty())
        assertTrue(
            state.error.orEmpty().contains("token"),
            "the panel must name the missing token: ${state.error}",
        )
    }

    @Test
    fun `a token stored after the panel started ends the error on the next poll`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        var stored = ""
        val poll = YandexPoll(client(token = { stored }))
        val tiles = poll.bulbs

        poll.refresh()
        assertNotNull(tiles.state.value.error)
        stored = "y0_stored_later"
        poll.refresh()

        assertNull(tiles.state.value.error)
        assertEquals(18, tiles.state.value.tiles.size)
    }

    @Test
    fun `a poll that recovers clears the error`() = runTest {
        server.enqueue(MockResponse(code = 500, body = "boom"))
        server.enqueue(MockResponse(body = fixture()))
        val poll = YandexPoll(client())
        val tiles = poll.bulbs

        tiles.state.test {
            assertEquals(BulbPanelState(), awaitItem())

            poll.refresh()
            assertNotNull(awaitItem().error)

            poll.refresh()
            val recovered = awaitItem()
            assertNull(recovered.error)
            assertEquals(18, recovered.tiles.size)
        }
    }

    @Test
    fun `toggling an on bulb asks Yandex to turn it off, then re-reads`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-1","devices":[]}"""))
        server.enqueue(MockResponse(body = fixture()))
        val poll = YandexPoll(client())
        val tiles = poll.bulbs

        poll.refresh()
        assertEquals(true, tiles.state.value.tiles.single { it.id == "light-01" }.isOn)
        tiles.toggle("light-01")

        server.takeRequest() // the first poll
        val action = server.takeRequest()
        assertEquals("/v1.0/devices/actions", action.target)
        assertTrue(requireNotNull(action.body).utf8().contains(""""value":false"""))
        // Yandex answers DONE without promising the bulb changed, so the tile is repainted
        // from a fresh read rather than from the action result.
        assertEquals("/v1.0/user/info", server.takeRequest().target)
    }

    @Test
    fun `a toggle that fails leaves the tiles alone and reports the failure`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        server.enqueue(MockResponse(code = 404, body = "unknown device"))
        val poll = YandexPoll(client())
        val tiles = poll.bulbs

        poll.refresh()
        val before = tiles.state.value.tiles
        tiles.toggle("light-01")

        assertEquals(before, tiles.state.value.tiles)
        assertTrue(tiles.state.value.error.orEmpty().contains("404"))
    }

    private fun client(
        timeout: Duration = 10.seconds,
        token: () -> String = { "test-token" },
    ) = YandexClient(
        http = OkHttpClient(),
        token = token,
        householdId = "household-flat",
        baseUrl = server.url("/"),
        timeout = timeout,
    )

    private fun fixture(): String = checkNotNull(javaClass.getResourceAsStream("/yandex/user_info.json")) {
        "missing fixture app/src/test/resources/yandex/user_info.json"
    }.use { it.readBytes().decodeToString() }
}
