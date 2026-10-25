package ru.domovoy.panel

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Position assertions use the second after the recorded timestamp to avoid fractional rounding. */
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
        server.enqueue(MockResponse(body = yandexFixture()))
        val poll = YandexPoll(server.yandexClient())
        val curtains = poll.curtains

        poll.refresh()

        val tile = curtains.state.value.tiles.single()
        assertEquals("curtain-01", tile.id)
        assertEquals("Шторы", tile.name)
        assertEquals("Спальня", tile.room)
        assertEquals(0.0, tile.openPercent)
        assertEquals("0% · 2 ч назад", statusLine(tile, Instant.ofEpochSecond(lastRead + 2 * 3600)))
    }

    @Test
    fun `the curtain does not land among the bulbs, nor a bulb among the curtains`() = runTest {
        server.enqueue(MockResponse(body = yandexFixture()))
        val poll = YandexPoll(server.yandexClient())

        poll.refresh()

        assertEquals(listOf("curtain-01"), poll.curtains.state.value.tiles.map { it.id })
        assertEquals(18, poll.bulbs.state.value.tiles.size)
        assertTrue(poll.bulbs.state.value.tiles.none { it.id == "curtain-01" })
    }

    @Test
    fun `a curtain that has reported no position reads as unknown, not as closed`() = runTest {
        // 0% is a real position — the curtains are shut. A range that never reported is not that,
        // and a tile that prints "0%" for it says the opposite of the truth.
        server.enqueue(MockResponse(body = fixtureWithoutCurtainPosition()))
        val poll = YandexPoll(server.yandexClient())
        val curtains = poll.curtains

        poll.refresh()

        val tile = curtains.state.value.tiles.single()
        assertNull(tile.openPercent)
        // Without a position, its timestamp must not imply a reading.
        assertEquals("Нет данных", statusLine(tile, Instant.ofEpochSecond(lastRead + 2 * 3600)))
    }

    @Test
    fun `a position the vendor has not confirmed for hours is no longer stated as the current one`() = runTest {
        // This curtain reports commanded values, not sensed positions; old commands go stale.
        // Verified behavior is recorded in docs/yandex.md.
        server.enqueue(MockResponse(body = yandexFixture()))
        val poll = YandexPoll(server.yandexClient())
        val curtains = poll.curtains

        poll.refresh()

        val tile = curtains.state.value.tiles.single()
        val now = Instant.ofEpochSecond(lastRead + 13 * 3600)
        // The number survives, in small type, with the age that is the whole point of it...
        assertEquals("0% · 13 ч назад", statusLine(tile, now))
        // ...and the wall stops saying it at four metres, in either of the two ways it could.
        assertNull(anatomy(tile, now, error = null).promoted)
        assertEquals(TileMood.Unknown, paint(tile, now, null).mood)
        // A curtain the panel cannot vouch for is not a curtain it will offer to open as though it
        // were certainly shut — the same answer an unread position gets.
        assertEquals(TileAction.Close, action(tile, now))
    }

    @Test
    fun `a curtain opened by voice reads as fully open at the next poll, not an hour later`() = runTest {
        // Voice commands update on_off but leave range/open unchanged. Use the newer command;
        // on_off=true means the advertised maximum. See docs/yandex.md.
        server.enqueue(MockResponse(body = fixtureWithCurtainOpenedByVoice()))
        val poll = YandexPoll(server.yandexClient())
        val curtains = poll.curtains

        poll.refresh()

        val tile = curtains.state.value.tiles.single()
        val now = Instant.ofEpochSecond(lastRead + 300)
        // The percentage the vendor still holds is the stale one, and the tile does not print it.
        assertEquals(0.0, tile.openPercent)
        assertEquals("3 мин назад", statusLine(tile, now))
        assertEquals("100%", anatomy(tile, now, error = null).promoted)
        assertEquals(TileMood.On, paint(tile, now, null).mood)
        assertEquals(TileAction.Close, action(tile, now))
    }

    @Test
    fun `a curtain closed by voice reads as shut, from the on_off the command left behind`() = runTest {
        // Both values say closed; assert the newer on/off source as well as the percentage.
        server.enqueue(MockResponse(body = fixtureWithCurtainClosedByVoice()))
        val poll = YandexPoll(server.yandexClient())
        val curtains = poll.curtains

        poll.refresh()

        val tile = curtains.state.value.tiles.single()
        val now = Instant.ofEpochSecond(lastRead + 300)
        assertEquals("3 мин назад", statusLine(tile, now))
        assertEquals(TileMood.Off, paint(tile, now, null).mood)
        assertEquals(TileAction.Open, action(tile, now))
    }

    @Test
    fun `a position read within the hour is still the panel's answer, zero included`() = runTest {
        // A recently confirmed zero is a valid closed position.
        server.enqueue(MockResponse(body = yandexFixture()))
        val poll = YandexPoll(server.yandexClient())
        val curtains = poll.curtains

        poll.refresh()

        val tile = curtains.state.value.tiles.single()
        val now = Instant.ofEpochSecond(lastRead + 120)
        assertEquals("2 мин назад", statusLine(tile, now))
        assertEquals("0%", anatomy(tile, now, error = null).promoted)
        assertEquals(TileMood.Off, paint(tile, now, null).mood)
        assertEquals(TileAction.Open, action(tile, now))
    }

    @Test
    fun `a failed poll keeps the last position and age, and says it is not updating`() = runTest {
        server.enqueue(MockResponse(body = yandexFixture()))
        server.enqueue(MockResponse(code = 500, body = "boom"))
        val poll = YandexPoll(server.yandexClient())
        val curtains = poll.curtains

        poll.refresh()
        val before = curtains.state.value.tiles.single()
        poll.refresh()
        val after = curtains.state.value

        assertNotNull(after.error)
        assertEquals(before, after.tiles.single())
        val now = Instant.ofEpochSecond(lastRead + 2 * 3600)
        assertEquals("0% · 2 ч назад", statusLine(after.tiles.single(), now))
        assertEquals("Не удалось выполнить запрос", anatomy(after.tiles.single(), now, after.error).detail)
    }

    @Test
    fun `a panel with no token stored reports a failed poll instead of standing there empty`() = runTest {
        // The sentence naming the token goes to `Log` now rather than to the wall — see
        // BulbTilesTest, which is where that trade is written down.
        val poll = YandexPoll(server.yandexClient(token = { "" }))
        val curtains = poll.curtains

        poll.refresh()

        assertTrue(curtains.state.value.tiles.isEmpty())
        assertEquals("Не удалось выполнить запрос", curtains.state.value.error)
    }

    @Test
    fun `setting a position posts a range action for that curtain, then re-reads`() = runTest {
        server.enqueue(MockResponse(body = yandexFixture()))
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-1","devices":[]}"""))
        server.enqueue(MockResponse(body = yandexFixture()))
        val poll = YandexPoll(server.yandexClient())
        val curtains = poll.curtains

        poll.refresh()
        curtains.setOpen("curtain-01", 70.0)

        server.takeRequest() // the first poll
        val action = server.takeRequest()
        assertEquals("/v1.0/devices/actions", action.target)
        val state = sentYandexAction(action.body?.utf8(), deviceId = "curtain-01")
        assertEquals("open", state["instance"]!!.jsonPrimitive.content)
        assertEquals("70", state["value"]!!.jsonPrimitive.content)
        // Yandex answers DONE without promising the curtain has moved, so the tile is repainted
        // from a fresh read rather than from the action result.
        assertEquals("/v1.0/user/info", server.takeRequest().target)
    }

    @Test
    fun `a position outside the range the curtain reports is snapped before it is sent`() = runTest {
        server.enqueue(MockResponse(body = yandexFixture()))
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-1","devices":[]}"""))
        server.enqueue(MockResponse(body = yandexFixture()))
        val poll = YandexPoll(server.yandexClient())
        val curtains = poll.curtains

        poll.refresh()
        curtains.setOpen("curtain-01", 140.0)

        server.takeRequest()
        val state = sentYandexAction(server.takeRequest().body?.utf8(), deviceId = "curtain-01")
        // The curtain reports 0..100 with precision 1; anything else is a request Yandex can only
        // reject, and a rejected action is a tile that says "not updating" for no reason.
        assertEquals("100", state["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a position between two steps is snapped to one the curtain accepts`() = runTest {
        server.enqueue(MockResponse(body = yandexFixture()))
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-1","devices":[]}"""))
        server.enqueue(MockResponse(body = yandexFixture()))
        val poll = YandexPoll(server.yandexClient())
        val curtains = poll.curtains

        poll.refresh()
        curtains.setOpen("curtain-01", 33.7)

        server.takeRequest()
        val state = sentYandexAction(server.takeRequest().body?.utf8(), deviceId = "curtain-01")
        assertEquals("34", state["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a set that fails leaves the tile alone and reports the failure`() = runTest {
        server.enqueue(MockResponse(body = yandexFixture()))
        server.enqueue(MockResponse(code = 404, body = "unknown device"))
        val poll = YandexPoll(server.yandexClient())
        val curtains = poll.curtains

        poll.refresh()
        val before = curtains.state.value.tiles
        curtains.setOpen("curtain-01", 70.0)

        assertEquals(before, curtains.state.value.tiles)
        assertEquals("Не удалось выполнить запрос", curtains.state.value.commandErrors["curtain-01"])
        assertNull(curtains.state.value.error)
    }

    @Test
    fun `a poll that recovers clears the error`() = runTest {
        server.enqueue(MockResponse(code = 500, body = "boom"))
        server.enqueue(MockResponse(body = yandexFixture()))
        val poll = YandexPoll(server.yandexClient())
        val curtains = poll.curtains

        poll.refresh()
        assertNotNull(curtains.state.value.error)
        poll.refresh()

        assertNull(curtains.state.value.error)
        assertEquals(0.0, curtains.state.value.tiles.single().openPercent)
    }

    /** Voice-open shape: on/off and state_changed_at are newer than the stored position. */
    private fun fixtureWithCurtainOpenedByVoice(): String = curtainOnOff(on = true)

    /** An unchanged close command advances last_updated while state_changed_at stays zero. */
    private fun fixtureWithCurtainClosedByVoice(): String = curtainOnOff(on = false)

    private fun curtainOnOff(on: Boolean): String = curtainCapability("devices.capabilities.on_off") { capability ->
        JsonObject(
            capability +
                ("last_updated" to JsonPrimitive(lastRead + 120)) +
                ("state_changed_at" to JsonPrimitive(if (on) lastRead + 120 else 0)) +
                ("state" to JsonObject(mapOf("instance" to JsonPrimitive("on"), "value" to JsonPrimitive(on)))),
        )
    }

    /** Remove range state, matching a null range state already present in the recorded response. */
    private fun fixtureWithoutCurtainPosition(): String = curtainCapability("devices.capabilities.range") { capability ->
        JsonObject(capability - "state")
    }

    /** The recorded body with one of `curtain-01`'s capabilities rewritten, and nothing else moved. */
    private fun curtainCapability(
        type: String,
        rewrite: (JsonObject) -> JsonObject,
    ): String {
        val root = Json.parseToJsonElement(yandexFixture()).jsonObject
        val devices =
            root["devices"]!!.jsonArray.map { device ->
                if (device.jsonObject["id"]?.jsonPrimitive?.content != "curtain-01") {
                    device
                } else {
                    val capabilities =
                        device.jsonObject["capabilities"]!!.jsonArray.map { capability ->
                            if (capability.jsonObject["type"]?.jsonPrimitive?.content != type) {
                                capability
                            } else {
                                rewrite(capability.jsonObject)
                            }
                        }
                    JsonObject(device.jsonObject + ("capabilities" to JsonArray(capabilities)))
                }
            }
        return JsonObject(root + ("devices" to JsonArray(devices))).toString()
    }
}
