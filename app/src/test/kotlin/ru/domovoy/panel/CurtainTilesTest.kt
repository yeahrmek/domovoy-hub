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
        val poll = YandexPoll(client())
        val curtains = poll.curtains

        poll.refresh()

        val tile = curtains.state.value.tiles.single()
        assertEquals("curtain-01", tile.id)
        assertEquals("Шторы", tile.name)
        assertEquals("Спальня", tile.room)
        assertEquals(0.0, tile.openPercent)
        assertEquals("0% open · 2 h ago", statusLine(tile, Instant.ofEpochSecond(lastRead + 2 * 3600)))
    }

    @Test
    fun `the curtain does not land among the bulbs, nor a bulb among the curtains`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        val poll = YandexPoll(client())

        poll.refresh()

        assertEquals(listOf("curtain-01"), poll.curtains.state.value.tiles.map { it.id })
        assertEquals(18, poll.bulbs.state.value.tiles.size)
        assertTrue(poll.bulbs.state.value.tiles.none { it.id == "curtain-01" })
    }

    @Test
    fun `a curtain that has reported no position reads as unknown, not as closed`() = runTest {
        // 0% is a real position — the curtains are shut. A range that never reported is not that,
        // and a tile that prints "0% open" for it says the opposite of the truth.
        server.enqueue(MockResponse(body = fixtureWithoutCurtainPosition()))
        val poll = YandexPoll(client())
        val curtains = poll.curtains

        poll.refresh()

        val tile = curtains.state.value.tiles.single()
        assertNull(tile.openPercent)
        // And the age goes with the value it was about. The capability carries a `last_updated`,
        // but there is no position it is the age *of* — "unknown · 2 h ago" claimed to have read
        // something two hours ago, which is exactly what this test refuses one line up.
        assertEquals("unknown", statusLine(tile, Instant.ofEpochSecond(lastRead + 2 * 3600)))
    }

    @Test
    fun `a position the vendor has not confirmed for hours is no longer stated as the current one`() = runTest {
        // 2026-08-31, on the wall: the tile said "0% open" at display size while the curtain stood
        // open. Nothing was wrong with the read — Yandex itself answered `open: 0`, `last_updated`
        // thirteen hours earlier, which is the moment the panel last drove the curtain. This device
        // reports no position back at all — not even for a move a Yandex station made on Yandex's
        // own hub — so what the panel holds is a memory of its own last write. See docs/yandex.md.
        server.enqueue(MockResponse(body = fixture()))
        val poll = YandexPoll(client())
        val curtains = poll.curtains

        poll.refresh()

        val tile = curtains.state.value.tiles.single()
        val now = Instant.ofEpochSecond(lastRead + 13 * 3600)
        // The number survives, in small type, with the age that is the whole point of it...
        assertEquals("0% open · 13 h ago", statusLine(tile, now))
        // ...and the wall stops saying it at four metres, in either of the two ways it could.
        assertNull(anatomy(tile, now, error = null).promoted)
        assertEquals(TileMood.Unknown, paint(tile, now, null).mood)
        // A curtain the panel cannot vouch for is not a curtain it will offer to open as though it
        // were certainly shut — the same answer an unread position gets.
        assertEquals(TileAction.Close, action(tile, now))
    }

    @Test
    fun `a curtain opened by voice reads as fully open at the next poll, not an hour later`() = runTest {
        // 2026-08-31, watched live: "Алиса, открой шторы" opened the curtain fully and left
        // `range/open` untouched at its old value — a percentage nothing had commanded since the
        // evening before. What the command did write was `on_off`: `true`, stamped as it happened.
        // Neither capability on this device is a sensor, so the panel takes the newer of the two,
        // and an open is the top of the range the curtain itself reported. See docs/yandex.md.
        server.enqueue(MockResponse(body = fixtureWithCurtainOpenedByVoice()))
        val poll = YandexPoll(client())
        val curtains = poll.curtains

        poll.refresh()

        val tile = curtains.state.value.tiles.single()
        val now = Instant.ofEpochSecond(lastRead + 300)
        // The percentage the vendor still holds is the stale one, and the tile does not print it.
        assertEquals(0.0, tile.openPercent)
        assertEquals("100% open", statusLine(tile, now))
        assertEquals("100% open", anatomy(tile, now, error = null).promoted)
        assertEquals(TileMood.On, paint(tile, now, null).mood)
        assertEquals(TileAction.Close, action(tile, now))
    }

    @Test
    fun `a curtain closed by voice reads as shut, from the on_off the command left behind`() = runTest {
        // The other half, and the one that catches a wrong position rather than a stale one: the
        // recorded percentage is 0 here, so this asserts the *source* rather than the number — the
        // on/off is newer, and it says closed.
        server.enqueue(MockResponse(body = fixtureWithCurtainClosedByVoice()))
        val poll = YandexPoll(client())
        val curtains = poll.curtains

        poll.refresh()

        val tile = curtains.state.value.tiles.single()
        val now = Instant.ofEpochSecond(lastRead + 300)
        assertEquals("0% open", statusLine(tile, now))
        assertEquals(TileMood.Off, paint(tile, now, null).mood)
        assertEquals(TileAction.Open, action(tile, now))
    }

    @Test
    fun `a position read within the hour is still the panel's answer, zero included`() = runTest {
        // The other half of the rule, and the reason it is an age and not a blanket refusal: 0% is
        // a real position — the curtains are shut — and a reading Yandex confirmed minutes ago is
        // exactly what the wall exists to show.
        server.enqueue(MockResponse(body = fixture()))
        val poll = YandexPoll(client())
        val curtains = poll.curtains

        poll.refresh()

        val tile = curtains.state.value.tiles.single()
        val now = Instant.ofEpochSecond(lastRead + 120)
        assertEquals("0% open", statusLine(tile, now))
        assertEquals("0% open", anatomy(tile, now, error = null).promoted)
        assertEquals(TileMood.Off, paint(tile, now, null).mood)
        assertEquals(TileAction.Open, action(tile, now))
    }

    @Test
    fun `a failed poll keeps the last position and age, and says it is not updating`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        server.enqueue(MockResponse(code = 500, body = "boom"))
        val poll = YandexPoll(client())
        val curtains = poll.curtains

        poll.refresh()
        val before = curtains.state.value.tiles.single()
        poll.refresh()
        val after = curtains.state.value

        assertNotNull(after.error)
        assertEquals(before, after.tiles.single())
        val now = Instant.ofEpochSecond(lastRead + 2 * 3600)
        assertEquals("0% open · 2 h ago", statusLine(after.tiles.single(), now))
        assertEquals("failed", anatomy(after.tiles.single(), now, after.error).detail)
    }

    @Test
    fun `a panel with no token stored reports a failed poll instead of standing there empty`() = runTest {
        // The sentence naming the token goes to `Log` now rather than to the wall — see
        // BulbTilesTest, which is where that trade is written down.
        val poll = YandexPoll(client(token = { "" }))
        val curtains = poll.curtains

        poll.refresh()

        assertTrue(curtains.state.value.tiles.isEmpty())
        assertEquals("failed", curtains.state.value.error)
    }

    @Test
    fun `setting a position posts a range action for that curtain, then re-reads`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-1","devices":[]}"""))
        server.enqueue(MockResponse(body = fixture()))
        val poll = YandexPoll(client())
        val curtains = poll.curtains

        poll.refresh()
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
        val poll = YandexPoll(client())
        val curtains = poll.curtains

        poll.refresh()
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
        val poll = YandexPoll(client())
        val curtains = poll.curtains

        poll.refresh()
        curtains.setOpen("curtain-01", 33.7)

        server.takeRequest()
        val state = sentAction(server.takeRequest().body?.utf8())
        assertEquals("34", state["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a set that fails leaves the tile alone and reports the failure`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        server.enqueue(MockResponse(code = 404, body = "unknown device"))
        val poll = YandexPoll(client())
        val curtains = poll.curtains

        poll.refresh()
        val before = curtains.state.value.tiles
        curtains.setOpen("curtain-01", 70.0)

        assertEquals(before, curtains.state.value.tiles)
        assertEquals("failed", curtains.state.value.error)
    }

    @Test
    fun `a poll that recovers clears the error`() = runTest {
        server.enqueue(MockResponse(code = 500, body = "boom"))
        server.enqueue(MockResponse(body = fixture()))
        val poll = YandexPoll(client())
        val curtains = poll.curtains

        poll.refresh()
        assertNotNull(curtains.state.value.error)
        poll.refresh()

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
     * The recorded body with the curtain's `on_off` stamped two minutes *after* its position and
     * carrying `true` — the shape a spoken "открой шторы" leaves behind, watched live on 2026-08-31
     * and recorded in docs/yandex.md, `state_changed_at` included because the value changed.
     */
    private fun fixtureWithCurtainOpenedByVoice(): String = curtainOnOff(on = true)

    /**
     * The same for "закрой шторы", where the value was already `false`: the command moved the
     * capability's clock and left `state_changed_at` at `0.0`, which is the pair of timestamps
     * Yandex uses for a value written without changing.
     */
    private fun fixtureWithCurtainClosedByVoice(): String = curtainOnOff(on = false)

    private fun curtainOnOff(on: Boolean): String = curtainCapability("devices.capabilities.on_off") { capability ->
        JsonObject(
            capability +
                ("last_updated" to JsonPrimitive(lastRead + 120)) +
                ("state_changed_at" to JsonPrimitive(if (on) lastRead + 120 else 0)) +
                ("state" to JsonObject(mapOf("instance" to JsonPrimitive("on"), "value" to JsonPrimitive(on)))),
        )
    }

    /**
     * The recorded body with the curtain's `range` state dropped. The shape is not invented: the
     * same response carries `"state": null` on the TV's `channel` range — a capability that is
     * simply out of the panel's scope, so the curtain is where it can be asserted.
     */
    private fun fixtureWithoutCurtainPosition(): String = curtainCapability("devices.capabilities.range") { capability ->
        JsonObject(capability - "state")
    }

    /** The recorded body with one of `curtain-01`'s capabilities rewritten, and nothing else moved. */
    private fun curtainCapability(
        type: String,
        rewrite: (JsonObject) -> JsonObject,
    ): String {
        val root = Json.parseToJsonElement(fixture()).jsonObject
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
