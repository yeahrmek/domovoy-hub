package ru.domovoy.panel

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FixedHomeDevicesTest {
    @get:Rule
    val compose = createComposeRule()

    private val home = hasAnyAncestor(hasTestTag("section:Главная"))

    @Test
    fun `startup waits for both inventories before reserving the initial Home order`() {
        var acs by mutableStateOf(AcPanelState())
        var recuperators by mutableStateOf(RecuperatorPanelState())
        var saved by mutableStateOf(CardPreferences())
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                PanelRooms(
                    acs = acs, curtains = CurtainPanelState(), strips = LightStripPanelState(),
                    recuperators = recuperators, bulbs = BulbPanelState(), launchers = Flat.launchers,
                    now = Flat.NOW, yandexInterval = Flat.YANDEX_INTERVAL, tuyaInterval = Flat.TUYA_INTERVAL,
                    cardPreferences = saved, onCardPreferencesChange = { saved = it },
                )
            }
        }
        compose.runOnIdle { assertEquals(null, saved.orders["Главная"]) }
        acs = Flat.acs
        compose.runOnIdle { assertEquals(null, saved.orders["Главная"]) }
        recuperators = Flat.recuperators
        compose.runOnIdle { assertEquals("breezers", saved.orders["Главная"]?.first()) }
    }

    @Test
    fun `bedroom curtain and humidifier are on Home by default`() {
        val humidifier = Device("humidifier", "Увлажнитель", DeviceKind.Other, "Спальня", null, vendorType = "devices.types.humidifier")
        panel(curtains = Flat.curtains.copy(tiles = listOf(Flat.curtains.tiles.single().copy(room = "Спальня"))), discovered = { listOf(humidifier) })
        compose.onNode(hasContentDescription("Настроить: Штора, Спальня") and home).assertIsDisplayed()
        compose.onNode(hasText("Увлажнитель") and home).performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/fixed-home/home-bedroom.png")
    }

    @Test
    fun `lost lamp readings keep the Home group and neighbouring card in place`() {
        val lamp = Flat.bulbs.tiles.first().copy(room = "Коридор", isOn = true)
        var bulbs by mutableStateOf(Flat.bulbs.copy(tiles = listOf(lamp)))
        val sensor = Device("sensor", "Датчик", DeviceKind.Other, "Коридор", null)
        panel(bulbs = { bulbs }, discovered = { listOf(sensor) })
        val neighbour = compose.onNode(hasText("Датчик") and home)
        val before = neighbour.getUnclippedBoundsInRoot()
        bulbs = bulbs.copy(tiles = listOf(lamp.copy(isOn = null)))
        compose.onNode(hasContentDescription("Показать лампы: Свет, Коридор") and home).assertIsDisplayed()
        assertEquals(before, neighbour.getUnclippedBoundsInRoot())
    }

    @Test
    fun `a fixed light group can turn off known lights while another light is unknown`() {
        val known = Flat.bulbs.tiles.first().copy(room = "Коридор", isOn = true)
        val unknown = known.copy(id = "unknown", isOn = null)
        val commands = mutableListOf<String>()
        panel(bulbs = { Flat.bulbs.copy(tiles = listOf(known, unknown)) }, onToggleBulb = { commands += it })
        compose.onNode(hasContentDescription("Питание: Свет, Коридор") and home).performClick()
        assertEquals(listOf(known.id), commands)
    }

    @Test
    fun `inventory reordering renaming and arrival append without swapping existing Home cards`() {
        val first = Device("a", "А", DeviceKind.Other, "Коридор", null)
        val second = first.copy(id = "b", name = "Б")
        var devices by mutableStateOf(listOf(first, second))
        var saved = CardPreferences()
        panel(discovered = { devices }, onSave = { saved = it })
        val before = compose.onNode(hasText("А") and home).getUnclippedBoundsInRoot()
        devices = listOf(second, first.copy(name = "Я"), first.copy(id = "c", name = "В"))
        assertEquals(before, compose.onNode(hasText("Я") and home).getUnclippedBoundsInRoot())
        compose.runOnIdle { assertEquals(listOf("discovered:a", "discovered:b", "discovered:c"), saved.orders["Главная"]) }
    }

    private fun panel(
        curtains: CurtainPanelState = CurtainPanelState(lastPolledAt = Flat.NOW),
        bulbs: () -> BulbPanelState = { BulbPanelState(lastPolledAt = Flat.NOW) },
        discovered: () -> List<Device> = { emptyList() },
        onSave: (CardPreferences) -> Unit = {},
        onToggleBulb: (String) -> Unit = {},
    ) {
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                Surface {
                    PanelRooms(
                        acs = AcPanelState(lastPolledAt = Flat.NOW), curtains = curtains,
                        strips = LightStripPanelState(lastPolledAt = Flat.NOW), recuperators = RecuperatorPanelState(lastPolledAt = Flat.NOW),
                        bulbs = bulbs(), discovered = DiscoveredPanelState(discovered(), lastPolledAt = Flat.NOW), launchers = emptyList(),
                        now = Flat.NOW, yandexInterval = Flat.YANDEX_INTERVAL, tuyaInterval = Flat.TUYA_INTERVAL,
                        onCardPreferencesChange = onSave, onToggleBulb = onToggleBulb,
                    )
                }
            }
        }
    }
}
