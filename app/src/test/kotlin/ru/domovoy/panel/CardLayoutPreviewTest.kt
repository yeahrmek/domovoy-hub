package ru.domovoy.panel

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
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
import ru.domovoy.panelDarkScheme
import ru.domovoy.panelLightScheme
import ru.domovoy.panelTypography

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalRoborazziApi::class)
class CardLayoutPreviewTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `home light`() = capture("home-light")

    @Test
    fun `home dark`() = capture("home-dark", dark = true)

    @Test
    fun `home dragging`() = capture("home-dragging", dragging = true)

    @Test
    fun `home dragging dark`() = capture("home-dragging-dark", dark = true, dragging = true)

    @Test
    @Config(qualifiers = "w393dp-h851dp-port-340dpi")
    fun `home narrow`() = capture("home-narrow")

    @Test
    @Config(qualifiers = "w1204dp-h753dp-land-340dpi")
    fun `home landscape`() = capture("home-landscape")

    @Test
    fun `home large text`() = capture("home-large-text", fontScale = 1.5f)

    @Test
    fun `room matches home`() = capture("room", room = "Зал")

    private fun capture(name: String, dark: Boolean = false, dragging: Boolean = false, fontScale: Float = 1f, room: String? = null) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MaterialTheme(colorScheme = if (dark) panelDarkScheme else panelLightScheme, typography = panelTypography) {
                    Surface {
                        PanelRooms(
                            modifier = Modifier.fillMaxSize(),
                            acs = Flat.acs, curtains = Flat.curtains, strips = Flat.strips,
                            recuperators = Flat.recuperators, bulbs = Flat.bulbs, launchers = Flat.launchers,
                            now = Flat.NOW, yandexInterval = Flat.YANDEX_INTERVAL, tuyaInterval = Flat.TUYA_INTERVAL,
                        )
                    }
                }
            }
        }
        if (dragging) {
            val home = hasAnyAncestor(hasTestTag("section:Главная"))
            val source = compose.onNode(hasContentDescription("Показать бризеры: Бризеры, Все комнаты") and home)
            val from = source.getUnclippedBoundsInRoot()
            val to = compose.onNode(hasContentDescription("Настроить: Кондиционер, Зал") and home).getUnclippedBoundsInRoot()
            source.performTouchInput {
                down(center)
                moveTo(center, delayMillis = 700)
                moveTo(center + Offset((to.left - from.left).toPx(), (to.top - from.top).toPx()), delayMillis = 200)
            }
        }
        if (room != null) {
            compose.onNodeWithText("Комнаты").performClick()
            compose.onNodeWithContentDescription("Перейти: $room").performClick()
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        if (dragging) {
            compose.onRoot().captureRoboImage("build/outputs/card-reordering/$name.png", roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        } else {
            compose.onRoot().captureRoboImage("src/test/screenshots/cards-$name.png")
        }
    }
}
