package ru.domovoy.panel

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
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
import ru.domovoy.core.Bounds
import ru.domovoy.core.Device
import ru.domovoy.core.DeviceKind
import ru.domovoy.core.FloatProperty
import ru.domovoy.core.Mode
import ru.domovoy.core.OnOff
import ru.domovoy.core.Range
import ru.domovoy.core.Reading
import ru.domovoy.panelDarkScheme
import ru.domovoy.panelLightScheme
import ru.domovoy.panelTypography
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HumidifierUiTest {
    @get:Rule
    val compose = createComposeRule()
    private val reading = Reading.At(Flat.NOW)
    private val device = Device(
        "humidifier",
        "Увлажнитель",
        DeviceKind.Other,
        "Спальня",
        OnOff(false, reading),
        vendorType = "devices.types.humidifier",
        modes = mapOf("fan_speed" to Mode("medium", listOf("low", "medium", "high", "turbo"), reading)),
        properties = mapOf(
            "temperature" to FloatProperty(24.0, reading, "unit.temperature.celsius"),
            "humidity" to FloatProperty(30.0, reading, "unit.percent"),
        ),
    )

    @Test
    fun `humidifier hides intensity under a disclosure while power readings and details stay available`() {
        val powerCommands = mutableListOf<String>()
        val modeCommands = mutableListOf<Triple<String, String, String>>()
        render(onToggle = { powerCommands += it }, onMode = { id, instance, value -> modeCommands += Triple(id, instance, value) })
        cardDescription("Питание: Увлажнитель, Спальня").assertIsOff()
            .performClick()
        assertEquals(listOf("humidifier"), powerCommands)
        cardTextNode("Средняя").assertDoesNotExist()
        cardTextNode("Турбо").assertDoesNotExist()
        capture("card-collapsed-light")
        cardDescription("Показать уровни: Увлажнитель, Спальня")
            .assertWidthIsAtLeast(64.dp).assertHeightIsAtLeast(64.dp).performClick()
        compose.onNode(hasText("Средняя") and isSelected() and roomCard).assertExists()
        cardTextNode("Турбо").performClick()
        assertEquals(listOf(Triple("humidifier", "fan_speed", "turbo")), modeCommands)
        val temperature = cardTextNode("24 °C", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val humidity = cardTextNode("30%", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(temperature.top == humidity.top && temperature.right < humidity.left)
        cardTextNode("Температура").assertDoesNotExist()
        cardTextNode("Влажность").assertDoesNotExist()
        capture("card-light")
        cardDescription("Скрыть уровни: Увлажнитель, Спальня").performClick()
        cardTextNode("Турбо").assertDoesNotExist()
        cardTextNode("30%", useUnmergedTree = true).assertExists()
        cardTextNode("Увлажнитель", useUnmergedTree = true).performTouchInput { click() }
        capture("details-light")
        compose.onNodeWithText("Температура").assertExists()
        compose.onNode(hasText("Средняя") and isSelected()).assertExists()
        compose.onNodeWithText("Турбо").assertExists()
        compose.onNodeWithText("Низкая").performClick()
        assertEquals(Triple("humidifier", "fan_speed", "low"), modeCommands.last())
        compose.onNodeWithContentDescription("Питание: Увлажнитель, Спальня").performClick()
        assertEquals(listOf("humidifier", "humidifier"), powerCommands)
    }

    @Test
    @Config(qualifiers = "w375dp-h851dp-port-340dpi")
    fun `humidifier modes wrap with large text and pending commands disable controls in dark theme`() {
        render(dark = true, pending = true, fontScale = 1.5f)
        cardDescription("Питание: Увлажнитель, Спальня").assertIsNotEnabled()
        cardTextNode("Низкая").assertDoesNotExist()
        capture("collapsed-narrow-dark-large-text")
        cardDescription("Показать уровни: Увлажнитель, Спальня").performClick()
        cardTextNode("Низкая").assertIsNotEnabled()
        cardTextNode("Турбо").assertIsNotEnabled()
        val temperature = cardTextNode("24 °C", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val humidity = cardTextNode("30%", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(temperature.top == humidity.top && temperature.right < humidity.left)
        capture("pending-narrow-dark-large-text")
    }

    @Test
    fun `direct humidifier target stays visible while modes are collapsed and details control the same target`() {
        val commands = mutableListOf<Pair<String, Double>>()
        val modes = mutableListOf<Triple<String, String, String>>()
        render(device = directDevice(), onHumidity = { id, target -> commands += id to target }, onMode = { id, instance, value -> modes += Triple(id, instance, value) })
        cardTextNode("Целевая влажность", useUnmergedTree = true).assertExists()
        cardTextNode("45%", useUnmergedTree = true).assertExists()
        cardTextNode("Авто").assertDoesNotExist()
        cardDescription("Целевая влажность").performSemanticsAction(SemanticsActions.SetProgress) { it(55f) }
        assertEquals(listOf("humidifier" to 55.0), commands)
        capture("target-collapsed-light")
        cardDescription("Показать уровни: Увлажнитель, Спальня").performClick()
        compose.onNode(hasText("Авто") and isSelected() and roomCard).assertExists()
        cardTextNode("Низкая").performClick()
        cardTextNode("Авто").performClick()
        assertEquals(Triple("humidifier", "fan_speed", "auto"), modes.last())
        cardTextNode("Увлажнитель", useUnmergedTree = true).performTouchInput { click() }
        compose.onNodeWithContentDescription("Целевая влажность").performSemanticsAction(SemanticsActions.SetProgress) { it(60f) }
        assertEquals("humidifier" to 60.0, commands.last())
        capture("target-details-light")
    }

    @Test
    @Config(qualifiers = "w375dp-h851dp-port-340dpi")
    fun `pending direct target and modes are disabled in dark theme with large text`() {
        render(device = directDevice(), dark = true, pending = true, fontScale = 1.5f)
        cardDescription("Целевая влажность").assertIsNotEnabled()
        cardTextNode("45%", useUnmergedTree = true).assertExists()
        capture("target-collapsed-narrow-dark-large-text")
        cardDescription("Показать уровни: Увлажнитель, Спальня").performClick()
        cardTextNode("Авто").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        capture("target-pending-narrow-dark-large-text")
    }

    @Test
    fun `fresh direct humidifier ignores Yandex failure on card and in details`() {
        val local = directDevice()
        render(
            discovered = DiscoveredPanelState(
                tiles = listOf(local),
                error = "Яндекс недоступен",
                localIds = setOf(local.id),
                localPolledAt = mapOf(local.id to Flat.NOW),
            ),
        )
        cardDescription("Настроить: Увлажнитель, Спальня")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, ""))
        cardTextNode("Увлажнитель", useUnmergedTree = true).performTouchInput { click() }
        compose.onNodeWithText("Нет обновления: Яндекс недоступен").assertDoesNotExist()
        compose.onNodeWithContentDescription("Целевая влажность").assertExists()
    }

    @Test
    fun `direct read failure is retained on the humidifier card and in details`() {
        val local = directDevice()
        render(
            discovered = DiscoveredPanelState(
                tiles = listOf(local),
                localIds = setOf(local.id),
                localPolledAt = mapOf(local.id to Flat.NOW),
                readErrors = mapOf(local.id to "Увлажнитель не ответил"),
            ),
        )
        cardDescription("Настроить: Увлажнитель, Спальня")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Увлажнитель не ответил"))
        cardTextNode("Увлажнитель", useUnmergedTree = true).performTouchInput { click() }
        compose.onNodeWithText("Нет обновления: Увлажнитель не ответил").assertExists()
    }

    @Test
    @Config(qualifiers = "w1204dp-h753dp-land-340dpi")
    fun `unknown climate readings stay unknown and unsupported controls are absent`() {
        render(device = device.copy(properties = emptyMap(), modes = emptyMap(), onOff = null))
        cardDescription("Температура: —").assertExists()
        cardDescription("Влажность: —").assertExists()
        cardDescription("Питание: Увлажнитель, Спальня").assertDoesNotExist()
        cardTextNode("Низкая").assertDoesNotExist()
        cardDescription("Показать уровни: Увлажнитель, Спальня").assertDoesNotExist()
        cardDescription("Целевая влажность").assertDoesNotExist()
        capture("unknown-landscape")
    }

    private val roomCard = hasAnyAncestor(hasTestTag("section:Спальня"))
    private fun directDevice() = device.copy(
        modes = mapOf("fan_speed" to Mode("auto", listOf("low", "medium", "high", "auto"), reading)),
        ranges = mapOf("humidity" to Range(45.0, Bounds(40.0, 80.0, 1.0), "unit.percent", reading)),
    )
    private fun cardDescription(label: String) = compose.onNode(hasContentDescription(label) and roomCard)
    private fun cardTextNode(text: String, useUnmergedTree: Boolean = false) = compose.onNode(hasText(text) and roomCard, useUnmergedTree)

    private fun render(
        dark: Boolean = false,
        pending: Boolean = false,
        fontScale: Float = 1f,
        device: Device = this.device,
        onToggle: (String) -> Unit = {},
        onMode: (String, String, String) -> Unit = { _, _, _ -> },
        onHumidity: (String, Double) -> Unit = { _, _ -> },
        discovered: DiscoveredPanelState? = null,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MaterialTheme(colorScheme = if (dark) panelDarkScheme else panelLightScheme, typography = panelTypography) {
                    Surface {
                        PanelRooms(
                            cardPreferences = CardPreferences(favourites = emptyList()),
                            acs = AcPanelState(), curtains = CurtainPanelState(), strips = LightStripPanelState(),
                            recuperators = RecuperatorPanelState(), bulbs = BulbPanelState(), launchers = emptyList(),
                            discovered = discovered ?: DiscoveredPanelState(tiles = listOf(device), lastPolledAt = Flat.NOW, pending = if (pending) setOf(device.id) else emptySet()),
                            now = Flat.NOW, yandexInterval = 1.minutes, tuyaInterval = 6.minutes,
                            onToggleDiscovered = onToggle,
                            onSetDiscoveredMode = onMode,
                            onSetDiscoveredHumidity = onHumidity,
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String) {
        compose.onRoot().captureRoboImage(File("build/outputs/humidifier/$name.png"), roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
    }
}
