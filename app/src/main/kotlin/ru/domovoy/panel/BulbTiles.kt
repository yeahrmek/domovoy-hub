package ru.domovoy.panel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.domovoy.core.Device
import ru.domovoy.core.DeviceKind
import ru.domovoy.core.Reading
import ru.domovoy.integrations.yandex.YandexClient
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
    /**
     * When the poll behind these tiles last succeeded, which is what makes the group stale — not
     * [BulbTileState.lastUpdated], which is when the *bulb* last reported. Null until the first
     * refresh lands. See [isStale] and docs/ui.md, "Stale".
     */
    val lastPolledAt: Instant? = null,
)

/**
 * Holds what the bulb tiles show.
 *
 * Nothing here fetches or schedules: [YandexPoll] reads the house once for the whole panel and
 * hands the devices to [show], so a test drives a poll directly instead of waiting for one.
 * [reread] is that same shared read, used after an action.
 */
class BulbTiles(
    private val client: YandexClient,
    private val reread: suspend () -> Unit,
) {
    private val mutableState = MutableStateFlow(BulbPanelState())
    val state: StateFlow<BulbPanelState> = mutableState.asStateFlow()

    /**
     * What one poll read, and when it read it. Every device in the house arrives; the bulbs are
     * picked out here.
     */
    fun show(
        devices: List<Device>,
        polledAt: Instant,
    ) {
        mutableState.value =
            BulbPanelState(
                tiles = devices.filter { it.kind == DeviceKind.Bulb }.map(Device::toTile),
                lastPolledAt = polledAt,
            )
    }

    /** The poll failed. The tiles keep their last known value and age, plus an error. */
    fun showFailure(reason: String) {
        mutableState.value = mutableState.value.copy(error = reason)
    }

    /**
     * Flips one bulb, then re-reads. Yandex answers `DONE` without promising the bulb has
     * physically changed, so the tile is repainted from a fresh poll, not from the action result.
     */
    suspend fun toggle(id: String) {
        val tile = mutableState.value.tiles.firstOrNull { it.id == id } ?: return
        client
            .setOn(id, on = tile.isOn != true)
            .onSuccess { reread() }
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

/** How a failure reads on a tile; shared with the curtain group, which fails the same way. */
internal fun Throwable.describe(): String = message?.takeIf { it.isNotBlank() } ?: this::class.simpleName.orEmpty()
