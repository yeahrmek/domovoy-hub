package ru.domovoy.panel

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.domovoy.core.Bounds
import ru.domovoy.core.ColorSetting
import ru.domovoy.core.Device
import ru.domovoy.core.DeviceKind
import ru.domovoy.core.Reading
import ru.domovoy.integrations.tuya.TuyaApiException
import ru.domovoy.integrations.yandex.YandexClient
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
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
    /** Brightness is absent on relay-backed lights and present only on dimmable bulbs. */
    val brightnessPercent: Double? = null,
    val brightnessBounds: Bounds? = null,
    val brightnessLastUpdated: Reading = Reading.Never,
    val color: ColorSetting? = null,
)

/**
 * The bulb half of the panel. One `/v1.0/user/info` call is the whole house, so a failed poll
 * fails every bulb at once and [error] belongs to the group rather than to a tile; the tiles
 * keep the last values that were read, and stay on screen next to the error.
 */
data class BulbPanelState(
    val tiles: List<BulbTileState> = emptyList(),
    /** Non-null when the last poll failed. [tiles] then hold the last known values. */
    val error: String? = null,
    /**
     * When the poll behind these tiles last succeeded, which is what makes the group stale — not
     * [BulbTileState.lastUpdated], which is when the *bulb* last reported. Null until the first
     * refresh lands. See [isStale] and docs/ui.md, "Stale".
     */
    val lastPolledAt: Instant? = null,
    val pending: Set<String> = emptySet(),
    val commandErrors: Map<String, String> = emptyMap(),
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
            mutableState.value.copy(
                tiles = devices.filter { it.kind == DeviceKind.Bulb }.map(Device::toTile),
                error = null,
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
        if (id in mutableState.value.pending) return
        val tile = mutableState.value.tiles.firstOrNull { it.id == id } ?: return
        command(id) { client.setOn(id, on = tile.isOn != true) }
    }

    suspend fun setBrightness(
        id: String,
        percent: Double,
    ) {
        if (id in mutableState.value.pending) return
        val tile = mutableState.value.tiles.firstOrNull { it.id == id } ?: return
        val bounds = tile.brightnessBounds
        if (bounds == null) {
            mutableState.value = mutableState.value.copy(
                commandErrors = mutableState.value.commandErrors + (id to "Яркость не регулируется"),
            )
            return
        }
        command(id) { client.setRange(id, instance = BRIGHTNESS, value = bounds.snap(percent)) }
    }

    suspend fun setScene(
        id: String,
        scene: String,
    ) {
        if (id in mutableState.value.pending) return
        val tile = mutableState.value.tiles.firstOrNull { it.id == id } ?: return
        if (scene !in tile.color?.scenes.orEmpty()) {
            mutableState.value = mutableState.value.copy(
                commandErrors = mutableState.value.commandErrors + (id to "Сцена недоступна"),
            )
            return
        }
        command(id) { client.setColor(id, instance = SCENE, value = scene) }
    }

    suspend fun setRgb(
        id: String,
        rgb: Int,
    ) {
        if (id in mutableState.value.pending) return
        val tile = mutableState.value.tiles.firstOrNull { it.id == id } ?: return
        if (tile.color?.instance != RGB && tile.color?.instance != SCENE) {
            mutableState.value = mutableState.value.copy(
                commandErrors = mutableState.value.commandErrors + (id to "Цвет не регулируется"),
            )
            return
        }
        command(id) { client.setColor(id, instance = RGB, value = rgb.coerceIn(0, 0xFFFFFF)) }
    }

    private suspend fun command(
        id: String,
        issue: suspend () -> Result<Unit>,
    ) {
        if (id in mutableState.value.pending) return
        mutableState.value = mutableState.value.copy(
            pending = mutableState.value.pending + id,
            commandErrors = mutableState.value.commandErrors - id,
        )
        try {
            issue().getOrThrow()
            reread()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            mutableState.value = mutableState.value.copy(
                commandErrors = mutableState.value.commandErrors + (id to failure.describe()),
            )
        } finally {
            mutableState.value = mutableState.value.copy(pending = mutableState.value.pending - id)
        }
    }
}

private const val BRIGHTNESS = "brightness"
private const val RGB = "rgb"
private const val SCENE = "scene"

private fun Device.toTile(): BulbTileState {
    val brightness = ranges[BRIGHTNESS]
    return BulbTileState(
        id = id,
        name = name,
        room = room,
        isOn = onOff?.isOn,
        lastUpdated = onOff?.lastUpdated ?: Reading.Never,
        brightnessPercent = brightness?.value,
        brightnessBounds = brightness?.bounds,
        brightnessLastUpdated = brightness?.lastUpdated ?: Reading.Never,
        color = color,
    )
}

/** Short UI labels selected by exception type and structured vendor code; raw details stay in Log. */
internal fun reason(failure: Throwable): String = when (failure) {
    // 28841002 is the observed IoT Core subscription-expired error; never match vendor prose.
    is TuyaApiException -> if (failure.code == 28841002L) "Подписка истекла" else "Сервис недоступен"
    is UnknownHostException, is NoRouteToHostException -> "Нет связи с сервисом"
    is SocketTimeoutException, is InterruptedIOException -> "Сервис не ответил"
    is ConnectException -> "Сервис отклонил соединение"
    else -> "Не удалось выполнить запрос"
}

/** Where the exception's own words go, since they no longer go on the wall. */
private const val LOG_TAG = "DomovoyPanel"

/**
 * How a failure reads on a tile: [reason]'s short label, with everything the exception
 * actually said written to `Log` on the way past.
 *
 * Shared by every group — one Yandex call feeds four of them and Tuya's per-device reads the
 * fifth — so this is the single edge where a throwable stops being a throwable and becomes a word
 * the panel owns.
 */
internal fun Throwable.describe(): String {
    Log.w(LOG_TAG, "a vendor call failed", this)
    return reason(this)
}
