package ru.domovoy

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Proves the test toolchain works end to end before any vendor code exists:
 * JUnit5 discovery, kotlin.test assertions, coroutines-test, Turbine and MockK.
 * Delete these once real tests cover the same ground.
 */
class ScaffoldTest {
    @Test
    fun `a state flow emits its current value and then each update`() = runTest {
        val state = MutableStateFlow("stale")

        state.test {
            assertEquals("stale", awaitItem())
            state.value = "fresh"
            assertEquals("fresh", awaitItem())
        }
    }

    @Test
    fun `mockk stands in for a reading that would otherwise throw`() {
        val battery = mockk<Battery>()
        every { battery.level() } returns 87

        assertEquals(87, battery.level())
    }
}

private class Battery {
    fun level(): Int = error("a real reading would need the hub")
}
