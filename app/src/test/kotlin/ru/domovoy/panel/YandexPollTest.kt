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
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * One `/v1.0/user/info` call is the whole house, so one panel refresh is one request no matter how
 * many tile groups read from it. Yandex publishes no rate limit for this endpoint — see
 * docs/yandex.md — and a call per group scales with every tile type the panel grows, so what is
 * asserted here is the request count itself, not just the values that come out of it.
 */
class YandexPollTest {
    /**
     * Late enough to be after all three groups' readings. One "now" across the panel, and the three
     * groups still print three different ages — which is the point: they were read at different
     * times and each keeps its own.
     */
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
    fun `one poll is one request, and it feeds the bulbs, the curtains and the air conditioners`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        val poll = YandexPoll(client())

        poll.refresh()

        assertEquals(1, server.requestCount, "one panel refresh must be one call to Yandex")
        assertEquals("/v1.0/user/info", server.takeRequest().target)
        assertEquals(18, poll.bulbs.state.value.tiles.size)
        assertEquals(listOf("curtain-01"), poll.curtains.state.value.tiles.map { it.id })
        assertEquals(listOf("ac-01", "ac-02", "ac-03"), poll.acs.state.value.tiles.map { it.id })
    }

    @Test
    fun `one failed poll errors every group at once, each keeping its own values and ages`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        server.enqueue(MockResponse(code = 500, body = "boom"))
        val poll = YandexPoll(client())

        poll.refresh()
        val bulbsBefore = poll.bulbs.state.value.tiles
        val curtainsBefore = poll.curtains.state.value.tiles
        val acsBefore = poll.acs.state.value.tiles
        poll.refresh()

        assertEquals(2, server.requestCount, "a failed refresh must not be retried once per group")
        val bulbs = poll.bulbs.state.value
        val curtains = poll.curtains.state.value
        val acs = poll.acs.state.value
        // The one failure reaches all three groups: no group is left painting a value as current
        // while the panel behind it is not updating.
        assertTrue(bulbs.error.orEmpty().contains("500"), "the bulbs must say why: ${bulbs.error}")
        assertTrue(curtains.error.orEmpty().contains("500"), "the curtains must say why: ${curtains.error}")
        assertTrue(acs.error.orEmpty().contains("500"), "the air conditioners must say why: ${acs.error}")
        assertEquals(bulbsBefore, bulbs.tiles)
        assertEquals(curtainsBefore, curtains.tiles)
        assertEquals(acsBefore, acs.tiles)
        // Sharing the fetch does not merge the ages: the bulb, the curtain and the ac were read
        // days apart, and one "last read" for the panel would be a lie about two of them.
        assertEquals(
            "on · 20 d ago · not updating: ${bulbs.error}",
            statusLine(bulbs.tiles.single { it.id == "light-01" }, now, bulbs.error),
        )
        assertEquals(
            "0% open · 2 h ago · not updating: ${curtains.error}",
            statusLine(curtains.tiles.single(), now, curtains.error),
        )
        assertEquals(
            "off · 17 d ago · 18 °C · 98 d ago · not updating: ${acs.error}",
            statusLine(acs.tiles.single { it.id == "ac-01" }, now, acs.error),
        )
    }

    @Test
    fun `the re-read after a toggle is the shared poll, not a fourth call`() = runTest {
        server.enqueue(MockResponse(body = fixture()))
        server.enqueue(MockResponse(body = """{"status":"ok","request_id":"r-1","devices":[]}"""))
        // The curtain moved while the bulb was being toggled. Nothing but a shared re-read can put
        // that on the curtain tile, so this is what tells a shared poll from a bulbs-only one.
        server.enqueue(MockResponse(body = fixtureWithCurtainOpen(40)))
        val poll = YandexPoll(client())

        poll.refresh()
        poll.bulbs.toggle("light-01")

        assertEquals(3, server.requestCount, "the poll, the action, and one shared re-read")
        assertEquals("/v1.0/user/info", server.takeRequest().target)
        assertEquals("/v1.0/devices/actions", server.takeRequest().target)
        assertEquals("/v1.0/user/info", server.takeRequest().target)
        assertEquals(40.0, poll.curtains.state.value.tiles.single().openPercent)
        assertEquals(3, poll.acs.state.value.tiles.size)
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

    /** The recorded body with curtain-01's `range` position set to [percent]. */
    private fun fixtureWithCurtainOpen(percent: Int): String {
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
