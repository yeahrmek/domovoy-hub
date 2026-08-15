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
import ru.domovoy.core.OnOff
import ru.domovoy.core.Reading
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
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
            devices.map { it.id }.sorted(),
        )
    }

    @Test
    fun `bulbs of another household and things that are not bulbs are left out`() = runTest {
        server.enqueue(MockResponse(body = fixture()))

        val ids = client().devices().map { list -> list.map { it.id } }.getOrThrow()

        // light-09 and light-13 are bulbs, but of other households; light-strip-01 is a
        // strip, ac-01 an air conditioner, curtain-01 the curtains — none in scope here.
        assertTrue(
            ids.none { it in setOf("light-09", "light-13", "light-14", "light-strip-01", "ac-01", "curtain-01") },
            "leaked out-of-scope devices: $ids",
        )
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
}
