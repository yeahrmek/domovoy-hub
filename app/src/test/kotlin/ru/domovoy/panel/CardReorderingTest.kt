package ru.domovoy.panel

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.domovoy.core.FakeSharedPreferences
import ru.domovoy.panelLightScheme
import ru.domovoy.panelTypography
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalRoborazziApi::class)
class CardReorderingTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `long pressing a normal card reorders it and keeps that order after recreation`() = reorderAfterRestart()

    @Test
    @Config(qualifiers = "w393dp-h851dp-port-340dpi")
    fun `long pressing reorders a single column and survives recreation`() = reorderAfterRestart()

    @Test
    fun `cancelling a live preview restores the original layout and stored order`() = reorderAfterRestart(cancelled = true)

    @Test
    fun `moving inside the same drag slot does not recompose the panel`() {
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                PanelRooms(
                    acs = AcPanelState(lastPolledAt = Flat.NOW), curtains = CurtainPanelState(lastPolledAt = Flat.NOW),
                    strips = LightStripPanelState(lastPolledAt = Flat.NOW), recuperators = RecuperatorPanelState(lastPolledAt = Flat.NOW),
                    bulbs = BulbPanelState(lastPolledAt = Flat.NOW), launchers = listOf(launcherTiles { true }.last()),
                    now = Flat.NOW, yandexInterval = Flat.YANDEX_INTERVAL, tuyaInterval = Flat.TUYA_INTERVAL,
                )
            }
        }
        val card = compose.onNode(hasText("Пылесос") and hasAnyAncestor(hasTestTag("section:Главная")))
        val start = card.getUnclippedBoundsInRoot()
        card.performTouchInput {
            down(center)
            moveTo(center, delayMillis = 700)
            moveBy(Offset(0.5.dp.toPx(), 0f), delayMillis = 16)
        }
        val before = compose.runOnIdle { Recomposer.runningRecomposers.value.sumOf { it.changeCount } }
        repeat(8) { step ->
            compose.onRoot().performTouchInput {
                moveTo(Offset(((start.left + start.right) / 2 + (step + 1).dp).toPx(), ((start.top + start.bottom) / 2).toPx()), delayMillis = 16)
            }
        }
        val after = compose.runOnIdle { Recomposer.runningRecomposers.value.sumOf { it.changeCount } }
        compose.onRoot().performTouchInput { cancel() }
        assertEquals(0L, after - before, "Pointer-only movement should update the graphics layer, without recomposing static cards")
    }

    @Test
    fun `an upward drag crosses full width rows after its source batch leaves the viewport`() {
        val scroll = LazyGridState()
        var saved by mutableStateOf(CardPreferences())
        val vacuum = launcherTiles { true }.last()
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                Surface {
                    PanelRooms(
                        acs = AcPanelState(lastPolledAt = Flat.NOW),
                        curtains = Flat.curtains.copy(tiles = (0..7).map { Flat.curtains.tiles.first().copy(id = "curtain-$it", name = "Штора $it") }),
                        strips = LightStripPanelState(lastPolledAt = Flat.NOW), recuperators = RecuperatorPanelState(lastPolledAt = Flat.NOW),
                        bulbs = BulbPanelState(lastPolledAt = Flat.NOW), launchers = listOf(vacuum),
                        now = Flat.NOW, yandexInterval = Flat.YANDEX_INTERVAL, tuyaInterval = Flat.TUYA_INTERVAL,
                        scroll = scroll, cardPreferences = saved, onCardPreferencesChange = { saved = it },
                    )
                }
            }
        }
        val home = hasAnyAncestor(hasTestTag("section:Главная"))
        compose.onNode(hasText(vacuum.name) and home).performScrollTo().performTouchInput {
            down(center)
            moveTo(center, delayMillis = 700)
            moveBy(Offset(0f, -180.dp.toPx()), delayMillis = 200)
        }
        compose.runOnIdle { runBlocking { scroll.scrollToItem(0) } }
        val destination = compose.onNode(hasContentDescription("Настроить: Штора 0, Зал") and home).getUnclippedBoundsInRoot()
        compose.onRoot().performTouchInput {
            moveTo(Offset(((destination.left + destination.right) / 2).toPx(), ((destination.top + destination.bottom) / 2).toPx()), delayMillis = 200)
            up()
        }
        compose.runOnIdle {
            assertEquals(listOf("launcher:${vacuum.packageName}") + (0..7).map { "curtain:curtain-$it" }, saved.orders["Главная"])
        }
    }

    private fun reorderAfterRestart(cancelled: Boolean = false) {
        val prefs = FakeSharedPreferences()
        var generation by mutableStateOf(0)
        var launchers by mutableStateOf(
            listOf(
                LauncherTileState("example.a", "А первый", "Коридор", true),
                LauncherTileState("example.b", "Б второй", "Коридор", true),
            ),
        )
        val opened = mutableListOf<String>()
        compose.setContent {
            key(generation) {
                val store = remember { CardPreferencesStore(prefs) }
                var saved by remember { mutableStateOf(store.load()) }
                MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                    Surface {
                        PanelRooms(
                            acs = AcPanelState(lastPolledAt = Flat.NOW), curtains = CurtainPanelState(lastPolledAt = Flat.NOW),
                            strips = LightStripPanelState(lastPolledAt = Flat.NOW), recuperators = RecuperatorPanelState(lastPolledAt = Flat.NOW),
                            bulbs = BulbPanelState(lastPolledAt = Flat.NOW), launchers = launchers,
                            now = Flat.NOW, yandexInterval = Flat.YANDEX_INTERVAL, tuyaInterval = Flat.TUYA_INTERVAL,
                            cardPreferences = saved,
                            onCardPreferencesChange = {
                                saved = it
                                store.save(it)
                            },
                            onOpenApp = { opened += it },
                        )
                    }
                }
            }
        }
        val home = hasAnyAncestor(hasTestTag("section:Главная"))
        val first = compose.onNode(hasText("А первый") and home)
        val second = compose.onNode(hasText("Б второй") and home)
        first.performClick()
        assertEquals(listOf("example.a"), opened)
        opened.clear()
        val from = first.getUnclippedBoundsInRoot()
        val to = second.getUnclippedBoundsInRoot()
        val destinationSlot = compose.onNode(hasTestTag("card-slot:launcher:example.b") and home).getUnclippedBoundsInRoot()
        first.performTouchInput {
            down(center)
            moveTo(center, delayMillis = 700)
            moveTo(center + Offset((to.left - from.left).toPx(), (to.top - from.top).toPx()), delayMillis = 200)
        }
        val slot = compose.onNodeWithTag("drop-slot:launcher:example.a").getUnclippedBoundsInRoot()
        assertEquals(destinationSlot, slot)
        assertEquals(from.left, second.getUnclippedBoundsInRoot().left)
        assertEquals(from.top, second.getUnclippedBoundsInRoot().top)
        assertEquals(to.left, first.getUnclippedBoundsInRoot().left, "The dragged card must stay under the finger")
        assertEquals(to.top, first.getUnclippedBoundsInRoot().top)
        compose.onRoot().performTouchInput {
            repeat(3) {
                moveTo(Offset(((to.left + to.right) / 2).toPx(), ((to.top + to.bottom) / 2).toPx()), delayMillis = 100)
            }
        }
        assertEquals(slot, compose.onNodeWithTag("drop-slot:launcher:example.a").getUnclippedBoundsInRoot(), "A stationary finger must not make the preview oscillate")
        if (!cancelled) {
            compose.onRoot().captureRoboImage(
                "build/outputs/card-reordering/preview-${if (destinationSlot.left > 0.dp) "columns" else "narrow"}.png",
                roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
            )
        }
        assertEquals(listOf("launcher:example.a", "launcher:example.b"), CardPreferencesStore(prefs).load().orders["Главная"], "Preview must not save before release")
        compose.onRoot().performTouchInput { if (cancelled) cancel() else up() }
        val expected = if (cancelled) listOf("launcher:example.a", "launcher:example.b") else listOf("launcher:example.b", "launcher:example.a")
        assertEquals(expected, CardPreferencesStore(prefs).load().orders["Главная"])
        assertTrue(opened.isEmpty(), "Reordering must not open the vendor app")
        compose.runOnIdle {
            launchers = launchers.reversed().map { if (it.packageName == "example.b") it.copy(name = "Я второй") else it }
            generation++
        }
        val restoredFirst = compose.onNode(hasText("Я второй") and home).getUnclippedBoundsInRoot()
        val restoredSecond = first.getUnclippedBoundsInRoot()
        if (cancelled) {
            assertTrue(restoredSecond.left < restoredFirst.left || restoredSecond.top < restoredFirst.top)
        } else {
            assertTrue(restoredFirst.left < restoredSecond.left || restoredFirst.top < restoredSecond.top)
        }
        assertEquals(listOf("launcher:example.a", "launcher:example.b"), CardPreferencesStore(prefs).load().orders["Коридор"])
    }
}
