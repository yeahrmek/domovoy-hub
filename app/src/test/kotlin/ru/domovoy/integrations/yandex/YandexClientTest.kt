package ru.domovoy.integrations.yandex

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
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
import ru.domovoy.core.DeviceKind
import ru.domovoy.core.OnOff
import ru.domovoy.core.Reading
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Drives the client against the recorded `/v1.0/user/info` body over a loopback socket, so the
 * assertions are on what the panel ends up with and on what Yandex would actually have received.
 */
class YandexClientTest {
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
    fun `only the bulbs of the configured household come back`() = runTest {
        server.enqueue(MockResponse(body = fixture()))

        val devices = client().devices().getOrThrow()

        // The account holds four households — the flat, two other homes and a dacha. The
        // dacha's lights must not land on the hallway wall.
        assertEquals(
            listOf(
                "light-01", "light-02", "light-03", "light-04", "light-05", "light-06",
                "light-07", "light-08", "light-10", "light-11", "light-12", "light-15",
                "light-16", "light-17", "light-18", "light-19", "light-20", "light-21",
            ),
            devices.filter { it.kind == DeviceKind.Bulb }.map { it.id }.sorted(),
        )
    }

    @Test
    fun `one poll brings back every kind the panel has a tile for, each said apart`() = runTest {
        server.enqueue(MockResponse(body = fixture()))

        val devices = client().devices().getOrThrow()

        // One /v1.0/user/info call is the whole house, so the kinds travel together and are told
        // apart here rather than by a second call per tile group.
        assertEquals(
            mapOf(
                DeviceKind.Bulb to 18,
                DeviceKind.LightStrip to 2,
                DeviceKind.Curtain to 1,
                DeviceKind.AirConditioner to 3,
            ),
            devices.groupingBy { it.kind }.eachCount(),
        )
    }

    @Test
    fun `devices of another household and types the panel has no tile for are left out`() = runTest {
        server.enqueue(MockResponse(body = fixture()))

        val ids = client().devices().map { list -> list.map { it.id } }.getOrThrow()

        // light-09 and light-13 are bulbs, but of other households; light-14 is of another
        // household too. The type match is still exact — a vacuum or a socket has no tile — but
        // devices.types.light.strip now has one of its own.
        assertTrue(
            ids.none { it in setOf("light-09", "light-13", "light-14") },
            "leaked out-of-scope devices: $ids",
        )
        assertTrue(
            ids.none { it.startsWith("vacuum-") || it.startsWith("socket-") },
            "leaked a type the panel has no tile for: $ids",
        )
    }

    @Test
    fun `a light strip carries its brightness in the same Range the curtain's position uses`() = runTest {
        server.enqueue(MockResponse(body = fixture()))

        val strip = client().devices().getOrThrow().single { it.id == "light-strip-01" }

        assertEquals(DeviceKind.LightStrip, strip.kind)
        assertEquals("Подсветка в зале", strip.name)
        assertEquals("Зал", strip.room)
        assertEquals(setOf("brightness"), strip.ranges.keys)
        val brightness = strip.ranges.getValue("brightness")
        assertEquals(26.0, brightness.value)
        assertEquals(Bounds(min = 1.0, max = 100.0, precision = 1.0), brightness.bounds)
        assertEquals("unit.percent", brightness.unit)
        assertEquals(true, strip.onOff?.isOn)
    }

    @Test
    fun `a colour that has never reported is kept as a capability with no instance, not dropped`() = runTest {
        // light-strip-02 carries "state": null on its color_setting, and a color_setting names its
        // instance only inside state — so unlike a range it has no instance to fall back on. It is
        // still a capability the device has: "never reported" is not "no colour at all".
        server.enqueue(MockResponse(body = fixture()))

        val devices = client().devices().getOrThrow()

        val reported = devices.single { it.id == "light-strip-01" }.color
        assertEquals("temperature_k", reported?.instance)
        assertEquals(2700.0, reported?.value)
        val silent = devices.single { it.id == "light-strip-02" }.color
        assertNotNull(silent)
        assertNull(silent.instance)
        assertNull(silent.value)
    }

    @Test
    fun `a Kelvin light keeps the temperature bounds the device reported`() = runTest {
        server.enqueue(MockResponse(body = fixture()))

        val light = client().devices().getOrThrow().single { it.id == "light-01" }

        assertEquals(Bounds(min = 2700.0, max = 6500.0, precision = 1.0), light.color?.temperatureBounds)
        assertEquals(emptyList(), light.color?.scenes)
    }

    @Test
    fun `an RGB light keeps only the scenes the device advertised`() = runTest {
        server.enqueue(MockResponse(body = fixture()))

        val light = client().devices().getOrThrow().single { it.id == "light-21" }

        assertNull(light.color?.temperatureBounds)
        assertEquals(listOf("candle", "rest", "movie", "sunrise"), light.color?.scenes)
    }

    @Test
    fun `an air conditioner keeps its measured temperature property apart from its target`() = runTest {
        server.enqueue(MockResponse(body = fixture()))

        val ac = client().devices().getOrThrow().single { it.id == "ac-03" }

        assertEquals(16.0, ac.ranges.getValue("temperature").value)
        val measured = ac.properties.getValue("temperature")
        assertEquals(28.0, measured.value)
        assertEquals("unit.temperature.celsius", measured.unit)
        assertEquals(1_786_755_372, (measured.lastUpdated as Reading.At).instant.epochSecond)
    }

    @Test
    fun `the air conditioner comes back with its temperature range as the vendor reports it`() = runTest {
        server.enqueue(MockResponse(body = fixture()))

        val ac = client().devices().getOrThrow().single { it.id == "ac-01" }

        assertEquals(DeviceKind.AirConditioner, ac.kind)
        assertEquals("Residential air conditioner", ac.name)
        assertEquals("Детская", ac.room)
        assertEquals(setOf("temperature"), ac.ranges.keys)
        val temperature = ac.ranges.getValue("temperature")
        assertEquals(18.0, temperature.value)
        assertEquals(Bounds(min = 16.0, max = 32.0, precision = 1.0), temperature.bounds)
        assertEquals("unit.temperature.celsius", temperature.unit)
        assertEquals(1_778_169_164, (temperature.lastUpdated as Reading.At).instant.epochSecond)
    }

    @Test
    fun `a mode that never reported names the values the device accepts and no current one`() = runTest {
        // Every mode on ac-01 carries "state": null — the API lists what the device accepts
        // without saying which is active. Reading the first of the list as the current one would
        // put "fan_only" on the wall for an air conditioner nobody has asked to blow.
        server.enqueue(MockResponse(body = fixture()))

        val ac = client().devices().getOrThrow().single { it.id == "ac-01" }

        assertEquals(setOf("thermostat", "fan_speed", "swing"), ac.modes.keys)
        val thermostat = ac.modes.getValue("thermostat")
        assertNull(thermostat.current)
        assertEquals(listOf("fan_only", "heat", "cool", "dry", "auto"), thermostat.available)
        assertEquals(listOf("turbo", "high", "medium", "low", "quiet", "auto"), ac.modes.getValue("fan_speed").available)
        assertEquals(listOf("stationary", "vertical", "horizontal", "auto"), ac.modes.getValue("swing").available)
        assertEquals(Reading.Never, thermostat.lastUpdated)
    }

    @Test
    fun `a mode that did report comes back with the value it reported, from the list it listed`() = runTest {
        server.enqueue(MockResponse(body = fixture()))

        val ac = client().devices().getOrThrow().single { it.id == "ac-03" }

        val thermostat = ac.modes.getValue("thermostat")
        assertEquals("cool", thermostat.current)
        assertEquals(listOf("fan_only", "heat", "cool", "dry", "auto"), thermostat.available)
        assertTrue(thermostat.current in thermostat.available)
    }

    @Test
    fun `a toggle that never reported reads as unknown, not as off`() = runTest {
        server.enqueue(MockResponse(body = fixture()))

        val ac = client().devices().getOrThrow().single { it.id == "ac-01" }

        assertEquals(setOf("ionization", "keep_warm", "backlight"), ac.toggles.keys)
        assertNull(ac.toggles.getValue("ionization").isOn)
        assertEquals(Reading.Never, ac.toggles.getValue("ionization").lastUpdated)
    }

    @Test
    fun `a toggle the device does not have at all is absent, not unknown`() = runTest {
        // ac-03 carries no backlight capability, while ac-01 carries one that has never reported.
        // "no such capability" and "never reported" are different things and must stay so.
        server.enqueue(MockResponse(body = fixture()))

        val ac = client().devices().getOrThrow().single { it.id == "ac-03" }

        assertEquals(setOf("ionization", "keep_warm"), ac.toggles.keys)
        assertEquals(false, ac.toggles.getValue("ionization").isOn)
    }

    @Test
    fun `the curtain comes back with its open range as the vendor reports it`() = runTest {
        server.enqueue(MockResponse(body = fixture()))

        val curtain = client().devices().getOrThrow().single { it.kind == DeviceKind.Curtain }

        assertEquals("Шторы", curtain.name)
        assertEquals("Спальня", curtain.room)
        // on_off and zigbee_node are on this device too; neither is a range, and zigbee_node's
        // state is an object, so a mapper that took every capability would choke on it.
        assertEquals(setOf("open"), curtain.ranges.keys)
        val open = curtain.ranges.getValue("open")
        assertEquals(0.0, open.value)
        assertEquals(Bounds(min = 0.0, max = 100.0, precision = 1.0), open.bounds)
        assertEquals("unit.percent", open.unit)
        assertEquals(1_786_667_879, (open.lastUpdated as Reading.At).instant.epochSecond)
        // The range has never changed since Yandex started counting: 0.0, which is not the epoch.
        assertEquals(Reading.Never, open.stateChangedAt)
    }

    @Test
    fun `a bulb's brightness range is read too, without becoming a bulb tile's business`() = runTest {
        // The model has to carry every range the poll returned, or the AC's temperature would
        // need a second parse of the same response.
        server.enqueue(MockResponse(body = fixture()))

        val bulb = client().devices().getOrThrow().single { it.id == "light-01" }

        assertEquals(5.0, bulb.ranges.getValue("brightness").value)
    }

    @Test
    fun `driving a range posts the instance and a whole-number value`() = runTest {
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-1","devices":[]}"""))

        client().setRange("curtain-01", instance = "open", value = 70.0).getOrThrow()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1.0/devices/actions", request.target)
        val body = requireNotNull(request.body) { "the action was sent with no body" }.utf8()
        val device = Json.parseToJsonElement(body).jsonObject["devices"]!!.jsonArray.single()
        assertEquals("curtain-01", device.jsonObject["id"]!!.jsonPrimitive.content)
        val action = device.jsonObject["actions"]!!.jsonArray.single().jsonObject
        assertEquals("devices.capabilities.range", action["type"]!!.jsonPrimitive.content)
        val state = action["state"]!!.jsonObject
        assertEquals("open", state["instance"]!!.jsonPrimitive.content)
        // The curtain reports its position as 0, not 0.0, and its precision is 1 — so "70.0" is a
        // difference from what the vendor itself sends for no gain.
        assertEquals("70", state["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun `driving a mode posts the verified mode capability shape`() = runTest {
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-mode"}"""))

        client().setMode("ac-03", instance = "fan_speed", value = "low").getOrThrow()

        val action = actionFrom(server.takeRequest().body!!.utf8())
        assertEquals("devices.capabilities.mode", action["type"]!!.jsonPrimitive.content)
        val state = action["state"]!!.jsonObject
        assertEquals("fan_speed", state["instance"]!!.jsonPrimitive.content)
        assertEquals("low", state["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun `driving a toggle posts the verified toggle capability shape`() = runTest {
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-toggle"}"""))

        client().setToggle("ac-03", instance = "ionization", on = true).getOrThrow()

        val action = actionFrom(server.takeRequest().body!!.utf8())
        assertEquals("devices.capabilities.toggle", action["type"]!!.jsonPrimitive.content)
        val state = action["state"]!!.jsonObject
        assertEquals("ionization", state["instance"]!!.jsonPrimitive.content)
        assertEquals(true, state["value"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `driving RGB and a scene uses the same verified color capability with each instance`() = runTest {
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-rgb"}"""))
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-scene"}"""))

        client().setColor("light-21", instance = "rgb", value = 0xFF0000).getOrThrow()
        client().setColor("light-21", instance = "scene", value = "candle").getOrThrow()

        val rgb = actionFrom(server.takeRequest().body!!.utf8())
        assertEquals("devices.capabilities.color_setting", rgb["type"]!!.jsonPrimitive.content)
        assertEquals("rgb", rgb["state"]!!.jsonObject["instance"]!!.jsonPrimitive.content)
        assertEquals("16711680", rgb["state"]!!.jsonObject["value"]!!.jsonPrimitive.content)
        val scene = actionFrom(server.takeRequest().body!!.utf8())
        assertEquals("devices.capabilities.color_setting", scene["type"]!!.jsonPrimitive.content)
        assertEquals("scene", scene["state"]!!.jsonObject["instance"]!!.jsonPrimitive.content)
        assertEquals("candle", scene["state"]!!.jsonObject["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun `with no token stored a range action is refused rather than sent`() = runTest {
        val result = client(token = { "" }).setRange("curtain-01", instance = "open", value = 70.0)

        assertTrue(result.isFailure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a bulb keeps its name, room and both capability timestamps`() = runTest {
        server.enqueue(MockResponse(body = fixture()))

        val bulb = client().devices().getOrThrow().single { it.id == "light-01" }

        assertEquals("Лампа 4", bulb.name)
        assertEquals("Зал", bulb.room)
        assertEquals(
            OnOff(
                isOn = true,
                lastUpdated = Reading.At(Instant.ofEpochSecond(1_784_883_564)),
                stateChangedAt = Reading.At(Instant.ofEpochSecond(1_778_228_459)),
            ),
            bulb.onOff,
        )
    }

    @Test
    fun `a capability that never reported reads as never, not as the epoch`() = runTest {
        server.enqueue(MockResponse(body = fixture()))

        val bulb = client().devices().getOrThrow().single { it.id == "light-04" }

        assertEquals(Reading.Never, bulb.onOff?.lastUpdated)
        assertEquals(Reading.Never, bulb.onOff?.stateChangedAt)
    }

    @Test
    fun `the poll asks user info with the bearer token`() = runTest {
        server.enqueue(MockResponse(body = fixture()))

        client().devices().getOrThrow()

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/v1.0/user/info", request.target)
        assertEquals("Bearer test-token", request.headers["Authorization"])
    }

    @Test
    fun `with no token stored the poll fails with a readable message and never leaves the tablet`() = runTest {
        val result = client(token = { "" }).devices()

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message.orEmpty().contains("token"),
            "the panel has to be able to print why: ${result.exceptionOrNull()?.message}",
        )
        // Not a request with an empty Bearer header either — Yandex would answer 403 and the
        // tile would blame the scopes rather than the missing token.
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `with no token stored a toggle is refused rather than sent`() = runTest {
        val result = client(token = { "" }).setOn("light-01", on = true)

        assertTrue(result.isFailure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `the token is read at every call, so one stored later is used without a restart`() = runTest {
        // The tablet is wall-mounted: whatever writes a fresh token must take effect without
        // the panel being restarted, which is why the token is read per call and not held.
        server.enqueue(MockResponse(body = fixture()))
        var stored = ""
        val client = client(token = { stored })

        assertTrue(client.devices().isFailure)
        stored = "y0_stored_later"

        assertTrue(client.devices().isSuccess)
        assertEquals("Bearer y0_stored_later", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `a store that cannot be read at all is a failed poll, not a crash`() = runTest {
        // EncryptedSharedPreferences can fail to open — a keystore the tablet lost across a
        // reboot or a restored backup. That has to reach the tile like any other failure.
        val result = client(token = { error("secure storage unavailable") }).devices()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("secure storage unavailable"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a poll rejected with Forbidden fails instead of throwing`() = runTest {
        // A token issued before the iot scopes were added answers with a bare, non-JSON body.
        server.enqueue(MockResponse(code = 403, body = "Forbidden"))

        val result = client().devices()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("403"))
    }

    @Test
    fun `a poll that outlives its timeout fails instead of hanging`() = runTest {
        server.enqueue(
            MockResponse
                .Builder()
                .body(fixture())
                .bodyDelay(10, TimeUnit.SECONDS)
                .build(),
        )

        val result = client(timeout = 200.milliseconds).devices()

        assertTrue(result.isFailure)
    }

    @Test
    fun `toggling posts the on-off action for that one bulb`() = runTest {
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-1","devices":[]}"""))

        client().setOn("light-01", on = false).getOrThrow()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1.0/devices/actions", request.target)
        assertEquals("Bearer test-token", request.headers["Authorization"])

        val body = requireNotNull(request.body) { "the action was sent with no body" }.utf8()
        val device = Json.parseToJsonElement(body).jsonObject["devices"]!!.jsonArray.single()
        assertEquals("light-01", device.jsonObject["id"]!!.jsonPrimitive.content)
        val action = device.jsonObject["actions"]!!.jsonArray.single().jsonObject
        assertEquals("devices.capabilities.on_off", action["type"]!!.jsonPrimitive.content)
        val state = action["state"]!!.jsonObject
        assertEquals("on", state["instance"]!!.jsonPrimitive.content)
        assertEquals(false, state["value"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `an action Yandex answers with an error status fails`() = runTest {
        server.enqueue(MockResponse(body = """{"status":"error","request_id":"r-2"}"""))

        val result = client().setOn("light-01", on = true)

        assertTrue(result.isFailure)
        // request_id is the only thing Yandex support can act on, so it has to survive.
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("r-2"))
    }

    @Test
    fun `a poll leaves the thread it was started from free to run other work`() = runBlocking {
        // Reproduces NetworkOnMainThreadException on the tablet: reading the response body is
        // a blocking socket read, and it used to happen on whichever dispatcher called
        // devices() — which on the panel is Dispatchers.Main. Real threads and real time here,
        // because that is exactly what the bug is about.
        val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "panel-main") }
        val main = executor.asCoroutineDispatcher()
        server.enqueue(
            MockResponse
                .Builder()
                .body(fixture())
                .bodyDelay(1, TimeUnit.SECONDS)
                .build(),
        )

        val poll = async(main) { client().devices() }
        // OkHttp hands back the response as soon as the headers land, so the blocking read of
        // the delayed body only starts after that. Probe once it is genuinely underway.
        delay(300)
        val ticked = CompletableDeferred<Unit>()
        launch(main) { ticked.complete(Unit) }

        // The poll is mid-read, so the dispatcher it started on must still run other work.
        withTimeout(400) { ticked.await() }

        assertTrue(poll.await().isSuccess)
        executor.shutdown()
    }

    private fun client(
        householdId: String = "household-flat",
        timeout: Duration = 10.seconds,
        token: () -> String = { "test-token" },
    ) = YandexClient(
        http = OkHttpClient(),
        token = token,
        householdId = householdId,
        baseUrl = server.url("/"),
        timeout = timeout,
    )

    private fun fixture(): String = checkNotNull(javaClass.getResourceAsStream("/yandex/user_info.json")) {
        "missing fixture app/src/test/resources/yandex/user_info.json"
    }.use { it.readBytes().decodeToString() }

    private fun actionFrom(body: String) = Json.parseToJsonElement(body).jsonObject["devices"]!!.jsonArray.single().jsonObject["actions"]!!
        .jsonArray.single().jsonObject
}
