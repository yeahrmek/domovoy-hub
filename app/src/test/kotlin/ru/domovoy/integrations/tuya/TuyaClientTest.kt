package ru.domovoy.integrations.tuya

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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.domovoy.core.DeviceKind
import ru.domovoy.core.Reading
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Drives the client against the recorded Tuya bodies over a loopback socket. Every fixture under
 * `/tuya/` is a real response from the account with the device ids, local keys, WAN address,
 * coordinates and tokens replaced — see docs/tuya.md, "Recorded responses".
 *
 * Two things separate this from the Yandex client and are asserted here rather than assumed: the
 * token is cached until it expires instead of fetched per call, and real state costs one call per
 * recuperator because the batch route answers `40001900 No space permission`.
 */
class TuyaClientTest {
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
    fun `the inventory brings back the five recuperators and nothing else in the account`() = runTest {
        enqueueToken()
        server.enqueue(MockResponse(body = fixture("devices.json")))

        val devices = client().devices().getOrThrow()

        // The account holds 20 devices — 11 lamps, 2 towel rails, a gateway and a wall switch as
        // well. Only the `xfj` recuperators have a tile.
        assertEquals(listOf("xfj-01", "xfj-02", "xfj-03", "xfj-04", "xfj-05"), devices.map { it.id })
        assertTrue(devices.all { it.kind == DeviceKind.Recuperator })
        assertEquals("Бризер зал", devices.single { it.id == "xfj-05" }.name)
    }

    @Test
    fun `the inventory reports whether a device is online, which the panel cannot get from a poll alone`() = runTest {
        enqueueToken()
        server.enqueue(MockResponse(body = fixture("devices.json")))

        val devices = client().devices().getOrThrow()

        // All five recuperators were online when the response was recorded; the 11 lamps were not,
        // which is why the field is read at all — a reachable API says nothing about the device.
        assertEquals(listOf(true, true, true, true, true), devices.map { it.online })
    }

    @Test
    fun `the inventory carries no state, because the status it does carry is filtered to switch`() = runTest {
        // `/v1.0/users/{uid}/devices` returns a `status` array filtered to Tuya's standard
        // instruction set: for this product that is `switch` alone, timestamped only by the
        // device-level `update_time`. Reading it as the tile's power would put an age on the
        // value that belongs to a different reading. See docs/tuya.md.
        enqueueToken()
        server.enqueue(MockResponse(body = fixture("devices.json")))

        val device = client().devices().getOrThrow().first()

        assertNull(device.onOff)
        assertTrue(device.ranges.isEmpty())
        assertTrue(device.toggles.isEmpty())
    }

    @Test
    fun `reading one recuperator fills in the thirteen datapoints the thing model has`() = runTest {
        enqueueToken()
        server.enqueue(MockResponse(body = fixture("devices.json")))
        server.enqueue(MockResponse(body = fixture("shadow_properties.json")))
        val client = client()

        val inventory = client.devices().getOrThrow().first()
        val device = client.read(inventory).getOrThrow()

        assertEquals("xfj-01", device.id)
        assertEquals("Бризер данина комната", device.name)
        assertEquals(false, device.onOff?.isOn)
        // Every bool datapoint but `switch` — the three speeds, the three humidity steps, sleep
        // and the three modes. dp 110 does not exist; the numbering has a hole.
        assertEquals(
            setOf(
                "speed_one", "speed_two", "speed_three", "sleep_mode",
                "huimidity_one", "huimidity_two", "huimidity_three",
                "in_mode", "out_mode", "auto_mode",
            ),
            device.toggles.keys,
        )
        assertEquals(false, device.toggles.getValue("speed_one").isOn)
    }

    @Test
    fun `humidity and temperature come back as tenths, with no unit invented for them`() = runTest {
        enqueueToken()
        server.enqueue(MockResponse(body = fixture("devices.json")))
        server.enqueue(MockResponse(body = fixture("shadow_properties.json")))
        val client = client()

        val device = client.read(client.devices().getOrThrow().first()).getOrThrow()

        // 322 is 32.2 %RH and 293 is 29.3 °C — `scale: 1` in the thing model.
        assertEquals(32.2, device.ranges.getValue("huimi").value)
        assertEquals(29.3, device.ranges.getValue("temper").value)
        // The thing model names no unit at all for either, so neither may print one, and the
        // shadow response carries no bounds.
        assertNull(device.ranges.getValue("temper").unit)
        assertNull(device.ranges.getValue("temper").bounds)
    }

    @Test
    fun `the recorded thing model still says the two values are tenths and carry no unit`() {
        // The divisor above is a constant in the client, not something the panel re-reads at
        // runtime — one call per refresh is already the constraint. This is the guard on it: if a
        // re-record ever comes back with another scale or a unit, this fails rather than the
        // wall quietly showing 293 °C.
        val model = Json.parseToJsonElement(fixture("thing_model.json"))
            .jsonObject["result"]!!
            .jsonObject["model"]!!
            .jsonPrimitive
            .content
        val properties = Json.parseToJsonElement(model)
            .jsonObject["services"]!!
            .jsonArray
            .flatMap { it.jsonObject["properties"]!!.jsonArray }
            .associateBy { it.jsonObject["code"]!!.jsonPrimitive.content }

        listOf("huimi", "temper").forEach { code ->
            val spec = properties.getValue(code).jsonObject["typeSpec"]!!.jsonObject
            assertEquals("value", spec["type"]!!.jsonPrimitive.content)
            assertEquals(1, spec["scale"]!!.jsonPrimitive.int)
            assertEquals("", spec["unit"]!!.jsonPrimitive.content, "the vendor now names a unit for $code")
        }
    }

    @Test
    fun `each datapoint carries its own age, so a tile can say how old that one reading is`() = runTest {
        enqueueToken()
        server.enqueue(MockResponse(body = fixture("devices.json")))
        server.enqueue(MockResponse(body = fixture("shadow_properties.json")))
        val client = client()

        val device = client.read(client.devices().getOrThrow().first()).getOrThrow()

        // `time` is milliseconds, not seconds: 1786539930159 is 2026-08-12, and reading it as
        // seconds would put the switch 54,000 years in the future.
        assertEquals(
            Reading.At(Instant.ofEpochMilli(1_786_539_930_159L)),
            device.onOff?.lastUpdated,
        )
        // The humidity was read minutes before the poll while the switch had not moved in days,
        // so the two ages are genuinely different and are kept apart.
        assertEquals(
            Reading.At(Instant.ofEpochMilli(1_786_817_884_638L)),
            device.ranges.getValue("huimi").lastUpdated,
        )
    }

    @Test
    fun `the token is fetched once and reused, not fetched per call`() = runTest {
        // The allowance is metered: a token call per business call would double every refresh.
        enqueueToken()
        server.enqueue(MockResponse(body = fixture("devices.json")))
        server.enqueue(MockResponse(body = fixture("shadow_properties.json")))
        val client = client()

        val device = client.devices().getOrThrow().first()
        client.read(device).getOrThrow()

        assertEquals(3, server.requestCount)
        assertEquals("/v1.0/token?grant_type=1", server.takeRequest().target)
        assertEquals("/v1.0/users/eu-test-uid/devices", server.takeRequest().target)
        assertEquals("/v2.0/cloud/thing/xfj-01/shadow/properties", server.takeRequest().target)
    }

    @Test
    fun `a token past its expiry is fetched again rather than sent stale`() = runTest {
        // The account answers `expire_time` as the seconds *left* on the token it hands out, not
        // a flat 7200 — 5385 in the recorded response. So the expiry is now plus that, and a
        // panel that runs for weeks has to notice it passing.
        enqueueToken()
        server.enqueue(MockResponse(body = fixture("devices.json")))
        enqueueToken()
        server.enqueue(MockResponse(body = fixture("devices.json")))
        var now = Instant.ofEpochSecond(1_786_817_910)
        val client = client(now = { now })

        client.devices().getOrThrow()
        now = now.plusSeconds(5_385)
        client.devices().getOrThrow()

        assertEquals(4, server.requestCount)
        assertEquals("/v1.0/token?grant_type=1", server.takeRequest().target)
        assertEquals("/v1.0/users/eu-test-uid/devices", server.takeRequest().target)
        assertEquals("/v1.0/token?grant_type=1", server.takeRequest().target)
    }

    @Test
    fun `the token call is signed with an empty access token, and business calls with the token`() = runTest {
        enqueueToken()
        server.enqueue(MockResponse(body = fixture("devices.json")))

        client().devices().getOrThrow()

        val token = server.takeRequest()
        assertEquals("test-client-id", token.headers["client_id"])
        assertEquals("HMAC-SHA256", token.headers["sign_method"])
        // No access_token header at all on the token call — there is none to send yet.
        assertNull(token.headers["access_token"])
        assertEquals("test-access-token", server.takeRequest().headers["access_token"])
    }

    @Test
    fun `the signature is the uppercase HMAC of what Tuya will rebuild on its side`() = runTest {
        // Spelled out rather than delegated to the client's own signer: this is the wire contract,
        // and getting it wrong is a day of `1004 sign invalid` (docs/tuya.md).
        enqueueToken()
        server.enqueue(MockResponse(body = fixture("devices.json")))

        client().devices().getOrThrow()

        val token = server.takeRequest()
        val t = token.headers["t"]!!
        val emptyBody = sha256Hex("")
        val stringToSign = "GET\n$emptyBody\n\n/v1.0/token?grant_type=1"
        assertEquals(
            hmacSha256Upper("test-client-id" + "" + t + "" + stringToSign, "test-client-secret"),
            token.headers["sign"],
        )

        val devices = server.takeRequest()
        val deviceT = devices.headers["t"]!!
        val deviceStringToSign = "GET\n$emptyBody\n\n/v1.0/users/eu-test-uid/devices"
        assertEquals(
            hmacSha256Upper(
                "test-client-id" + "test-access-token" + deviceT + "" + deviceStringToSign,
                "test-client-secret",
            ),
            devices.headers["sign"],
        )
    }

    @Test
    fun `a 13-digit millisecond timestamp is sent, which is what the host checks the skew against`() = runTest {
        enqueueToken()
        server.enqueue(MockResponse(body = fixture("devices.json")))

        client(now = { Instant.ofEpochSecond(1_786_817_910) }).devices().getOrThrow()

        assertEquals("1786817910000", server.takeRequest().headers["t"])
    }

    @Test
    fun `a business error that arrives as HTTP 200 is a failure naming the code and the message`() = runTest {
        // Tuya answers `success: false` with a 200, so a client that only checks the status code
        // would hand the panel an empty device list and call it a good poll.
        enqueueToken()
        server.enqueue(MockResponse(body = fixture("batch_no_permission.json")))

        val result = client().devices()

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message.contains("40001900"), "the code has to survive: $message")
        assertTrue(message.contains("No space permission"), "the message has to survive: $message")
    }

    @Test
    fun `a token call that fails is a failed poll, not a crash and not an unsigned request`() = runTest {
        server.enqueue(MockResponse(body = """{"code":1004,"msg":"sign invalid","success":false,"t":1}"""))

        val result = client().devices()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("1004"))
        // The device call is never attempted without a token.
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `with no credentials stored nothing leaves the tablet`() = runTest {
        val result = client(credentials = { TuyaCredentials("", "", "") }).devices()

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message.orEmpty().contains("local.properties"),
            "the panel has to be able to print how a credential gets in: ${result.exceptionOrNull()?.message}",
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a store that cannot be read at all is a failed poll, not a crash`() = runTest {
        val result = client(credentials = { error("secure storage unavailable") }).devices()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("secure storage unavailable"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `credentials are read at every call, so ones stored later are used without a restart`() = runTest {
        enqueueToken()
        server.enqueue(MockResponse(body = fixture("devices.json")))
        var stored = TuyaCredentials("", "", "")
        val client = client(credentials = { stored })

        assertTrue(client.devices().isFailure)
        stored = TuyaCredentials("test-client-id", "test-client-secret", "eu-test-uid")

        assertTrue(client.devices().isSuccess)
    }

    @Test
    fun `a poll rejected by the host fails instead of throwing`() = runTest {
        enqueueToken()
        server.enqueue(MockResponse(code = 502, body = "Bad Gateway"))

        val result = client().devices()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("502"))
    }

    @Test
    fun `a read that outlives its timeout fails instead of hanging`() = runTest {
        enqueueToken()
        server.enqueue(
            MockResponse
                .Builder()
                .body(fixture("devices.json"))
                .bodyDelay(10, TimeUnit.SECONDS)
                .build(),
        )

        val result = client(timeout = 200.milliseconds).devices()

        assertTrue(result.isFailure)
    }

    @Test
    fun `switching a recuperator on issues the switch property for that one device`() = runTest {
        enqueueToken()
        server.enqueue(MockResponse(body = """{"result":true,"success":true,"t":1,"tid":"test-tid"}"""))

        client().setOn("xfj-01", on = true).getOrThrow()

        server.takeRequest()
        val issue = server.takeRequest()
        assertEquals("POST", issue.method)
        assertEquals("/v2.0/cloud/thing/xfj-01/shadow/properties/issue", issue.target)
        val body = requireNotNull(issue.body) { "the command was sent with no body" }.utf8()
        // `properties` is a JSON *string* inside the JSON body, which is Tuya's own shape.
        val properties = Json.parseToJsonElement(body).jsonObject["properties"]!!.jsonPrimitive.content
        assertEquals(true, Json.parseToJsonElement(properties).jsonObject["switch"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `selecting a recuperator speed issues only that verified speed property`() = runTest {
        enqueueToken()
        server.enqueue(MockResponse(body = """{"result":true,"success":true,"t":1,"tid":"test-tid"}"""))

        client().setSpeed("xfj-01", code = "speed_two").getOrThrow()

        server.takeRequest()
        val issue = server.takeRequest()
        assertEquals("POST", issue.method)
        val body = requireNotNull(issue.body).utf8()
        val properties =
            Json.parseToJsonElement(
                Json.parseToJsonElement(body).jsonObject["properties"]!!.jsonPrimitive.content,
            ).jsonObject
        assertEquals(setOf("speed_two"), properties.keys)
        assertEquals(true, properties.getValue("speed_two").jsonPrimitive.boolean)
    }

    @Test
    fun `a recuperator speed outside the three verified datapoints is refused before a request`() = runTest {
        val result = client().setSpeed("xfj-01", code = "sleep_mode")

        assertTrue(result.isFailure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a command is signed over its body, not over an empty one`() = runTest {
        enqueueToken()
        server.enqueue(MockResponse(body = """{"result":true,"success":true,"t":1,"tid":"test-tid"}"""))

        client().setOn("xfj-01", on = false).getOrThrow()

        server.takeRequest()
        val issue = server.takeRequest()
        val body = requireNotNull(issue.body).utf8()
        val stringToSign = "POST\n${sha256Hex(body)}\n\n/v2.0/cloud/thing/xfj-01/shadow/properties/issue"
        assertEquals(
            hmacSha256Upper(
                "test-client-id" + "test-access-token" + issue.headers["t"]!! + "" + stringToSign,
                "test-client-secret",
            ),
            issue.headers["sign"],
        )
    }

    @Test
    fun `a command the host refuses fails instead of reporting success`() = runTest {
        enqueueToken()
        server.enqueue(MockResponse(body = """{"code":2008,"msg":"command invalid","success":false,"t":1}"""))

        val result = client().setOn("xfj-01", on = true)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("command invalid"))
    }

    @Test
    fun `a poll leaves the thread it was started from free to run other work`() = runBlocking {
        // The same NetworkOnMainThreadException the Yandex client had: reading the body is a
        // blocking socket read, and the panel polls from Dispatchers.Main.
        val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "panel-main") }
        val main = executor.asCoroutineDispatcher()
        enqueueToken()
        server.enqueue(
            MockResponse
                .Builder()
                .body(fixture("devices.json"))
                .bodyDelay(1, TimeUnit.SECONDS)
                .build(),
        )

        val poll = async(main) { client().devices() }
        delay(300)
        val ticked = CompletableDeferred<Unit>()
        launch(main) { ticked.complete(Unit) }

        withTimeout(400) { ticked.await() }

        assertTrue(poll.await().isSuccess)
        executor.shutdown()
    }

    private fun enqueueToken() = server.enqueue(MockResponse(body = fixture("token.json")))

    private fun client(
        credentials: () -> TuyaCredentials = { TuyaCredentials("test-client-id", "test-client-secret", "eu-test-uid") },
        timeout: Duration = 10.seconds,
        now: () -> Instant = { Instant.ofEpochSecond(1_786_817_910) },
    ) = TuyaClient(
        http = OkHttpClient(),
        credentials = credentials,
        baseUrl = server.url("/"),
        timeout = timeout,
        now = now,
    )

    private fun sha256Hex(value: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun hmacSha256Upper(
        value: String,
        secret: String,
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(value.toByteArray()).joinToString("") { "%02X".format(it) }
    }

    private fun fixture(name: String): String = checkNotNull(javaClass.getResourceAsStream("/tuya/$name")) {
        "missing fixture app/src/test/resources/tuya/$name"
    }.use { it.readBytes().decodeToString() }
}
