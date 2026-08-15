package ru.domovoy.panel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * A wall panel is walked up to by someone who did not leave it where it is, so it goes back to
 * Главная by itself. The clock is here rather than inside a composable for the same reason
 * [pollPausingForCalls]'s is: a clock that cannot be advanced in a test is a clock nobody checks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IdleResetTest {
    private val timeout = 2.minutes

    @Test
    fun `fires once the timeout has passed with nothing touched`() {
        runTest {
            val touches = MutableSharedFlow<Unit>()
            var resets = 0
            backgroundScope.launch { resetAfterIdle(touches, timeout) { resets++ } }
            runCurrent()

            advanceTimeBy(timeout - 1.seconds)
            runCurrent()
            assertEquals(0, resets)

            advanceTimeBy(1.seconds)
            runCurrent()
            assertEquals(1, resets)
        }
    }

    @Test
    fun `does not fire while the panel is being touched`() {
        runTest {
            val touches = MutableSharedFlow<Unit>()
            var resets = 0
            backgroundScope.launch { resetAfterIdle(touches, timeout) { resets++ } }
            runCurrent()

            // Somebody standing at the wall, a touch every half a timeout for three timeouts'
            // worth of time. The tab they are looking at is the one they chose.
            repeat(6) {
                advanceTimeBy(timeout / 2)
                runCurrent()
                touches.emit(Unit)
                runCurrent()
            }

            assertEquals(0, resets)
        }
    }

    @Test
    fun `restarts its clock on each touch`() {
        runTest {
            val touches = MutableSharedFlow<Unit>()
            var resets = 0
            backgroundScope.launch { resetAfterIdle(touches, timeout) { resets++ } }
            runCurrent()

            advanceTimeBy(90.seconds)
            runCurrent()
            touches.emit(Unit)
            runCurrent()

            // 90 s before the touch and 90 s after it is three minutes on the panel and half a
            // timeout since the hand left it.
            advanceTimeBy(90.seconds)
            runCurrent()
            assertEquals(0, resets)

            advanceTimeBy(30.seconds)
            runCurrent()
            assertEquals(1, resets)
        }
    }

    @Test
    fun `fires again after the next touch goes idle`() {
        runTest {
            val touches = MutableSharedFlow<Unit>()
            var resets = 0
            backgroundScope.launch { resetAfterIdle(touches, timeout) { resets++ } }
            runCurrent()

            advanceTimeBy(timeout)
            runCurrent()
            assertEquals(1, resets)

            touches.emit(Unit)
            runCurrent()
            advanceTimeBy(timeout)
            runCurrent()

            assertEquals(2, resets)
        }
    }
}
