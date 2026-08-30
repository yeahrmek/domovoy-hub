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

/**
 * Calls [close] whenever a call starts, which is the panel putting away whatever somebody left open
 * on it before the intercom rang.
 *
 * The one thing the panel now holds that a passer-by can leave in front of the wall is a device
 * sheet — see [DeviceSheet]. It cannot *cover* Domonap's screen, which is another app's activity and
 * is in front of this one by construction; what it can do is be sitting there when the call ends and
 * the panel comes back, over the tiles somebody is about to want. So it goes at the start of the
 * call rather than at the end of it: by the time anybody looks at the wall again it is a wall.
 *
 * Nothing here cancels, snoozes, delays or covers the notification, and nothing here touches audio.
 * It only lets go of the panel's own screen — the same passive yielding [pollPausingForCalls] does,
 * for the same reason.
 */
suspend fun closeOnCall(
    calls: StateFlow<CallState>,
    close: () -> Unit,
) {
    calls.collect { state -> if (state is CallState.Active) close() }
}
