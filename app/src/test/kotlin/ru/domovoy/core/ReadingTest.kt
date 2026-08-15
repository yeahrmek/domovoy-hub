package ru.domovoy.core

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class ReadingTest {
    @Test
    fun `zero means never reported, not the epoch`() {
        assertEquals(Reading.Never, Reading.ofEpochSeconds(0.0))
    }

    @Test
    fun `a float timestamp keeps its sub-second part`() {
        // A double only resolves about 0.2 µs at this magnitude, so the nanos below are what the
        // fixture's 1786790249.4202769 can actually carry — not the digits Yandex printed.
        assertEquals(
            Reading.At(Instant.ofEpochSecond(1_786_790_249, 420_276_880)),
            Reading.ofEpochSeconds(1_786_790_249.4202769),
        )
    }

    @Test
    fun `a whole-second timestamp has no stray nanos`() {
        assertEquals(Reading.At(Instant.ofEpochSecond(1_784_883_564)), Reading.ofEpochSeconds(1_784_883_564.0))
    }
}
