package ru.domovoy.panel

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import ru.domovoy.integrations.domonap.CallState
import kotlin.time.Duration

/**
 * Polls every [interval], except while the intercom is mid-call.
 *
 * This is the whole of the panel's yielding, and it is deliberately passive. Domonap's
 * `IncomingDomofonCallActivity` declares `showWhenLocked` and `turnScreenOn` and is launched by a
 * full-screen intent, so the platform puts it on top and wakes the tablet on its own; the ringtone
 * is the app's, not a notification channel's. There is nothing for the panel to launch and nothing
 * to turn up — only work to stop doing, so the panel is not polling Wi-Fi and repainting behind a
 * call. Nothing here cancels, snoozes or covers the notification.
 *
 * When the call ends the panel refreshes at once rather than waiting out the interval, so the tiles
 * are not showing state that went stale for the length of the call.
 */
suspend fun pollPausingForCalls(
    calls: StateFlow<CallState>,
    interval: Duration,
    refresh: suspend () -> Unit,
): Nothing {
    while (true) {
        calls.first { it is CallState.Idle }
        refresh()
        delay(interval)
    }
}
