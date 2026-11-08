package ru.domovoy.panel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.domovoy.core.Device
import ru.domovoy.core.DeviceKind
import ru.domovoy.core.EventProperty
import ru.domovoy.core.Mode
import ru.domovoy.core.Reading
import ru.domovoy.integrations.xiaomi.XiaomiLanClient
import ru.domovoy.integrations.yandex.YandexClient
import java.time.Instant

/** Discovered devices expose readings, with verified power and mode controls where supported. */
data class DiscoveredPanelState(
    val tiles: List<Device> = emptyList(),
    val error: String? = null,
    val lastPolledAt: Instant? = null,
    val pending: Set<String> = emptySet(),
    val commandErrors: Map<String, String> = emptyMap(),
    val localIds: Set<String> = emptySet(),
    val localPolledAt: Map<String, Instant> = emptyMap(),
    val readErrors: Map<String, String> = emptyMap(),
    val intercomGroups: Map<String, List<Device>> = emptyMap(),
)

class DiscoveredDevices(
    private val client: YandexClient,
    private val xiaomi: XiaomiLanClient? = null,
    private val now: () -> Instant = Instant::now,
    private val reread: suspend () -> Unit,
) {
    private val mutableState = MutableStateFlow(DiscoveredPanelState())
    val state = mutableState.asStateFlow()
    private var yandexDevices = emptyList<Device>()
    private var localInventory = emptyList<Device>()
    private val localReadings = mutableMapOf<String, Device>()

    /** Local devices are polled independently of Yandex and retain their existing card identities. */
    suspend fun refreshLocal(): Boolean = coroutineScope {
        val inventory = try {
            xiaomi?.inventory().orEmpty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = mutableState.value.copy(
                readErrors = mutableState.value.readErrors + localInventory.associate { it.id to "Не удалось прочитать настройки увлажнителя" },
            )
            render()
            return@coroutineScope false
        }
        localInventory = inventory
        val ids = inventory.map { it.id }.toSet()
        localReadings.keys.retainAll(ids)
        mutableState.value = mutableState.value.copy(localIds = ids)
        render()
        inventory.map { base -> async { readLocal(base) } }.awaitAll().all { it }
    }

    private suspend fun readLocal(base: Device): Boolean {
        val result = requireNotNull(xiaomi).read(base)
        result.onSuccess { device ->
            localReadings[base.id] = device
            mutableState.value = mutableState.value.copy(
                localPolledAt = mutableState.value.localPolledAt + (base.id to now()),
                readErrors = mutableState.value.readErrors - base.id,
            )
        }.onFailure { failure ->
            mutableState.value = mutableState.value.copy(readErrors = mutableState.value.readErrors + (base.id to failure.describe()))
        }
        render()
        return result.isSuccess
    }

    private fun render() {
        val inventory = localInventory
        val bases = (yandexDevices + inventory.filter { local -> yandexDevices.none { it.id == local.id } })
        val devices = bases.map { base ->
            localReadings[base.id]?.copy(name = base.name, room = base.room) ?: base
        }
        val discovered = combineSwitchButtons(devices.filter { it.kind == DeviceKind.Other })
        val groups = intercomGroups(discovered)
        val groupedIds = groups.values.flatten().map { it.id }.toSet()
        mutableState.value = mutableState.value.copy(
            tiles = discovered.filter { it.id !in groupedIds } + groups.map { (id, entries) -> intercomCard(id, entries) },
            intercomGroups = groups,
        )
    }

    fun show(devices: List<Device>, polledAt: Instant) {
        yandexDevices = devices
        mutableState.value = mutableState.value.copy(error = null, lastPolledAt = polledAt)
        render()
    }

    fun showFailure(reason: String) {
        mutableState.value = mutableState.value.copy(error = reason)
    }

    suspend fun toggle(id: String) {
        if (id in mutableState.value.pending) return
        val tile = mutableState.value.tiles.firstOrNull { it.id == id } ?: return
        if (!canTogglePower(tile)) return
        command(id) {
            if (xiaomi?.configured(id) == true) xiaomi.setOn(id, tile.onOff?.isOn != true) else client.setOn(id, on = tile.onOff?.isOn != true)
        }
    }

    suspend fun setMode(
        id: String,
        instance: String,
        value: String,
    ) {
        if (id in mutableState.value.pending) return
        val tile = mutableState.value.tiles.firstOrNull { it.id == id } ?: return
        if (tile.vendorType != "devices.types.humidifier") return
        if (value !in humidifierModes(tile)[instance]?.available.orEmpty()) {
            mutableState.value = mutableState.value.copy(
                commandErrors = mutableState.value.commandErrors + (id to "Режим недоступен"),
            )
            return
        }
        command(id) {
            if (xiaomi?.configured(id) == true) xiaomi.setMode(id, value) else client.setMode(id, instance, value)
        }
    }

    suspend fun setHumidity(id: String, percent: Double) {
        if (id in mutableState.value.pending) return
        val tile = mutableState.value.tiles.firstOrNull { it.id == id } ?: return
        val bounds = tile.ranges["humidity"]?.bounds ?: return
        if (id !in mutableState.value.localIds) return
        if (!percent.isFinite()) {
            mutableState.value = mutableState.value.copy(commandErrors = mutableState.value.commandErrors + (id to "Некорректная влажность"))
            return
        }
        command(id) {
            val local = requireNotNull(xiaomi)
            val target = local.setHumidity(id, bounds.snap(percent))
            if (target.isSuccess && tile.modes["fan_speed"]?.current != "auto") local.setMode(id, "auto") else target
        }
    }

    private suspend fun command(id: String, action: suspend () -> Result<Unit>) {
        mutableState.value = mutableState.value.copy(
            pending = mutableState.value.pending + id,
            commandErrors = mutableState.value.commandErrors - id,
        )
        try {
            action().getOrThrow()
            if (xiaomi?.configured(id) == true) {
                val tile = mutableState.value.tiles.first { it.id == id }
                check(readLocal(tile)) { "Не удалось подтвердить состояние увлажнителя" }
            } else {
                reread()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            mutableState.value = mutableState.value.copy(commandErrors = mutableState.value.commandErrors + (id to failure.describe()))
        } finally {
            mutableState.value = mutableState.value.copy(pending = mutableState.value.pending - id)
        }
    }
}

/** Locks and entry relays stay read-only even when the vendor reports an on/off capability. */
internal fun canTogglePower(tile: Device): Boolean = tile.vendorType in setOf("devices.types.socket", "devices.types.humidifier") && tile.onOff != null

internal fun humidifierModes(tile: Device): Map<String, Mode> = if (tile.vendorType == "devices.types.humidifier") {
    tile.modes.filter { (instance, mode) -> instance == "fan_speed" && mode.available.isNotEmpty() }
} else {
    emptyMap()
}

/** The wardrobe switch drives its room's light; ambiguous inventories never choose an arbitrary lamp. */
internal fun switchLight(tile: Device, lights: List<BulbTileState>): BulbTileState? {
    if (tile.vendorType != "devices.types.switch" || tile.room != "Гардероб" || "upper_button" !in tile.events || "lower_button" !in tile.events) return null
    return lights.singleOrNull { it.room == tile.room }
}

/** These two wardrobe endpoints belong to one wireless switch, with a shared battery on its lower endpoint. */
internal fun combineSwitchButtons(devices: List<Device>): List<Device> {
    val candidates = devices.filter { it.vendorType == "devices.types.switch" && it.room == "Гардероб" && it.onOff == null }
    val upper = candidates.singleOrNull { it.name == "Верхняя кнопка" } ?: return devices
    val lower = candidates.singleOrNull { it.name == "Нижняя кнопка" } ?: return devices
    val combined = lower.copy(
        name = "Выключатель",
        properties = upper.properties + lower.properties,
        events = upper.events.mapKeys { "upper_${it.key}" } + lower.events.mapKeys { "lower_${it.key}" } + mapOf(
            "upper_button" to (upper.events["button"] ?: EventProperty(null, Reading.Never)),
            "lower_button" to (lower.events["button"] ?: EventProperty(null, Reading.Never)),
        ),
    )
    return devices.filterNot { it.id == upper.id }.map { if (it.id == lower.id) combined else it }
}
