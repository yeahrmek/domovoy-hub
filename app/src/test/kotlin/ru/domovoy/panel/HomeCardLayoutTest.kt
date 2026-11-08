package ru.domovoy.panel

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.domovoy.panelLightScheme
import ru.domovoy.panelTypography
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeCardLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "w393dp-h851dp-port-340dpi")
    fun `fixed shortcuts wrap as buttons rather than breaking their labels`() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f)) {
                MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                    PanelNavigation(
                        intercom = Flat.launchers.first(),
                        rooms = emptyList(),
                        roomsOpen = false,
                        onRoomsOpen = {},
                        onHome = {},
                        onRoom = {},
                        onOpenApp = {},
                    )
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Комнаты").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
    }

    @Test
    @Config(qualifiers = "w393dp-h851dp-port-340dpi")
    fun `a narrow curtain keeps a usable slider with large text`() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f)) {
                MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                    Surface {
                        PanelRooms(
                            acs = AcPanelState(lastPolledAt = Flat.NOW),
                            curtains = Flat.curtains,
                            strips = LightStripPanelState(lastPolledAt = Flat.NOW),
                            recuperators = RecuperatorPanelState(lastPolledAt = Flat.NOW),
                            bulbs = BulbPanelState(lastPolledAt = Flat.NOW),
                            launchers = emptyList(), now = Flat.NOW,
                            yandexInterval = Flat.YANDEX_INTERVAL, tuyaInterval = Flat.TUYA_INTERVAL,
                        )
                    }
                }
            }
        }
        val slider = compose.onNode(
            hasContentDescription("Открытие") and hasAnyAncestor(hasContentDescription("Настроить: Штора, Зал")) and hasAnyAncestor(hasTestTag("section:Главная")),
        ).getUnclippedBoundsInRoot()
        assertTrue(slider.right - slider.left >= 180.dp)
    }

    @Test
    fun `large text leaves a gap between the range label and bounds`() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f)) {
                MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                    LevelControl(
                        id = "curtain",
                        value = 40.0,
                        bounds = ru.domovoy.core.Bounds(0.0, 100.0, 1.0),
                        onSetValue = {},
                        modifier = Modifier.width(210.dp),
                        label = "Открытие",
                    )
                }
            }
        }
        val label = compose.onNodeWithText("Открытие").getUnclippedBoundsInRoot()
        val range = compose.onNodeWithText("0–100 %").getUnclippedBoundsInRoot()
        assertTrue(range.left - label.right >= 8.dp || range.top >= label.bottom)
    }

    @Test
    fun `home restores device pictures and a light does not stretch to its taller neighbour`() {
        val strip = Flat.strips.tiles.first()
        val fan = Flat.recuperators.tiles.first()
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                Surface {
                    PanelRooms(
                        acs = AcPanelState(lastPolledAt = Flat.NOW),
                        curtains = CurtainPanelState(lastPolledAt = Flat.NOW),
                        strips = Flat.strips.copy(tiles = listOf(strip)),
                        recuperators = Flat.recuperators.copy(tiles = listOf(fan)),
                        bulbs = BulbPanelState(lastPolledAt = Flat.NOW),
                        launchers = emptyList(), now = Flat.NOW,
                        yandexInterval = Flat.YANDEX_INTERVAL, tuyaInterval = Flat.TUYA_INTERVAL,
                    )
                }
            }
        }
        val onHome = hasAnyAncestor(hasTestTag("section:Главная"))
        compose.onNode(hasContentDescription("Показать бризеры: Бризеры, Зал") and onHome).performScrollTo().performClick()
        compose.onAllNodes(hasTestTag("device-art") and onHome, useUnmergedTree = true).assertCountEquals(3)
        val light = compose.onNode(hasContentDescription("Настроить: ${strip.name}, ${strip.room}") and onHome).getUnclippedBoundsInRoot()
        val breather = compose.onNode(hasContentDescription("Настроить: ${fan.name}, ${fan.room}") and onHome).getUnclippedBoundsInRoot()
        assertTrue(light.bottom - light.top < breather.bottom - breather.top, "A light must use its own height rather than its neighbour's")
        assertTrue(light.right - light.left < 400.dp)
    }
}
