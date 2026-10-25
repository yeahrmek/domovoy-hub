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
import kotlin.test.assertNull

/** One shared fetch serves every group; the request count protects the vendor allowance. */
class YandexPollTest {
    /** After all recorded readings; each group still retains its own age. */
    private val now = Instant.ofEpochSecond(1_786_667_880L + 2 * 3600)

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
    fun `one poll is one request, and it feeds every tile group`() = runTest {
        server.enqueue(MockResponse(body = yandexFixture()))
        val poll = YandexPoll(server.yandexClient())

        poll.refresh()

        assertEquals(1, server.requestCount, "one panel refresh must be one call to Yandex")
        assertEquals("/v1.0/user/info", server.takeRequest().target)
        assertEquals(18, poll.bulbs.state.value.tiles.size)
        assertEquals(listOf("curtain-01"), poll.curtains.state.value.tiles.map { it.id })
        assertEquals(listOf("ac-01", "ac-02", "ac-03"), poll.acs.state.value.tiles.map { it.id })
        // A fourth group costs no fourth call — that is the whole reason the fetch is shared.
        assertEquals(listOf("light-strip-01", "light-strip-02"), poll.strips.state.value.tiles.map { it.id })
    }

    @Test
    fun `one failed poll errors every group at once, each keeping its own values and ages`() = runTest {
        server.enqueue(MockResponse(body = yandexFixture()))
        server.enqueue(MockResponse(code = 500, body = "boom"))
        val poll = YandexPoll(server.yandexClient())

        poll.refresh()
        val bulbsBefore = poll.bulbs.state.value.tiles
        val curtainsBefore = poll.curtains.state.value.tiles
        val acsBefore = poll.acs.state.value.tiles
        val stripsBefore = poll.strips.state.value.tiles
        poll.refresh()

        assertEquals(2, server.requestCount, "a failed refresh must not be retried once per group")
        val bulbs = poll.bulbs.state.value
        val curtains = poll.curtains.state.value
        val acs = poll.acs.state.value
        val strips = poll.strips.state.value
        // A shared fetch failure reaches every group, using the bounded error labels.
        assertEquals("Не удалось выполнить запрос", bulbs.error, "the bulbs must say why")
        assertEquals("Не удалось выполнить запрос", curtains.error, "the curtains must say why")
        assertEquals("Не удалось выполнить запрос", acs.error, "the air conditioners must say why")
        assertEquals("Не удалось выполнить запрос", strips.error, "the strips must say why")
        assertEquals(bulbsBefore, bulbs.tiles)
        assertEquals(curtainsBefore, curtains.tiles)
        assertEquals(acsBefore, acs.tiles)
        assertEquals(stripsBefore, strips.tiles)
        // Shared fetching must preserve each capability's timestamp.
        assertEquals("Включено · 20 дн. назад", statusLine(bulbs.tiles.single { it.id == "light-01" }, now))
        assertEquals("0% · 2 ч назад", statusLine(curtains.tiles.single(), now))
        assertEquals("Выключено · 98 дн. назад", statusLine(acs.tiles.single { it.id == "ac-01" }, now))
        assertEquals("Включено · Время неизвестно", statusLine(strips.tiles.single { it.id == "light-strip-01" }, now))
    }

    @Test
    fun `a refresh that succeeded stamps when the panel last read the house, and a failed one does not`() = runTest {
        // lastPolledAt records our successful fetch, independently of vendor timestamps.
        server.enqueue(MockResponse(body = yandexFixture()))
        server.enqueue(MockResponse(code = 500, body = "boom"))
        var clock = Instant.ofEpochSecond(1_786_700_000)
        val poll = YandexPoll(server.yandexClient(), now = { clock })

        assertNull(poll.bulbs.state.value.lastPolledAt, "nothing has been read yet")
        poll.refresh()
        val read = clock
        clock = clock.plusSeconds(60)
        poll.refresh()

        // One call is the whole house, so one stamp reaches all four groups — and the failure a
        // minute later moves none of them: a refresh that failed read nothing.
        assertEquals(
            listOf(read, read, read, read),
            listOf(
                poll.bulbs.state.value.lastPolledAt,
                poll.curtains.state.value.lastPolledAt,
                poll.acs.state.value.lastPolledAt,
                poll.strips.state.value.lastPolledAt,
            ),
        )
    }

    @Test
    fun `the re-read after a toggle is the shared poll, not a fourth call`() = runTest {
        server.enqueue(MockResponse(body = yandexFixture()))
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-1","devices":[]}"""))
        // The curtain moved while the bulb was being toggled. Nothing but a shared re-read can put
        // that on the curtain tile, so this is what tells a shared poll from a bulbs-only one.
        server.enqueue(MockResponse(body = fixtureWithCurtainOpen(40)))
        val poll = YandexPoll(server.yandexClient())

        poll.refresh()
        poll.bulbs.toggle("light-01")

        assertEquals(3, server.requestCount, "the poll, the action, and one shared re-read")
        assertEquals("/v1.0/user/info", server.takeRequest().target)
        assertEquals("/v1.0/devices/actions", server.takeRequest().target)
        assertEquals("/v1.0/user/info", server.takeRequest().target)
        assertEquals(40.0, poll.curtains.state.value.tiles.single().openPercent)
        assertEquals(3, poll.acs.state.value.tiles.size)
        assertEquals(2, poll.strips.state.value.tiles.size)
    }

    /** The recorded body with curtain-01's `range` position set to [percent]. */
    private fun fixtureWithCurtainOpen(percent: Int): String {
        val root = Json.parseToJsonElement(yandexFixture()).jsonObject
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
                                val state = capability.jsonObject["state"]!!.jsonObject
                                val moved = JsonObject(state + ("value" to JsonPrimitive(percent)))
                                JsonObject(capability.jsonObject + ("state" to moved))
                            }
                        }
                    JsonObject(device.jsonObject + ("capabilities" to JsonArray(capabilities)))
                }
            }
        return JsonObject(root + ("devices" to JsonArray(devices))).toString()
    }
}
