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
import ru.domovoy.core.Bounds
import ru.domovoy.core.Reading
import ru.domovoy.integrations.yandex.YandexClient
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The two GLEDOPTO strips of the flat, which the bulb tile's exact type match left off the wall.
 *
 * Every capability on both of them carries `last_updated: 0.0`, so every age below is "never read".
 * That is what the recorded response says and it is the point rather than an inconvenience: the
 * strips are the panel's first tiles whose values are all there and whose read times are all
 * missing, and printing 1 Jan 1970 for them would be worse than saying nothing.
 */
class LightStripTilesTest {
    /** Long after everything in the recorded response; every strip age is "never read" regardless. */
    private val now = Instant.ofEpochSecond(1_786_790_000L)

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
    fun `both strips of the flat become a tile with a name, a room, on-off and brightness`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        val poll = YandexPoll(client())
        val strips = poll.strips

        poll.refresh()

        assertEquals(listOf("light-strip-01", "light-strip-02"), strips.state.value.tiles.map { it.id })
        val tile = strips.state.value.tiles.single { it.id == "light-strip-01" }
        assertEquals("Подсветка в зале", tile.name)
        assertEquals("Зал", tile.room)
        assertEquals(true, tile.isOn)
        assertEquals(26.0, tile.brightnessPercent)
        assertEquals("on · 26% · never read", statusLine(tile, now))
    }

    @Test
    fun `brightness is the same Range model the curtain's open percent is`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        val poll = YandexPoll(client())

        poll.refresh()

        // 1..100, not 0..100: the bottom of a strip's brightness is dim, not off, and the snapping
        // below depends on the panel carrying what the device reported rather than a guess.
        val tile = poll.strips.state.value.tiles.single { it.id == "light-strip-01" }
        assertEquals(Bounds(min = 1.0, max = 100.0, precision = 1.0), tile.bounds)
        assertEquals("unit.percent", tile.unit)
        assertEquals(Reading.Never, tile.brightnessLastUpdated)
    }

    @Test
    fun `a strip is a tile of its own and is not also a bulb, a curtain or an air conditioner`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        val poll = YandexPoll(client())

        poll.refresh()

        val others =
            poll.bulbs.state.value.tiles.map { it.id } +
                poll.curtains.state.value.tiles.map { it.id } +
                poll.acs.state.value.tiles.map { it.id }
        assertTrue(others.none { it.startsWith("light-strip-") }, "a strip leaked into another group: $others")
        // The bulb group is exactly what it was before the strips existed.
        assertEquals(18, poll.bulbs.state.value.tiles.size)
    }

    @Test
    fun `the colour is shown, and the tile says it cannot be driven`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        val poll = YandexPoll(client())

        poll.refresh()

        val tile = poll.strips.state.value.tiles.single { it.id == "light-strip-01" }
        assertEquals("temperature_k", tile.color?.instance)
        assertEquals(2700.0, tile.color?.value)
        assertEquals("2700 K · not controllable", colorLine(tile))
    }

    @Test
    fun `a strip whose colour never reported says unknown rather than dropping the line`() = runTest {
        // light-strip-02 carries "state": null on its color_setting — a capability that is there
        // and has never reported, which is a different thing from having no colour at all.
        server.enqueue(MockResponse(body = fixture()))
        val poll = YandexPoll(client())

        poll.refresh()

        val tile = poll.strips.state.value.tiles.single { it.id == "light-strip-02" }
        val color = assertNotNull(tile.color)
        assertNull(color.instance)
        assertNull(color.value)
        assertEquals("unknown · not controllable", colorLine(tile))
    }

    @Test
    fun `an rgb colour is printed as its hex rather than as a raw number`() = runTest {
        // light-21 reports colour this way in the same recorded response — instance "rgb", value
        // 16777200 — so this is a shape the panel has seen, grafted onto a strip that can show it.
        server.enqueue(MockResponse(body = fixtureWithStripColorState(instance = "rgb", value = 16_777_200)))
        val poll = YandexPoll(client())

        poll.refresh()

        val tile = poll.strips.state.value.tiles.single { it.id == "light-strip-01" }
        assertEquals("#FFFFF0 · not controllable", colorLine(tile))
    }

    @Test
    fun `a strip with no colour capability at all has no colour line to print`() = runTest {
        server.enqueue(MockResponse(body = fixtureWithoutStripColor()))
        val poll = YandexPoll(client())

        poll.refresh()

        val tile = poll.strips.state.value.tiles.single { it.id == "light-strip-01" }
        assertNull(tile.color)
        assertNull(colorLine(tile))
    }

    @Test
    fun `a strip that reported no brightness reads as unknown, not as the bottom of its range`() = runTest {
        // 1% is a real setting — it is what light-21 is at. A range that never reported is not
        // that, and a tile that prints the bottom of the range for it says the opposite of the truth.
        server.enqueue(MockResponse(body = fixtureWithStripBrightnessParts(dropState = true)))
        val poll = YandexPoll(client())

        poll.refresh()

        val tile = poll.strips.state.value.tiles.single { it.id == "light-strip-01" }
        assertNull(tile.brightnessPercent)
        assertEquals("on · unknown · never read", statusLine(tile, now))
    }

    @Test
    fun `a brightness range with no unit prints the number without inventing a percent sign`() = runTest {
        server.enqueue(MockResponse(body = fixtureWithStripBrightnessParts(dropUnit = true)))
        val poll = YandexPoll(client())

        poll.refresh()

        val tile = poll.strips.state.value.tiles.single { it.id == "light-strip-01" }
        assertEquals("on · 26 · never read", statusLine(tile, now))
    }

    @Test
    fun `a strip that reported no on-off reads as unknown, not as off`() = runTest {
        server.enqueue(MockResponse(body = fixtureWithoutStripPower()))
        val poll = YandexPoll(client())

        poll.refresh()

        val tile = poll.strips.state.value.tiles.single { it.id == "light-strip-01" }
        assertNull(tile.isOn)
        assertEquals("unknown · 26% · never read", statusLine(tile, now))
    }

    @Test
    fun `setting the brightness posts a range action for that strip, then re-reads`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-1","devices":[]}"""))
        server.enqueue(MockResponse(body = fixture()))
        val poll = YandexPoll(client())

        poll.refresh()
        poll.strips.setBrightness("light-strip-02", 40.0)

        server.takeRequest() // the first poll
        val action = server.takeRequest()
        assertEquals("/v1.0/devices/actions", action.target)
        val state = sentAction(action.body?.utf8(), deviceId = "light-strip-02")
        assertEquals("brightness", state["instance"]!!.jsonPrimitive.content)
        assertEquals("40", state["value"]!!.jsonPrimitive.content)
        // Yandex answers DONE without promising the strip has changed, so the tile is repainted
        // from a fresh read rather than from the action result.
        assertEquals("/v1.0/user/info", server.takeRequest().target)
    }

    @Test
    fun `a brightness below the range the strip reports is snapped before it is sent`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-1","devices":[]}"""))
        server.enqueue(MockResponse(body = fixture()))
        val poll = YandexPoll(client())

        poll.refresh()
        poll.strips.setBrightness("light-strip-01", 0.0)

        server.takeRequest()
        val state = sentAction(server.takeRequest().body?.utf8(), deviceId = "light-strip-01")
        // The strip reports 1..100: a slider dragged to the bottom hands over 0, which Yandex can
        // only reject, and a rejected action reaches the wall as "not updating" for a reason of ours.
        assertEquals("1", state["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun `turning a strip off posts the on-off action for that one strip, then re-reads`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-1","devices":[]}"""))
        server.enqueue(MockResponse(body = fixture()))
        val poll = YandexPoll(client())

        poll.refresh()
        poll.strips.toggle("light-strip-01")

        server.takeRequest()
        val state =
            sentAction(
                server.takeRequest().body?.utf8(),
                deviceId = "light-strip-01",
                type = "devices.capabilities.on_off",
            )
        // Both strips are on in the recorded response, so the tap turns this one off.
        assertEquals("false", state["value"]!!.jsonPrimitive.content)
        assertEquals("/v1.0/user/info", server.takeRequest().target)
    }

    @Test
    fun `a strip whose on-off never reported is turned on by the first tap, not off`() = runTest {
        server.enqueue(MockResponse(body = fixtureWithoutStripPower()))
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-1","devices":[]}"""))
        server.enqueue(MockResponse(body = fixtureWithoutStripPower()))
        val poll = YandexPoll(client())

        poll.refresh()
        poll.strips.toggle("light-strip-01")

        server.takeRequest()
        val state =
            sentAction(
                server.takeRequest().body?.utf8(),
                deviceId = "light-strip-01",
                type = "devices.capabilities.on_off",
            )
        assertEquals("true", state["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a failed poll keeps the last values and ages, and says it is not updating`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        server.enqueue(MockResponse(code = 500, body = "boom"))
        val poll = YandexPoll(client())
        val strips = poll.strips

        poll.refresh()
        val before = strips.state.value.tiles
        poll.refresh()
        val after = strips.state.value

        assertNotNull(after.error)
        assertEquals(before, after.tiles)
        val tile = after.tiles.single { it.id == "light-strip-01" }
        // The values and the age stay put on the first line; the reason is the second one, where it
        // takes the place of the colour this strip reports — bad news outranks a second reading,
        // because a second reading is stale by definition once the poll behind it stopped landing.
        assertEquals("on · 26% · never read", statusLine(tile, now))
        assertEquals("failed", anatomy(tile, now, after.error).detail)
    }

    @Test
    fun `a set that fails leaves the tiles alone and reports the failure`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        server.enqueue(MockResponse(code = 404, body = "unknown device"))
        val poll = YandexPoll(client())
        val strips = poll.strips

        poll.refresh()
        val before = strips.state.value.tiles
        strips.setBrightness("light-strip-01", 50.0)

        assertEquals(before, strips.state.value.tiles)
        assertEquals("failed", strips.state.value.error)
    }

    @Test
    fun `a panel with no token stored reports a failed poll instead of standing there empty`() = runTest {
        // The sentence naming the token goes to `Log` now rather than to the wall — see
        // BulbTilesTest, which is where that trade is written down.
        val poll = YandexPoll(client(token = { "" }))
        val strips = poll.strips

        poll.refresh()

        assertTrue(strips.state.value.tiles.isEmpty())
        assertEquals("failed", strips.state.value.error)
    }

    @Test
    fun `a poll that recovers clears the error`() = runTest {
        server.enqueue(MockResponse(code = 500, body = "boom"))
        server.enqueue(MockResponse(body = fixture()))
        val poll = YandexPoll(client())
        val strips = poll.strips

        poll.refresh()
        assertNotNull(strips.state.value.error)
        poll.refresh()

        assertNull(strips.state.value.error)
        assertEquals(26.0, strips.state.value.tiles.single { it.id == "light-strip-01" }.brightnessPercent)
    }

    private fun sentAction(
        body: String?,
        deviceId: String,
        type: String = "devices.capabilities.range",
    ): JsonObject {
        val device = Json.parseToJsonElement(requireNotNull(body) { "the action was sent with no body" })
            .jsonObject["devices"]!!
            .jsonArray
            .single()
        assertEquals(deviceId, device.jsonObject["id"]!!.jsonPrimitive.content)
        val action = device.jsonObject["actions"]!!.jsonArray.single().jsonObject
        assertEquals(type, action["type"]!!.jsonPrimitive.content)
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

    /** The recorded body with light-strip-01's `range/brightness` state and/or unit dropped. */
    private fun fixtureWithStripBrightnessParts(
        dropState: Boolean = false,
        dropUnit: Boolean = false,
    ): String = editStripCapability("devices.capabilities.range") { capability ->
        val withoutState = if (dropState) capability - "state" else capability
        if (!dropUnit) {
            JsonObject(withoutState)
        } else {
            val parameters = JsonObject(capability["parameters"]!!.jsonObject - "unit")
            JsonObject(withoutState + ("parameters" to parameters))
        }
    }

    /** The recorded body with light-strip-01's `on_off` state dropped, as light-strip-02's colour is. */
    private fun fixtureWithoutStripPower(): String = editStripCapability("devices.capabilities.on_off") {
        JsonObject(it - "state" - "last_updated" - "state_changed_at")
    }

    /** The recorded body with light-strip-01's `color_setting` state replaced by another shape. */
    private fun fixtureWithStripColorState(
        instance: String,
        value: Int,
    ): String = editStripCapability("devices.capabilities.color_setting") { capability ->
        val state = JsonObject(mapOf("instance" to JsonPrimitive(instance), "value" to JsonPrimitive(value)))
        JsonObject(capability + ("state" to state))
    }

    /** The recorded body with light-strip-01's `color_setting` capability removed outright. */
    private fun fixtureWithoutStripColor(): String {
        val root = Json.parseToJsonElement(fixture()).jsonObject
        val devices =
            root["devices"]!!.jsonArray.map { device ->
                if (device.jsonObject["id"]?.jsonPrimitive?.content != "light-strip-01") {
                    device
                } else {
                    val capabilities =
                        device.jsonObject["capabilities"]!!.jsonArray.filter {
                            it.jsonObject["type"]?.jsonPrimitive?.content != "devices.capabilities.color_setting"
                        }
                    JsonObject(device.jsonObject + ("capabilities" to JsonArray(capabilities)))
                }
            }
        return JsonObject(root + ("devices" to JsonArray(devices))).toString()
    }

    private fun editStripCapability(
        type: String,
        edit: (JsonObject) -> JsonObject,
    ): String {
        val root = Json.parseToJsonElement(fixture()).jsonObject
        val devices =
            root["devices"]!!.jsonArray.map { device ->
                if (device.jsonObject["id"]?.jsonPrimitive?.content != "light-strip-01") {
                    device
                } else {
                    val capabilities =
                        device.jsonObject["capabilities"]!!.jsonArray.map { capability ->
                            if (capability.jsonObject["type"]?.jsonPrimitive?.content != type) {
                                capability
                            } else {
                                edit(capability.jsonObject)
                            }
                        }
                    JsonObject(device.jsonObject + ("capabilities" to JsonArray(capabilities)))
                }
            }
        return JsonObject(root + ("devices" to JsonArray(devices))).toString()
    }
}
