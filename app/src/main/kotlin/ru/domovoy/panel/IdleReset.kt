package ru.domovoy.panel

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import kotlin.time.Duration

/**
 * Calls [onIdle] once [timeout] has passed with nothing arriving on [touches], and again after
 * each later spell of quiet.
 *
 * This is the panel going back to Главная by itself. A phone app may stay where you left it; a wall
 * panel is walked up to by someone who did not leave it there, and one showing Балкон because that
 * is where the last person got to is showing the wrong room to everyone after them.
 *
 * The clock is here, in a suspend function over a flow, rather than inside a composable — the same
 * choice [pollPausingForCalls] makes and for the same reason: a clock that cannot be advanced in a
 * test is a clock nobody checks.
 *
 * The wait starts when this does, not at the first touch, so a panel nobody has touched since it
 * booted is on Главная. Each touch cancels the wait in flight and starts a new one, so the count
 * that matters is time since the hand left, not time since the tab was chosen.
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
