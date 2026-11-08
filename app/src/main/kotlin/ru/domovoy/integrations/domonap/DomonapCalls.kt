package ru.domovoy.integrations.domonap

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface CallState {
    data object Idle : CallState

    data object Active : CallState
}

private const val DOMONAP_PACKAGE = "com.domonap.app"

// Verified call channels. Missed and silent notifications must not pause polling.
private val CALL_CHANNELS = setOf("telecom_incoming_channel3", "telecom_ongoing_channel3")

/** Tracks live call notifications. Never opens the call screen or changes notifications/audio. */
class DomonapCalls {
    private val up = mutableSetOf<String>()
    private val mutableState = MutableStateFlow<CallState>(CallState.Idle)
    val state: StateFlow<CallState> = mutableState.asStateFlow()

    @Synchronized
    fun onPosted(
        packageName: String,
        channelId: String,
        key: String,
    ) {
        if (packageName != DOMONAP_PACKAGE || channelId !in CALL_CHANNELS) return
        up.add(key)
        mutableState.value = CallState.Active
    }

    @Synchronized
    fun onRemoved(key: String) {
        if (up.remove(key) && up.isEmpty()) mutableState.value = CallState.Idle
    }

    /** Removals while unbound were lost; the listener replays current notifications after this. */
    @Synchronized
    fun onListenerReconnected() {
        up.clear()
        mutableState.value = CallState.Idle
    }
}

/** Shared by the notification-listener service and panel activity. */
val domonapCalls = DomonapCalls()
