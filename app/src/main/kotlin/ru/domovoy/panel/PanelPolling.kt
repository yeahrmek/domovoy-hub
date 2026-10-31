package ru.domovoy.panel

import kotlinx.coroutines.flow.StateFlow
import ru.domovoy.integrations.domonap.CallState

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
 * It only lets go of the panel's own screen — the same passive yielding [AdaptivePolling] does,
 * for the same reason.
 */
suspend fun closeOnCall(
    calls: StateFlow<CallState>,
    close: () -> Unit,
) {
    calls.collect { state -> if (state is CallState.Active) close() }
}
