package ru.domovoy.panel

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.domovoy.panelLightScheme
import ru.domovoy.panelTypography
import kotlin.math.roundToInt
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardPolishTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `home light keeps its setting label without printing the range`() {
        panel()
        val home = hasAnyAncestor(hasContentDescription("Настроить: Лента, Коридор")) and hasAnyAncestor(hasTestTag("section:Главная"))
        compose.onNode(hasText("Яркость") and home, useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasText("60%") and home, useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasText("1–100 %") and home, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `home light fits a compact control card while power keeps its wall target`() {
        panel()
        val home = hasAnyAncestor(hasTestTag("section:Главная"))
        val card = compose.onNode(hasContentDescription("Настроить: Лента, Коридор") and home).getUnclippedBoundsInRoot()
        val power = compose.onNode(hasContentDescription("Питание: Лента, Коридор") and hasAnyAncestor(hasContentDescription("Настроить: Лента, Коридор")) and home).getUnclippedBoundsInRoot()
        assertTrue((card.bottom - card.top) <= 240.dp, "A simple light should fit within 240 dp: ${(card.bottom - card.top)}")
        assertTrue((power.right - power.left) >= 64.dp && (power.bottom - power.top) >= 64.dp, "Power target: $power")
    }

    @Test
    @Config(qualifiers = "w375dp-h851dp-port-340dpi")
    fun `room controls keep usable width with large system text`() {
        panel(room = "Спальня", fontScale = 1.5f)
        val slider = compose.onNode(hasContentDescription("Яркость") and hasAnyAncestor(hasTestTag("section:Спальня"))).getUnclippedBoundsInRoot()
        assertTrue((slider.right - slider.left) >= 180.dp, "A room slider needs usable width: ${(slider.right - slider.left)}")
        assertTrue((slider.bottom - slider.top).value.roundToInt() >= 64, "Slider target: $slider")
        compose.onRoot().captureRoboImage("src/test/screenshots/panel-room-narrow.png")
    }

    @Test
    fun `a prominent slider does not present its minimum as an unknown reading`() {
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                LevelControl(
                    id = "unknown-light",
                    value = null,
                    bounds = ru.domovoy.core.Bounds(1.0, 100.0, 1.0),
                    onSetValue = {},
                    label = "Яркость",
                    prominentValue = true,
                )
            }
        }
        compose.onNodeWithText("—").assertIsDisplayed()
        compose.onNodeWithText("1%").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w375dp-h851dp-port-340dpi")
    fun `large text leaves breathing room between the brand and clock`() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                    PanelNavigation(
                        intercom = null,
                        rooms = emptyList(),
                        roomsOpen = false,
                        onRoomsOpen = {},
                        onHome = {},
                        onRoom = {},
                        onOpenApp = {},
                        time = "12:34",
                    )
                }
            }
        }
        val brand = compose.onNodeWithContentDescription("На главную").getUnclippedBoundsInRoot()
        val clock = compose.onNodeWithText("12:34").getUnclippedBoundsInRoot()
        assertTrue(clock.left - brand.right >= 16.dp || clock.top >= brand.bottom)
    }

    @Test
    @Config(qualifiers = "w705dp-h1204dp-port-340dpi")
    fun `room fan actions retain wall sized targets between tablet breakpoints`() {
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                Surface {
                    PanelRooms(
                        acs = AcPanelState(lastPolledAt = Flat.NOW),
                        curtains = CurtainPanelState(lastPolledAt = Flat.NOW),
                        strips = LightStripPanelState(lastPolledAt = Flat.NOW),
                        recuperators = Flat.recuperators.copy(tiles = listOf(Flat.recuperators.tiles.first().copy(room = "Спальня"))),
                        bulbs = BulbPanelState(lastPolledAt = Flat.NOW),
                        launchers = emptyList(), now = Flat.NOW,
                        yandexInterval = Flat.YANDEX_INTERVAL, tuyaInterval = Flat.TUYA_INTERVAL,
                    )
                }
            }
        }
        compose.onNodeWithText("Комнаты").performClick()
        compose.onNodeWithContentDescription("Перейти: Спальня").performClick()
        val room = hasAnyAncestor(hasTestTag("section:Спальня"))
        compose.onNode(hasContentDescription("Показать бризеры: Бризеры, Спальня") and room).performClick()
        listOf("Низкая", "Средняя", "Высокая").forEach { speed ->
            val action = compose.onNode(hasText(speed) and room).getUnclippedBoundsInRoot()
            assertTrue((action.right - action.left).value.roundToInt() >= 64, "Fan action target: $action")
            assertTrue((action.bottom - action.top).value.roundToInt() >= 64)
        }
    }

    private fun panel(room: String = "Коридор", fontScale: Float = 1f) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                    Surface {
                        PanelRooms(
                            acs = AcPanelState(lastPolledAt = Flat.NOW),
                            curtains = CurtainPanelState(lastPolledAt = Flat.NOW),
                            strips = Flat.strips.copy(tiles = listOf(Flat.strips.tiles.first().copy(room = room))),
                            recuperators = RecuperatorPanelState(lastPolledAt = Flat.NOW),
                            bulbs = BulbPanelState(lastPolledAt = Flat.NOW),
                            launchers = emptyList(), now = Flat.NOW,
                            yandexInterval = Flat.YANDEX_INTERVAL, tuyaInterval = Flat.TUYA_INTERVAL,
                        )
                    }
                }
            }
        }
    }
}
