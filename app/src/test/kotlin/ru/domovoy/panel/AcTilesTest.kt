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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.domovoy.core.Bounds
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** AC power and target temperature have separate timestamps; the tile shows the older age. */
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
    fun `a tile shows whether the ac is on, its target temperature and the older of the two ages`() = runTest {
        server.enqueue(MockResponse(body = yandexFixture()))
        val poll = YandexPoll(server.yandexClient())
        val acs = poll.acs

        poll.refresh()

        val tile = acs.state.value.tiles.single { it.id == "ac-01" }
        assertEquals("Residential air conditioner", tile.name)
        assertEquals("Детская", tile.room)
        assertEquals(false, tile.isOn)
        assertEquals(18.0, tile.targetTemperature)
        assertEquals(Bounds(min = 16.0, max = 32.0, precision = 1.0), tile.bounds)
        assertEquals("Выключено · 81 дн. назад", statusLine(tile, now(hours = 2)))
    }

    @Test
    fun `an ac tile state retains measured temperature modes and toggles for its detail sheet`() = runTest {
        server.enqueue(MockResponse(body = yandexFixture()))
        val poll = YandexPoll(server.yandexClient())

        poll.refresh()

        val tile = poll.acs.state.value.tiles.single { it.id == "ac-03" }
        assertEquals(28.0, tile.measuredTemperature)
        assertEquals("auto", tile.modes.getValue("fan_speed").current)
        assertEquals("cool", tile.modes.getValue("thermostat").current)
        assertEquals("horizontal", tile.modes.getValue("swing").current)
        assertEquals(false, tile.toggles.getValue("ionization").isOn)
        assertTrue("backlight" !in tile.toggles)
    }

    @Test
    fun `setting an advertised AC mode posts it and re-reads`() = runTest {
        server.enqueue(MockResponse(body = yandexFixture()))
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-mode"}"""))
        server.enqueue(MockResponse(body = yandexFixture()))
        val poll = YandexPoll(server.yandexClient())

        poll.refresh()
        poll.acs.setMode("ac-03", instance = "fan_speed", value = "low")

        server.takeRequest()
        val state =
            sentYandexAction(
                server.takeRequest().body?.utf8(),
                deviceId = "ac-03",
                type = "devices.capabilities.mode",
            )
        assertEquals("fan_speed", state["instance"]!!.jsonPrimitive.content)
        assertEquals("low", state["value"]!!.jsonPrimitive.content)
        assertEquals("/v1.0/user/info", server.takeRequest().target)
    }

    @Test
    fun `an AC mode not advertised by that device never leaves the tablet`() = runTest {
        server.enqueue(MockResponse(body = yandexFixture()))
        val poll = YandexPoll(server.yandexClient())

        poll.refresh()
        poll.acs.setMode("ac-03", instance = "fan_speed", value = "warp")

        assertEquals(1, server.requestCount)
        assertEquals("Режим недоступен", poll.acs.state.value.commandErrors["ac-03"])
        assertNull(poll.acs.state.value.error)
    }

    @Test
    fun `setting an AC toggle posts it and re-reads`() = runTest {
        server.enqueue(MockResponse(body = yandexFixture()))
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-toggle"}"""))
        server.enqueue(MockResponse(body = yandexFixture()))
        val poll = YandexPoll(server.yandexClient())

        poll.refresh()
        poll.acs.setToggle("ac-03", instance = "ionization", on = true)

        server.takeRequest()
        val state =
            sentYandexAction(
                server.takeRequest().body?.utf8(),
                deviceId = "ac-03",
                type = "devices.capabilities.toggle",
            )
        assertEquals("ionization", state["instance"]!!.jsonPrimitive.content)
        assertEquals("true", state["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun `all three air conditioners of the flat become tiles, and none of them a bulb or a curtain`() = runTest {
        server.enqueue(MockResponse(body = yandexFixture()))
        val poll = YandexPoll(server.yandexClient())

        poll.refresh()

        val bulbs = poll.bulbs.state.value.tiles.map { it.id }
        val curtains = poll.curtains.state.value.tiles.map { it.id }
        assertEquals(listOf("ac-01", "ac-02", "ac-03"), poll.acs.state.value.tiles.map { it.id })
        assertEquals(18, bulbs.size)
        assertEquals(listOf("curtain-01"), curtains)
        assertTrue((bulbs + curtains).none { it.startsWith("ac-") })
    }

    @Test
    fun `an ac that reported no temperature reads as unknown, not as the bottom of its range`() = runTest {
        // 16 °C is a real setting — it is what ac-03 is set to. A range that never reported is not
        // that, and a tile that prints the bottom of the range for it says the opposite of the truth.
        server.enqueue(MockResponse(body = fixtureWithAcRangeParts(dropState = true)))
        val poll = YandexPoll(server.yandexClient())
        val acs = poll.acs

        poll.refresh()

        val tile = acs.state.value.tiles.single { it.id == "ac-01" }
        assertNull(tile.targetTemperature)
        // A missing temperature has no age; use the reported power timestamp.
        assertEquals("Выключено · Температура неизвестна · 2 ч назад", statusLine(tile, now(hours = 2)))
    }

    @Test
    fun `an ac that reported no on-off reads as unknown, not as off`() = runTest {
        server.enqueue(MockResponse(body = fixtureWithoutAcPower()))
        val poll = YandexPoll(server.yandexClient())
        val acs = poll.acs

        poll.refresh()

        val tile = acs.state.value.tiles.single { it.id == "ac-01" }
        assertNull(tile.isOn)
        assertEquals("Нет данных · 81 дн. назад", statusLine(tile, now(hours = 2)))
    }

    @Test
    fun `a temperature range with no unit prints the number without inventing one`() = runTest {
        // The same recorded response carries "" as the unit on the TV's volume range, so a range
        // that names no unit is a shape this panel has actually seen.
        server.enqueue(MockResponse(body = fixtureWithAcRangeParts(dropUnit = true)))
        val poll = YandexPoll(server.yandexClient())
        val acs = poll.acs

        poll.refresh()

        val tile = acs.state.value.tiles.single { it.id == "ac-01" }
        assertEquals("Выключено · 81 дн. назад", statusLine(tile, now(hours = 2)))
    }

    @Test
    fun `setting a temperature posts a range action for that ac, then re-reads`() = runTest {
        server.enqueue(MockResponse(body = yandexFixture()))
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-1","devices":[]}"""))
        server.enqueue(MockResponse(body = yandexFixture()))
        val poll = YandexPoll(server.yandexClient())
        val acs = poll.acs

        poll.refresh()
        acs.setTemperature("ac-02", 24.0)

        server.takeRequest() // the first poll
        val action = server.takeRequest()
        assertEquals("/v1.0/devices/actions", action.target)
        val state = sentYandexAction(action.body?.utf8(), deviceId = "ac-02")
        assertEquals("temperature", state["instance"]!!.jsonPrimitive.content)
        assertEquals("24", state["value"]!!.jsonPrimitive.content)
        // Yandex answers DONE without promising the ac has changed, so the tile is repainted from
        // a fresh read rather than from the action result.
        assertEquals("/v1.0/user/info", server.takeRequest().target)
    }

    @Test
    fun `a temperature outside the range the ac reports is snapped before it is sent`() = runTest {
        server.enqueue(MockResponse(body = yandexFixture()))
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-1","devices":[]}"""))
        server.enqueue(MockResponse(body = yandexFixture()))
        val poll = YandexPoll(server.yandexClient())
        val acs = poll.acs

        poll.refresh()
        acs.setTemperature("ac-01", 40.0)

        server.takeRequest()
        val state = sentYandexAction(server.takeRequest().body?.utf8(), deviceId = "ac-01")
        // The ac reports 16..32 with precision 1; anything else is a request Yandex can only
        // reject, and a rejected action is a tile that says "not updating" for no reason.
        assertEquals("32", state["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun `turning an ac on posts the on-off action for that one ac, then re-reads`() = runTest {
        server.enqueue(MockResponse(body = yandexFixture()))
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-1","devices":[]}"""))
        server.enqueue(MockResponse(body = yandexFixture()))
        val poll = YandexPoll(server.yandexClient())
        val acs = poll.acs

        poll.refresh()
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
        val poll = YandexPoll(server.yandexClient())
        val acs = poll.acs

        poll.refresh()
        acs.toggle("ac-01")

        server.takeRequest()
        val state = sentYandexAction(server.takeRequest().body?.utf8(), deviceId = "ac-01", type = "devices.capabilities.on_off")
        assertEquals("true", state["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a failed poll keeps the last values and ages, and says it is not updating`() = runTest {
        server.enqueue(MockResponse(body = yandexFixture()))
        server.enqueue(MockResponse(code = 500, body = "boom"))
        val poll = YandexPoll(server.yandexClient())
        val acs = poll.acs

        poll.refresh()
        val before = acs.state.value.tiles
        poll.refresh()
        val after = acs.state.value

        assertNotNull(after.error)
        assertEquals(before, after.tiles)
        val tile = after.tiles.single { it.id == "ac-01" }
        assertEquals("Выключено · 81 дн. назад", statusLine(tile, now(hours = 2)))
        assertEquals(
            "Не удалось выполнить запрос",
            anatomy(tile, now(hours = 2), after.error).detail,
            "the tile has to keep its values and say why they are not moving",
        )
    }

    @Test
    fun `a set that fails leaves the tiles alone and reports the failure`() = runTest {
        server.enqueue(MockResponse(body = yandexFixture()))
        server.enqueue(MockResponse(code = 404, body = "unknown device"))
        val poll = YandexPoll(server.yandexClient())
        val acs = poll.acs

        poll.refresh()
        val before = acs.state.value.tiles
        acs.setTemperature("ac-01", 24.0)

        assertEquals(before, acs.state.value.tiles)
        assertEquals("Не удалось выполнить запрос", acs.state.value.commandErrors["ac-01"])
        assertNull(acs.state.value.error)
    }

    @Test
    fun `a panel with no token stored reports a failed poll instead of standing there empty`() = runTest {
        // The sentence naming the token goes to `Log` now rather than to the wall — see
        // BulbTilesTest, which is where that trade is written down.
        val poll = YandexPoll(server.yandexClient(token = { "" }))
        val acs = poll.acs

        poll.refresh()

        assertTrue(acs.state.value.tiles.isEmpty())
        assertEquals("Не удалось выполнить запрос", acs.state.value.error)
    }

    @Test
    fun `a poll that recovers clears the error`() = runTest {
        server.enqueue(MockResponse(code = 500, body = "boom"))
        server.enqueue(MockResponse(body = yandexFixture()))
        val poll = YandexPoll(server.yandexClient())
        val acs = poll.acs

        poll.refresh()
        assertNotNull(acs.state.value.error)
        poll.refresh()

        assertNull(acs.state.value.error)
        assertEquals(18.0, acs.state.value.tiles.single { it.id == "ac-01" }.targetTemperature)
    }

    private fun now(hours: Long): Instant = Instant.ofEpochSecond(powerRead + hours * 3600)

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

    /** Remove on/off state, matching the null states already present on this device. */
    private fun fixtureWithoutAcPower(): String = editAcCapability("devices.capabilities.on_off") {
        JsonObject(it - "state" - "last_updated" - "state_changed_at")
    }

    private fun editAcCapability(
        type: String,
        edit: (JsonObject) -> JsonObject,
    ): String {
        val root = Json.parseToJsonElement(yandexFixture()).jsonObject
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
