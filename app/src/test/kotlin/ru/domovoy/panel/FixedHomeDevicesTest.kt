package ru.domovoy.panel

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
    fun `initial order waits for Yandex and Tuya attempts before including launchers`() {
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
        acs = Flat.acs.copy(tiles = listOf(Flat.acs.tiles.first()))
        compose.runOnIdle { assertEquals(null, saved.orders["Главная"]) }
        recuperators = RecuperatorPanelState(error = "Нет связи")
        compose.runOnIdle { assertEquals("ac:${acs.tiles.single().id}", saved.orders["Главная"]?.first()) }
    }

    @Test
    fun `renaming response reordering and an earlier format arrival keep existing cards in place`() {
        val first = Device("a", "А", DeviceKind.Other, "Коридор", null)
        val second = first.copy(id = "b", name = "Б")
        var devices by mutableStateOf(listOf(first, second))
        var acs by mutableStateOf(AcPanelState(lastPolledAt = Flat.NOW))
        var saved by mutableStateOf(CardPreferences())
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                PanelRooms(
                    acs = acs, curtains = CurtainPanelState(lastPolledAt = Flat.NOW), strips = LightStripPanelState(lastPolledAt = Flat.NOW),
                    recuperators = RecuperatorPanelState(lastPolledAt = Flat.NOW), bulbs = BulbPanelState(lastPolledAt = Flat.NOW),
                    discovered = DiscoveredPanelState(devices, lastPolledAt = Flat.NOW), launchers = emptyList(),
                    now = Flat.NOW, yandexInterval = Flat.YANDEX_INTERVAL, tuyaInterval = Flat.TUYA_INTERVAL,
                    cardPreferences = saved, onCardPreferencesChange = { saved = it },
                )
            }
        }
        val before = compose.onNode(hasText("А") and home).getUnclippedBoundsInRoot()
        devices = listOf(second, first.copy(name = "Я"))
        acs = Flat.acs.copy(tiles = listOf(Flat.acs.tiles.first()))
        assertEquals(before, compose.onNode(hasText("Я") and home).getUnclippedBoundsInRoot())
        compose.runOnIdle { assertEquals(listOf("discovered:a", "discovered:b", "ac:${acs.tiles.single().id}"), saved.orders["Главная"]) }
    }

    @Test
    fun `lost power readings retain the Home light group and disable an unknown group`() {
        val lamp = Flat.bulbs.tiles.first().copy(room = "Коридор", isOn = true)
        var bulbs by mutableStateOf(Flat.bulbs.copy(tiles = listOf(lamp)))
        panel(bulbs = { bulbs })
        val group = compose.onNode(hasText("Свет") and home)
        val before = group.getUnclippedBoundsInRoot()
        bulbs = bulbs.copy(tiles = listOf(lamp.copy(isOn = null)))
        assertEquals(before, group.getUnclippedBoundsInRoot())
        compose.onNode(hasText(lamp.name) and home).assertDoesNotExist()
        compose.onNode(hasContentDescription("Питание: Свет, Коридор") and home).assertIsNotEnabled()
    }

    @Test
    fun `a partially unknown Home group toggles only lamps with known power`() {
        val known = Flat.bulbs.tiles.first().copy(room = "Коридор", isOn = true)
        val unknown = known.copy(id = "unknown", isOn = null)
        val commands = mutableListOf<String>()
        panel(bulbs = { Flat.bulbs.copy(tiles = listOf(known, unknown)) }, onToggle = { commands += it })
        compose.onNode(hasContentDescription("Питание: Свет, Коридор") and home).performClick()
        assertEquals(listOf(known.id), commands)
    }

    @Test
    fun `an explicitly favourite individual bulb stays individual and hidden room groups stay hidden`() {
        val lamp = Flat.bulbs.tiles.first().copy(room = "Спальня", isOn = null)
        var bulbs by mutableStateOf(Flat.bulbs.copy(tiles = listOf(lamp)))
        panel(bulbs = { bulbs }, preferences = CardPreferences(favourites = listOf("bulb:${lamp.id}")))
        compose.onNode(hasText(lamp.name) and home).assertIsDisplayed()
        compose.onNode(hasText("Свет") and home).assertDoesNotExist()
        bulbs = bulbs.copy(tiles = listOf(lamp.copy(isOn = true)))
        compose.onNode(hasText(lamp.name) and home).assertIsDisplayed()
        compose.onNode(hasText("Свет") and home).assertDoesNotExist()
    }

    @Test
    fun `bedroom curtain and humidifier are on Home by default`() {
        val humidifier = Device("humidifier", "Увлажнитель", DeviceKind.Other, "Спальня", null, vendorType = "devices.types.humidifier")
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                Surface {
                    PanelRooms(
                        acs = AcPanelState(lastPolledAt = Flat.NOW),
                        curtains = Flat.curtains.copy(tiles = listOf(Flat.curtains.tiles.single().copy(room = "Спальня"))),
                        strips = LightStripPanelState(lastPolledAt = Flat.NOW), recuperators = RecuperatorPanelState(lastPolledAt = Flat.NOW),
                        bulbs = BulbPanelState(lastPolledAt = Flat.NOW), discovered = DiscoveredPanelState(listOf(humidifier), lastPolledAt = Flat.NOW),
                        launchers = emptyList(), now = Flat.NOW, yandexInterval = Flat.YANDEX_INTERVAL, tuyaInterval = Flat.TUYA_INTERVAL,
                    )
                }
            }
        }
        compose.onNode(hasText("Штора") and home).performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Увлажнитель") and home).performScrollTo().assertIsDisplayed()
    }
    private fun panel(
        bulbs: () -> BulbPanelState,
        preferences: CardPreferences = CardPreferences(),
        onToggle: (String) -> Unit = {},
    ) {
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                Surface {
                    PanelRooms(
                        acs = AcPanelState(lastPolledAt = Flat.NOW), curtains = CurtainPanelState(lastPolledAt = Flat.NOW),
                        strips = LightStripPanelState(lastPolledAt = Flat.NOW), recuperators = RecuperatorPanelState(lastPolledAt = Flat.NOW),
                        bulbs = bulbs(), launchers = emptyList(), now = Flat.NOW,
                        yandexInterval = Flat.YANDEX_INTERVAL, tuyaInterval = Flat.TUYA_INTERVAL,
                        cardPreferences = preferences, onToggleBulb = onToggle,
                    )
                }
            }
        }
    }
}
