package ru.domovoy.panel

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test
import ru.domovoy.core.Device
import ru.domovoy.core.DeviceKind
import ru.domovoy.core.Mode
import ru.domovoy.core.Reading
import ru.domovoy.integrations.xiaomi.XiaomiLanClient
import ru.domovoy.integrations.xiaomi.XiaomiLanDevice
import ru.domovoy.integrations.xiaomi.XiaomiMiioProtocol
import ru.domovoy.integrations.yandex.YandexClient
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LocalHumidifierTest {
    private val at = Instant.parse("2026-11-08T12:00:00Z")
    private val token = "00112233445566778899aabbccddeeff"
    private val first = XiaomiLanDevice("humidifier-one", "192.0.2.1", token, "Увлажнитель", "Спальня")
    private val second = XiaomiLanDevice("humidifier-two", "192.0.2.2", token, "Увлажнитель", "Зал")

    @Test
    fun `local inventory and native settings work before any successful Yandex poll`() = runTest {
        val hardware = Hardware()
        val devices = panel(mapOf(first to hardware))
        devices.showFailure("Yandex unavailable")

        assertTrue(devices.refreshLocal())

        val state = devices.state.value
        val tile = state.tiles.single()
        assertEquals(first.id, tile.id)
        assertEquals(first.room, tile.room)
        assertEquals(55.0, tile.ranges.getValue("humidity").value)
        assertEquals("auto", tile.modes.getValue("fan_speed").current)
        assertEquals(setOf(first.id), state.localIds)
        assertEquals(at, state.localPolledAt[first.id])
        assertEquals("Yandex unavailable", state.error)
        assertTrue(state.readErrors.isEmpty())
    }

    @Test
    fun `a later Yandex inventory cannot replace current local target or auto mode`() = runTest {
        val devices = panel(mapOf(first to Hardware()))
        devices.refreshLocal()
        val local = devices.state.value.tiles.single()
        val stale = local.copy(
            name = "Новое название",
            ranges = emptyMap(),
            modes = mapOf("fan_speed" to Mode("turbo", listOf("low", "medium", "high", "turbo"), Reading.Never)),
        )

        devices.show(listOf(stale), at.plusSeconds(60))

        val tile = devices.state.value.tiles.single()
        assertEquals("Новое название", tile.name)
        assertEquals(55.0, tile.ranges.getValue("humidity").value)
        assertEquals("auto", tile.modes.getValue("fan_speed").current)
        assertEquals(at, devices.state.value.localPolledAt[first.id])
    }

    @Test
    fun `one unreachable humidifier retains its last reading while its peer updates`() = runTest {
        var clock = at
        val failed = Hardware()
        val healthy = Hardware(target = 60)
        val devices = panel(mapOf(first to failed, second to healthy)) { clock }
        assertTrue(devices.refreshLocal())
        failed.failReads = true
        healthy.target = 61
        clock = at.plusSeconds(15)

        assertFalse(devices.refreshLocal())

        val state = devices.state.value
        assertEquals(55.0, state.tiles.single { it.id == first.id }.ranges.getValue("humidity").value)
        assertEquals(61.0, state.tiles.single { it.id == second.id }.ranges.getValue("humidity").value)
        assertEquals(at, state.localPolledAt[first.id])
        assertEquals(clock, state.localPolledAt[second.id])
        assertEquals(setOf(first.id), state.readErrors.keys)
    }

    @Test
    fun `choosing a target snaps it enables native auto and confirms without changing power`() = runTest {
        val hardware = Hardware(mode = 2, power = false)
        val devices = panel(mapOf(first to hardware))
        devices.refreshLocal()

        devices.setHumidity(first.id, 57.3)

        val state = devices.state.value
        val tile = state.tiles.single()
        assertEquals(57.0, tile.ranges.getValue("humidity").value)
        assertEquals("auto", tile.modes.getValue("fan_speed").current)
        assertEquals(false, tile.onOff?.isOn)
        assertTrue(state.commandErrors.isEmpty())
        assertTrue(state.pending.isEmpty())
    }

    @Test
    fun `rejected target and failed confirmation retain reported value and release pending`() = runTest {
        val hardware = Hardware()
        val devices = panel(mapOf(first to hardware))
        devices.refreshLocal()
        hardware.rejectWrites = true

        devices.setHumidity(first.id, 57.0)

        assertEquals(55.0, devices.state.value.tiles.single().ranges.getValue("humidity").value)
        assertNotNull(devices.state.value.commandErrors[first.id])
        assertTrue(devices.state.value.pending.isEmpty())
        hardware.rejectWrites = false
        hardware.failReads = true

        devices.setHumidity(first.id, 58.0)

        assertEquals(55.0, devices.state.value.tiles.single().ranges.getValue("humidity").value)
        assertNotNull(devices.state.value.commandErrors[first.id])
        assertNotNull(devices.state.value.readErrors[first.id])
        assertTrue(devices.state.value.pending.isEmpty())
        hardware.failReads = false

        devices.setHumidity(first.id, 59.0)

        assertEquals(59.0, devices.state.value.tiles.single().ranges.getValue("humidity").value)
        assertTrue(devices.state.value.commandErrors.isEmpty())
        assertTrue(devices.state.value.readErrors.isEmpty())
    }

    @Test
    fun `an invalid humidity does not modify the native setting`() = runTest {
        val hardware = Hardware()
        val devices = panel(mapOf(first to hardware))
        devices.refreshLocal()

        devices.setHumidity(first.id, Double.NaN)

        assertEquals(55.0, devices.state.value.tiles.single().ranges.getValue("humidity").value)
        assertEquals(55, hardware.target)
        assertNotNull(devices.state.value.commandErrors[first.id])
        assertTrue(devices.state.value.pending.isEmpty())
    }

    @Test
    fun `unavailable local configuration cannot break independent Yandex readings`() = runTest {
        val devices = panel(emptyMap(), configuration = { error("secure storage unavailable") })
        val sensor = Device("sensor", "Датчик", DeviceKind.Other, "Зал", null, vendorType = "devices.types.sensor.climate")

        devices.show(listOf(sensor), at)
        assertFalse(devices.refreshLocal())
        devices.show(listOf(sensor.copy(name = "Новое название")), at.plusSeconds(15))

        assertEquals("Новое название", devices.state.value.tiles.single().name)
        assertEquals(at.plusSeconds(15), devices.state.value.lastPolledAt)
    }

    @Test
    fun `lost local configuration preserves readings exposes failures and recovers`() = runTest {
        var unavailable = false
        val devices = panel(mapOf(first to Hardware()), configuration = {
            if (unavailable) error("secure storage unavailable")
            listOf(first)
        })
        assertTrue(devices.refreshLocal())
        val original = devices.state.value.tiles.single()
        unavailable = true

        assertFalse(devices.refreshLocal())
        devices.show(listOf(original.copy(name = "Новое название")), at.plusSeconds(15))

        val failed = devices.state.value
        assertEquals(setOf(first.id), failed.localIds)
        assertEquals(at, failed.localPolledAt[first.id])
        assertEquals(original.ranges, failed.tiles.single().ranges)
        assertNotNull(failed.readErrors[first.id])
        assertEquals("Новое название", failed.tiles.single().name)

        devices.setHumidity(first.id, 60.0)
        assertNotNull(devices.state.value.commandErrors[first.id])
        assertTrue(devices.state.value.pending.isEmpty())

        unavailable = false
        assertTrue(devices.refreshLocal())
        assertTrue(devices.state.value.readErrors.isEmpty())
        assertEquals(55.0, devices.state.value.tiles.single().ranges.getValue("humidity").value)
    }

    private fun panel(
        hardware: Map<XiaomiLanDevice, Hardware>,
        configuration: () -> List<XiaomiLanDevice> = { hardware.keys.toList() },
        now: () -> Instant = { at },
    ): DiscoveredDevices {
        val byHost = hardware.mapKeys { it.key.host }
        val local = XiaomiLanClient(
            configuration = configuration,
            socket = { Appliance(token, byHost) },
            now = now,
        )
        val yandex = YandexClient(OkHttpClient(), { error("Local controls must not request a Yandex token") }, "test-household")
        return DiscoveredDevices(yandex, local, now) { error("Local controls must not refresh Yandex") }
    }

    private class Hardware(
        var target: Int = 55,
        var mode: Int = 4,
        var power: Boolean = true,
        var failReads: Boolean = false,
        var rejectWrites: Boolean = false,
    )

    /** An in-memory appliance; no bind, connect, send or receive reaches the network. */
    private class Appliance(token: String, private val byHost: Map<String, Hardware>) : DatagramSocket(null as SocketAddress?) {
        private val protocol = XiaomiMiioProtocol(token)
        private lateinit var hardware: Hardware
        private var response = byteArrayOf()
        private var failResponse = false

        override fun connect(address: SocketAddress) {
            hardware = byHost.getValue((address as InetSocketAddress).hostString)
        }

        override fun send(packet: DatagramPacket) {
            val data = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
            if (data.size == 32) {
                response = protocol.encodePayload(7, 100, JsonObject(emptyMap())).copyOfRange(0, 32).also {
                    it[2] = 0
                    it[3] = 32
                }
                return
            }
            val request = protocol.decode(data, 7)
            val read = request.getValue("method").jsonPrimitive.content == "get_properties"
            failResponse = read && hardware.failReads
            val parameters = (request.getValue("params") as JsonArray).map { it.jsonObject }
            val result = parameters.map { property ->
                val service = property.getValue("siid").jsonPrimitive.intOrNull!!
                val id = property.getValue("piid").jsonPrimitive.intOrNull!!
                if (!read && !hardware.rejectWrites) {
                    val value = property.getValue("value").jsonPrimitive
                    when (id) {
                        1 -> hardware.power = value.booleanOrNull!!
                        5 -> hardware.mode = value.intOrNull!!
                        6 -> hardware.target = value.intOrNull!!
                    }
                }
                val fields = property.toMutableMap().apply {
                    put("code", JsonPrimitive(if (!read && hardware.rejectWrites) -4004 else 0))
                    if (read) {
                        put(
                            "value",
                            when (service to id) {
                                2 to 1 -> JsonPrimitive(hardware.power)
                                2 to 2 -> JsonPrimitive(0)
                                2 to 5 -> JsonPrimitive(hardware.mode)
                                2 to 6 -> JsonPrimitive(hardware.target)
                                3 to 1 -> JsonPrimitive(42)
                                else -> JsonPrimitive(23.5)
                            },
                        )
                    }
                }
                JsonObject(fields)
            }
            response = protocol.encodePayload(7, 100, JsonObject(mapOf("id" to request.getValue("id"), "result" to JsonArray(result))))
        }

        override fun receive(packet: DatagramPacket) {
            if (failResponse) throw SocketTimeoutException()
            response.copyInto(packet.data, packet.offset)
            packet.length = response.size
        }
    }
}
