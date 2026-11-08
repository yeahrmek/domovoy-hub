package ru.domovoy.panel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class CardEditorTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `starting and finishing an unchanged drag does not recompose the card`() {
        compose.setContent { MaterialTheme { Card() } }
        val before = compose.runOnIdle { Recomposer.runningRecomposers.value.sumOf { it.changeCount } }
        compose.onNodeWithText("Лампа").performTouchInput {
            down(center)
            moveTo(center, delayMillis = 700)
            moveBy(Offset(1.dp.toPx(), 0f), delayMillis = 16)
        }
        compose.onRoot().performTouchInput { up() }
        val after = compose.runOnIdle { Recomposer.runningRecomposers.value.sumOf { it.changeCount } }
        assertEquals(0L, after - before)
    }

    @Test
    fun `normal cards have no editing controls`() {
        compose.setContent { MaterialTheme { Card() } }
        compose.onNodeWithText("Лампа").assertExists()
        compose.onNodeWithContentDescription("Добавить в избранное: Лампа").assertDoesNotExist()
        compose.onNodeWithContentDescription("Перетащить: Лампа").assertDoesNotExist()
    }

    @Test
    fun `cards expose accessible favourite and movement alternatives to dragging`() {
        var favourites = 0
        var up = 0
        var down = 0
        compose.setContent {
            MaterialTheme {
                Card(onFavourite = { favourites++ }, onMoveUp = { up++ }, onMoveDown = { down++ })
            }
        }
        compose.onNodeWithText("Лампа").performCustomAccessibilityActionWithLabel("Добавить в избранное: Лампа")
        compose.onNodeWithText("Лампа").performCustomAccessibilityActionWithLabel("Выше: Лампа")
        compose.onNodeWithText("Лампа").performCustomAccessibilityActionWithLabel("Ниже: Лампа")
        assertEquals(listOf(1, 1, 1), listOf(favourites, up, down))
    }

    @Test
    fun `favourite cards offer removal`() {
        var removed = false
        compose.setContent { MaterialTheme { Card(favourite = true, onFavourite = { removed = true }) } }
        compose.onNodeWithText("Лампа").performCustomAccessibilityActionWithLabel("Убрать из избранного: Лампа")
        assertEquals(true, removed)
    }

    @Test
    fun `long pressing a card onto another card drops its key`() {
        val bounds = mutableMapOf<String, Rect>()
        var dropped = 0
        val positions = mutableListOf<Offset?>()
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(350.dp)) {
                    Card(cardKey = "one", label = "Первая", bounds = bounds, onDrop = { dropped++ }, onDragPosition = { positions.add(it) })
                    Card(cardKey = "two", label = "Вторая", bounds = bounds)
                }
            }
        }
        val handle = compose.onNodeWithText("Первая")
        val handleBounds = handle.getUnclippedBoundsInRoot()
        val destination = compose.onNodeWithText("Вторая").getUnclippedBoundsInRoot()
        handle.performTouchInput {
            down(center)
            moveTo(center, delayMillis = 700)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 24.dp.toPx()), delayMillis = 100)
            moveTo(androidx.compose.ui.geometry.Offset(((destination.left + destination.right) / 2 - handleBounds.left).toPx(), ((destination.top + destination.bottom) / 2 - handleBounds.top).toPx()), delayMillis = 200)
            up()
        }
        assertEquals(1, dropped)
        assertTrue(positions.any { it != null })
        assertEquals(null, positions.last())
    }

    @Test
    fun `a dragged card stays under the finger when its section scrolls`() {
        var scrollShift by mutableStateOf(0.dp)
        compose.setContent {
            MaterialTheme {
                Box(Modifier.padding(top = 120.dp).offset(y = scrollShift).width(350.dp)) {
                    Card()
                }
            }
        }
        val handle = compose.onNodeWithText("Лампа")
        handle.performTouchInput {
            down(center)
            moveTo(center, delayMillis = 700)
            moveBy(Offset(0f, 24.dp.toPx()), delayMillis = 100)
        }
        val before = compose.onNodeWithText("Лампа").getUnclippedBoundsInRoot().top
        compose.runOnIdle { scrollShift = (-40).dp }
        val after = compose.onNodeWithText("Лампа").getUnclippedBoundsInRoot().top
        assertEquals(before, after)
        handle.performTouchInput { up() }
    }

    @Test
    fun `release settles the card into its slot without an immediate jump`() {
        compose.setContent { MaterialTheme { Card() } }
        val card = compose.onNodeWithText("Лампа")
        val initial = card.getUnclippedBoundsInRoot()
        card.performTouchInput {
            down(center)
            moveTo(center, delayMillis = 700)
            moveBy(Offset(60.dp.toPx(), 0f), delayMillis = 100)
        }
        val dragged = card.getUnclippedBoundsInRoot()
        compose.mainClock.autoAdvance = false
        compose.onRoot().performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()
        val released = card.getUnclippedBoundsInRoot()
        assertTrue(released.left > initial.left && released.left <= dragged.left, "Release should start from the floating position")
        compose.mainClock.advanceTimeBy(80)
        val moving = card.getUnclippedBoundsInRoot()
        assertTrue(moving.left >= initial.left && moving.left < released.left)
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(initial, card.getUnclippedBoundsInRoot())
    }

    @Test
    fun `holding a normal card permits dragging without the edit bar`() {
        val bounds = mutableMapOf<String, Rect>()
        var dropped = 0
        val positions = mutableListOf<Offset?>()
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(350.dp)) {
                    Card(cardKey = "one", label = "Первая", bounds = bounds, onDrop = { dropped++ }, onDragPosition = { positions.add(it) })
                    Card(cardKey = "two", label = "Вторая", bounds = bounds)
                }
            }
        }
        val card = compose.onNodeWithText("Первая")
        val from = card.getUnclippedBoundsInRoot()
        val to = compose.onNodeWithText("Вторая").getUnclippedBoundsInRoot()
        card.performTouchInput {
            down(center)
            moveTo(center, delayMillis = 700)
            moveTo(center + Offset(0f, (to.top - from.top).toPx()), delayMillis = 200)
            cancel()
        }
        assertEquals(0, dropped, "Cancelling a drag must keep the order")
        card.performTouchInput {
            down(center)
            moveTo(center, delayMillis = 700)
            moveTo(center + Offset(0f, (to.top - from.top).toPx()), delayMillis = 200)
            up()
        }
        assertTrue(positions.any { it != null }, "The long press should start dragging")
        assertEquals(1, dropped)
    }

    @Test
    fun `upward scrolling keeps the drag alive after the source item leaves the viewport`() {
        val scroll = LazyListState(firstVisibleItemIndex = 8)
        val positions = mutableListOf<Offset?>()
        val bounds = mutableMapOf<String, Rect>()
        var dropped = 0
        compose.setContent {
            MaterialTheme {
                LazyColumn(Modifier.width(350.dp).height(320.dp), state = scroll) {
                    items((0..23).toList(), key = { it }) { index ->
                        Card(
                            cardKey = "$index",
                            label = "Карточка $index",
                            bounds = bounds,
                            onDrop = { dropped++ },
                            onDragPosition = { positions.add(it) },
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("Карточка 9").performTouchInput {
            down(center)
            moveTo(center, delayMillis = 700)
            moveBy(Offset(0f, -40.dp.toPx()), delayMillis = 100)
        }
        assertTrue(positions.last() != null)
        compose.runOnIdle { runBlocking { scroll.scrollToItem(0) } }
        assertTrue(positions.last() != null, "Scrolling past the source must not cancel the drag")
        compose.onRoot().performTouchInput { up() }
        assertEquals(1, dropped)
        assertEquals(null, positions.last())
    }

    @Composable
    private fun Card(
        cardKey: String = "lamp",
        label: String = "Лампа",
        favourite: Boolean = false,
        bounds: MutableMap<String, Rect> = mutableMapOf(),
        onFavourite: () -> Unit = {},
        onMoveUp: () -> Unit = {},
        onMoveDown: () -> Unit = {},
        onDrop: () -> Unit = {},
        onDragPosition: (Offset?) -> Unit = {},
    ) {
        EditablePanelCard(cardKey, label, favourite, true, true, onFavourite, onMoveUp, onMoveDown, onDrop, bounds, onDragPosition = onDragPosition) { actions ->
            Text(label, Modifier.height(100.dp).then(actions))
        }
    }
}

internal fun hasCardAction(label: String): SemanticsMatcher = SemanticsMatcher("Card action: $label") {
    it.config.getOrNull(SemanticsActions.CustomActions)?.any { action -> action.label == label } == true
}
