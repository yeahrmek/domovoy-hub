package ru.domovoy.panel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.domovoy.core.Bounds
import ru.domovoy.core.Device
import ru.domovoy.core.DeviceKind
import ru.domovoy.core.Reading
import ru.domovoy.integrations.yandex.YandexClient

/** The instance of the curtain's `range` capability: how far open it is, in percent. */
private const val OPEN = "open"

/** What one curtain tile renders. */
data class CurtainTileState(
    val id: String,
    val name: String,
    val room: String?,
    /** Null when the curtain reported no position at all — shown as unknown, never as shut. */
    val openPercent: Double?,
    /** What the curtain will accept, as it reported it; null when it named no bounds. */
    val bounds: Bounds?,
    /** When the position was read; this is the age the tile prints. */
    val lastUpdated: Reading,
    /** When the curtain last actually moved. Kept, not yet shown — see docs/yandex.md. */
    val stateChangedAt: Reading,
)

/**
 * The curtain half of the panel. As with the bulbs, one `/v1.0/user/info` call is the whole house,
 * so [error] belongs to the group rather than to a tile and the tiles keep their last values.
 */
data class CurtainPanelState(
    val tiles: List<CurtainTileState> = emptyList(),
    /** Non-null when the last poll or move failed. [tiles] then hold the last known values. */
    val error: String? = null,
)

/**
 * Polls the Yandex curtains and holds what the tiles show.
 *
 * Nothing here schedules: [refresh] is called by whatever owns the timer, so a test drives the
 * poll directly instead of waiting for one.
 */
class CurtainTiles(
    private val client: YandexClient,
) {
    private val mutableState = MutableStateFlow(CurtainPanelState())
    val state: StateFlow<CurtainPanelState> = mutableState.asStateFlow()

    /** Polls once. On failure the tiles keep their last known position and age, plus an error. */
    suspend fun refresh() {
        client
            .devices()
            .onSuccess { devices ->
                mutableState.value =
                    CurtainPanelState(
                        tiles = devices.filter { it.kind == DeviceKind.Curtain }.map(Device::toTile),
                    )
            }
            .onFailure { failure -> mutableState.value = mutableState.value.copy(error = failure.describe()) }
    }

    /**
     * Drives one curtain to [percent], then re-reads. The value is snapped to what the curtain
     * said it accepts first — a slider can hand over anything, and Yandex can only reject what is
     * off the grid. As with the bulbs, the tile is repainted from a fresh poll rather than from
     * the action result, which promises only that Yandex took the request.
     */
    suspend fun setOpen(
        id: String,
        percent: Double,
    ) {
        val tile = mutableState.value.tiles.firstOrNull { it.id == id } ?: return
        client
            .setRange(id, instance = OPEN, value = tile.bounds?.snap(percent) ?: percent)
            .onSuccess { refresh() }
            .onFailure { failure -> mutableState.value = mutableState.value.copy(error = failure.describe()) }
    }
}

private fun Device.toTile(): CurtainTileState {
    val open = ranges[OPEN]
    return CurtainTileState(
        id = id,
        name = name,
        room = room,
        openPercent = open?.value,
        bounds = open?.bounds,
        // The age shown is the age of the position, not of the on/off the curtain also carries:
        // on this device that one has never reported at all.
        lastUpdated = open?.lastUpdated ?: Reading.Never,
        stateChangedAt = open?.stateChangedAt ?: Reading.Never,
    )
}
