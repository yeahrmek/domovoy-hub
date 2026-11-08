package ru.domovoy.integrations.xiaomi

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import ru.domovoy.core.Device
import ru.domovoy.core.DeviceKind
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XiaomiLanClientTest {
    private val token = "00112233445566778899aabbccddeeff"
    private val at = Instant.parse("2026-11-08T12:00:00Z")
    private val device = Device("test-humidifier", "Увлажнитель", DeviceKind.Other, "Спальня", null, vendorType = "devices.types.humidifier")
    private val config = XiaomiLanDevice(device.id, "192.0.2.1", token)

    @Test
    fun `local readings preserve card identity and expose native humidity and auto mode`() = runTest {
        val fake = Appliance(token)
        val client = XiaomiLanClient({ listOf(config) }, { fake }, { at })
        val read = client.read(device).getOrThrow()
        assertEquals(device.id, read.id)
        assertEquals(device.name, read.name)
        assertEquals(device.room, read.room)
        assertEquals(55.0, read.ranges.getValue("humidity").value)
        assertEquals(40.0, read.ranges.getValue("humidity").bounds?.min)
        assertEquals(80.0, read.ranges.getValue("humidity").bounds?.max)
        assertEquals("auto", read.modes.getValue("fan_speed").current)
        assertEquals(listOf("low", "medium", "high", "auto"), read.modes.getValue("fan_speed").available)
        assertEquals(42.0, read.properties.getValue("humidity").value)
        assertEquals(23.5, read.properties.getValue("temperature").value)
        assertTrue(read.onOff!!.isOn)
        assertTrue(fake.closed)
    }

    @Test
    fun `native target and auto mode writes use verified property identifiers`() = runTest {
        val requests = mutableListOf<JsonObject>()
        val client = XiaomiLanClient({ listOf(config.copy(deviceId = "test-device-id")) }, { Appliance(token, requests = requests) })
        client.setHumidity(device.id, 57.0).getOrThrow()
        client.setMode(device.id, "auto").getOrThrow()
        client.setOn(device.id, false).getOrThrow()
        assertTrue(requests.all { ((it["params"] as JsonArray).single() as JsonObject)["did"]!!.jsonPrimitive.content == "test-device-id" })
        assertEquals(listOf(6, 5, 1), requests.map { ((it["params"] as JsonArray).single() as JsonObject)["piid"]!!.jsonPrimitive.content.toInt() })
        assertEquals(listOf("57", "4", "false"), requests.map { ((it["params"] as JsonArray).single() as JsonObject)["value"]!!.jsonPrimitive.content })
    }

    @Test
    fun `invalid humidity or modes never open a socket`() = runTest {
        val client = XiaomiLanClient({ listOf(config) }, { error("Unexpected network access") })
        listOf(Double.NaN, Double.POSITIVE_INFINITY, 39.0, 81.0, 50.5).forEach {
            assertTrue(client.setHumidity(device.id, it).isFailure)
        }
        assertTrue(client.setMode(device.id, "turbo").isFailure)
        assertFalse(client.configured("unknown"))
    }

    @Test
    fun `device rejection and malformed state are failures rather than confirmed readings`() = runTest {
        val client = XiaomiLanClient({ listOf(config) }, { Appliance(token, failedProperty = true) })
        assertTrue(client.setHumidity(device.id, 55.0).isFailure)
        assertTrue(client.read(device).isFailure)
    }

    @Test
    fun `hardware fault and mismatched returned properties cannot become healthy readings`() = runTest {
        val fault = XiaomiLanClient({ listOf(config) }, { Appliance(token, fault = 1) })
        assertTrue(fault.read(device).isFailure)
        val wrong = XiaomiLanClient({ listOf(config) }, { Appliance(token, wrongProperty = true) })
        assertTrue(wrong.read(device).isFailure)
    }

    @Test
    fun `a lost write acknowledgement is not retried`() = runTest {
        val fake = Appliance(token, lostReply = true)
        val client = XiaomiLanClient({ listOf(config) }, { fake })
        assertTrue(client.setHumidity(device.id, 55.0).isFailure)
        assertEquals(1, fake.requestCount)
        assertTrue(fake.closed)
    }

    @Test
    fun `inventory remains available before a device read`() {
        val client = XiaomiLanClient({ listOf(config.copy(name = "Спальня", room = "Спальня")) })
        val tile = client.inventory().single()
        assertEquals(device.id, tile.id)
        assertEquals("Спальня", tile.name)
        assertEquals("Спальня", tile.room)
        assertEquals(null, tile.onOff)
        assertTrue(tile.ranges.isEmpty())
    }

    @Test
    fun `configuration rejects malformed entries and never prints tokens`() {
        val parsed = xiaomiLanDevices("""[{"id":"unit","host":"192.0.2.1","token":"$token"}]""")
        assertEquals(listOf(XiaomiLanDevice("unit", "192.0.2.1", token)), parsed)
        assertFalse(parsed.single().toString().contains(token))
        assertTrue(xiaomiLanDevices("not json").isEmpty())
        assertTrue(xiaomiLanDevices("""[{"id":"unit","host":"192.0.2.1","token":"bad"}]""").isEmpty())
    }

    private class Appliance(
        token: String,
        private val requests: MutableList<JsonObject> = mutableListOf(),
        private val failedProperty: Boolean = false,
        private val lostReply: Boolean = false,
        private val fault: Int = 0,
        private val wrongProperty: Boolean = false,
    ) : DatagramSocket(null as SocketAddress?) {
        private val protocol = XiaomiMiioProtocol(token)
        private var response = byteArrayOf()
        var closed = false
        var requestCount = 0

        override fun connect(address: SocketAddress) = Unit
        override fun send(packet: DatagramPacket) {
            val data = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
            if (data.size == 32) {
                response = protocol.encodePayload(7, 100, JsonObject(emptyMap())).copyOfRange(0, 32).also {
                    it[2] = 0
                    it[3] = 32
                }
            } else {
                val body = protocol.decode(data, 7)
                requests += body
                requestCount++
                val id = body.getValue("id")
                val result = if (body.getValue("method").jsonPrimitive.content == "get_properties") {
                    val values = listOf(JsonPrimitive(true), JsonPrimitive(fault), JsonPrimitive(4), JsonPrimitive(55), JsonPrimitive(42), JsonPrimitive(23.5))
                    val properties = values.mapIndexed { index, value ->
                        val (service, property) = listOf(2 to 1, 2 to 2, 2 to 5, 2 to 6, 3 to 1, 3 to 7)[index]
                        JsonObject(mapOf("did" to JsonPrimitive(index.toString()), "siid" to JsonPrimitive(service), "piid" to JsonPrimitive(if (wrongProperty) 99 else property), "code" to JsonPrimitive(if (failedProperty) -1 else 0), "value" to value))
                    }
                    JsonArray(properties)
                } else {
                    val property = (body["params"] as JsonArray).single().jsonObject["piid"]!!
                    JsonArray(listOf(JsonObject(mapOf("siid" to JsonPrimitive(2), "piid" to property, "code" to JsonPrimitive(if (failedProperty) -1 else 0)))))
                }
                response = protocol.encodePayload(7, 100, JsonObject(mapOf("id" to id, "result" to result)))
            }
        }

        override fun receive(packet: DatagramPacket) {
            if (lostReply && requestCount > 0) throw SocketTimeoutException()
            response.copyInto(packet.data, packet.offset)
            packet.length = response.size
        }

        override fun close() {
            closed = true
            super.close()
        }
    }
}
