package ru.domovoy.integrations.tuya

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import ru.domovoy.core.Device
import ru.domovoy.core.DeviceKind
import ru.domovoy.core.OnOff
import ru.domovoy.core.Range
import ru.domovoy.core.Reading
import ru.domovoy.core.Toggle
import java.io.FilterInputStream
import java.io.IOException
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The cloud and LAN transports expose the same operations to the existing panel. */
interface TuyaDevices {
    suspend fun devices(): Result<List<Device>>
    suspend fun read(device: Device): Result<Device>
    suspend fun setOn(deviceId: String, on: Boolean): Result<Unit>
    suspend fun setSpeed(deviceId: String, code: String): Result<Unit>
}

/** Apartment-specific connection details, stored only in encrypted preferences. */
@Serializable
data class TuyaLanDevice(
    val id: String,
    val name: String,
    val localKey: String,
    val host: String? = null,
    val port: Int = 6668,
) {
    override fun toString(): String = "TuyaLanDevice(configured=${id.isNotBlank()})"
}

fun tuyaLanDevices(spec: String): List<TuyaLanDevice> = try {
    Json.decodeFromString<List<TuyaLanDevice>>(spec).also { devices ->
        require(devices.isNotEmpty() && devices.map { it.id }.distinct().size == devices.size)
        require(devices.all { it.id.isNotBlank() && it.name.isNotBlank() && it.localKey.toByteArray(Charsets.UTF_8).size == 16 && it.port in 1..65535 })
    }
} catch (_: Exception) {
    error("Не настроены локальные ключи бризеров")
}

/** Native protocol 3.4. One bounded authenticated connection per read or command. */
class TuyaLanClient(
    private val configuration: () -> List<TuyaLanDevice>,
    private val connect: (String, Int) -> Socket,
    private val discover: suspend () -> Map<String, String> = { emptyMap() },
    private val now: () -> Instant = Instant::now,
    private val timeout: Duration = 6.seconds,
) : TuyaDevices {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val hosts = ConcurrentHashMap<String, String>()
    private val observations = ConcurrentHashMap<String, Device>()
    private var lastDiscovery: Instant? = null

    @Volatile
    private var discoveryNeeded = true

    override suspend fun devices(): Result<List<Device>> = result {
        val configured = configuration()
        require(configured.isNotEmpty()) { "Не настроены локальные ключи бризеров" }
        configured.forEach { it.host?.takeIf(String::isNotBlank)?.let { host -> hosts.putIfAbsent(it.id, host) } }
        if (discoveryNeeded || lastDiscovery?.plusSeconds(5.minutes.inWholeSeconds)?.isAfter(now()) != true) {
            try {
                val found = discover()
                configured.forEach { device -> found[device.id]?.let { hosts[device.id] = it } }
                lastDiscovery = now()
                discoveryNeeded = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Verified address hints still work when broadcast discovery is unavailable.
            }
        }
        configured.map { it.inventory() }
    }

    override suspend fun read(device: Device): Result<Device> = result {
        transaction(device.id) { query(device) }
    }

    override suspend fun setOn(deviceId: String, on: Boolean): Result<Unit> = result {
        transaction(deviceId) {
            val state = query(spec.inventory())
            check("1" in currentQueryDps) { "Бризер ещё не сообщил питание" }
            val current = state.onOff?.isOn ?: error("Бризер ещё не сообщил питание")
            if (current != on) control("1", on)
        }
    }

    override suspend fun setSpeed(deviceId: String, code: String): Result<Unit> = result {
        val dp = SPEEDS[code] ?: error("Неизвестная скорость бризера")
        transaction(deviceId) {
            val state = query(spec.inventory())
            check("1" in currentQueryDps) { "Бризер ещё не сообщил питание" }
            check(state.onOff?.isOn == true) { "Сначала включите бризер" }
            if (!currentQueryDps.containsAll(SPEEDS.values) || state.toggles[code]?.isOn != true || SPEEDS.keys.any { it != code && state.toggles[it]?.isOn != false }) {
                control(dp, true)
            }
        }
    }

    private suspend fun <T> transaction(id: String, action: Session.() -> T): T = locks.getOrPut(id) { Mutex() }.withLock {
        val spec = configuration().firstOrNull { it.id == id } ?: error("Бризер не настроен")
        val host = hosts[id] ?: spec.host ?: error("Бризер не найден в локальной сети")
        val completed = withTimeoutOrNull(timeout.inWholeMilliseconds) {
            withContext(Dispatchers.IO) {
                suspendCancellableCoroutine<T> { continuation ->
                    val socket = AtomicReference<Socket?>()
                    continuation.invokeOnCancellation { runCatching { socket.get()?.close() } }
                    try {
                        val connected = connect(host, spec.port)
                        socket.set(connected)
                        connected.use {
                            if (!continuation.isActive) return@use
                            val session = Session(spec, connected)
                            session.authenticate()
                            val value = session.action()
                            continuation.resume(value)
                        }
                    } catch (failure: Exception) {
                        discoveryNeeded = true
                        if (continuation.isActive) continuation.resumeWithException(failure)
                    }
                }
            }.let { Result.success(it) }
        }
        if (completed == null) throw SocketTimeoutException("Бризер не ответил вовремя")
        completed.getOrThrow()
    }

    private inner class Session(val spec: TuyaLanDevice, private val socket: Socket) {
        val currentQueryDps = mutableSetOf<String>()
        private val protocol = TuyaLanProtocol(spec.localKey.toByteArray(Charsets.UTF_8))
        private var key = spec.localKey.toByteArray(Charsets.UTF_8)
        private var sequence = 1
        private var peerSequence = 0
        private var independentPeerCounter = false
        private var sequentialPeerCounter = true
        private val deadline = System.nanoTime() + timeout.inWholeNanoseconds
        private val input = object : FilterInputStream(socket.getInputStream()) {
            override fun read(): Int {
                setDeadline()
                return super.read()
            }
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                setDeadline()
                return `in`.read(bytes, offset, length)
            }
        }

        private fun setDeadline() {
            val remaining = (deadline - System.nanoTime()) / 1_000_000
            if (remaining <= 0) throw SocketTimeoutException("Бризер не ответил вовремя")
            socket.soTimeout = remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1)
        }

        fun authenticate() {
            val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val sent = send(3, nonce)
            val challenge = protocol.read(input, key)
            check(challenge.returnCode == null || challenge.returnCode == 0) { "Ошибка авторизации бризера" }
            val session = protocol.acceptChallenge(nonce, challenge)
            peerSequence = challenge.sequence
            independentPeerCounter = challenge.sequence != sent
            send(5, session.finishPayload)
            key = session.key
        }

        fun query(inventory: Device): Device {
            currentQueryDps.clear()
            val wanted = send(16, "{}".toByteArray())
            while (true) {
                val frame = protocol.read(input, key)
                check(frame.returnCode == null || frame.returnCode == 0) { "Бризер отклонил запрос" }
                val nextPeerSequence = frame.sequence == peerSequence + 1
                val advance = (frame.sequence.toLong() - peerSequence.toLong()) and 0xffffffffL
                val advancesPeer = advance in 1..0x7fffffffL
                // KCvents uses its own reply counter even on 3.4; other peers echo request IDs.
                val peerReply = (independentPeerCounter && advancesPeer) || (sequentialPeerCounter && nextPeerSequence)
                val matched = frame.command == 16 && ((!independentPeerCounter && frame.sequence == wanted) || peerReply)
                if (!nextPeerSequence) sequentialPeerCounter = false
                if (advancesPeer) peerSequence = frame.sequence
                val report = frame.command == 8 && (!independentPeerCounter || advancesPeer)
                val dps = if (report || matched) dataPoints(frame.payload) else null
                if (dps != null) {
                    currentQueryDps.addAll(dps.filterValues { it.reportedBoolean() != null }.keys)
                    observations[spec.id] = applyDataPoints(observations[spec.id] ?: inventory, dps, now())
                }
                if (matched && dps != null) {
                    return (observations[spec.id] ?: inventory).copy(name = inventory.name, room = inventory.room, online = true)
                }
            }
        }

        fun control(dp: String, value: Boolean) {
            val body = JsonObject(mapOf("protocol" to JsonPrimitive(5), "t" to JsonPrimitive(now().epochSecond), "data" to JsonObject(mapOf("dps" to JsonObject(mapOf(dp to JsonPrimitive(value)))))))
            // Never retry a write after a transport error: the device might have accepted it.
            try {
                send(13, body.toString().toByteArray())
            } catch (_: IOException) {
                throw IOException("Результат команды неизвестен. Проверьте состояние бризера")
            }
        }

        private fun send(command: Int, payload: ByteArray): Int {
            setDeadline()
            val sent = sequence++
            socket.getOutputStream().write(protocol.encode(sent, command, payload, key))
            socket.getOutputStream().flush()
            return sent
        }
    }

    private suspend fun <T> result(block: suspend () -> T): Result<T> = runCatching { block() }
        .onFailure { if (it is CancellationException) throw it }
}

private val SPEEDS = mapOf("speed_one" to "101", "speed_two" to "102", "speed_three" to "103")
private val TOGGLES = mapOf("101" to "speed_one", "102" to "speed_two", "103" to "speed_three", "104" to "sleep_mode", "105" to "huimidity_one", "106" to "huimidity_two", "107" to "huimidity_three", "111" to "in_mode", "112" to "out_mode", "113" to "auto_mode")

private fun TuyaLanDevice.inventory() = Device(id = id, name = name, kind = DeviceKind.Recuperator, room = null, onOff = null)

private fun dataPoints(payload: ByteArray): JsonObject? {
    if (payload.isEmpty()) return null
    val value = runCatching { Json.parseToJsonElement(payload.toString(Charsets.UTF_8)).jsonObject }.getOrNull() ?: return null
    return (value["dps"] as? JsonObject) ?: ((value["data"] as? JsonObject)?.get("dps") as? JsonObject)
}

internal fun applyDataPoints(device: Device, values: JsonObject, at: Instant): Device {
    val read = Reading.At(at)
    val power = values["1"].reportedBoolean()
    val toggles = device.toggles.toMutableMap()
    TOGGLES.forEach { (dp, code) ->
        values[dp].reportedBoolean()?.let { toggles[code] = Toggle(it, read) }
    }
    val ranges = device.ranges.toMutableMap()
    mapOf("108" to "huimi", "109" to "temper").forEach { (dp, code) ->
        (values[dp] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() }?.let { ranges[code] = Range(it / 10, null, null, read) }
    }
    return device.copy(onOff = power?.let { OnOff(it, read) } ?: device.onOff, toggles = toggles, ranges = ranges, online = true)
}

private fun JsonElement?.reportedBoolean(): Boolean? = (this as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
