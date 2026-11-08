package ru.domovoy.integrations.tuya

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import ru.domovoy.core.Device
import ru.domovoy.core.Reading
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** A loopback device exercises the actual encrypted client; no vendor or LAN calls are made. */
class TuyaLanClientTest {
    private val instant = Instant.parse("2026-10-31T12:00:00Z")

    @Test
    fun `invalid local configuration cannot expose the key through error details`() {
        val malformed = """[{"id":"test-recuperator","name":"Fake","localKey":"0123456789abcdef"""

        val failure = assertFailsWith<IllegalStateException> { tuyaLanDevices(malformed) }

        assertFalse(failure.stackTraceToString().contains(LOCAL_KEY))
    }

    @Test
    fun `a connection record does not expose its key when formatted for diagnostics`() {
        val device = TuyaLanDevice(DEVICE_ID, "Test recuperator", LOCAL_KEY, "192.0.2.10")

        assertFalse(device.toString().contains(LOCAL_KEY))
    }

    @Test
    fun `a complete local reply maps power all toggles and scaled readings`() = runBlocking {
        FakeDevice().use { device ->
            val client = client(device)
            val result = client.read(inventory(client)).getOrThrow()

            assertEquals(true, result.onOff?.isOn)
            assertEquals(Reading.At(instant), result.onOff?.lastUpdated)
            assertEquals(true, result.toggles["speed_one"]?.isOn)
            assertEquals(false, result.toggles["speed_two"]?.isOn)
            assertEquals(false, result.toggles["speed_three"]?.isOn)
            assertEquals(true, result.toggles["in_mode"]?.isOn)
            assertEquals(false, result.toggles["out_mode"]?.isOn)
            assertEquals(false, result.toggles["auto_mode"]?.isOn)
            assertEquals(10, result.toggles.size)
            assertEquals(20.7, result.ranges["huimi"]?.value)
            assertEquals(22.1, result.ranges["temper"]?.value)
            assertEquals(true, result.online)
        }
    }

    @Test
    fun `a device can use its own advancing counter instead of echoing request sequence`() = runBlocking {
        listOf(1000, 1).forEach { initialCounter ->
            FakeDevice().use { device ->
                device.independentCounterStart = initialCounter
                val client = client(device)

                val result = client.read(inventory(client)).getOrThrow()

                assertEquals(true, result.onOff?.isOn)
                assertEquals(true, result.toggles["speed_one"]?.isOn)
                assertEquals(20.7, result.ranges["huimi"]?.value)
            }
        }
    }

    @Test
    fun `an unsolicited sensor report and an unrelated query reply cannot complete a status read`() = runBlocking {
        FakeDevice().use { device ->
            device.beforeReply = { peer, query ->
                peer.send(8, query.sequence + 20, """{"protocol":4,"data":{"dps":{"108":999}}}""")
                peer.send(16, query.sequence + 21, """{"dps":{"1":false}}""")
            }
            val client = client(device)

            val result = client.read(inventory(client)).getOrThrow()

            assertEquals(true, result.onOff?.isOn)
            assertEquals(true, result.toggles["speed_one"]?.isOn)
        }
    }

    @Test
    fun `an unrelated query reply cannot populate power missing from the matched reply`() = runBlocking {
        FakeDevice("""{"108":207,"109":221}""").use { device ->
            device.beforeReply = { peer, query ->
                peer.send(16, query.sequence + 20, """{"dps":{"1":true}}""")
            }
            val client = client(device)

            val result = client.read(inventory(client)).getOrThrow()

            assertNull(result.onOff)
            assertEquals(20.7, result.ranges["huimi"]?.value)
        }
    }

    @Test
    fun `a control acknowledgement cannot populate power missing from the device report`() = runBlocking {
        FakeDevice("""{"108":207,"109":221}""").use { device ->
            device.beforeReply = { peer, query ->
                peer.send(13, query.sequence, """{"dps":{"1":true}}""")
            }
            val client = client(device)

            val result = client.read(inventory(client)).getOrThrow()

            assertNull(result.onOff)
            assertEquals(20.7, result.ranges["huimi"]?.value)
        }
    }

    @Test
    fun `a sensor only query leaves never reported power and speeds unknown`() = runBlocking {
        FakeDevice("""{"108":207,"109":221}""").use { device ->
            val client = client(device)

            val result = client.read(inventory(client)).getOrThrow()

            assertNull(result.onOff)
            assertTrue(result.toggles.values.all { it.isOn == null })
            assertEquals(20.7, result.ranges["huimi"]?.value)
        }
    }

    @Test
    fun `partial sensor updates preserve previously observed booleans and their age`() = runBlocking {
        FakeDevice().use { device ->
            var clock = instant
            val client = client(device, now = { clock })
            val identity = inventory(client)
            client.read(identity).getOrThrow()
            clock = instant.plusSeconds(30)
            device.dps = Json.parseToJsonElement("""{"108":208,"109":222}""").jsonObject

            val result = client.read(identity).getOrThrow()

            assertEquals(true, result.onOff?.isOn)
            assertEquals(Reading.At(instant), result.onOff?.lastUpdated)
            assertEquals(true, result.toggles["speed_one"]?.isOn)
            assertEquals(Reading.At(instant), result.toggles["speed_one"]?.lastUpdated)
            assertEquals(20.8, result.ranges["huimi"]?.value)
            assertEquals(Reading.At(clock), result.ranges["huimi"]?.lastUpdated)
        }
    }

    @Test
    fun `already requested power is not sent again to a device with toggle like firmware`() = runBlocking {
        FakeDevice().use { device ->
            val client = client(device)

            client.setOn(DEVICE_ID, true).getOrThrow()
            client.read(inventory(client)).getOrThrow()

            assertTrue(device.controls.isEmpty())
        }
    }

    @Test
    fun `speed cannot be changed before power has been observed on`() = runBlocking {
        FakeDevice("""{"1":false,"101":false,"102":false,"103":false}""").use { device ->
            val client = client(device)

            val result = client.setSpeed(DEVICE_ID, "speed_two")

            assertTrue(result.isFailure)
            assertTrue(device.controls.isEmpty())
        }
    }

    @Test
    fun `unknown power does not trigger a blind power command`() = runBlocking {
        FakeDevice("""{"108":207,"109":221}""").use { device ->
            val client = client(device)

            val result = client.setOn(DEVICE_ID, true)

            assertTrue(result.isFailure)
            assertTrue(device.controls.isEmpty())
        }
    }

    @Test
    fun `old cached power cannot authorize a command when the current query omits power`() = runBlocking {
        FakeDevice().use { device ->
            val client = client(device)
            client.read(inventory(client)).getOrThrow()
            device.dps = Json.parseToJsonElement("""{"108":207,"109":221}""").jsonObject

            val result = client.setOn(DEVICE_ID, false)

            assertTrue(result.isFailure)
            assertTrue(device.controls.isEmpty())
        }
    }

    @Test
    fun `fresh null or wrongly typed power cannot authorize a command using old cached power`() = runBlocking {
        listOf("null", "0", "\"unknown\"").forEach { reported ->
            FakeDevice().use { device ->
                val client = client(device)
                client.read(inventory(client)).getOrThrow()
                device.dps = Json.parseToJsonElement("""{"1":$reported,"108":207,"109":221}""").jsonObject

                val result = client.setOn(DEVICE_ID, false)

                assertTrue(result.isFailure)
                assertTrue(device.controls.isEmpty())
            }
        }
    }

    @Test
    fun `old cached power cannot authorize speed when the current query omits power`() = runBlocking {
        FakeDevice().use { device ->
            val client = client(device)
            client.read(inventory(client)).getOrThrow()
            device.dps = Json.parseToJsonElement("""{"108":207,"109":221}""").jsonObject

            val result = client.setSpeed(DEVICE_ID, "speed_two")

            assertTrue(result.isFailure)
            assertTrue(device.controls.isEmpty())
        }
    }

    @Test
    fun `cached selected speed is not treated as freshly confirmed when the current query omits speeds`() = runBlocking {
        FakeDevice().use { device ->
            val client = client(device)
            client.read(inventory(client)).getOrThrow()
            device.dps = Json.parseToJsonElement("""{"1":true,"108":207,"109":221}""").jsonObject

            client.setSpeed(DEVICE_ID, "speed_one").getOrThrow()
            val result = client.read(inventory(client)).getOrThrow()

            assertEquals(listOf(mapOf("101" to JsonPrimitive(true))), device.controls.map { it.toMap() })
            assertEquals(true, result.toggles["speed_one"]?.isOn)
        }
    }

    @Test
    fun `medium speed sets only its verified datapoint and is observable on a following read`() = runBlocking {
        FakeDevice().use { device ->
            val client = client(device)

            client.setSpeed(DEVICE_ID, "speed_two").getOrThrow()
            val result = client.read(inventory(client)).getOrThrow()

            assertEquals(listOf(mapOf("102" to JsonPrimitive(true))), device.controls.map { it.toMap() })
            assertEquals(false, result.toggles["speed_one"]?.isOn)
            assertEquals(true, result.toggles["speed_two"]?.isOn)
            assertEquals(false, result.toggles["speed_three"]?.isOn)
        }
    }

    @Test
    fun `a lost acknowledgement never causes a control command to be resent`() = runBlocking {
        FakeDevice("""{"1":false,"101":false,"102":false,"103":false}""").use { device ->
            device.closeAfterControl = true
            val client = client(device, timeout = 500.milliseconds)

            client.setOn(DEVICE_ID, true)
            val result = client.read(inventory(client)).getOrThrow()

            assertEquals(true, result.onOff?.isOn)
            assertEquals(1, device.controls.size)
            client.setOn(DEVICE_ID, true).getOrThrow()
            assertEquals(1, device.controls.size)
        }
    }

    @Test
    fun `a server that cannot prove the local key is rejected before device values are accepted`() = runBlocking {
        FakeDevice().use { device ->
            device.badChallenge = true
            val client = client(device)

            val result = client.read(inventory(client))

            assertTrue(result.isFailure)
            assertTrue(device.controls.isEmpty())
        }
    }

    @Test
    fun `discovery updates a stale configured address for the known device`() = runBlocking {
        FakeDevice().use { device ->
            val seen = CopyOnWriteArrayList<String>()
            val client = client(device, connectedHosts = seen, discovery = { mapOf(DEVICE_ID to "192.0.2.20") })

            client.read(inventory(client)).getOrThrow()

            assertEquals(listOf("192.0.2.20"), seen.toList())
        }
    }

    @Test
    fun `ongoing unsolicited sensor reports do not extend the absolute query deadline`() = runBlocking {
        FakeDevice().use { device ->
            device.omitQueryReply = true
            device.beforeReply = { peer, query ->
                repeat(80) {
                    peer.send(8, query.sequence + 100 + it, """{"dps":{"108":207}}""")
                    Thread.sleep(20)
                }
            }
            val client = client(device, timeout = 200.milliseconds)

            val result = withTimeout(2.seconds) { client.read(inventory(client)) }

            assertTrue(result.isFailure)
        }
    }

    @Test
    fun `cancelling a pending local read completes promptly for an incoming call`() = runBlocking {
        FakeDevice().use { device ->
            val queryStarted = CountDownLatch(1)
            device.omitQueryReply = true
            device.beforeReply = { peer, query ->
                queryStarted.countDown()
                repeat(200) {
                    peer.send(8, query.sequence + 100 + it, """{"dps":{"108":207}}""")
                    Thread.sleep(20)
                }
            }
            val client = client(device, timeout = 10.seconds)
            val identity = inventory(client)
            val read = async { client.read(identity) }
            withTimeout(2.seconds) {
                while (queryStarted.count != 0L) delay(5)
            }

            withTimeout(1.seconds) { read.cancelAndJoin() }

            assertTrue(read.isCancelled)
        }
    }

    private fun client(
        device: FakeDevice,
        now: () -> Instant = { instant },
        timeout: Duration = 1.seconds,
        connectedHosts: MutableList<String> = CopyOnWriteArrayList(),
        discovery: suspend () -> Map<String, String> = { emptyMap() },
    ) = TuyaLanClient(
        configuration = { listOf(TuyaLanDevice(DEVICE_ID, "Test recuperator", LOCAL_KEY, "192.0.2.10", device.port)) },
        connect = { host, _ ->
            connectedHosts.add(host)
            Socket(InetAddress.getLoopbackAddress(), device.port)
        },
        discover = discovery,
        now = now,
        timeout = timeout,
    )

    private suspend fun inventory(client: TuyaLanClient): Device = client.devices().getOrThrow().single()

    private class Peer(val socket: Socket, val protocol: TuyaLanProtocol, val sessionKey: ByteArray) {
        fun send(command: Int, sequence: Int, json: String) {
            socket.getOutputStream().write(protocol.encode(sequence, command, json.toByteArray(), sessionKey, returnCode = 0))
            socket.getOutputStream().flush()
        }
    }

    private class FakeDevice(initial: String = FULL_DPS) : Closeable {
        private val server = ServerSocket(0, 10, InetAddress.getLoopbackAddress())
        private val executor = Executors.newSingleThreadExecutor()
        private val sockets = CopyOnWriteArrayList<Socket>()
        val controls = CopyOnWriteArrayList<JsonObject>()
        val port: Int get() = server.localPort

        @Volatile var dps: JsonObject = Json.parseToJsonElement(initial).jsonObject

        @Volatile var beforeReply: (Peer, TuyaLanFrame) -> Unit = { _, _ -> }

        @Volatile var closeAfterControl = false

        @Volatile var badChallenge = false

        @Volatile var omitQueryReply = false

        @Volatile var independentCounterStart: Int? = null

        init {
            executor.submit {
                while (!server.isClosed) {
                    try {
                        server.accept().use { socket ->
                            sockets.add(socket)
                            try {
                                serve(socket)
                            } finally {
                                sockets.remove(socket)
                            }
                        }
                    } catch (_: IOException) {
                        // The client closes the socket on timeout, authentication failure or completion.
                    }
                }
            }
        }

        private fun serve(socket: Socket) {
            socket.soTimeout = 2_000
            var peerSequence = independentCounterStart
            fun replySequence(requestSequence: Int): Int {
                val current = peerSequence ?: return requestSequence
                peerSequence = current + 1
                return current
            }
            val key = LOCAL_KEY.toByteArray()
            val protocol = TuyaLanProtocol(key)
            val start = protocol.read(socket.getInputStream(), hasReturnCode = false)
            if (start.command != 3) throw IOException("Expected a session start")
            val remoteNonce = "ponmlkjihgfedcba".toByteArray()
            val proof = hmac(key, start.payload).also { if (badChallenge) it[0] = (it[0].toInt() xor 1).toByte() }
            val challenge = remoteNonce + proof
            val challengeSequence = replySequence(start.sequence)
            socket.getOutputStream().write(protocol.encode(challengeSequence, 4, challenge, returnCode = 0))
            socket.getOutputStream().flush()
            if (badChallenge) return
            val finish = protocol.read(socket.getInputStream(), hasReturnCode = false)
            if (finish.command != 5 || !finish.payload.contentEquals(hmac(key, remoteNonce))) throw IOException("Bad client proof")
            val session = protocol.acceptChallenge(start.payload, TuyaLanFrame(challengeSequence, 4, challenge, 0))
            val peer = Peer(socket, protocol, session.key)
            while (!socket.isClosed) {
                val frame = protocol.read(socket.getInputStream(), session.key, hasReturnCode = false)
                when (frame.command) {
                    16 -> {
                        beforeReply(peer, frame)
                        if (!omitQueryReply) peer.send(16, replySequence(frame.sequence), JsonObject(mapOf("dps" to dps)).toString())
                    }
                    13 -> {
                        val body = Json.parseToJsonElement(frame.payload.toString(Charsets.UTF_8)).jsonObject
                        val values = body.getValue("data").jsonObject.getValue("dps").jsonObject
                        controls.add(values)
                        val updated = dps.toMutableMap().apply { putAll(values) }
                        val selectedSpeed = listOf("101", "102", "103").firstOrNull { values[it]?.jsonPrimitive?.booleanOrNull == true }
                        if (selectedSpeed != null) listOf("101", "102", "103").forEach { updated[it] = JsonPrimitive(it == selectedSpeed) }
                        dps = JsonObject(updated)
                        if (closeAfterControl) return
                        peer.send(13, replySequence(frame.sequence), "")
                    }
                    9 -> peer.send(9, replySequence(frame.sequence), "")
                    else -> throw IOException("Unexpected command")
                }
            }
        }

        override fun close() {
            server.close()
            sockets.forEach { it.close() }
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS), "Fake device did not stop")
        }

        private fun hmac(key: ByteArray, payload: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(payload)
        }
    }

    private companion object {
        const val DEVICE_ID = "test-recuperator-01"
        const val LOCAL_KEY = "0123456789abcdef"
        const val FULL_DPS = """{"1":true,"101":true,"102":false,"103":false,"104":false,"105":false,"106":false,"107":false,"108":207,"109":221,"111":true,"112":false,"113":false}"""
    }
}
