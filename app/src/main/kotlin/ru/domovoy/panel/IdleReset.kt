package ru.domovoy.panel

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.MutableState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import kotlin.time.Duration

/**
 * Calls [onIdle] once [timeout] has passed with nothing arriving on [touches], and again after
 * each later spell of quiet.
 *
 * This is the panel going back to Главная by itself — which, now that the rooms are one scroll
 * rather than a strip of tabs, means back to the top. A phone app may stay where you left it; a wall
 * panel is walked up to by someone who did not leave it there, and one showing Балкон because that
 * is where the last person got to is showing the wrong room to everyone after them.
 *
 * The clock is here, in a suspend function over a flow, rather than inside a composable — the same
 * choice [pollPausingForCalls] makes and for the same reason: a clock that cannot be advanced in a
 * test is a clock nobody checks.
 *
 * The wait starts when this does, not at the first touch, so a panel nobody has touched since it
 * booted is on Главная. Each touch cancels the wait in flight and starts a new one, so the count
 * that matters is time since the hand left, not time since somebody scrolled.
 */
suspend fun resetAfterIdle(
    touches: Flow<Unit>,
    timeout: Duration,
    onIdle: () -> Unit,
) {
    touches.onStart { emit(Unit) }.collectLatest {
        delay(timeout)
        onIdle()
    }
}

/**
 * **What going back to Главная actually means, in one place**: nothing open in front of the wall,
 * and the scroll at the top.
 *
 * It was a `scrollToItem(0)` written inline in `MainActivity` while the top of the scroll was the
 * whole of it. It is not any more — a tap on a tile now opens a device sheet over the panel, and a
 * sheet left open by whoever walked away is a wall panel that has stopped being a panel: the next
 * person gets a scrim and one device instead of the flat. So the two moves are one function, named
 * for what it does rather than for how, and a test can call it.
 *
 * **The sheet goes first.** Scrolling underneath something that is still covering the wall is work
 * nobody can see, and if the scroll ever suspends for a frame the sheet should not outlive it.
 *
 * The lamps a room has open are deliberately *not* closed here, and that is unchanged: they are
 * tiles in the grid rather than something in front of it, so an open group eleven sections down is
 * out of sight rather than in the way.
 */
suspend fun returnToHome(
    scroll: LazyGridState,
    /** Which device's sheet is open, or null when none is. See [DeviceSheet]. */
    openSheet: MutableState<String?>,
) {
    openSheet.value = null
    scroll.scrollToItem(0)
}
