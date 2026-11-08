package ru.domovoy.kiosk

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
    fun `full removal is a separate action available to the current device owner`() {
        var removals = 0
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                KioskSettings(
                    KioskState(deviceOwner = true),
                    {},
                    {},
                    {},
                    onRemoveOwner = { removals++ },
                )
            }
        }
        compose.onNodeWithText("Снять управление планшетом").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, removals)
        compose.onRoot().captureRoboImage("build/outputs/kiosk/remove-owner-light.png")
    }

    @Test
    fun `a panel without ownership does not offer owner removal`() {
        compose.setContent {
            MaterialTheme(colorScheme = panelDarkScheme, typography = panelTypography) {
                KioskSettings(KioskState(), {}, {}, {})
            }
        }
        compose.onNodeWithText("Снять управление планшетом").assertDoesNotExist()
    }

    @Test
    fun `allowed apps are reachable without leaving protection`() {
        val opened = mutableListOf<String>()
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                KioskSettings(KioskState(enabled = true, locked = true), {}, {}, {}, onOpenApp = { opened += it })
            }
        }
        kioskApps.forEach { (packageName, name) ->
            compose.onNodeWithText(name).performScrollTo().assertIsDisplayed().performClick()
            assertEquals(packageName, opened.last())
        }
        compose.onNodeWithText("Domonap").performScrollTo()
        compose.onRoot().captureRoboImage("build/outputs/kiosk/allowed-apps-light.png")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-port-340dpi")
    fun `a launch failure becomes visible after scrolling the app list with larger text`() {
        val error = mutableStateOf<String?>(null)
        val failure = "Не удалось открыть приложение. Проверьте, что оно установлено и включено, затем повторите попытку."
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f)) {
                MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                    KioskSettings(
                        KioskState(enabled = true, locked = true, error = error.value),
                        {},
                        {},
                        {},
                        onOpenApp = { error.value = failure },
                    )
                }
            }
        }
        compose.onNodeWithText("Галерея").performScrollTo().performClick()
        compose.onNodeWithText(failure).assertIsDisplayed()
        compose.onNodeWithText("Галерея").performScrollTo().performClick()
        compose.onNodeWithText(failure).assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/kiosk/launch-error-narrow.png")
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
        compose.onNodeWithText("Галерея").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/kiosk/allowed-apps-narrow.png")
    }
}
