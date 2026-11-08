package ru.domovoy.kiosk

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
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
class KioskSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `unprepared tablet explains setup and cannot enable protection`() {
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                KioskSettings(KioskState(problem = "Нужна однократная настройка планшета через компьютер."), {}, {}, {})
            }
        }
        compose.onNodeWithText("Нужна однократная настройка планшета через компьютер.").assertIsDisplayed()
        compose.onNodeWithText("Включить защиту").assertIsNotEnabled()
    }

    @Test
    fun `enabled protection offers exit and close as separate actions`() {
        var exited = 0
        var closed = 0
        compose.setContent {
            MaterialTheme(colorScheme = panelDarkScheme, typography = panelTypography) {
                KioskSettings(KioskState(enabled = true, locked = true), {}, { exited++ }, { closed++ })
            }
        }
        compose.onNodeWithText("Выйти из панели").assertIsDisplayed()
        compose.onNodeWithText("Выйти из панели").performClick()
        compose.onNodeWithText("Закрыть").performClick()
        assertEquals(1, exited)
        assertEquals(1, closed)
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-port-340dpi")
    fun `setup and action labels remain reachable on a narrow screen with larger text`() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f)) {
                MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                    KioskSettings(KioskState(), {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("Включить защиту").assertIsDisplayed()
        compose.onNodeWithText("Закрыть").assertIsDisplayed()
    }
}
