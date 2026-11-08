package ru.domovoy.panel

import androidx.compose.ui.geometry.Rect
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CardDragScrollTest {
    private val viewport = Rect(0f, 100f, 500f, 1100f)

    @Test
    fun `scrolling accelerates towards the edge and stops in the middle`() {
        assertEquals(0f, cardDragScrollVelocity(600f, viewport, 100f))
        val nearTop = cardDragScrollVelocity(190f, viewport, 100f)
        val atTop = cardDragScrollVelocity(100f, viewport, 100f)
        assertTrue(nearTop < 0f && abs(nearTop) < abs(atTop))
        assertEquals(-atTop, cardDragScrollVelocity(1100f, viewport, 100f))
        assertEquals(atTop, cardDragScrollVelocity(-100f, viewport, 100f))
    }

    @Test
    fun `unmeasured viewport has no scrolling velocity`() {
        assertEquals(0f, cardDragScrollVelocity(50f, Rect.Zero, 100f))
        assertEquals(0f, cardDragScrollVelocity(50f, viewport, 0f))
    }
}
