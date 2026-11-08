package ru.domovoy.panel

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.domovoy.panelDarkScheme
import ru.domovoy.panelLightScheme
import ru.domovoy.panelTypography
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeviceRefreshUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `refresh stays visible and communicates loading failure and successful retry`() {
        var state by mutableStateOf(DeviceRefreshState())
        var clicks = 0
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                PanelRooms(
                    acs = Flat.acs, curtains = Flat.curtains, strips = Flat.strips,
                    recuperators = Flat.recuperators, bulbs = Flat.bulbs, launchers = Flat.launchers,
                    now = Flat.NOW, yandexInterval = Flat.YANDEX_INTERVAL, tuyaInterval = Flat.TUYA_INTERVAL,
                    onPanelAccess = {},
                    deviceRefresh = state,
                    onRefreshDevices = {
                        clicks++
                        state = DeviceRefreshState(refreshing = true)
                    },
                )
            }
        }
        compose.onNodeWithContentDescription("Обновить устройства").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Обновляем устройства…").assertIsNotEnabled()
        compose.onNodeWithText("Обновить устройства").assertDoesNotExist()
        assertEquals(1, clicks)
        state = DeviceRefreshState(error = "Нет связи")
        compose.onNodeWithText("Не удалось обновить устройства: Нет связи. Нажмите ещё раз.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Обновить устройства").performClick()
        assertEquals(2, clicks)
        state = DeviceRefreshState(updated = true)
        compose.onNodeWithText("Устройства обновлены").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/device-refresh/panel.png")
    }

    @Test
    @Config(qualifiers = "w393dp-h851dp-port-340dpi")
    fun `refresh and navigation fit on a narrow screen`() = checkLayout(dark = false, fontScale = 1f, name = "narrow")

    @Test
    fun `loading and navigation fit with large text in dark theme`() = checkLayout(dark = true, fontScale = 1.5f, name = "dark-large-text")

    private fun checkLayout(dark: Boolean, fontScale: Float, name: String) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MaterialTheme(colorScheme = if (dark) panelDarkScheme else panelLightScheme, typography = panelTypography) {
                    Surface {
                        PanelNavigation(
                            intercom = Flat.launchers.first(), rooms = emptyList(), roomsOpen = false,
                            onRoomsOpen = {}, onHome = {}, onRoom = {}, onOpenApp = {}, time = "12:34",
                            onPanelAccess = {}, onRefreshDevices = {}, refreshingDevices = dark,
                        )
                    }
                }
            }
        }
        compose.onNodeWithContentDescription(if (dark) "Обновляем устройства…" else "Обновить устройства").assertIsDisplayed()
        compose.onNodeWithContentDescription("Открыть домофон").assertIsDisplayed()
        compose.onNodeWithText("Комнаты").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/device-refresh/$name.png")
    }
}
