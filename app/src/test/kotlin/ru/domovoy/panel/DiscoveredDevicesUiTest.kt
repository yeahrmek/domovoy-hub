package ru.domovoy.panel

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import ru.domovoy.core.Device
import ru.domovoy.core.DeviceKind
import ru.domovoy.core.EventProperty
import ru.domovoy.core.FloatProperty
import ru.domovoy.core.Reading
import ru.domovoy.panelDarkScheme
import ru.domovoy.panelLightScheme
import ru.domovoy.panelTypography
import java.io.File
import java.time.Instant
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiscoveredDevicesUiTest {
    @get:Rule
    val compose = createComposeRule()
    private val now = Instant.parse("2026-10-31T12:00:00Z")
    private val reading = Reading.At(now.minusSeconds(300))
    private val devices = listOf(
        Device(
            id = "climate",
            name = "Датчик климата",
            kind = DeviceKind.Other,
            room = "Коридор",
            onOff = null,
            vendorType = "devices.types.sensor.climate",
            properties = mapOf(
                "temperature" to FloatProperty(23.5, reading, "unit.temperature.celsius"),
                "humidity" to FloatProperty(40.9, reading, "unit.percent"),
                "battery_level" to FloatProperty(100.0, reading, "unit.percent"),
            ),
        ),
        Device(
            id = "lock",
            name = "Умный замок",
            kind = DeviceKind.Other,
            room = "Коридор",
            onOff = null,
            vendorType = "devices.types.openable.door_lock",
            events = mapOf("door" to EventProperty("closed", reading)),
            properties = mapOf("battery_level" to FloatProperty(89.0, reading, "unit.percent")),
        ),
        Device(
            id = "intercom",
            name = "Домофон вход со двора 2",
            kind = DeviceKind.Other,
            room = "Коридор",
            onOff = null,
            vendorType = "devices.types.openable",
            events = mapOf("open" to EventProperty("closed", reading)),
        ),
        Device(id = "future", name = "Новое устройство", kind = DeviceKind.Other, room = "Коридор", onOff = null),
    )

    @Test
    fun `climate sensor is a full width slim row with both readings side by side`() {
        render(dark = false)
        val onHome = hasAnyAncestor(hasTestTag("section:Главная"))
        val card = compose.onNode(hasText("Датчик климата") and onHome).getUnclippedBoundsInRoot()
        assertTrue(card.right - card.left >= 680.dp, "The climate row spans both columns")
        assertTrue(card.bottom - card.top <= 112.dp, "The climate row stays slim")
        val temperature = compose.onNode(hasText("23.5 °C") and onHome, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val humidity = compose.onNode(hasText("40.9%") and onHome, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(humidity.left > temperature.right)
        assertTrue(humidity.top == temperature.top)
        compose.onNodeWithText("Температура").assertDoesNotExist()
        compose.onNodeWithText("Влажность").assertDoesNotExist()
        compose.onNode(hasText("Датчик климата") and onHome).performClick()
        compose.onNodeWithText("Батарея").assertExists()
        compose.onNodeWithText("100%").assertExists()
    }

    @Test
    @Config(qualifiers = "w393dp-h851dp-port-340dpi")
    fun `climate readings wrap below the name on a narrow screen with large text`() {
        render(dark = false, fontScale = 1.3f)
        val onHome = hasAnyAncestor(hasTestTag("section:Главная"))
        val card = compose.onNode(hasText("Датчик климата") and onHome).getUnclippedBoundsInRoot()
        val name = compose.onNode(hasText("Датчик климата") and onHome, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val temperature = compose.onNode(hasText("23.5 °C") and onHome, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val humidity = compose.onNode(hasText("40.9%") and onHome, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(temperature.top >= name.bottom)
        assertTrue(humidity.left > temperature.right)
        assertTrue(humidity.right < card.right)
        capture("climate-row-narrow-large-text")
    }

    @Test
    fun `new devices are readable in light theme and a lock sheet never offers controls`() {
        render(dark = false)
        capture("discovered-devices-light")
        compose.onAllNodesWithText("23.5 °C")[0].assertExists()
        compose.onAllNodesWithText("Умный замок")[0].performClick()
        compose.onNodeWithText("Дверь").assertExists()
        compose.onNodeWithText("Батарея").assertExists()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).assertCountEquals(0)
        compose.onNodeWithText("Открыть").assertDoesNotExist()
        capture("discovered-lock-sheet-light")
        compose.onNodeWithText("Готово").performClick()
        compose.onAllNodesWithText("Новое устройство")[0].assertExists()
    }

    @Test
    fun `new devices remain readable in dark theme`() {
        render(dark = true)
        capture("discovered-devices-dark")
        compose.onAllNodesWithText("Закрыта").assertCountEquals(4)
        compose.onNodeWithText("Дверь", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Батарея:", substring = true).assertDoesNotExist()
        compose.onNodeWithText("назад", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a shared failure uses a dash per card and retains readings and accessible reasons`() {
        render(dark = false, error = "Нет связи с сервисом")
        compose.onAllNodesWithText("Устройства Яндекса: нет обновления — Нет связи с сервисом").assertCountEquals(0)
        compose.onAllNodesWithText("Нет обновления: Нет связи с сервисом").assertCountEquals(0)
        compose.onNode(
            hasText("Датчик климата") and hasAnyAncestor(hasTestTag("section:Главная")),
        ).assert(hasText("23.5 °C"))
            .assert(hasText("—"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Нет связи с сервисом"))
    }

    private fun render(dark: Boolean, error: String? = null, fontScale: Float = 1f) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = fontScale)) {
                MaterialTheme(colorScheme = if (dark) panelDarkScheme else panelLightScheme, typography = panelTypography) {
                    Surface {
                        PanelRooms(
                            acs = AcPanelState(), curtains = CurtainPanelState(), strips = LightStripPanelState(),
                            recuperators = RecuperatorPanelState(), bulbs = BulbPanelState(), launchers = emptyList(),
                            discovered = DiscoveredPanelState(devices, error = error, lastPolledAt = now),
                            now = now, yandexInterval = 15.seconds, tuyaInterval = 6.minutes,
                            openSheet = androidx.compose.runtime.remember { mutableStateOf<String?>(null) },
                        )
                    }
                }
            }
        }
    }

    private fun capture(name: String) {
        compose.onRoot().captureRoboImage(File("build/outputs/discovered-devices/$name.png"))
    }
}
