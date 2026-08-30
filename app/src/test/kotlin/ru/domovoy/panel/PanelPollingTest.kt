package ru.domovoy.panel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.domovoy.integrations.domonap.CallState
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * The panel yields to Domonap's call screen by getting out of the way: while a call is up it does
 * no polling at all, so it is not competing for Wi-Fi or repainting behind the call. It never
 * launches, covers or dismisses anything — the call screen is Domonap's own activity, which the
 * platform already brings to the front.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PanelPollingTest {
    private val interval = 15.seconds
    private val ringing = CallState.Active(since = Instant.ofEpochSecond(1_786_000_000))

    @Test
    fun `polls every interval while no call is up`() = runTest {
        val calls = MutableStateFlow<CallState>(CallState.Idle)
        var polls = 0
        backgroundScope.launch { pollPausingForCalls(calls, interval) { polls++ } }

        runCurrent()
        assertEquals(1, polls)

        advanceTimeBy(interval)
        runCurrent()
        assertEquals(2, polls)

        advanceTimeBy(interval)
        runCurrent()
        assertEquals(3, polls)
    }

    @Test
    fun `stops polling while a call is up`() = runTest {
        val calls = MutableStateFlow<CallState>(CallState.Idle)
        var polls = 0
        backgroundScope.launch { pollPausingForCalls(calls, interval) { polls++ } }
        runCurrent()
        assertEquals(1, polls)

        calls.value = ringing
        advanceTimeBy(interval * 4)
        runCurrent()

        assertEquals(1, polls)
    }

    @Test
    fun `comes back the moment the call ends, without waiting out the interval`() = runTest {
        val calls = MutableStateFlow<CallState>(CallState.Idle)
        var polls = 0
        backgroundScope.launch { pollPausingForCalls(calls, interval) { polls++ } }
        runCurrent()
        calls.value = ringing
        advanceTimeBy(interval * 4)
        runCurrent()
        assertEquals(1, polls)

        calls.value = CallState.Idle
        runCurrent()

        // No time advanced: the panel refreshes as soon as the call is over rather than leaving
        // state that went stale for the length of the call.
        assertEquals(2, polls)
    }

    @Test
    fun `a call puts away whatever was left open in front of the wall`() = runTest {
        // The panel's second way of getting out of the way, and it exists because the panel now has
        // something a passer-by can leave in front of the tiles: a device sheet. It cannot cover
        // Domonap's screen — that is another app's activity and is in front of this one by
        // construction — but it can be sitting there when the call ends, over the wall somebody is
        // about to want. So it goes at the *start* of the call.
        val calls = MutableStateFlow<CallState>(CallState.Idle)
        var closes = 0
        backgroundScope.launch { closeOnCall(calls) { closes++ } }
        runCurrent()
        assertEquals(0, closes)

        calls.value = ringing
        runCurrent()

        assertEquals(1, closes)
    }

    @Test
    fun `the call ending does not open anything back up`() = runTest {
        // Whoever was reading a tile before the intercom rang has answered the door by now. The
        // panel comes back as a panel — the wall, at the top — rather than as the one device
        // somebody was looking at four minutes ago.
        val calls = MutableStateFlow<CallState>(CallState.Idle)
        var closes = 0
        backgroundScope.launch { closeOnCall(calls) { closes++ } }
        runCurrent()

        calls.value = ringing
        runCurrent()
        calls.value = CallState.Idle
        advanceTimeBy(interval * 4)
        runCurrent()

        assertEquals(1, closes)
    }
}
