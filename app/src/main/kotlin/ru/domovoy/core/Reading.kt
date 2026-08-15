package ru.domovoy.core

import java.time.Instant
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * When a capability was last read.
 *
 * Yandex reports `0.0` for a capability that has never reported — 33 of the 116 capabilities in
 * the recorded `/v1.0/user/info` response, and every capability on 5 devices. That is [Never],
 * not the epoch: a tile that formats it as a date shows *1 Jan 1970*.
 */
sealed interface Reading {
    /** The capability has never reported a value. */
    data object Never : Reading

    /** The capability last reported at [instant]. */
    data class At(val instant: Instant) : Reading

    companion object {
        /** Yandex sends unix seconds as a float; `0.0` means "never", not 1 Jan 1970. */
        fun ofEpochSeconds(seconds: Double): Reading {
            if (!seconds.isFinite() || seconds <= 0.0) return Never
            val whole = floor(seconds).toLong()
            val nanos = ((seconds - whole) * 1_000_000_000L).roundToLong()
            return At(Instant.ofEpochSecond(whole, nanos))
        }
    }
}
