package ru.domovoy.integrations.xiaomi

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.domovoy.core.Bounds
import ru.domovoy.core.Device
import ru.domovoy.core.DeviceKind
import ru.domovoy.core.FloatProperty
import ru.domovoy.core.Mode
import ru.domovoy.core.OnOff
import ru.domovoy.core.Range
import ru.domovoy.core.Reading
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Existing Yandex identity joins the local transport to the same card. */
data class XiaomiLanDevice(val id: String, val host: String, val token: String, val name: String = "Увлажнитель", val room: String? = null, val deviceId: String = "") {
    override fun toString(): String = "XiaomiLanDevice(credentials=hidden)"
}

/** Invalid configurations cannot break other integrations or expose partially parsed secrets. */
fun xiaomiLanDevices(json: String): List<XiaomiLanDevice> = runCatching {
    (Json.parseToJsonElement(json) as JsonArray).mapNotNull { entry ->
        val value = entry.jsonObject
        val id = value["id"]?.jsonPrimitive?.content.orEmpty().trim()
        val host = value["host"]?.jsonPrimitive?.content.orEmpty().trim()
        val token = value["token"]?.jsonPrimitive?.content.orEmpty().trim()
        val name = value["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: "Увлажнитель"
        val room = value["room"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() && it != "null" }
        val deviceId = value["deviceId"]?.jsonPrimitive?.content.orEmpty().trim()
        if (id.isBlank() || host.isBlank() || !token.matches(Regex("[0-9a-fA-F]{32}"))) null else XiaomiLanDevice(id, host, token, name, room, deviceId)
    }.groupBy { it.id }.values.mapNotNull { it.singleOrNull() }
}.getOrDefault(emptyList())

/** Bounded local miIO requests for the verified deerma.humidifier.jsq5 property map. */
class XiaomiLanClient(
    private val configuration: () -> List<XiaomiLanDevice>,
    private val socket: () -> DatagramSocket = { DatagramSocket() },
    private val now: () -> Instant = Instant::now,
    private val timeoutMillis: Int = 3_000,
) {
    init {
        require(timeoutMillis > 0) { "Некорректный тайм-аут Xiaomi" }
    }

    private val locks = ConcurrentHashMap<String, Mutex>()
    private val sequence = AtomicInteger(1)

    fun configured(id: String): Boolean = configuration().any { it.id == id }

    fun inventory(): List<Device> = configuration().map { Device(it.id, it.name, DeviceKind.Other, it.room, null, vendorType = "devices.types.humidifier") }

    suspend fun read(base: Device): Result<Device> = result {
        val properties = PROPERTIES.mapIndexed { index, (service, property) ->
            JsonObject(mapOf("did" to JsonPrimitive(index.toString()), "siid" to JsonPrimitive(service), "piid" to JsonPrimitive(property)))
        }
        val reply = request(base.id, "get_properties", JsonArray(properties))
        val values = (reply["result"] as? JsonArray)?.map { it.jsonObject }?.associateBy { it["did"]?.jsonPrimitive?.content }
            ?: throw IOException("Нет состояния увлажнителя")
        fun value(index: Int): JsonPrimitive? = values[index.toString()]?.takeIf {
            val (service, property) = PROPERTIES[index]
            it["code"]?.jsonPrimitive?.intOrNull == 0 && it["siid"]?.jsonPrimitive?.intOrNull == service && it["piid"]?.jsonPrimitive?.intOrNull == property
        }?.get("value") as? JsonPrimitive
        val fault = value(1)?.intOrNull ?: throw IOException("Нет состояния исправности увлажнителя")
        if (fault != 0) {
            val reason = when (fault) {
                1 -> "В увлажнителе закончилась вода"
                2 -> "Бак увлажнителя снят"
                else -> "Увлажнитель сообщил об ошибке"
            }
            throw IOException(reason)
        }
        val power = value(0)?.booleanOrNull ?: throw IOException("Нет состояния питания увлажнителя")
        val mode = value(2)?.intOrNull?.let { MODES.entries.firstOrNull { mode -> mode.value == it }?.key }
            ?: throw IOException("Нет режима увлажнителя")
        val target = value(3)?.doubleOrNull?.takeIf { it.isFinite() && it in 40.0..80.0 && it % 1.0 == 0.0 }
            ?: throw IOException("Нет заданной влажности увлажнителя")
        val at = Reading.At(now())
        base.copy(
            onOff = OnOff(power, at),
            ranges = base.ranges + ("humidity" to Range(target, Bounds(40.0, 80.0, 1.0), "unit.percent", at)),
            modes = base.modes + ("fan_speed" to Mode(mode, MODES.keys.toList(), at)),
            properties = base.properties + mapOf(
                "humidity" to FloatProperty(value(4)?.doubleOrNull?.takeIf { it.isFinite() && it in 0.0..100.0 }, at, "unit.percent"),
                "temperature" to FloatProperty(value(5)?.doubleOrNull?.takeIf { it.isFinite() }, at, "unit.temperature.celsius"),
            ),
            online = true,
        )
    }

    suspend fun setHumidity(id: String, percent: Double): Result<Unit> = result {
        require(percent.isFinite() && percent in 40.0..80.0 && percent % 1.0 == 0.0) { "Влажность должна быть целым числом от 40 до 80%" }
        write(id, 6, JsonPrimitive(percent.toInt()))
    }

    suspend fun setMode(id: String, value: String): Result<Unit> = result {
        val mode = MODES[value] ?: throw IllegalArgumentException("Режим недоступен")
        write(id, 5, JsonPrimitive(mode))
    }

    suspend fun setOn(id: String, on: Boolean): Result<Unit> = result { write(id, 1, JsonPrimitive(on)) }

    private suspend fun write(id: String, property: Int, value: JsonElement) {
        val device = configuration().singleOrNull { it.id == id } ?: throw IOException("Прямое подключение увлажнителя не настроено")
        val params = mapOf(
            "did" to JsonPrimitive(device.deviceId.ifBlank { "control" }),
            "siid" to JsonPrimitive(2),
            "piid" to JsonPrimitive(property),
            "value" to value,
        )
        val response = request(id, "set_properties", JsonArray(listOf(JsonObject(params))))
        val result = response["result"] as? JsonArray
        val accepted = result?.singleOrNull()?.jsonObject
        if (accepted == null || accepted["code"]?.jsonPrimitive?.intOrNull != 0 || accepted["siid"]?.jsonPrimitive?.intOrNull != 2 || accepted["piid"]?.jsonPrimitive?.intOrNull != property) {
            throw IOException("Увлажнитель отклонил команду")
        }
    }

    private suspend fun request(id: String, method: String, params: JsonArray): JsonObject = locks.getOrPut(id) { Mutex() }.withLock {
        val device = configuration().singleOrNull { it.id == id } ?: throw IOException("Прямое подключение увлажнителя не настроено")
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                val connection = socket()
                continuation.invokeOnCancellation { connection.close() }
                try {
                    val answer = connection.use {
                        val protocol = XiaomiMiioProtocol(device.token)
                        val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
                        fun receive(): ByteArray {
                            val remaining = (deadline - System.nanoTime()) / 1_000_000L
                            if (remaining <= 0) throw SocketTimeoutException()
                            connection.soTimeout = remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1)
                            val packet = DatagramPacket(ByteArray(65_535), 65_535)
                            connection.receive(packet)
                            return packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                        }
                        fun send(bytes: ByteArray) = connection.send(DatagramPacket(bytes, bytes.size))
                        val octets = device.host.split('.').map { it.toIntOrNull() }
                        if (octets.size != 4 || octets.any { it == null || it !in 0..255 }) {
                            throw IOException("Некорректный локальный адрес увлажнителя")
                        }
                        // Inventory contains numeric IPv4 addresses; do not let DNS outlive the deadline.
                        val address = InetAddress.getByAddress(octets.map { it!!.toByte() }.toByteArray())
                        connection.connect(InetSocketAddress(address, 54321))
                        send(XiaomiMiioProtocol.hello)
                        val (deviceId, timestamp) = XiaomiMiioProtocol.handshake(receive())
                        val requestId = sequence.getAndUpdate { if (it == Int.MAX_VALUE) 1 else it + 1 }
                        send(protocol.encodePayload(deviceId, timestamp, JsonObject(mapOf("id" to JsonPrimitive(requestId), "method" to JsonPrimitive(method), "params" to params))))
                        var reply: JsonObject
                        do {
                            reply = protocol.decode(receive(), deviceId)
                        } while (reply["id"]?.jsonPrimitive?.intOrNull != requestId)
                        if (reply["error"] != null) throw IOException("Увлажнитель отклонил запрос")
                        reply
                    }
                    continuation.resume(answer)
                } catch (failure: Exception) {
                    val safe = when (failure) {
                        is SocketTimeoutException -> IOException("Увлажнитель не ответил вовремя")
                        is IOException -> IOException("Не удалось связаться с увлажнителем")
                        else -> IOException("Не удалось прочитать ответ увлажнителя")
                    }
                    continuation.resumeWithException(safe)
                }
            }
        }
    }

    private suspend fun <T> result(block: suspend () -> T): Result<T> = runCatching { block() }
        .onFailure { if (it is CancellationException) throw it }
}

private val MODES = linkedMapOf("low" to 1, "medium" to 2, "high" to 3, "auto" to 4)
private val PROPERTIES = listOf(2 to 1, 2 to 2, 2 to 5, 2 to 6, 3 to 1, 3 to 7)
