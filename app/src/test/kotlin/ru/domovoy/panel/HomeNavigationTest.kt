package ru.domovoy.panel

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.domovoy.core.Device
import ru.domovoy.core.DeviceKind
import ru.domovoy.panelLightScheme
import ru.domovoy.panelTypography
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `intercom stays reachable after scrolling to a distant room`() {
        val scroll = LazyGridState()
        var opened: String? = null
        compose.setContent { Panel(scroll = scroll, onOpenApp = { opened = it }) }
        compose.runOnIdle { runBlocking { scroll.scrollToItem(scroll.layoutInfo.totalItemsCount - 1) } }

        compose.onNodeWithContentDescription("Открыть домофон").assertIsDisplayed().performClick()

        assertEquals(Flat.launchers.first().packageName, opened)
    }

    @Test
    fun `room picker jumps to the room heading and home returns to the top`() {
        val scroll = LazyGridState()
        compose.setContent { Panel(scroll = scroll) }
        compose.onNodeWithText("Комнаты").performClick()
        compose.onNodeWithContentDescription("Перейти: Спальня").performClick()
        compose.onNodeWithText("Спальня", useUnmergedTree = true).assertIsDisplayed().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit),
        )
        assertTrue(compose.runOnIdle { scroll.firstVisibleItemIndex } > 0)

        compose.onNodeWithContentDescription("На главную").performClick()

        assertEquals(0, compose.runOnIdle { scroll.firstVisibleItemIndex })
    }

    @Test
    fun `compact home dial has no step buttons and complete controls remain reachable`() {
        compose.setContent { Panel() }
        val tile = compose.onNode(hasText("Кондиционер") and hasText("Зал") and hasClickAction())
        val bounds = tile.getUnclippedBoundsInRoot()
        val dial = compose.onNodeWithContentDescription("Температура").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(kotlin.math.abs((dial.bottom - dial.top).value - 210f) < 0.5f)
        assertTrue(bounds.bottom - bounds.top >= 250.dp && bounds.bottom - bounds.top < 304.dp)
        compose.onNodeWithContentDescription("Увеличить температуру").assertDoesNotExist()
        compose.onNodeWithContentDescription("Уменьшить температуру").assertDoesNotExist()
        tile.performClick()
        compose.onNodeWithText("Температура").assertIsDisplayed()
        compose.onNodeWithContentDescription("Увеличить").assertIsDisplayed()
    }

    @Test
    fun `silently stopped updates remain explained in a read-only device sheet`() {
        val device = Device(id = "test-lock", name = "Замок", room = "Коридор", kind = DeviceKind.Other, onOff = null)
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                Surface {
                    PanelRooms(
                        acs = AcPanelState(), curtains = CurtainPanelState(), strips = LightStripPanelState(),
                        recuperators = RecuperatorPanelState(), bulbs = BulbPanelState(), launchers = emptyList(),
                        discovered = DiscoveredPanelState(listOf(device), lastPolledAt = Flat.NOW.minusSeconds(300)),
                        now = Flat.NOW, yandexInterval = Flat.YANDEX_INTERVAL, tuyaInterval = Flat.TUYA_INTERVAL,
                        openSheet = remember { mutableStateOf<String?>(device.id) },
                    )
                }
            }
        }

        compose.onNodeWithText("Нет обновления: Обновления не поступают").assertIsDisplayed()
        compose.onNodeWithText("Открыть").assertDoesNotExist()
    }

    @Test
    fun `a failed intercom launch remains visible beside its fixed shortcut`() {
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                PanelRooms(
                    acs = Flat.acs, curtains = Flat.curtains, strips = Flat.strips,
                    recuperators = Flat.recuperators, bulbs = Flat.bulbs, launchers = Flat.launchers,
                    now = Flat.NOW, yandexInterval = Flat.YANDEX_INTERVAL, tuyaInterval = Flat.TUYA_INTERVAL,
                    launcherErrors = mapOf(Flat.launchers.first().packageName to "Не удалось открыть приложение"),
                )
            }
        }
        compose.onNodeWithText("Не удалось открыть приложение").assertIsDisplayed()
        compose.onNodeWithContentDescription("Открыть домофон").assertIsDisplayed()
    }

    @Test
    fun `expanded home lamps offer brightness both inline and in their sheet`() {
        val lamp = Flat.bulbs.tiles.first().copy(
            id = "home-dimmer",
            name = "Лампа у окна",
            room = "Коридор",
            isOn = true,
            brightnessPercent = 40.0,
            brightnessBounds = ru.domovoy.core.Bounds(1.0, 100.0, 1.0),
        )
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                PanelRooms(
                    acs = AcPanelState(lastPolledAt = Flat.NOW), curtains = CurtainPanelState(lastPolledAt = Flat.NOW),
                    strips = LightStripPanelState(lastPolledAt = Flat.NOW), recuperators = RecuperatorPanelState(lastPolledAt = Flat.NOW),
                    bulbs = BulbPanelState(tiles = listOf(lamp), lastPolledAt = Flat.NOW), launchers = emptyList(),
                    now = Flat.NOW, yandexInterval = Flat.YANDEX_INTERVAL, tuyaInterval = Flat.TUYA_INTERVAL,
                )
            }
        }
        compose.onNodeWithText("Показать лампы").performClick()
        compose.onNodeWithText(lamp.name).assertIsDisplayed()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(1)
        compose.onRoot().captureRoboImage("src/test/screenshots/panel-home-expanded-dimmer.png")

        compose.onNodeWithText(lamp.name).performClick()

        compose.onNodeWithContentDescription("Яркость").assertIsDisplayed()
    }

    @Composable
    private fun Panel(scroll: LazyGridState = LazyGridState(), onOpenApp: (String) -> Unit = {}) {
        MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
            Surface {
                PanelRooms(
                    acs = Flat.acs, curtains = Flat.curtains, strips = Flat.strips,
                    recuperators = Flat.recuperators, bulbs = Flat.bulbs, launchers = Flat.launchers,
                    now = Flat.NOW, yandexInterval = Flat.YANDEX_INTERVAL, tuyaInterval = Flat.TUYA_INTERVAL,
                    scroll = scroll, onOpenApp = onOpenApp,
                )
            }
        }
    }
}
