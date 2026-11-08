package ru.domovoy.panel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.domovoy.core.Device
import ru.domovoy.core.DeviceKind
import ru.domovoy.core.EventProperty
import ru.domovoy.core.Reading
import ru.domovoy.integrations.yandex.YandexClient
import java.time.Instant

/** Discovered devices expose readings; sockets alone can use their advertised power capability. */
data class DiscoveredPanelState(
    val tiles: List<Device> = emptyList(),
    val error: String? = null,
    val lastPolledAt: Instant? = null,
    val pending: Set<String> = emptySet(),
    val commandErrors: Map<String, String> = emptyMap(),
    val intercomGroups: Map<String, List<Device>> = emptyMap(),
)

class DiscoveredDevices(
    private val client: YandexClient,
    private val reread: suspend () -> Unit,
) {
    private val mutableState = MutableStateFlow(DiscoveredPanelState())
    val state = mutableState.asStateFlow()

    fun show(devices: List<Device>, polledAt: Instant) {
        val discovered = combineSwitchButtons(devices.filter { it.kind == DeviceKind.Other })
        val groups = intercomGroups(discovered)
        val groupedIds = groups.values.flatten().map { it.id }.toSet()
        mutableState.value = mutableState.value.copy(
            tiles = discovered.filter { it.id !in groupedIds } + groups.map { (id, entries) -> intercomCard(id, entries) },
            error = null,
            lastPolledAt = polledAt,
            intercomGroups = groups,
        )
    }

    fun showFailure(reason: String) {
        mutableState.value = mutableState.value.copy(error = reason)
    }

    suspend fun toggle(id: String) {
        if (id in mutableState.value.pending) return
        val tile = mutableState.value.tiles.firstOrNull { it.id == id } ?: return
        if (!canTogglePower(tile)) return
        mutableState.value = mutableState.value.copy(
            pending = mutableState.value.pending + id,
            commandErrors = mutableState.value.commandErrors - id,
        )
        try {
            client.setOn(id, on = tile.onOff?.isOn != true).getOrThrow()
            reread()
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
internal fun canTogglePower(tile: Device): Boolean = tile.vendorType == "devices.types.socket" && tile.onOff != null

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
