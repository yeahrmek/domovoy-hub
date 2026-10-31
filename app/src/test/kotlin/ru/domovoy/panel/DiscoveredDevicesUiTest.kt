package ru.domovoy.panel

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
    }

    @Test
    fun `a shared failure has one notice per section and retains readings on cards`() {
        render(dark = false, error = "Нет связи с сервисом")
        compose.onAllNodesWithText("Устройства Яндекса: нет обновления — Нет связи с сервисом").assertCountEquals(2)
        compose.onAllNodesWithText("Нет обновления: Нет связи с сервисом").assertCountEquals(0)
        compose.onAllNodesWithText("23.5 °C")[0].assertExists()
    }

    private fun render(dark: Boolean, error: String? = null) {
        compose.setContent {
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

    private fun capture(name: String) {
        compose.onRoot().captureRoboImage(File("build/outputs/discovered-devices/$name.png"))
    }
}
