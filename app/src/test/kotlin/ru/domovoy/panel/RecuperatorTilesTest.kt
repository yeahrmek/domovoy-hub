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
import ru.domovoy.integrations.tuya.TuyaClient
import ru.domovoy.integrations.tuya.TuyaCredentials
import java.time.Instant
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The recuperators differ from every Yandex tile group in one way that shows up all through here:
 * real state costs one call *per device*, so a refresh is five calls and one of them can fail on
 * its own. A failure there belongs to that tile, not to the group — four working recuperators must
 * not be labelled "not updating" because the fifth timed out.
 */
class RecuperatorTilesTest {
    /** The newest datapoint in the recorded shadow response: the humidity, read at this instant. */
    private val lastRead = Instant.ofEpochMilli(1_786_817_884_638L)

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
    fun `a tile shows whether the recuperator is on, its fan speed and the age of each reading`() = runTest {
        enqueueRefresh()
        val poll = TuyaPoll(client())

        poll.refresh()

        val tile = poll.recuperators.state.value.tiles.single { it.id == "xfj-01" }
        assertEquals("Бризер данина комната", tile.name)
        assertEquals(false, tile.isOn)
        assertEquals(true, tile.online)
        // All three speed booleans came back false, which is the device reporting no speed
        // running — not a device that failed to say.
        assertEquals(emptyList(), tile.speeds)
        assertEquals("off · 3 d ago · no speed · 3 d ago", statusLine(tile, now(minutes = 0)))
    }

    @Test
    fun `a tile shows the temperature and humidity, each with its own age`() = runTest {
        // These two are the only datapoints that move on their own, and they move at different
        // times: the humidity was 26 s old when the response was recorded and the temperature
        // nearly 4 minutes. One age for the pair would be wrong about one of them.
        enqueueRefresh()
        val poll = TuyaPoll(client())

        poll.refresh()

        val tile = poll.recuperators.state.value.tiles.single { it.id == "xfj-01" }
        assertEquals(29.3, tile.temperature)
        assertEquals(32.2, tile.humidity)
        assertEquals("29.3 °C · 3 min ago · 32.2 % · just now", climateLine(tile, now(minutes = 0)))
    }

    @Test
    fun `a climate reading the recuperator did not report reads as unknown, not as zero`() = runTest {
        enqueueToken()
        server.enqueue(MockResponse(body = fixture("devices.json")))
        server.enqueue(MockResponse(body = shadowWithout("temper")))
        repeat(4) { server.enqueue(MockResponse(body = fixture("shadow_properties.json"))) }
        val poll = TuyaPoll(client())

        poll.refresh()

        val tile = poll.recuperators.state.value.tiles.single { it.id == "xfj-01" }
        assertNull(tile.temperature)
        assertEquals("unknown · never read · 32.2 % · just now", climateLine(tile, now(minutes = 0)))
    }

    @Test
    fun `a recuperator that reported neither has no climate line rather than a line of unknowns`() = runTest {
        enqueueToken()
        server.enqueue(MockResponse(body = fixture("devices.json")))
        server.enqueue(MockResponse(body = shadowWithout("temper", "huimi")))
        repeat(4) { server.enqueue(MockResponse(body = fixture("shadow_properties.json"))) }
        val poll = TuyaPoll(client())

        poll.refresh()

        val tile = poll.recuperators.state.value.tiles.single { it.id == "xfj-01" }
        assertNull(climateLine(tile, now(minutes = 0)))
        // The tile is still there, and the line that carries its staleness is untouched.
        assertEquals("off · 3 d ago · no speed · 3 d ago", statusLine(tile, now(minutes = 0)))
    }

    @Test
    fun `the climate is printed the same way whatever locale the tablet is set to`() = runTest {
        // A wall tablet in this flat is set to ru-RU, where "%.1f" formats 29.3 as "29,3". The
        // panel prints one spelling, so the tile does not change shape with a system setting.
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ru-RU"))
            enqueueRefresh()
            val poll = TuyaPoll(client())

            poll.refresh()

            val tile = poll.recuperators.state.value.tiles.single { it.id == "xfj-01" }
            assertEquals("29.3 °C · 3 min ago · 32.2 % · just now", climateLine(tile, now(minutes = 0)))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `all five recuperators become tiles, in the order the account lists them`() = runTest {
        enqueueRefresh()
        val poll = TuyaPoll(client())

        poll.refresh()

        assertEquals(
            listOf("xfj-01", "xfj-02", "xfj-03", "xfj-04", "xfj-05"),
            poll.recuperators.state.value.tiles.map { it.id },
        )
    }

    @Test
    fun `one refresh costs the inventory plus one call per recuperator, and no more`() = runTest {
        // The allowance is the design constraint: five recuperators are five shadow calls, and a
        // sixth call per refresh would move the poll interval. See docs/tuya.md.
        enqueueRefresh()
        val poll = TuyaPoll(client())

        poll.refresh()

        assertEquals(7, server.requestCount) // token + inventory + 5 reads
        server.takeRequest()
        assertEquals("/v1.0/users/eu-test-uid/devices", server.takeRequest().target)
        repeat(5) { index ->
            assertEquals("/v2.0/cloud/thing/xfj-0${index + 1}/shadow/properties", server.takeRequest().target)
        }
    }

    @Test
    fun `a recuperator running on high says so`() = runTest {
        enqueueToken()
        server.enqueue(MockResponse(body = fixture("devices.json")))
        server.enqueue(MockResponse(body = shadowWith("speed_three", true)))
        repeat(4) { server.enqueue(MockResponse(body = fixture("shadow_properties.json"))) }
        val poll = TuyaPoll(client())

        poll.refresh()

        val tile = poll.recuperators.state.value.tiles.single { it.id == "xfj-01" }
        assertEquals(listOf(FanSpeed.High), tile.speeds)
        assertEquals("off · 3 d ago · high · 3 d ago", statusLine(tile, now(minutes = 0)))
    }

    @Test
    fun `two speeds reported at once are both printed rather than one of them being picked`() = runTest {
        // The three speeds are three separate booleans, and whether the device enforces mutual
        // exclusion is unverified (docs/tuya.md). Printing "low" for a device reporting low *and*
        // high would be the panel choosing which half of the reading to believe.
        enqueueToken()
        server.enqueue(MockResponse(body = fixture("devices.json")))
        server.enqueue(MockResponse(body = shadowWith("speed_one", true, "speed_three", true)))
        repeat(4) { server.enqueue(MockResponse(body = fixture("shadow_properties.json"))) }
        val poll = TuyaPoll(client())

        poll.refresh()

        val tile = poll.recuperators.state.value.tiles.single { it.id == "xfj-01" }
        assertEquals(listOf(FanSpeed.Low, FanSpeed.High), tile.speeds)
        assertEquals("off · 3 d ago · low + high · 3 d ago", statusLine(tile, now(minutes = 0)))
    }

    @Test
    fun `a recuperator that reported no speed datapoint at all reads as unknown, not as no speed`() = runTest {
        enqueueToken()
        server.enqueue(MockResponse(body = fixture("devices.json")))
        server.enqueue(MockResponse(body = shadowWithout("speed_one", "speed_two", "speed_three")))
        repeat(4) { server.enqueue(MockResponse(body = fixture("shadow_properties.json"))) }
        val poll = TuyaPoll(client())

        poll.refresh()

        val tile = poll.recuperators.state.value.tiles.single { it.id == "xfj-01" }
        assertEquals("off · 3 d ago · unknown · never read", statusLine(tile, now(minutes = 0)))
    }

    @Test
    fun `a recuperator that reported no switch reads as unknown, not as off`() = runTest {
        enqueueToken()
        server.enqueue(MockResponse(body = fixture("devices.json")))
        server.enqueue(MockResponse(body = shadowWithout("switch")))
        repeat(4) { server.enqueue(MockResponse(body = fixture("shadow_properties.json"))) }
        val poll = TuyaPoll(client())

        poll.refresh()

        val tile = poll.recuperators.state.value.tiles.single { it.id == "xfj-01" }
        assertNull(tile.isOn)
        assertEquals("unknown · never read · no speed · 3 d ago", statusLine(tile, now(minutes = 0)))
    }

    @Test
    fun `a device the account reports offline says so, even though the call itself succeeded`() = runTest {
        enqueueToken()
        server.enqueue(MockResponse(body = devicesWithOfflineFirstRecuperator()))
        repeat(5) { server.enqueue(MockResponse(body = fixture("shadow_properties.json"))) }
        val poll = TuyaPoll(client())

        poll.refresh()

        val tile = poll.recuperators.state.value.tiles.single { it.id == "xfj-01" }
        assertEquals(false, tile.online)
        assertTrue(
            statusLine(tile, now(minutes = 0)).startsWith("offline · "),
            "an offline device has to say so before anything it last reported: ${statusLine(tile, now(minutes = 0))}",
        )
    }

    @Test
    fun `one recuperator failing to read leaves the other four updating normally`() = runTest {
        enqueueToken()
        server.enqueue(MockResponse(body = fixture("devices.json")))
        server.enqueue(MockResponse(body = fixture("shadow_properties.json")))
        server.enqueue(MockResponse(code = 500, body = "boom"))
        repeat(3) { server.enqueue(MockResponse(body = fixture("shadow_properties.json"))) }
        val poll = TuyaPoll(client())

        poll.refresh()

        val state = poll.recuperators.state.value
        assertNull(state.error, "one device failing is not the group failing")
        assertNull(state.tiles.single { it.id == "xfj-01" }.error)
        val failed = state.tiles.single { it.id == "xfj-02" }
        assertNotNull(failed.error)
        assertTrue(failed.error.orEmpty().contains("500"))
    }

    @Test
    fun `a recuperator whose read fails keeps the values it last had, and says it is not updating`() = runTest {
        enqueueRefresh()
        // No second token: it is cached and still good, so the next refresh is inventory + reads.
        server.enqueue(MockResponse(body = fixture("devices.json")))
        server.enqueue(MockResponse(code = 500, body = "boom"))
        repeat(4) { server.enqueue(MockResponse(body = fixture("shadow_properties.json"))) }
        val poll = TuyaPoll(client())

        poll.refresh()
        val before = poll.recuperators.state.value.tiles.single { it.id == "xfj-01" }
        poll.refresh()

        val after = poll.recuperators.state.value.tiles.single { it.id == "xfj-01" }
        assertEquals(before.isOn, after.isOn)
        assertEquals(before.powerLastUpdated, after.powerLastUpdated)
        assertEquals(before.speeds, after.speeds)
        assertTrue(
            statusLine(after, now(minutes = 0)).startsWith("off · 3 d ago · no speed · 3 d ago · not updating"),
            "the tile has to keep its values and say why they are not moving: ${statusLine(after, now(minutes = 0))}",
        )
    }

    @Test
    fun `a read that recovers clears that tile's error`() = runTest {
        enqueueToken()
        server.enqueue(MockResponse(body = fixture("devices.json")))
        server.enqueue(MockResponse(code = 500, body = "boom"))
        repeat(4) { server.enqueue(MockResponse(body = fixture("shadow_properties.json"))) }
        server.enqueue(MockResponse(body = fixture("devices.json")))
        repeat(5) { server.enqueue(MockResponse(body = fixture("shadow_properties.json"))) }
        val poll = TuyaPoll(client())

        poll.refresh()
        assertNotNull(poll.recuperators.state.value.tiles.single { it.id == "xfj-01" }.error)
        poll.refresh()

        assertNull(poll.recuperators.state.value.tiles.single { it.id == "xfj-01" }.error)
    }

    @Test
    fun `a failed inventory call fails the whole group, and every tile keeps its values`() = runTest {
        enqueueRefresh()
        server.enqueue(MockResponse(code = 500, body = "boom"))
        val poll = TuyaPoll(client())

        poll.refresh()
        val before = poll.recuperators.state.value.tiles
        poll.refresh()

        val after = poll.recuperators.state.value
        assertNotNull(after.error)
        assertEquals(before, after.tiles)
    }

    @Test
    fun `a panel with no credentials stored says so instead of standing there empty`() = runTest {
        val poll = TuyaPoll(client(credentials = { TuyaCredentials("", "", "") }))

        poll.refresh()

        assertTrue(poll.recuperators.state.value.tiles.isEmpty())
        assertTrue(
            poll.recuperators.state.value.error.orEmpty().contains("local.properties"),
            "the panel must name how a credential gets in: ${poll.recuperators.state.value.error}",
        )
    }

    @Test
    fun `switching a recuperator on re-reads that one device and not the whole panel`() = runTest {
        // A refresh is five calls against a metered allowance; a tap has no business spending
        // five more to repaint one tile.
        enqueueRefresh()
        server.enqueue(MockResponse(body = """{"result":true,"success":true,"t":1,"tid":"test-tid"}"""))
        server.enqueue(MockResponse(body = shadowWith("switch", true)))
        val poll = TuyaPoll(client())

        poll.refresh()
        poll.recuperators.toggle("xfj-03")

        assertEquals(9, server.requestCount) // 7 for the refresh, then the command and one re-read
        repeat(7) { server.takeRequest() }
        assertEquals("/v2.0/cloud/thing/xfj-03/shadow/properties/issue", server.takeRequest().target)
        assertEquals("/v2.0/cloud/thing/xfj-03/shadow/properties", server.takeRequest().target)
        // The tile is repainted from the re-read, not from the command's own answer.
        assertEquals(true, poll.recuperators.state.value.tiles.single { it.id == "xfj-03" }.isOn)
    }

    @Test
    fun `a recuperator whose switch never reported is turned on by the first tap, not off`() = runTest {
        enqueueToken()
        server.enqueue(MockResponse(body = fixture("devices.json")))
        repeat(5) { server.enqueue(MockResponse(body = shadowWithout("switch"))) }
        server.enqueue(MockResponse(body = """{"result":true,"success":true,"t":1,"tid":"test-tid"}"""))
        server.enqueue(MockResponse(body = shadowWith("switch", true)))
        val poll = TuyaPoll(client())

        poll.refresh()
        poll.recuperators.toggle("xfj-01")

        repeat(7) { server.takeRequest() }
        val issued = server.takeRequest()
        val properties = Json
            .parseToJsonElement(requireNotNull(issued.body) { "the command was sent with no body" }.utf8())
            .jsonObject["properties"]!!
            .jsonPrimitive
            .content
        assertEquals("true", Json.parseToJsonElement(properties).jsonObject["switch"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a command that fails leaves the tile's values alone and reports the failure on that tile`() = runTest {
        enqueueRefresh()
        server.enqueue(MockResponse(code = 404, body = "unknown device"))
        val poll = TuyaPoll(client())

        poll.refresh()
        val before = poll.recuperators.state.value.tiles.single { it.id == "xfj-02" }
        poll.recuperators.toggle("xfj-02")

        val after = poll.recuperators.state.value.tiles.single { it.id == "xfj-02" }
        assertEquals(before.isOn, after.isOn)
        assertTrue(after.error.orEmpty().contains("404"))
        // The other four are untouched by one device's failed command.
        assertTrue(poll.recuperators.state.value.tiles.filter { it.id != "xfj-02" }.all { it.error == null })
    }

    private fun now(minutes: Long): Instant = lastRead.plusSeconds(minutes * 60)

    private fun enqueueToken() = server.enqueue(MockResponse(body = fixture("token.json")))

    /** Token, inventory and one shadow read per recuperator — the seven calls one refresh costs. */
    private fun enqueueRefresh() {
        enqueueToken()
        server.enqueue(MockResponse(body = fixture("devices.json")))
        repeat(5) { server.enqueue(MockResponse(body = fixture("shadow_properties.json"))) }
    }

    /** The recorded shadow response with the named datapoints set to the given values. */
    private fun shadowWith(vararg codeAndValue: Any): String {
        val wanted = codeAndValue.toList().chunked(2).associate { (code, value) -> code as String to value as Boolean }
        return editProperties { property ->
            val code = property["code"]!!.jsonPrimitive.content
            wanted[code]?.let { JsonObject(property + ("value" to JsonPrimitive(it))) } ?: property
        }
    }

    /** The recorded shadow response with the named datapoints missing entirely. */
    private fun shadowWithout(vararg codes: String): String = editProperties { property ->
        if (property["code"]!!.jsonPrimitive.content in codes) null else property
    }

    private fun editProperties(edit: (JsonObject) -> JsonObject?): String {
        val root = Json.parseToJsonElement(fixture("shadow_properties.json")).jsonObject
        val result = root["result"]!!.jsonObject
        val properties = result["properties"]!!.jsonArray.mapNotNull { edit(it.jsonObject) }
        return JsonObject(root + ("result" to JsonObject(result + ("properties" to JsonArray(properties))))).toString()
    }

    private fun devicesWithOfflineFirstRecuperator(): String {
        val root = Json.parseToJsonElement(fixture("devices.json")).jsonObject
        val devices =
            root["result"]!!.jsonArray.map { device ->
                if (device.jsonObject["id"]?.jsonPrimitive?.content != "xfj-01") {
                    device
                } else {
                    JsonObject(device.jsonObject + ("online" to JsonPrimitive(false)))
                }
            }
        return JsonObject(root + ("result" to JsonArray(devices))).toString()
    }

    private fun client(
        credentials: () -> TuyaCredentials = { TuyaCredentials("test-client-id", "test-client-secret", "eu-test-uid") },
        timeout: Duration = 10.seconds,
    ) = TuyaClient(
        http = OkHttpClient(),
        credentials = credentials,
        baseUrl = server.url("/"),
        timeout = timeout,
        now = { Instant.ofEpochSecond(1_786_817_910) },
    )

    private fun fixture(name: String): String = checkNotNull(javaClass.getResourceAsStream("/tuya/$name")) {
        "missing fixture app/src/test/resources/tuya/$name"
    }.use { it.readBytes().decodeToString() }
}
