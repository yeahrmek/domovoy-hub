package ru.domovoy.panel

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.domovoy.core.Reading
import ru.domovoy.integrations.yandex.YandexClient
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertEquals("on · 5% · 2 h ago", statusLine(tile, now))
    }

    @Test
    fun `a dimmable bulb retains brightness and colour capabilities while a relay has neither`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        val poll = YandexPoll(client())

        poll.refresh()

        val dimmable = poll.bulbs.state.value.tiles.single { it.id == "light-01" }
        assertEquals(5.0, dimmable.brightnessPercent)
        assertEquals(2700.0, dimmable.color?.temperatureBounds?.min)
        val relay = poll.bulbs.state.value.tiles.single { it.id == "light-20" }
        assertNull(relay.brightnessPercent)
        assertNull(relay.color)
    }

    @Test
    fun `setting bulb brightness uses its reported range and re-reads`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-level"}"""))
        server.enqueue(MockResponse(body = fixture()))
        val poll = YandexPoll(client())

        poll.refresh()
        poll.bulbs.setBrightness("light-01", 40.0)

        server.takeRequest()
        val request = server.takeRequest()
        val action = Json.parseToJsonElement(requireNotNull(request.body).utf8()).jsonObject["devices"]!!
            .jsonArray.single().jsonObject["actions"]!!.jsonArray.single().jsonObject
        assertEquals("devices.capabilities.range", action["type"]!!.jsonPrimitive.content)
        assertEquals("brightness", action["state"]!!.jsonObject["instance"]!!.jsonPrimitive.content)
        assertEquals("40", action["state"]!!.jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("/v1.0/user/info", server.takeRequest().target)
    }

    @Test
    fun `setting an advertised RGB scene uses the verified color action and re-reads`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-scene"}"""))
        server.enqueue(MockResponse(body = fixture()))
        val poll = YandexPoll(client())

        poll.refresh()
        poll.bulbs.setScene("light-21", "movie")

        server.takeRequest()
        val request = server.takeRequest()
        val action = Json.parseToJsonElement(requireNotNull(request.body).utf8()).jsonObject["devices"]!!
            .jsonArray.single().jsonObject["actions"]!!.jsonArray.single().jsonObject
        assertEquals("devices.capabilities.color_setting", action["type"]!!.jsonPrimitive.content)
        assertEquals("scene", action["state"]!!.jsonObject["instance"]!!.jsonPrimitive.content)
        assertEquals("movie", action["state"]!!.jsonObject["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun `every failure the panel can have comes out as one of four reasons`() {
        // The table `docs/design/panel-redesign.md` item 7 asks for. Four words, chosen here and
        // not by whoever threw: `message ?: className` put Java's own sentence in the middle of a
        // line whose other half was the panel's, and put it there at whatever length it happened
        // to be.
        assertEquals("unreachable", reason(UnknownHostException("openapi.tuyaeu.com")))
        assertEquals("unreachable", reason(NoRouteToHostException("no route to host")))
        assertEquals("timed out", reason(SocketTimeoutException("timeout")))
        // OkHttp's call timeout, which is the one every vendor client in this app sets, arrives as
        // the parent type rather than as a SocketTimeoutException.
        assertEquals("timed out", reason(InterruptedIOException("timeout")))
        assertEquals("refused", reason(ConnectException("Failed to connect to /10.0.0.2:443")))
        // The fallback, and both kinds of thing that land on it: an I/O failure with no name of its
        // own, and the panel's own check on a response it did not like.
        assertEquals("failed", reason(IOException("unexpected end of stream")))
        assertEquals("failed", reason(IllegalStateException("GET /v1.0/user/info failed: HTTP 403 Forbidden")))
    }

    @Test
    fun `the vendor's own words do not reach the wall`() {
        // The line this whole mapping is named after — it arrived on the tile as
        // `not updating: Unable to resolve host "openapi.tuyaeu.com"`, wrapped, and made that tile
        // taller than the one beside it.
        val fromJava =
            UnknownHostException("""Unable to resolve host "openapi.tuyaeu.com": No address associated with hostname""")

        assertEquals("unreachable", reason(fromJava))
        assertFalse(reason(fromJava).contains("openapi"), "the host name belongs in Log, not on the wall")
    }

    @Test
    fun `a bulb that never reported reads as never, not as 1 Jan 1970`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        val poll = YandexPoll(client())
        val tiles = poll.bulbs

        poll.refresh()

        val tile = tiles.state.value.tiles.single { it.id == "light-04" }
        assertEquals(Reading.Never, tile.lastUpdated)
        assertEquals("on · never read", statusLine(tile, Instant.ofEpochSecond(1_786_790_000)))
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
        // The reading and its age stay exactly where they were, and the reason the panel stopped
        // reading is the tile's *second* line — a status line carrying both is a status line no
        // narrow tile on this wall can hold.
        assertEquals("on · 5% · 2 h ago", statusLine(kept, now))
        assertEquals("failed", anatomy(kept, now, after.error).detail)
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
    fun `a panel with no token stored reports a failed poll instead of standing there empty`() = runTest {
        // No tile has ever been read, so there is nothing to hang the news on but the group error —
        // and without it the wall would show a blank panel and no reason for it.
        //
        // **It no longer names the token, and that is item 7's price.** The sentence the client
        // throws — "no Yandex token stored — set yandex.oauth.token in local.properties and
        // reinstall" — is 76 characters on a 156 dp tile, and the mapping the panel now runs every
        // throwable through has four words in it and no room for a 77th. It goes to `Log` with the
        // exception, and docs/yandex.md says so.
        val poll = YandexPoll(client(token = { "" }))
        val tiles = poll.bulbs

        poll.refresh()

        val state = tiles.state.value
        assertTrue(state.tiles.isEmpty())
        assertEquals("failed", state.error)
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
        // "404 unknown device" is Yandex's answer and it is in Log; what the wall gets is the one
        // of four words the panel is willing to print at a width it can hold.
        assertEquals("failed", tiles.state.value.error)
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
