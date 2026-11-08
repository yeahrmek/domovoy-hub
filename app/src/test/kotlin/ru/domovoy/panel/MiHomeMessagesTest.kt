package ru.domovoy.panel

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.domovoy.integrations.xiaomi.MiHomeNotification
import ru.domovoy.integrations.xiaomi.MiHomeNotificationStatus
import ru.domovoy.panelDarkScheme
import ru.domovoy.panelLightScheme
import ru.domovoy.panelTypography

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MiHomeMessagesTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `long notification remains readable with large text in light theme`() = capture(dark = false)

    @Test
    fun `long notification remains readable with large text in dark theme`() = capture(dark = true)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(dark: Boolean) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                MaterialTheme(colorScheme = if (dark) panelDarkScheme else panelLightScheme, typography = panelTypography) {
                    androidx.compose.foundation.layout.Box(Modifier.width(350.dp)) {
                        MiHomeMessages(listOf(MiHomeNotification("test", "Робот-пылесос", "Проверьте бак для грязной воды и установите его обратно на станцию", 1794160800000)), MiHomeNotificationStatus.Connected)
                    }
                }
            }
        }
        compose.onNodeWithText("Mi Home · 1 сообщение").performClick()
        compose.onNodeWithText("Проверьте бак для грязной воды и установите его обратно на станцию").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/mihome/notification-${if (dark) "dark" else "light"}.png", roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
    }

    @Test
    fun `messages show their source and open their full text without launching an app`() {
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                MiHomeMessages(
                    listOf(
                        MiHomeNotification("new", "Робот-пылесос", "Проверьте бак для грязной воды", 2000),
                        MiHomeNotification("old", "Увлажнитель", "Закончилась вода", 1000),
                    ),
                    MiHomeNotificationStatus.Connected,
                )
            }
        }
        compose.onNodeWithText("Mi Home · 2 сообщения").performClick()
        compose.onNodeWithText("Проверьте бак для грязной воды").assertExists()
        compose.onNodeWithText("Увлажнитель").assertExists()
        compose.onNodeWithText("Закончилась вода").assertExists()
    }

    @Test
    fun `an empty connected listener does not claim that the vacuum is fine`() {
        compose.setContent { MiHomeMessages(emptyList(), MiHomeNotificationStatus.Connected) }
        compose.onNodeWithText("Mi Home", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Всё в порядке").assertDoesNotExist()
    }

    @Test
    fun `missing access is visible even without messages`() {
        compose.setContent { MiHomeMessages(emptyList(), MiHomeNotificationStatus.AccessRequired) }
        compose.onNodeWithText("Mi Home: разрешите панели доступ к уведомлениям в настройках Android.").assertExists()
    }

    @Test
    fun `lost listener marks retained messages as potentially outdated`() {
        compose.setContent {
            MiHomeMessages(listOf(MiHomeNotification("old", "Пылесос", "Застрял", 1000)), MiHomeNotificationStatus.Disconnected)
        }
        compose.onNodeWithText("Связь с уведомлениями потеряна. Сообщения могут быть неактуальны.").assertExists()
    }
}
