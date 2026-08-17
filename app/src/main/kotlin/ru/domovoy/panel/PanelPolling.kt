package ru.domovoy.panel

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import ru.domovoy.integrations.domonap.CallState
import kotlin.time.Duration

/**
 * Polls every [interval], except while the intercom is mid-call.
 *
 * This is the whole of the panel's yielding, and it is deliberately passive — but no longer for the
 * reason first written here. That reason was that the platform launched Domonap's call screen
 * itself, leaving the panel nothing to do but keep out of the way. The 2026-08-16 captures killed
 * it: `IncomingDomofonCallActivity` never starts on this tablet, awake or over the keyguard, so
 * there is no takeover to yield *to*. Putting it up from the panel was tried the same evening and
 * hung the call up — see [ru.domovoy.integrations.domonap.DomonapCallListener] and docs/domonap.md.
 *
 * So this stays passive by decision rather than by inheritance, and it is still worth doing: the
 * panel is not polling Wi-Fi and repainting behind a live intercom call. Nothing here cancels,
 * snoozes or covers the notification.
 *
 * The ringtone stays Domonap's own: it plays it itself, on a `MediaPlayer` at full volume, on a
 * channel with no sound of its own. The panel must not touch audio to try to help.
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
