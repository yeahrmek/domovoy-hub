package ru.domovoy.panel

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.domovoy.core.Bounds
import ru.domovoy.core.ColorSetting
import ru.domovoy.core.Device
import ru.domovoy.core.DeviceKind
import ru.domovoy.core.Reading
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
    /** When the bulb last actually changed. Kept, not yet shown — see docs/yandex.md. */
    val stateChangedAt: Reading,
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

    suspend fun setBrightness(
        id: String,
        percent: Double,
    ) {
        val tile = mutableState.value.tiles.firstOrNull { it.id == id } ?: return
        val bounds = tile.brightnessBounds
        if (bounds == null) {
            mutableState.value = mutableState.value.copy(error = IllegalArgumentException("no brightness").describe())
            return
        }
        client
            .setRange(id, instance = BRIGHTNESS, value = bounds.snap(percent))
            .onSuccess { reread() }
            .onFailure { failure -> mutableState.value = mutableState.value.copy(error = failure.describe()) }
    }

    suspend fun setScene(
        id: String,
        scene: String,
    ) {
        val tile = mutableState.value.tiles.firstOrNull { it.id == id } ?: return
        if (scene !in tile.color?.scenes.orEmpty()) {
            mutableState.value = mutableState.value.copy(error = IllegalArgumentException("unsupported scene").describe())
            return
        }
        client
            .setColor(id, instance = SCENE, value = scene)
            .onSuccess { reread() }
            .onFailure { failure -> mutableState.value = mutableState.value.copy(error = failure.describe()) }
    }

    suspend fun setRgb(
        id: String,
        rgb: Int,
    ) {
        val tile = mutableState.value.tiles.firstOrNull { it.id == id } ?: return
        if (tile.color?.instance != RGB) {
            mutableState.value = mutableState.value.copy(error = IllegalArgumentException("no RGB").describe())
            return
        }
        client
            .setColor(id, instance = RGB, value = rgb.coerceIn(0, 0xFFFFFF))
            .onSuccess { reread() }
            .onFailure { failure -> mutableState.value = mutableState.value.copy(error = failure.describe()) }
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
        stateChangedAt = onOff?.stateChangedAt ?: Reading.Never,
        brightnessPercent = brightness?.value,
        brightnessBounds = brightness?.bounds,
        brightnessLastUpdated = brightness?.lastUpdated ?: Reading.Never,
        color = color,
    )
}

/**
 * The four words the panel is willing to print when something it polls stops answering, and the
 * whole vocabulary — `docs/design/panel-redesign.md` item 7.
 *
 * **It used to be `message ?: className`**, so Java's own sentence went onto the status line in the
 * middle of a line whose other half was the panel's: `not updating: Unable to resolve host
 * "openapi.tuyaeu.com"`. Two things were wrong with that and only one of them is about language. The
 * string is *unbounded* — a tile is 188 or 251 dp wide and holds sixteen to twenty-four characters
 * of `bodyMedium` — so a vendor's error text decided how tall that tile came out, which is the one
 * thing the anatomy in `TileCard` exists to stop anything doing.
 *
 * **The mapping is by type and not by message**, because a message is the vendor's to change and a
 * type is not. What the four cover, in the order a wall panel meets them:
 *
 * - **unreachable** — the tablet's Wi-Fi is down or DNS is not answering. `AGENTS.md` says to assume
 *   this happens, and on this flat's own network it is the common one.
 * - **timed out** — the request went out and nothing came back inside the client's call timeout.
 *   OkHttp's own call timeout arrives as the *parent* [InterruptedIOException] rather than as a
 *   [SocketTimeoutException], so both are here; every vendor client in this app sets one.
 * - **refused** — something answered the connection with "no".
 * - **failed** — everything else, which is one word for two very different things: an I/O failure
 *   with no name of its own, and the panel's own `error(…)` checks on a response it did not like
 *   (`HTTP 403`, a `status` that is not `ok`, a token that is not in the store). _That last one is
 *   this mapping's price and it is a real one_ — the client's "no Yandex token stored — set
 *   yandex.oauth.token in local.properties and reinstall" is 76 characters and cannot be on a tile
 *   whatever the rule is, so it goes to [describe]'s `Log` line and docs/yandex.md says so.
 *
 * Pure, and separate from [describe] for exactly that reason: this is the half a table test can ask
 * a hundred questions of, and `Log` is the half it cannot.
 */
internal fun reason(failure: Throwable): String = when (failure) {
    is UnknownHostException, is NoRouteToHostException -> "unreachable"
    is SocketTimeoutException, is InterruptedIOException -> "timed out"
    is ConnectException -> "refused"
    else -> "failed"
}

/** Where the exception's own words go, since they no longer go on the wall. */
private const val LOG_TAG = "DomovoyPanel"

/**
 * How a failure reads on a tile: one of [reason]'s four words, with everything the exception
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
