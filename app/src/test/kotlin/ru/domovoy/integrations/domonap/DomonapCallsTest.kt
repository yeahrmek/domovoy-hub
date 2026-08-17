package ru.domovoy.integrations.domonap

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the panel concludes from Domonap's notifications, and when it brings Domonap's own call
 * screen up. The package, the channel ids and the shape of a ringing call are the ones captured off
 * the tablet on 2026-08-16 — see docs/domonap.md.
 */
class DomonapCallsTest {
    private var clock = Instant.ofEpochSecond(1_786_000_000)

    private fun calls() = DomonapCalls { clock }

    /** A ringing call, as Domonap posts it once the record is populated. */
    private fun DomonapCalls.ring(
        key: String = "k1",
        hasCallScreen: Boolean = true,
    ) = onPosted("com.domonap.app", "telecom_incoming_channel3", key, hasCallScreen)

    @Test
    fun `an incoming call from Domonap puts the panel in a call`() = runTest {
        val calls = calls()

        calls.ring()

        assertEquals(CallState.Active(since = clock), calls.state.value)
    }

    @Test
    fun `a ringing call brings Domonap's call screen up`() = runTest {
        val calls = calls()

        assertTrue(calls.ring())
    }

    @Test
    fun `the call starts on the first post, before the call screen is on the record`() = runTest {
        val calls = calls()

        // Domonap's first post is the foreground service's, and carries no full-screen intent —
        // nothing to open yet, but the intercom is already ringing and the panel stops polling.
        assertFalse(calls.ring(hasCallScreen = false))
        assertEquals(CallState.Active(since = clock), calls.state.value)
    }

    @Test
    fun `the update that adds the call screen opens it, on the key already counted`() = runTest {
        val calls = calls()
        calls.ring(hasCallScreen = false)

        // 25 ms later on the tablet, same key, now with the full-screen intent on it.
        assertTrue(calls.ring(hasCallScreen = true))
    }

    @Test
    fun `the call screen is brought up once, not on every repost of the same call`() = runTest {
        val calls = calls()

        assertTrue(calls.ring())
        assertFalse(calls.ring())
        assertFalse(calls.ring())
    }

    @Test
    fun `the call screen is not brought up twice because the call moved to another notification`() = runTest {
        val calls = calls()
        calls.ring(key = "ringing")

        val opened = calls.onPosted("com.domonap.app", "telecom_ongoing_channel3", "talking", hasCallScreen = true)

        assertFalse(opened)
    }

    @Test
    fun `the next call brings the call screen up again`() = runTest {
        val calls = calls()
        calls.ring(key = "first")
        calls.onRemoved("first")

        assertTrue(calls.ring(key = "second"))
    }

    @Test
    fun `a call that is already up is not opened — the panel only shows a ringing one`() = runTest {
        val calls = calls()

        // Answered somewhere else, so the panel bound to a call already in progress. It is a call,
        // and polling yields to it, but throwing the call screen up at that point interrupts
        // somebody who is already talking.
        val opened = calls.onPosted("com.domonap.app", "telecom_ongoing_channel3", "talking", hasCallScreen = true)

        assertFalse(opened)
        assertEquals(CallState.Active(since = clock), calls.state.value)
    }

    @Test
    fun `another app ringing on a channel of the same name is not a Domonap call`() = runTest {
        val calls = calls()

        val opened = calls.onPosted("com.example.dialer", "telecom_incoming_channel3", "k1", hasCallScreen = true)

        assertFalse(opened)
        assertEquals(CallState.Idle, calls.state.value)
    }

    @Test
    fun `a missed call is a notice about a call that is over, not a call`() = runTest {
        val calls = calls()

        val opened = calls.onPosted("com.domonap.app", "telecom_missed_channel3", "k1", hasCallScreen = false)

        assertFalse(opened)
        assertEquals(CallState.Idle, calls.state.value)
    }

    @Test
    fun `the call ends when the notification goes away`() = runTest {
        val calls = calls()

        calls.state.test {
            assertEquals(CallState.Idle, awaitItem())

            calls.ring()
            assertEquals(CallState.Active(since = clock), awaitItem())

            calls.onRemoved("k1")
            assertEquals(CallState.Idle, awaitItem())
        }
    }

    @Test
    fun `the call stays up while Domonap swaps the ringing notification for the ongoing one`() = runTest {
        val calls = calls()
        val started = clock

        calls.ring(key = "ringing")
        clock = clock.plusSeconds(4)
        calls.onPosted("com.domonap.app", "telecom_ongoing_channel3", "talking", hasCallScreen = false)
        calls.onRemoved("ringing")

        // Still the same call, still timed from when it started ringing.
        assertEquals(CallState.Active(since = started), calls.state.value)

        calls.onRemoved("talking")
        assertEquals(CallState.Idle, calls.state.value)
    }

    @Test
    fun `updating the ringing notification does not restart the call`() = runTest {
        val calls = calls()
        val started = clock

        calls.ring()
        clock = clock.plusSeconds(9)
        calls.ring()

        assertEquals(CallState.Active(since = started), calls.state.value)
    }

    @Test
    fun `a call that ended while the listener was unbound does not strand the panel`() = runTest {
        val calls = calls()
        calls.ring()
        assertEquals(CallState.Active(since = clock), calls.state.value)

        // Unbound across the end of the call, so the removal of k1 was never delivered; on rebind
        // the listener replays what is actually up, which is nothing.
        calls.onListenerReconnected()

        assertEquals(CallState.Idle, calls.state.value)
    }

    @Test
    fun `a call still ringing when the listener rebinds is picked back up, and shown`() = runTest {
        val calls = calls()
        calls.ring()

        calls.onListenerReconnected()
        clock = clock.plusSeconds(30)

        // The intercom is ringing right now and this binding has shown nobody anything yet.
        assertTrue(calls.ring())
        assertEquals(CallState.Active(since = clock), calls.state.value)
    }

    @Test
    fun `a notification the panel never counted going away does not end a call`() = runTest {
        val calls = calls()

        calls.ring()
        calls.onRemoved("some-unrelated-notification")

        assertEquals(CallState.Active(since = clock), calls.state.value)
    }
}
