package ru.domovoy.integrations.domonap

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DomonapCallsTest {
    private fun DomonapCalls.ring(key: String = "k1") = onPosted("com.domonap.app", "telecom_incoming_channel3", key)

    @Test
    fun `an incoming call from Domonap puts the panel in a call`() {
        val calls = DomonapCalls()

        calls.ring()

        assertEquals(CallState.Active, calls.state.value)
    }

    @Test
    fun `an ongoing call is picked up when the listener binds`() {
        val calls = DomonapCalls()

        calls.onPosted("com.domonap.app", "telecom_ongoing_channel3", "talking")

        assertEquals(CallState.Active, calls.state.value)
    }

    @Test
    fun `another app ringing on a channel of the same name is ignored`() {
        val calls = DomonapCalls()

        calls.onPosted("com.example.dialer", "telecom_incoming_channel3", "k1")

        assertEquals(CallState.Idle, calls.state.value)
    }

    @Test
    fun `missed and silent notifications do not pause the panel`() {
        val calls = DomonapCalls()

        calls.onPosted("com.domonap.app", "telecom_missed_channel3", "missed")
        calls.onPosted("com.domonap.app", "telecom_silent_channel3", "silent")

        assertEquals(CallState.Idle, calls.state.value)
    }

    @Test
    fun `the call ends when the notification goes away`() = runTest {
        val calls = DomonapCalls()

        calls.state.test {
            assertEquals(CallState.Idle, awaitItem())
            calls.ring()
            assertEquals(CallState.Active, awaitItem())
            calls.onRemoved("k1")
            assertEquals(CallState.Idle, awaitItem())
        }
    }

    @Test
    fun `the call stays up while Domonap replaces ringing with an ongoing notification`() {
        val calls = DomonapCalls()
        calls.ring("ringing")
        calls.onPosted("com.domonap.app", "telecom_ongoing_channel3", "talking")

        calls.onRemoved("ringing")
        assertEquals(CallState.Active, calls.state.value)
        calls.onRemoved("talking")
        assertEquals(CallState.Idle, calls.state.value)
    }

    @Test
    fun `reposts of a notification are still one call`() {
        val calls = DomonapCalls()
        calls.ring()
        calls.ring()

        calls.onRemoved("k1")

        assertEquals(CallState.Idle, calls.state.value)
    }

    @Test
    fun `a call that ended while unbound does not strand the panel`() {
        val calls = DomonapCalls()
        calls.ring()

        calls.onListenerReconnected()

        assertEquals(CallState.Idle, calls.state.value)
    }

    @Test
    fun `a call still ringing on reconnection is picked back up`() {
        val calls = DomonapCalls()
        calls.ring()
        calls.onListenerReconnected()

        calls.ring()

        assertEquals(CallState.Active, calls.state.value)
    }

    @Test
    fun `removing an unrelated notification does not end the call`() {
        val calls = DomonapCalls()
        calls.ring()

        calls.onRemoved("unrelated")

        assertEquals(CallState.Active, calls.state.value)
    }
}
