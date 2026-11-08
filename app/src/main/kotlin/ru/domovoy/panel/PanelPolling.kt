package ru.domovoy.panel

import kotlinx.coroutines.flow.StateFlow
import ru.domovoy.integrations.domonap.CallState

/** Closes the panel's own sheet when a call starts. Never touches Domonap's screen or audio. */
suspend fun closeOnCall(
    calls: StateFlow<CallState>,
    close: () -> Unit,
) {
    calls.collect { state -> if (state is CallState.Active) close() }
}
