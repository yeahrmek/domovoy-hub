package ru.domovoy.panel

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import ru.domovoy.integrations.domonap.CallState
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/** One read at a time, driven by usage, local night hours, lifecycle and vendor failures. */
class AdaptivePolling(
    private val activeInterval: Duration = 1.minutes,
    private val idleInterval: Duration = 5.minutes,
    private val nightInterval: Duration = 30.minutes,
    private val now: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val read: suspend () -> Boolean,
) {
    private val mutex = Mutex()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var lastTouch: Instant? = null
    private var touchRevision = 0L
    private var servedTouch = 0L
    private var lastFinishedAt: Instant? = null
    private var revision = 0L
    private var failures = 0
    private var allowed: () -> Boolean = { true }
    private var activeRead: Job? = null
    private val mutableInterval = MutableStateFlow(cadence())

    /** The planned cadence, also used by the panel's polling-health indicator. */
    val interval = mutableInterval.asStateFlow()

    /** Movement extends active use; only a new press requests an immediate read. */
    fun touch(refresh: Boolean = true) {
        lastTouch = now()
        if (refresh) touchRevision++
        mutableInterval.value = cadence()
        wake.trySend(Unit)
    }

    /** A command confirmation must start after that command, even if an older read is in flight. */
    suspend fun refresh(force: Boolean = false) {
        if (!allowed()) return
        val before = revision
        mutex.withLock {
            if (!allowed() || (!force && revision != before)) return
            val success = coroutineScope {
                val job = currentCoroutineContext().job
                activeRead = job
                try {
                    read()
                } finally {
                    if (activeRead == job) activeRead = null
                }
            }
            lastFinishedAt = now()
            servedTouch = touchRevision
            failures = if (success) 0 else (failures + 1).coerceAtMost(7)
            revision++
            mutableInterval.value = cadence()
            wake.trySend(Unit)
        }
    }

    /** Hidden panels and intercom calls cancel reads; returning performs one immediate refresh. */
    suspend fun run(foreground: StateFlow<Boolean>, calls: StateFlow<CallState>) {
        allowed = { foreground.value && calls.value is CallState.Idle }
        try {
            combine(foreground, calls) { visible, call -> visible && call is CallState.Idle }
                .distinctUntilChanged()
                .collectLatest { enabled ->
                    if (!enabled) {
                        activeRead?.cancel()
                        return@collectLatest
                    }
                    refresh()
                    while (true) {
                        if (!allowed()) return@collectLatest
                        val interval = cadence()
                        mutableInterval.value = interval
                        val elapsed = lastFinishedAt?.let { (now().toEpochMilli() - it.toEpochMilli()).milliseconds }
                        val remaining = interval - (elapsed ?: interval).coerceAtLeast(Duration.ZERO)
                        if (touchRevision != servedTouch || remaining <= Duration.ZERO) {
                            refresh()
                        } else {
                            // Re-evaluate local time and idle/night boundaries without sending a request.
                            val wait = minOf(remaining, 1.minutes).inWholeMilliseconds.coerceAtLeast(1)
                            withTimeoutOrNull(wait) { wake.receive() }
                        }
                    }
                }
        } finally {
            allowed = { false }
            activeRead?.cancel()
        }
    }

    private fun cadence(): Duration {
        val time = now()
        val active = lastTouch?.let { time < it.plusSeconds(120) } == true
        val night = time.atZone(zone()).hour.let { it >= 23 || it < 7 }
        val base = when {
            active -> activeInterval
            night -> nightInterval
            else -> idleInterval
        }
        val ceiling = if (night && !active) 2.hours else 30.minutes
        return minOf(base * (1 shl failures), ceiling)
    }
}
