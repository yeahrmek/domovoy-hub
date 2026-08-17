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

/** The channel a call rings on. Only a ringing call is worth putting on the wall. */
private const val RINGING_CHANNEL = "telecom_incoming_channel3"

/**
 * Turns Domonap's notifications into "is there a call", and decides when to bring Domonap's own
 * call screen up.
 *
 * Keyed on package and channel; of the record's contents only *whether it carries a call screen* is
 * used. The 2026-08-16 capture recorded the rest — the door in `android.title`, a constant
 * `"Входящий вызов"` in `android.text` — and the panel shows none of it, because the call screen it
 * opens shows all of it, with the video, from Domonap itself.
 *
 * Domonap posts nothing at all when idle, which is what makes the channel test safe: the panel
 * cannot be left thinking a call is up because some unrelated notification is sitting there.
 *
 * Written from the listener's binder thread and read from the panel's, hence the locking.
 */
class DomonapCalls(private val now: () -> Instant = Instant::now) {
    /** Keys of the call notifications currently up; a call lasts as long as any of them does. */
    private val up = LinkedHashSet<String>()

    /**
     * Whether this call's screen has already been asked for. A call is four posts on the tablet,
     * and every one of them carries the same full-screen intent; without this the panel would fling
     * the call screen up four times for one ring.
     */
    private var callScreenAsked = false
    private val mutableState = MutableStateFlow<CallState>(CallState.Idle)
    val state: StateFlow<CallState> = mutableState.asStateFlow()

    /**
     * Takes one posted notification.
     *
     * [hasCallScreen] is whether the record carries a full-screen intent — Domonap's own ringing
     * screen, with the video from the intercom and its accept and decline. Returns whether the
     * caller should bring it up now: true at most once per call, and only while the call is still
     * *ringing*.
     *
     * The two posts this splits apart were measured, not guessed. Domonap's first post is the
     * `phoneCall` foreground service's and carries no extras and no full-screen intent; the
     * populated one lands 25 ms later **on the same key**. So a repost of a key already counted is
     * not redundant — it is where the call screen arrives — and the call itself starts on the first
     * post, 25 ms earlier, which is when polling should already be yielding.
     */
    @Synchronized
    fun onPosted(
        packageName: String,
        channelId: String,
        key: String,
        hasCallScreen: Boolean,
    ): Boolean {
        if (packageName != DOMONAP_PACKAGE || channelId !in CALL_CHANNELS) return false
        // Domonap re-posts the same key to update the ringing notification; that is the same call,
        // so the clock keeps running from when it started rather than restarting.
        up.add(key)
        if (mutableState.value is CallState.Idle) mutableState.value = CallState.Active(since = now())

        // Only a ringing call. A call on the ongoing channel is one somebody is already talking on
        // — answered on another phone, or here before the listener bound — and throwing the call
        // screen over that interrupts them. Opening it never answers: this is the screen the
        // platform itself would have shown, and it comes up ringing.
        if (channelId != RINGING_CHANNEL || !hasCallScreen || callScreenAsked) return false
        callScreenAsked = true
        return true
    }

    @Synchronized
    fun onRemoved(key: String) {
        if (!up.remove(key)) return
        if (up.isEmpty()) endCall()
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
        endCall()
    }

    /**
     * The call is over as far as the panel can see, so the next one gets its screen put up too.
     * A missed-call notification is posted at this moment, on its own channel and its own key; it
     * is not a call and nothing here counts it.
     */
    private fun endCall() {
        callScreenAsked = false
        mutableState.value = CallState.Idle
    }
}

/**
 * The one instance the listener writes and the panel reads. The listener is a service and the panel
 * an activity, so the state has to outlive both; there is no second implementation to abstract over.
 */
val domonapCalls = DomonapCalls()
