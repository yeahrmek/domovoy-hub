package ru.domovoy.integrations.domonap

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

/**
 * What the panel concludes from Domonap's notifications. The package and the channel ids are the
 * ones recorded off the tablet in docs/domonap.md; no field of the notification's extras is read,
 * because no incoming call has been captured yet.
 */
class DomonapCallsTest {
    private var clock = Instant.ofEpochSecond(1_786_000_000)

    private fun calls() = DomonapCalls { clock }

    @Test
    fun `an incoming call from Domonap puts the panel in a call`() = runTest {
        val calls = calls()

        calls.onPosted("com.domonap.app", "telecom_incoming_channel3", key = "k1")

        assertEquals(CallState.Active(since = clock), calls.state.value)
    }

    @Test
    fun `another app ringing on a channel of the same name is not a Domonap call`() = runTest {
        val calls = calls()

        calls.onPosted("com.example.dialer", "telecom_incoming_channel3", key = "k1")

        assertEquals(CallState.Idle, calls.state.value)
    }

    @Test
    fun `a missed call is a notice about a call that is over, not a call`() = runTest {
        val calls = calls()

        calls.onPosted("com.domonap.app", "telecom_missed_channel3", key = "k1")

        assertEquals(CallState.Idle, calls.state.value)
    }

    @Test
    fun `the call ends when the notification goes away`() = runTest {
        val calls = calls()

        calls.state.test {
            assertEquals(CallState.Idle, awaitItem())

            calls.onPosted("com.domonap.app", "telecom_incoming_channel3", key = "k1")
            assertEquals(CallState.Active(since = clock), awaitItem())

            calls.onRemoved("k1")
            assertEquals(CallState.Idle, awaitItem())
        }
    }

    @Test
    fun `the call stays up while Domonap swaps the ringing notification for the ongoing one`() = runTest {
        val calls = calls()
        val started = clock

        calls.onPosted("com.domonap.app", "telecom_incoming_channel3", key = "ringing")
        clock = clock.plusSeconds(4)
        calls.onPosted("com.domonap.app", "telecom_ongoing_channel3", key = "talking")
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

        calls.onPosted("com.domonap.app", "telecom_incoming_channel3", key = "k1")
        clock = clock.plusSeconds(9)
        calls.onPosted("com.domonap.app", "telecom_incoming_channel3", key = "k1")

        assertEquals(CallState.Active(since = started), calls.state.value)
    }

    @Test
    fun `a call that ended while the listener was unbound does not strand the panel`() = runTest {
        val calls = calls()
        calls.onPosted("com.domonap.app", "telecom_incoming_channel3", key = "k1")
        assertEquals(CallState.Active(since = clock), calls.state.value)

        // Unbound across the end of the call, so the removal of k1 was never delivered; on rebind
        // the listener replays what is actually up, which is nothing.
        calls.onListenerReconnected()

        assertEquals(CallState.Idle, calls.state.value)
    }

    @Test
    fun `a call still ringing when the listener rebinds is picked back up`() = runTest {
        val calls = calls()
        calls.onPosted("com.domonap.app", "telecom_incoming_channel3", key = "k1")

        calls.onListenerReconnected()
        clock = clock.plusSeconds(30)
        calls.onPosted("com.domonap.app", "telecom_incoming_channel3", key = "k1")

        assertEquals(CallState.Active(since = clock), calls.state.value)
    }

    @Test
    fun `a notification the panel never counted going away does not end a call`() = runTest {
        val calls = calls()

        calls.onPosted("com.domonap.app", "telecom_incoming_channel3", key = "k1")
        calls.onRemoved("some-unrelated-notification")

        assertEquals(CallState.Active(since = clock), calls.state.value)
    }
}
