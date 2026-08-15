package ru.domovoy.panel

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.domovoy.integrations.yandex.YandexClient
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The curtain is the panel's first tile whose state is a number rather than a switch, so these
 * assertions are on the position the wall ends up showing and on what Yandex would have received.
 *
 * The `range` capability was last read at 1786667879.4; every "now" below is offset from the whole
 * second after it, so the fractional part cannot round an age down to the previous hour.
 */
class CurtainTilesTest {
    private val lastRead = 1_786_667_880L

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
    fun `a tile shows how far open the curtain is and how old that reading is`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        val curtains = CurtainTiles(client())

        curtains.refresh()

        val tile = curtains.state.value.tiles.single()
        assertEquals("curtain-01", tile.id)
        assertEquals("Шторы", tile.name)
        assertEquals("Спальня", tile.room)
        assertEquals(0.0, tile.openPercent)
        assertEquals("0% open · 2 h ago", statusLine(tile, Instant.ofEpochSecond(lastRead + 2 * 3600), error = null))
    }

    @Test
    fun `the curtain does not land among the bulbs, nor a bulb among the curtains`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        server.enqueue(MockResponse(body = fixture()))
        val curtains = CurtainTiles(client())
        val bulbs = BulbTiles(client())

        curtains.refresh()
        bulbs.refresh()

        assertEquals(listOf("curtain-01"), curtains.state.value.tiles.map { it.id })
        assertEquals(18, bulbs.state.value.tiles.size)
        assertTrue(bulbs.state.value.tiles.none { it.id == "curtain-01" })
    }

    @Test
    fun `a curtain that has reported no position reads as unknown, not as closed`() = runTest {
        // 0% is a real position — the curtains are shut. A range that never reported is not that,
        // and a tile that prints "0% open" for it says the opposite of the truth.
        server.enqueue(MockResponse(body = fixtureWithoutCurtainPosition()))
        val curtains = CurtainTiles(client())

        curtains.refresh()

        val tile = curtains.state.value.tiles.single()
        assertNull(tile.openPercent)
        assertEquals("unknown · 2 h ago", statusLine(tile, Instant.ofEpochSecond(lastRead + 2 * 3600), error = null))
    }

    @Test
    fun `a failed poll keeps the last position and age, and says it is not updating`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        server.enqueue(MockResponse(code = 500, body = "boom"))
        val curtains = CurtainTiles(client())

        curtains.refresh()
        val before = curtains.state.value.tiles.single()
        curtains.refresh()
        val after = curtains.state.value

        assertNotNull(after.error)
        assertEquals(before, after.tiles.single())
        val now = Instant.ofEpochSecond(lastRead + 2 * 3600)
        assertTrue(statusLine(after.tiles.single(), now, after.error).startsWith("0% open · 2 h ago · not updating"))
    }

    @Test
    fun `a panel with no token stored says so instead of standing there empty`() = runTest {
        val curtains = CurtainTiles(client(token = { "" }))

        curtains.refresh()

        assertTrue(curtains.state.value.tiles.isEmpty())
        assertTrue(
            curtains.state.value.error.orEmpty().contains("token"),
            "the panel must name the missing token: ${curtains.state.value.error}",
        )
    }

    @Test
    fun `setting a position posts a range action for that curtain, then re-reads`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-1","devices":[]}"""))
        server.enqueue(MockResponse(body = fixture()))
        val curtains = CurtainTiles(client())

        curtains.refresh()
        curtains.setOpen("curtain-01", 70.0)

        server.takeRequest() // the first poll
        val action = server.takeRequest()
        assertEquals("/v1.0/devices/actions", action.target)
        val state = sentAction(action.body?.utf8())
        assertEquals("open", state["instance"]!!.jsonPrimitive.content)
        assertEquals("70", state["value"]!!.jsonPrimitive.content)
        // Yandex answers DONE without promising the curtain has moved, so the tile is repainted
        // from a fresh read rather than from the action result.
        assertEquals("/v1.0/user/info", server.takeRequest().target)
    }

    @Test
    fun `a position outside the range the curtain reports is snapped before it is sent`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-1","devices":[]}"""))
        server.enqueue(MockResponse(body = fixture()))
        val curtains = CurtainTiles(client())

        curtains.refresh()
        curtains.setOpen("curtain-01", 140.0)

        server.takeRequest()
        val state = sentAction(server.takeRequest().body?.utf8())
        // The curtain reports 0..100 with precision 1; anything else is a request Yandex can only
        // reject, and a rejected action is a tile that says "not updating" for no reason.
        assertEquals("100", state["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a position between two steps is snapped to one the curtain accepts`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-1","devices":[]}"""))
        server.enqueue(MockResponse(body = fixture()))
        val curtains = CurtainTiles(client())

        curtains.refresh()
        curtains.setOpen("curtain-01", 33.7)

        server.takeRequest()
        val state = sentAction(server.takeRequest().body?.utf8())
        assertEquals("34", state["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a set that fails leaves the tile alone and reports the failure`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        server.enqueue(MockResponse(code = 404, body = "unknown device"))
        val curtains = CurtainTiles(client())

        curtains.refresh()
        val before = curtains.state.value.tiles
        curtains.setOpen("curtain-01", 70.0)

        assertEquals(before, curtains.state.value.tiles)
        assertTrue(curtains.state.value.error.orEmpty().contains("404"))
    }

    @Test
    fun `a poll that recovers clears the error`() = runTest {
        server.enqueue(MockResponse(code = 500, body = "boom"))
        server.enqueue(MockResponse(body = fixture()))
        val curtains = CurtainTiles(client())

        curtains.refresh()
        assertNotNull(curtains.state.value.error)
        curtains.refresh()

        assertNull(curtains.state.value.error)
        assertEquals(0.0, curtains.state.value.tiles.single().openPercent)
    }

    private fun sentAction(body: String?): JsonObject {
        val device = Json.parseToJsonElement(requireNotNull(body) { "the action was sent with no body" })
            .jsonObject["devices"]!!
            .jsonArray
            .single()
        assertEquals("curtain-01", device.jsonObject["id"]!!.jsonPrimitive.content)
        val action = device.jsonObject["actions"]!!.jsonArray.single().jsonObject
        assertEquals("devices.capabilities.range", action["type"]!!.jsonPrimitive.content)
        return action["state"]!!.jsonObject
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

    /**
     * The recorded body with the curtain's `range` state dropped. The shape is not invented: the
     * same response carries `"state": null` on the TV's `channel` range — a capability that is
     * simply out of the panel's scope, so the curtain is where it can be asserted.
     */
    private fun fixtureWithoutCurtainPosition(): String {
        val root = Json.parseToJsonElement(fixture()).jsonObject
        val devices =
            root["devices"]!!.jsonArray.map { device ->
                if (device.jsonObject["id"]?.jsonPrimitive?.content != "curtain-01") {
                    device
                } else {
                    val capabilities =
                        device.jsonObject["capabilities"]!!.jsonArray.map { capability ->
                            if (capability.jsonObject["type"]?.jsonPrimitive?.content != "devices.capabilities.range") {
                                capability
                            } else {
                                JsonObject(capability.jsonObject - "state")
                            }
                        }
                    JsonObject(device.jsonObject + ("capabilities" to JsonArray(capabilities)))
                }
            }
        return JsonObject(root + ("devices" to JsonArray(devices))).toString()
    }
}
