package ru.domovoy.panel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.domovoy.core.Device
import ru.domovoy.core.Reading
import ru.domovoy.integrations.yandex.YandexClient
import java.time.Duration
import java.time.Instant

/** What one bulb tile renders. */
data class BulbTileState(
    val id: String,
    val name: String,
    val room: String?,
    /** Null when the bulb reported no on/off value at all — shown as unknown, never as "off". */
    val isOn: Boolean?,
    /** When the reading was taken; this is the age the tile prints. */
    val lastUpdated: Reading,
    /** When the bulb last actually changed. Kept, not yet shown — see docs/yandex.md. */
    val stateChangedAt: Reading,
)

/**
 * The bulb half of the panel. One `/v1.0/user/info` call is the whole house, so a failed poll
 * fails every bulb at once and [error] belongs to the group rather than to a tile; the tiles
 * keep the last values that were read, and stay on screen next to the error.
 */
data class BulbPanelState(
    val tiles: List<BulbTileState> = emptyList(),
    /** Non-null when the last poll or toggle failed. [tiles] then hold the last known values. */
    val error: String? = null,
)

/**
 * Polls the Yandex bulbs and holds what the tiles show.
 *
 * Nothing here schedules: [refresh] is called by whatever owns the timer, so a test drives the
 * poll directly instead of waiting for one.
 */
class BulbTiles(
    private val client: YandexClient,
) {
    private val mutableState = MutableStateFlow(BulbPanelState())
    val state: StateFlow<BulbPanelState> = mutableState.asStateFlow()

    /** Polls once. On failure the tiles keep their last known value and age, plus an error. */
    suspend fun refresh() {
        client
            .devices()
            .onSuccess { devices -> mutableState.value = BulbPanelState(tiles = devices.map(Device::toTile)) }
            .onFailure { failure -> mutableState.value = mutableState.value.copy(error = failure.describe()) }
    }

    /**
     * Flips one bulb, then re-reads. Yandex answers `DONE` without promising the bulb has
     * physically changed, so the tile is repainted from a fresh poll, not from the action result.
     */
    suspend fun toggle(id: String) {
        val tile = mutableState.value.tiles.firstOrNull { it.id == id } ?: return
        client
            .setOn(id, on = tile.isOn != true)
            .onSuccess { refresh() }
            .onFailure { failure -> mutableState.value = mutableState.value.copy(error = failure.describe()) }
    }
}

private fun Device.toTile() = BulbTileState(
    id = id,
    name = name,
    room = room,
    isOn = onOff?.isOn,
    lastUpdated = onOff?.lastUpdated ?: Reading.Never,
    stateChangedAt = onOff?.stateChangedAt ?: Reading.Never,
)

private fun Throwable.describe(): String = message?.takeIf { it.isNotBlank() } ?: this::class.simpleName.orEmpty()

/**
 * How old a reading is, in the words the tile prints. A capability that never reported comes back
 * as "never read" — formatting its `0.0` as a date would show *1 Jan 1970*.
 */
fun ageLabel(
    reading: Reading,
    now: Instant,
): String = when (reading) {
    Reading.Never -> "never read"
    is Reading.At -> {
        val seconds = Duration.between(reading.instant, now).seconds
        when {
            seconds < 60 -> "just now"
            seconds < 3600 -> "${seconds / 60} min ago"
            seconds < 86_400 -> "${seconds / 3600} h ago"
            else -> "${seconds / 86_400} d ago"
        }
    }
}
