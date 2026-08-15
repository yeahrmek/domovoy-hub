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
import ru.domovoy.core.Bounds
import ru.domovoy.integrations.yandex.YandexClient
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The air conditioner is the panel's first tile whose two values were read at different times:
 * ac-01 reported its on/off on 1785174334.35 and its target temperature 81 days earlier, on
 * 1778169164.79. One age for both would be a lie about one of them, so the tile prints two — and
 * every "now" below is offset from the whole second after the newer of the pair.
 */
class AcTilesTest {
    private val powerRead = 1_785_174_335L

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
    fun `a tile shows whether the ac is on, its target temperature and the age of each reading`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        val acs = AcTiles(client())

        acs.refresh()

        val tile = acs.state.value.tiles.single { it.id == "ac-01" }
        assertEquals("Residential air conditioner", tile.name)
        assertEquals("Детская", tile.room)
        assertEquals(false, tile.isOn)
        assertEquals(18.0, tile.targetTemperature)
        assertEquals(Bounds(min = 16.0, max = 32.0, precision = 1.0), tile.bounds)
        assertEquals("off · 2 h ago · 18 °C · 81 d ago", statusLine(tile, now(hours = 2), error = null))
    }

    @Test
    fun `all three air conditioners of the flat become tiles, and none of them a bulb or a curtain`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        server.enqueue(MockResponse(body = fixture()))
        server.enqueue(MockResponse(body = fixture()))
        val acs = AcTiles(client())
        val bulbs = BulbTiles(client())
        val curtains = CurtainTiles(client())

        acs.refresh()
        bulbs.refresh()
        curtains.refresh()

        assertEquals(listOf("ac-01", "ac-02", "ac-03"), acs.state.value.tiles.map { it.id })
        assertEquals(18, bulbs.state.value.tiles.size)
        assertEquals(listOf("curtain-01"), curtains.state.value.tiles.map { it.id })
        assertTrue((bulbs.state.value.tiles.map { it.id } + curtains.state.value.tiles.map { it.id }).none { it.startsWith("ac-") })
    }

    @Test
    fun `an ac that reported no temperature reads as unknown, not as the bottom of its range`() = runTest {
        // 16 °C is a real setting — it is what ac-03 is set to. A range that never reported is not
        // that, and a tile that prints the bottom of the range for it says the opposite of the truth.
        server.enqueue(MockResponse(body = fixtureWithAcRangeParts(dropState = true)))
        val acs = AcTiles(client())

        acs.refresh()

        val tile = acs.state.value.tiles.single { it.id == "ac-01" }
        assertNull(tile.targetTemperature)
        assertEquals("off · 2 h ago · unknown · 81 d ago", statusLine(tile, now(hours = 2), error = null))
    }

    @Test
    fun `an ac that reported no on-off reads as unknown, not as off`() = runTest {
        server.enqueue(MockResponse(body = fixtureWithoutAcPower()))
        val acs = AcTiles(client())

        acs.refresh()

        val tile = acs.state.value.tiles.single { it.id == "ac-01" }
        assertNull(tile.isOn)
        assertEquals("unknown · never read · 18 °C · 81 d ago", statusLine(tile, now(hours = 2), error = null))
    }

    @Test
    fun `a temperature range with no unit prints the number without inventing one`() = runTest {
        // The same recorded response carries "" as the unit on the TV's volume range, so a range
        // that names no unit is a shape this panel has actually seen.
        server.enqueue(MockResponse(body = fixtureWithAcRangeParts(dropUnit = true)))
        val acs = AcTiles(client())

        acs.refresh()

        val tile = acs.state.value.tiles.single { it.id == "ac-01" }
        assertEquals("off · 2 h ago · 18 · 81 d ago", statusLine(tile, now(hours = 2), error = null))
    }

    @Test
    fun `setting a temperature posts a range action for that ac, then re-reads`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-1","devices":[]}"""))
        server.enqueue(MockResponse(body = fixture()))
        val acs = AcTiles(client())

        acs.refresh()
        acs.setTemperature("ac-02", 24.0)

        server.takeRequest() // the first poll
        val action = server.takeRequest()
        assertEquals("/v1.0/devices/actions", action.target)
        val state = sentAction(action.body?.utf8(), deviceId = "ac-02")
        assertEquals("temperature", state["instance"]!!.jsonPrimitive.content)
        assertEquals("24", state["value"]!!.jsonPrimitive.content)
        // Yandex answers DONE without promising the ac has changed, so the tile is repainted from
        // a fresh read rather than from the action result.
        assertEquals("/v1.0/user/info", server.takeRequest().target)
    }

    @Test
    fun `a temperature outside the range the ac reports is snapped before it is sent`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-1","devices":[]}"""))
        server.enqueue(MockResponse(body = fixture()))
        val acs = AcTiles(client())

        acs.refresh()
        acs.setTemperature("ac-01", 40.0)

        server.takeRequest()
        val state = sentAction(server.takeRequest().body?.utf8(), deviceId = "ac-01")
        // The ac reports 16..32 with precision 1; anything else is a request Yandex can only
        // reject, and a rejected action is a tile that says "not updating" for no reason.
        assertEquals("32", state["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun `turning an ac on posts the on-off action for that one ac, then re-reads`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-1","devices":[]}"""))
        server.enqueue(MockResponse(body = fixture()))
        val acs = AcTiles(client())

        acs.refresh()
        acs.toggle("ac-03")

        server.takeRequest()
        val action = server.takeRequest()
        val body = Json.parseToJsonElement(requireNotNull(action.body?.utf8())).jsonObject
        val device = body["devices"]!!.jsonArray.single().jsonObject
        assertEquals("ac-03", device["id"]!!.jsonPrimitive.content)
        val sent = device["actions"]!!.jsonArray.single().jsonObject
        assertEquals("devices.capabilities.on_off", sent["type"]!!.jsonPrimitive.content)
        // ac-03 is off in the recorded response, so the tap turns it on.
        assertEquals("true", sent["state"]!!.jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("/v1.0/user/info", server.takeRequest().target)
    }

    @Test
    fun `an ac whose on-off never reported is turned on by the first tap, not off`() = runTest {
        server.enqueue(MockResponse(body = fixtureWithoutAcPower()))
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-1","devices":[]}"""))
        server.enqueue(MockResponse(body = fixtureWithoutAcPower()))
        val acs = AcTiles(client())

        acs.refresh()
        acs.toggle("ac-01")

        server.takeRequest()
        val state = sentAction(server.takeRequest().body?.utf8(), deviceId = "ac-01", type = "devices.capabilities.on_off")
        assertEquals("true", state["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a failed poll keeps the last values and ages, and says it is not updating`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        server.enqueue(MockResponse(code = 500, body = "boom"))
        val acs = AcTiles(client())

        acs.refresh()
        val before = acs.state.value.tiles
        acs.refresh()
        val after = acs.state.value

        assertNotNull(after.error)
        assertEquals(before, after.tiles)
        val tile = after.tiles.single { it.id == "ac-01" }
        assertTrue(
            statusLine(tile, now(hours = 2), after.error).startsWith("off · 2 h ago · 18 °C · 81 d ago · not updating"),
            "the tile has to keep its values and say why they are not moving: " +
                statusLine(tile, now(hours = 2), after.error),
        )
    }

    @Test
    fun `a set that fails leaves the tiles alone and reports the failure`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        server.enqueue(MockResponse(code = 404, body = "unknown device"))
        val acs = AcTiles(client())

        acs.refresh()
        val before = acs.state.value.tiles
        acs.setTemperature("ac-01", 24.0)

        assertEquals(before, acs.state.value.tiles)
        assertTrue(acs.state.value.error.orEmpty().contains("404"))
    }

    @Test
    fun `a panel with no token stored says so instead of standing there empty`() = runTest {
        val acs = AcTiles(client(token = { "" }))

        acs.refresh()

        assertTrue(acs.state.value.tiles.isEmpty())
        assertTrue(
            acs.state.value.error.orEmpty().contains("token"),
            "the panel must name the missing token: ${acs.state.value.error}",
        )
    }

    @Test
    fun `a poll that recovers clears the error`() = runTest {
        server.enqueue(MockResponse(code = 500, body = "boom"))
        server.enqueue(MockResponse(body = fixture()))
        val acs = AcTiles(client())

        acs.refresh()
        assertNotNull(acs.state.value.error)
        acs.refresh()

        assertNull(acs.state.value.error)
        assertEquals(18.0, acs.state.value.tiles.single { it.id == "ac-01" }.targetTemperature)
    }

    private fun now(hours: Long): Instant = Instant.ofEpochSecond(powerRead + hours * 3600)

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

    /** The recorded body with ac-01's `range/temperature` state and/or unit dropped. */
    private fun fixtureWithAcRangeParts(
        dropState: Boolean = false,
        dropUnit: Boolean = false,
    ): String = editAcCapability("devices.capabilities.range") { capability ->
        val withoutState = if (dropState) capability - "state" else capability
        if (!dropUnit) {
            JsonObject(withoutState)
        } else {
            val parameters = JsonObject(capability["parameters"]!!.jsonObject - "unit")
            JsonObject(withoutState + ("parameters" to parameters))
        }
    }

    /**
     * The recorded body with ac-01's `on_off` state dropped. ac-01 already carries `"state": null`
     * on every mode and toggle it has, so a capability that reports nothing is this device's own
     * shape rather than an invented one.
     */
    private fun fixtureWithoutAcPower(): String = editAcCapability("devices.capabilities.on_off") {
        JsonObject(it - "state" - "last_updated" - "state_changed_at")
    }

    private fun editAcCapability(
        type: String,
        edit: (JsonObject) -> JsonObject,
    ): String {
        val root = Json.parseToJsonElement(fixture()).jsonObject
        val devices =
            root["devices"]!!.jsonArray.map { device ->
                if (device.jsonObject["id"]?.jsonPrimitive?.content != "ac-01") {
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
