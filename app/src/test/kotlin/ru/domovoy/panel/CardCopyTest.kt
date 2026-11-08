package ru.domovoy.panel

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.domovoy.core.Device
import ru.domovoy.core.DeviceKind
import ru.domovoy.core.EventProperty
import ru.domovoy.core.FloatProperty
import ru.domovoy.core.Reading
import ru.domovoy.panelLightScheme
import ru.domovoy.panelTypography

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CardCopyTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `home omits routine power and freshness text`() {
        val tile = Flat.acs.tiles.first()
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                HomeDeviceTile(
                    anatomy = anatomy(tile, Flat.NOW, null),
                    paint = paint(tile, null),
                    polledAt = Flat.NOW,
                    now = Flat.NOW,
                    interval = Flat.YANDEX_INTERVAL,
                    onOpen = {},
                )
            }
        }
        compose.onNodeWithText("Включено", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Связь:", substring = true).assertDoesNotExist()
        compose.onNodeWithText("назад", substring = true).assertDoesNotExist()
        compose.onNodeWithText("только что", substring = true).assertDoesNotExist()
    }

    @Test
    fun `device details omit reading ages including unknown time`() {
        val details = sheet(Flat.acs.tiles.first(), Flat.NOW, null).copy(
            readings = listOf(
                SheetReading("Питание", "Включено", "Только что"),
                SheetReading("Задано", "22 °C", "2 дн. назад"),
                SheetReading("В комнате", "Нет данных", "Время неизвестно"),
            ),
        )
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                DeviceSheet(details, onDismiss = {})
            }
        }
        compose.onNodeWithText("22 °C").assertIsDisplayed()
        compose.onNodeWithText("Только что").assertDoesNotExist()
        compose.onNodeWithText("назад", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Время неизвестно").assertDoesNotExist()
        compose.onNodeWithText("Связь:", substring = true).assertDoesNotExist()
    }

    @Test
    fun `lock card shows its door state without secondary text`() {
        val reading = Reading.At(Flat.NOW.minusSeconds(300))
        val lock = Device(
            id = "lock",
            name = "Замок",
            kind = DeviceKind.Other,
            room = "Коридор",
            onOff = null,
            vendorType = "devices.types.openable.door_lock",
            events = mapOf("door" to EventProperty("closed", reading)),
            properties = mapOf("battery_level" to FloatProperty(89.0, reading, "unit.percent")),
        )
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                DiscoveredDeviceTile(lock, Flat.NOW, onOpen = {})
            }
        }
        compose.onNodeWithText("Закрыта").assertIsDisplayed()
        compose.onNodeWithText("Дверь", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Батарея", substring = true).assertDoesNotExist()
        compose.onNodeWithText("назад", substring = true).assertDoesNotExist()
    }
}
