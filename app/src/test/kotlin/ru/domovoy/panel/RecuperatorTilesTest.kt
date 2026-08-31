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
import ru.domovoy.core.FakeSharedPreferences
import ru.domovoy.core.KnownRecuperators
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
    fun `a tile shows whether the recuperator is on, its fan speed and one age for the four`() = runTest {
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
        // One age, and the oldest of the four datapoints this tile shows: the switch and the speed
        // have not moved in three days while the humidity is 26 s old. The tile under-claims its
        // freshness rather than quoting the newest of them — see StalenessTest.
        assertEquals("off · no speed · 3 d ago", statusLine(tile, now(minutes = 0)))
    }

    @Test
    fun `a tile shows the temperature and humidity on a line of their own, without ages`() = runTest {
        // These two are the only datapoints that move on their own, and they move at different
        // times: the humidity was 26 s old when the response was recorded and the temperature
        // nearly 4 minutes. Both ages used to be printed here, next to the two on the line above —
        // four timestamps on one tile, three of them the same number. They are folded into the one
        // age the status line prints, which is the oldest of all four.
        enqueueRefresh()
        val poll = TuyaPoll(client())

        poll.refresh()

        val tile = poll.recuperators.state.value.tiles.single { it.id == "xfj-01" }
        assertEquals(29.3, tile.temperature)
        assertEquals(32.2, tile.humidity)
        assertEquals("29.3 °C · 32.2 %", climateLine(tile))
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
        assertEquals("unknown · 32.2 %", climateLine(tile))
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
        assertNull(climateLine(tile))
        // The tile is still there, and the line that carries its staleness is untouched.
        assertEquals("off · no speed · 3 d ago", statusLine(tile, now(minutes = 0)))
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
            assertEquals("29.3 °C · 32.2 %", climateLine(tile))
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
        assertEquals("off · high · 3 d ago", statusLine(tile, now(minutes = 0)))
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
        assertEquals("off · low + high · 3 d ago", statusLine(tile, now(minutes = 0)))
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
        assertEquals("off · unknown · 3 d ago", statusLine(tile, now(minutes = 0)))
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
        assertEquals("unknown · no speed · 3 d ago", statusLine(tile, now(minutes = 0)))
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
        // **"offline" replaces the power word rather than leading a queue of echoes.** It used to
        // read `offline · unknown · low + medium + high · 3 d ago` — five facts about a device that
        // is not there, 39 characters of them on a 251 dp tile that holds about 24. What survives
        // is the state and its age; what the panel is failing to read is on the line below.
        assertEquals("offline · 3 d ago", statusLine(tile, now(minutes = 0)))
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
        assertEquals("failed", failed.error)
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
        assertEquals("off · no speed · 3 d ago", statusLine(after, now(minutes = 0)))
        assertEquals(
            "failed",
            anatomy(after, now(minutes = 0), groupError = null).detail,
            "the tile has to keep its values and say why they are not moving",
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
    fun `a refresh that read the inventory stamps when the panel last read, and a failed one does not`() = runTest {
        // The recuperators are on their own poll and their own timer, so they carry their own
        // stamp: 6 minutes between reads is healthy here and long dead on the Yandex groups.
        enqueueRefresh()
        server.enqueue(MockResponse(code = 500, body = "boom"))
        var clock = Instant.ofEpochSecond(1_786_818_000)
        val poll = TuyaPoll(client(), now = { clock })

        assertNull(poll.recuperators.state.value.lastPolledAt, "nothing has been read yet")
        poll.refresh()
        val read = clock
        clock = clock.plusSeconds(600)
        poll.refresh()

        assertEquals(read, poll.recuperators.state.value.lastPolledAt)
    }

    @Test
    fun `one device's read failing still stamps the group, because the poll itself ran`() = runTest {
        // The inventory answered and four devices read fine. What failed is one tile's own call,
        // which is on that tile — the group has not stopped being polled and must not say it has.
        enqueueToken()
        server.enqueue(MockResponse(body = fixture("devices.json")))
        server.enqueue(MockResponse(code = 500, body = "boom"))
        repeat(4) { server.enqueue(MockResponse(body = fixture("shadow_properties.json"))) }
        val polledAt = Instant.ofEpochSecond(1_786_818_000)
        val poll = TuyaPoll(client(), now = { polledAt })

        poll.refresh()

        assertEquals(polledAt, poll.recuperators.state.value.lastPolledAt)
        assertNotNull(poll.recuperators.state.value.tiles.single { it.id == "xfj-01" }.error)
    }

    @Test
    fun `a panel with no credentials stored reports a failed poll instead of standing there empty`() = runTest {
        // As on the Yandex side: the client's sentence about `local.properties` goes to `Log` with
        // the exception, and the wall gets one of the four words `reason` is willing to print. The
        // group failure line at the top of Главная is what stops this being a blank panel with no
        // reason given — see BulbTilesTest, where that trade is written down.
        val poll = TuyaPoll(client(credentials = { TuyaCredentials("", "", "") }))

        poll.refresh()

        assertTrue(poll.recuperators.state.value.tiles.isEmpty())
        assertEquals("failed", poll.recuperators.state.value.error)
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
    fun `selecting a speed on a powered recuperator issues it and re-reads only that device`() = runTest {
        enqueueToken()
        server.enqueue(MockResponse(body = fixture("devices.json")))
        repeat(5) { server.enqueue(MockResponse(body = shadowWith("switch", true))) }
        server.enqueue(MockResponse(body = """{"result":true,"success":true,"t":1,"tid":"test-tid"}"""))
        server.enqueue(MockResponse(body = shadowWith("speed_two", true)))
        val poll = TuyaPoll(client())

        poll.refresh()
        poll.recuperators.setSpeed("xfj-01", FanSpeed.Medium)

        repeat(7) { server.takeRequest() }
        val issue = server.takeRequest()
        val properties = Json.parseToJsonElement(requireNotNull(issue.body).utf8()).jsonObject["properties"]!!
            .jsonPrimitive.content
        assertEquals("true", Json.parseToJsonElement(properties).jsonObject["speed_two"]!!.jsonPrimitive.content)
        assertEquals("/v2.0/cloud/thing/xfj-01/shadow/properties", server.takeRequest().target)
        assertEquals(listOf(FanSpeed.Medium), poll.recuperators.state.value.tiles.single { it.id == "xfj-01" }.speeds)
    }

    @Test
    fun `speed selection is refused while the recuperator is off because the device ignores it`() = runTest {
        enqueueRefresh()
        val poll = TuyaPoll(client())

        poll.refresh()
        poll.recuperators.setSpeed("xfj-01", FanSpeed.Medium)

        assertEquals(7, server.requestCount)
        assertEquals("failed", poll.recuperators.state.value.tiles.single { it.id == "xfj-01" }.error)
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
        assertEquals("failed", after.error)
        // The other four are untouched by one device's failed command.
        assertTrue(poll.recuperators.state.value.tiles.filter { it.id != "xfj-02" }.all { it.error == null })
    }

    @Test
    fun `a recuperator is in the room the flat recorded for it, and in none when it did not`() = runTest {
        // Tuya's inventory carries no room for any device, so the only source is local.properties.
        // What is not in there is left unplaced rather than read out of the device's name: "Бризер
        // данина комната" names no room Yandex knows, and the name is renameable from the vendor's
        // own app. Unplaced is visible on the wall — see roomSections — not dropped.
        enqueueRefresh()
        val poll = TuyaPoll(client(), rooms = recuperatorRooms("xfj-01=Спальня;xfj-05=Зал"))

        poll.refresh()

        val tiles = poll.recuperators.state.value.tiles
        assertEquals("Спальня", tiles.single { it.id == "xfj-01" }.room)
        assertEquals("Зал", tiles.single { it.id == "xfj-05" }.room)
        assertNull(tiles.single { it.id == "xfj-03" }.room)
    }

    @Test
    fun `a tap does not move a recuperator out of its room`() = runTest {
        // The tile is repainted from a fresh read of that one device, and the read answers only
        // datapoints — nothing in it says which room the device is in.
        enqueueRefresh()
        server.enqueue(MockResponse(body = """{"result":true,"success":true,"t":1,"tid":"test-tid"}"""))
        server.enqueue(MockResponse(body = fixture("shadow_properties.json")))
        val poll = TuyaPoll(client(), rooms = recuperatorRooms("xfj-01=Спальня"))

        poll.refresh()
        poll.recuperators.toggle("xfj-01")

        assertEquals("Спальня", poll.recuperators.state.value.tiles.single { it.id == "xfj-01" }.room)
    }

    @Test
    fun `the recuperators the panel read last time are on the wall before the first poll`() = runTest {
        // A Tuya refresh is five calls and runs every 6 minutes, so a tablet that reboots while the
        // Wi-Fi is still coming up has no recuperators at all until the second attempt. The panel
        // puts up who it read last time instead, with no values on them and every age "never read".
        val prefs = FakeSharedPreferences()
        enqueueRefresh()
        TuyaPoll(client(), rooms = recuperatorRooms("xfj-01=Спальня"), known = KnownRecuperators(prefs)).refresh()

        val restarted = TuyaPoll(client(), known = KnownRecuperators(prefs))

        val tile = restarted.recuperators.state.value.tiles.single { it.id == "xfj-01" }
        assertEquals("Бризер данина комната", tile.name)
        assertEquals("Спальня", tile.room, "the room it was placed in is remembered with it")
        assertNull(tile.isOn, "a switch position from before the reboot is not a reading")
        assertEquals("unknown · unknown", statusLine(tile, now(minutes = 0)))
        assertNull(restarted.recuperators.state.value.lastPolledAt, "nothing has been read yet")
    }

    @Test
    fun `a first poll that never got through leaves the remembered tiles up and says why`() = runTest {
        // The whole point: five tiles saying "not updating" beat one line of error where five tiles
        // should be, because the wall still shows what is in the flat and where.
        val prefs = FakeSharedPreferences()
        enqueueRefresh()
        TuyaPoll(client(), known = KnownRecuperators(prefs)).refresh()
        // A fresh client after the restart, so it asks for a token before the inventory it fails on.
        enqueueToken()
        server.enqueue(MockResponse(code = 500, body = "boom"))

        val restarted = TuyaPoll(client(), known = KnownRecuperators(prefs))
        restarted.refresh()

        val state = restarted.recuperators.state.value
        assertEquals(5, state.tiles.size)
        val reason = requireNotNull(state.error)
        val detail = anatomy(state.tiles.first(), now(minutes = 0), groupError = reason).detail
        assertEquals(reason, detail, "the tile has to carry the group's reason")
    }

    @Test
    fun `a remembered recuperator can still be switched, and is not a tile that swallows the tap`() = runTest {
        // The tile is on the wall before any poll, so the tap has to work from the memory alone:
        // the command needs the id and the re-read needs the device, and both are remembered.
        val prefs = FakeSharedPreferences()
        enqueueRefresh()
        TuyaPoll(client(), known = KnownRecuperators(prefs)).refresh()
        enqueueToken()
        server.enqueue(MockResponse(body = """{"result":true,"success":true,"t":1,"tid":"test-tid"}"""))
        server.enqueue(MockResponse(body = shadowWith("switch", true)))

        val restarted = TuyaPoll(client(), known = KnownRecuperators(prefs))
        restarted.recuperators.toggle("xfj-03")

        assertEquals(true, restarted.recuperators.state.value.tiles.single { it.id == "xfj-03" }.isOn)
    }

    @Test
    fun `a mistyped room entry leaves that recuperator unplaced instead of failing the panel`() {
        // A build constant nobody can see is the worst place for a parse error to end the panel:
        // the wall would go blank for a missing "=". The entry is skipped, and the recuperator
        // turns up in the unplaced section, which is where the mistake becomes visible.
        assertEquals(
            mapOf("xfj-01" to "Спальня", "xfj-04" to "Кабинет"),
            recuperatorRooms(" xfj-01 = Спальня ;xfj-05;=Зал;xfj-02= ;xfj-04=Кабинет;"),
        )
        assertEquals(emptyMap(), recuperatorRooms(""))
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
