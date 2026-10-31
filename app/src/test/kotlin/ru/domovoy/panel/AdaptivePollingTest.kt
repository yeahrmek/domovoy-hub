package ru.domovoy.panel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.domovoy.integrations.domonap.CallState
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class AdaptivePollingTest {
    private val day = Instant.parse("2026-10-31T12:00:00Z")
    private val zone = ZoneId.of("Europe/Moscow")

    @Test
    fun `an untouched panel reads immediately and then every five minutes`() = runTest {
        val reads = mutableListOf<Long>()
        val polling = AdaptivePolling(now = { day.plusMillis(testScheduler.currentTime) }, zone = { zone }) {
            reads += testScheduler.currentTime
            true
        }
        backgroundScope.launch { polling.run(MutableStateFlow(true), MutableStateFlow(CallState.Idle)) }
        runCurrent()
        advanceTimeBy(5.minutes)
        runCurrent()
        assertEquals(listOf(0L, 300_000L), reads)
        assertEquals(5.minutes, polling.interval.value)
    }

    @Test
    fun `touch reads immediately then every minute and quiet returns to five minutes`() = runTest {
        val reads = mutableListOf<Long>()
        val polling = AdaptivePolling(now = { day.plusMillis(testScheduler.currentTime) }, zone = { zone }) {
            reads += testScheduler.currentTime
            true
        }
        backgroundScope.launch { polling.run(MutableStateFlow(true), MutableStateFlow(CallState.Idle)) }
        runCurrent()
        advanceTimeBy(10.seconds)
        polling.touch()
        runCurrent()
        advanceTimeBy(1.minutes)
        runCurrent()
        assertEquals(listOf(0L, 10_000L, 70_000L), reads)
        assertEquals(1.minutes, polling.interval.value)
        advanceTimeBy(1.minutes)
        runCurrent()
        assertEquals(5.minutes, polling.interval.value)
        assertEquals(3, reads.size)
        advanceTimeBy(4.minutes)
        runCurrent()
        assertEquals(listOf(0L, 10_000L, 70_000L, 370_000L), reads)
    }

    @Test
    fun `gesture movement keeps the panel active without issuing a request per pointer event`() = runTest {
        var reads = 0
        val polling = AdaptivePolling(now = { day.plusMillis(testScheduler.currentTime) }, zone = { zone }) {
            reads++
            true
        }
        backgroundScope.launch { polling.run(MutableStateFlow(true), MutableStateFlow(CallState.Idle)) }
        runCurrent()
        polling.touch()
        runCurrent()
        repeat(130) {
            advanceTimeBy(1.seconds)
            polling.touch(refresh = false)
            runCurrent()
        }
        assertEquals(4, reads, "startup, initial press, and two minute ticks")
        assertEquals(1.minutes, polling.interval.value)
    }

    @Test
    fun `idle night polling is half hourly but touch overrides it`() = runTest {
        val night = Instant.parse("2026-10-31T21:00:00Z")
        val reads = mutableListOf<Long>()
        val polling = AdaptivePolling(now = { night.plusMillis(testScheduler.currentTime) }, zone = { zone }) {
            reads += testScheduler.currentTime
            true
        }
        backgroundScope.launch { polling.run(MutableStateFlow(true), MutableStateFlow(CallState.Idle)) }
        runCurrent()
        assertEquals(30.minutes, polling.interval.value)
        advanceTimeBy(30.minutes)
        runCurrent()
        assertEquals(listOf(0L, 1_800_000L), reads)
        advanceTimeBy(1.seconds)
        polling.touch()
        runCurrent()
        assertEquals(1.minutes, polling.interval.value)
        advanceTimeBy(1.minutes)
        runCurrent()
        assertEquals(listOf(0L, 1_800_000L, 1_801_000L, 1_861_000L), reads)
        advanceTimeBy(1.minutes)
        runCurrent()
        assertEquals(30.minutes, polling.interval.value)
        assertEquals(4, reads.size)
    }

    @Test
    fun `night and morning boundaries change cadence without a touch or restart`() = runTest {
        val beforeNight = Instant.parse("2026-10-31T19:59:00Z")
        var reads = 0
        val polling = AdaptivePolling(now = { beforeNight.plusMillis(testScheduler.currentTime) }, zone = { zone }) {
            reads++
            true
        }
        backgroundScope.launch { polling.run(MutableStateFlow(true), MutableStateFlow(CallState.Idle)) }
        runCurrent()
        advanceTimeBy(1.minutes)
        runCurrent()
        assertEquals(30.minutes, polling.interval.value)
        advanceTimeBy(29.minutes)
        runCurrent()
        assertEquals(2, reads)
    }

    @Test
    fun `morning resumes day cadence even while waiting for the next night read`() = runTest {
        val beforeMorning = Instant.parse("2026-11-01T03:55:00Z")
        var reads = 0
        val polling = AdaptivePolling(now = { beforeMorning.plusMillis(testScheduler.currentTime) }, zone = { zone }) {
            reads++
            true
        }
        backgroundScope.launch { polling.run(MutableStateFlow(true), MutableStateFlow(CallState.Idle)) }
        runCurrent()
        advanceTimeBy(5.minutes)
        runCurrent()
        assertEquals(5.minutes, polling.interval.value)
        assertEquals(2, reads)
    }

    @Test
    fun `errors double the interval up to thirty minutes and success resets it`() = runTest {
        val reads = mutableListOf<Long>()
        var succeeds = false
        val polling = AdaptivePolling(now = { day.plusMillis(testScheduler.currentTime) }, zone = { zone }) {
            reads += testScheduler.currentTime
            succeeds
        }
        backgroundScope.launch { polling.run(MutableStateFlow(true), MutableStateFlow(CallState.Idle)) }
        runCurrent()
        assertEquals(10.minutes, polling.interval.value)
        advanceTimeBy(10.minutes)
        runCurrent()
        assertEquals(20.minutes, polling.interval.value)
        advanceTimeBy(20.minutes)
        runCurrent()
        assertEquals(30.minutes, polling.interval.value)
        advanceTimeBy(30.minutes)
        runCurrent()
        assertEquals(30.minutes, polling.interval.value)
        assertEquals(listOf(0L, 600_000L, 1_800_000L, 3_600_000L), reads)
        succeeds = true
        polling.touch()
        runCurrent()
        assertEquals(5, reads.size, "an explicit touch bypasses retry delay")
        assertEquals(1.minutes, polling.interval.value)
    }

    @Test
    fun `night errors back off up to two hours`() = runTest {
        val polling = AdaptivePolling(now = { Instant.parse("2026-10-31T21:00:00Z") }, zone = { zone }) { false }
        polling.refresh()
        assertEquals(1.hours, polling.interval.value)
        repeat(4) { polling.refresh() }
        assertEquals(2.hours, polling.interval.value)
    }

    @Test
    fun `background and calls stop reads including manual refresh then resume immediately`() = runTest {
        var reads = 0
        val foreground = MutableStateFlow(false)
        val calls = MutableStateFlow<CallState>(CallState.Idle)
        val polling = AdaptivePolling(now = { day.plusMillis(testScheduler.currentTime) }, zone = { zone }) {
            reads++
            true
        }
        backgroundScope.launch { polling.run(foreground, calls) }
        runCurrent()
        polling.touch()
        polling.refresh(force = true)
        advanceTimeBy(1.hours)
        runCurrent()
        assertEquals(0, reads)
        foreground.value = true
        runCurrent()
        assertEquals(1, reads)
        calls.value = CallState.Active(since = day)
        runCurrent()
        polling.touch()
        polling.refresh(force = true)
        advanceTimeBy(1.hours)
        runCurrent()
        assertEquals(1, reads)
        calls.value = CallState.Idle
        runCurrent()
        assertEquals(2, reads)
        foreground.value = false
        runCurrent()
        advanceTimeBy(1.hours)
        runCurrent()
        assertEquals(2, reads)
        foreground.value = true
        runCurrent()
        assertEquals(3, reads)
    }

    @Test
    fun `leaving foreground cancels an in-flight command confirmation read`() = runTest {
        val foreground = MutableStateFlow(true)
        var reads = 0
        var cancelled = 0
        var slow = false
        val polling = AdaptivePolling(now = { day.plusMillis(testScheduler.currentTime) }, zone = { zone }) {
            reads++
            if (slow) {
                try {
                    delay(1.hours)
                } finally {
                    cancelled++
                }
            }
            true
        }
        backgroundScope.launch { polling.run(foreground, MutableStateFlow(CallState.Idle)) }
        runCurrent()
        slow = true
        backgroundScope.launch { polling.refresh(force = true) }
        runCurrent()
        foreground.value = false
        runCurrent()
        assertEquals(1, cancelled)
        advanceTimeBy(2.hours)
        runCurrent()
        assertEquals(2, reads)
    }

    @Test
    fun `overlapping ordinary refreshes share a read but post-command confirmation gets a newer one`() = runTest {
        var reads = 0
        val released = CompletableDeferred<Unit>()
        val polling = AdaptivePolling(now = { day }, zone = { zone }) {
            reads++
            if (reads == 1) released.await()
            true
        }
        val first = async { polling.refresh() }
        runCurrent()
        val joined = async { polling.refresh() }
        val confirmation = async { polling.refresh(force = true) }
        runCurrent()
        assertEquals(1, reads)
        released.complete(Unit)
        first.await()
        joined.await()
        confirmation.await()
        assertEquals(2, reads)
    }

    @Test
    fun `command confirmation resets the periodic deadline`() = runTest {
        val reads = mutableListOf<Long>()
        val polling = AdaptivePolling(now = { day.plusMillis(testScheduler.currentTime) }, zone = { zone }) {
            reads += testScheduler.currentTime
            true
        }
        backgroundScope.launch { polling.run(MutableStateFlow(true), MutableStateFlow(CallState.Idle)) }
        runCurrent()
        advanceTimeBy(250.seconds)
        polling.refresh(force = true)
        advanceTimeBy(50.seconds)
        runCurrent()
        assertEquals(listOf(0L, 250_000L), reads)
        advanceTimeBy(250.seconds)
        runCurrent()
        assertEquals(listOf(0L, 250_000L, 550_000L), reads)
    }

    @Test
    fun `a short intercom call cancels the scheduled read and resumes immediately`() = runTest {
        var cancelled = 0
        var reads = 0
        var slow = true
        val calls = MutableStateFlow<CallState>(CallState.Idle)
        val polling = AdaptivePolling(now = { day.plusMillis(testScheduler.currentTime) }, zone = { zone }) {
            reads++
            if (slow) {
                try {
                    awaitCancellation()
                } finally {
                    cancelled++
                }
            }
            true
        }
        backgroundScope.launch { polling.run(MutableStateFlow(true), calls) }
        runCurrent()
        calls.value = CallState.Active(since = day)
        runCurrent()
        assertEquals(1, cancelled)
        advanceTimeBy(1.seconds)
        slow = false
        calls.value = CallState.Idle
        runCurrent()
        assertEquals(2, reads)
        assertEquals(5.minutes, polling.interval.value, "cancellation is not a vendor failure")
    }

    @Test
    fun `a queued touch cannot spin or read after the gate has closed`() = runTest {
        var reads = 0
        val foreground = MutableStateFlow(true)
        val calls = MutableStateFlow<CallState>(CallState.Idle)
        val polling = AdaptivePolling(now = { day.plusMillis(testScheduler.currentTime) }, zone = { zone }) {
            reads++
            true
        }
        backgroundScope.launch { polling.run(foreground, calls) }
        runCurrent()
        advanceTimeBy(1.seconds)
        polling.touch()
        foreground.value = false
        runCurrent()
        assertEquals(1, reads)
        foreground.value = true
        runCurrent()
        assertEquals(2, reads)
        polling.touch()
        calls.value = CallState.Active(since = day)
        runCurrent()
        assertEquals(2, reads)
    }

    @Test
    fun `touch during a read is served by its result without cancelling or repeating it`() = runTest {
        var reads = 0
        val polling = AdaptivePolling(now = { day.plusMillis(testScheduler.currentTime) }, zone = { zone }) {
            reads++
            delay(30.seconds)
            true
        }
        backgroundScope.launch { polling.run(MutableStateFlow(true), MutableStateFlow(CallState.Idle)) }
        runCurrent()
        advanceTimeBy(10.seconds)
        polling.touch()
        runCurrent()
        advanceTimeBy(20.seconds)
        runCurrent()
        assertEquals(1, reads)
    }
}
