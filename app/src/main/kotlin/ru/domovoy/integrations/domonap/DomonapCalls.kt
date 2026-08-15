package ru.domovoy.integrations.domonap

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/** Whether the intercom is mid-call, as far as the panel can see. */
sealed interface CallState {
    /** No Domonap call notification is up. */
    data object Idle : CallState

    /** A call has been up since [since] — how long, so a tile can say it rather than guess. */
    data class Active(val since: Instant) : CallState
}

private const val DOMONAP_PACKAGE = "com.domonap.app"

/**
 * The channels a live call posts on, both read off the tablet (docs/domonap.md):
 * `telecom_incoming_channel3` is "Входящие звонки" at importance MAX, `telecom_ongoing_channel3` is
 * the call that is already up.
 *
 * `telecom_missed_channel3` is deliberately absent — a missed call is a notice about a call that is
 * already over, and yielding the panel to it would strand the panel until the user swiped it away.
 * So is `telecom_silent_channel3`, whose purpose is unrecorded: Domonap runs a `phoneCall`
 * foreground service, and if that service's notification turns out to live on the silent channel,
 * treating it as a call would wedge the panel for as long as the service ran.
 */
private val CALL_CHANNELS = setOf("telecom_incoming_channel3", "telecom_ongoing_channel3")

/**
 * Turns Domonap's notifications into "is there a call". Keyed on package and channel only: no
 * incoming call has been captured yet, so nothing here reads the notification's extras, and the
 * caller's name and photo are simply not known — see the open questions in docs/domonap.md.
 *
 * Domonap posts nothing at all when idle, which is what makes the channel test safe: the panel
 * cannot be left thinking a call is up because some unrelated notification is sitting there.
 *
 * Written from the listener's binder thread and read from the panel's, hence the locking.
 */
class DomonapCalls(private val now: () -> Instant = Instant::now) {
    /** Keys of the call notifications currently up; a call lasts as long as any of them does. */
    private val up = LinkedHashSet<String>()
    private val mutableState = MutableStateFlow<CallState>(CallState.Idle)
    val state: StateFlow<CallState> = mutableState.asStateFlow()

    @Synchronized
    fun onPosted(
        packageName: String,
        channelId: String,
        key: String,
    ) {
        if (packageName != DOMONAP_PACKAGE || channelId !in CALL_CHANNELS) return
        // Domonap re-posts the same key to update the ringing notification; that is the same call,
        // so the clock keeps running from when it started rather than restarting.
        if (!up.add(key)) return
        if (mutableState.value is CallState.Idle) mutableState.value = CallState.Active(since = now())
    }

    @Synchronized
    fun onRemoved(key: String) {
        if (!up.remove(key)) return
        if (up.isEmpty()) mutableState.value = CallState.Idle
    }

    /**
     * The listener has (re)bound. Notification listeners are unbound and rebound behind our back —
     * after a reboot, or when the system reclaims the service — and removals that happened while
     * unbound were never delivered. Anything held from the previous binding is therefore dropped;
     * the listener replays what is actually up. Without this a call that ended while unbound would
     * leave the panel yielded for good.
     */
    @Synchronized
    fun onListenerReconnected() {
        up.clear()
        mutableState.value = CallState.Idle
    }
}

/**
 * The one instance the listener writes and the panel reads. The listener is a service and the panel
 * an activity, so the state has to outlive both; there is no second implementation to abstract over.
 */
val domonapCalls = DomonapCalls()
