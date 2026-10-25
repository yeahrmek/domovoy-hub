package ru.domovoy.panel

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.domovoy.panelLightScheme
import ru.domovoy.panelTypography
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelUxTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `controls in the same row have aligned bottom edges despite long names`() {
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                PanelRooms(
                    acs = AcPanelState(tiles = listOf(Flat.acs.tiles.first()), lastPolledAt = Flat.NOW),
                    curtains = CurtainPanelState(lastPolledAt = Flat.NOW),
                    strips = LightStripPanelState(
                        tiles = listOf(Flat.strips.tiles.first().copy(name = "Подсветка у окна, над полками и рядом с диваном", room = "Зал")),
                        lastPolledAt = Flat.NOW,
                    ),
                    recuperators = RecuperatorPanelState(lastPolledAt = Flat.NOW),
                    bulbs = BulbPanelState(lastPolledAt = Flat.NOW),
                    launchers = emptyList(),
                    now = Flat.NOW,
                    yandexInterval = Flat.YANDEX_INTERVAL,
                    tuyaInterval = Flat.TUYA_INTERVAL,
                )
            }
        }
        val ac = compose.onNode(hasText("Кондиционер") and hasText("Зал") and hasClickAction()).getUnclippedBoundsInRoot()
        val strip = compose.onNode(hasText("Подсветка у окна, над полками и рядом с диваном") and hasText("Зал") and hasClickAction()).getUnclippedBoundsInRoot()
        assertTrue(ac.top == strip.top)
        assertTrue(ac.bottom == strip.bottom)
    }

    @Test
    fun `a reading summary precedes compact devices even when their vendor is failing`() {
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                PanelRooms(
                    acs = AcPanelState(lastPolledAt = Flat.NOW),
                    curtains = CurtainPanelState(lastPolledAt = Flat.NOW),
                    strips = LightStripPanelState(lastPolledAt = Flat.NOW),
                    recuperators = RecuperatorPanelState(lastPolledAt = Flat.NOW),
                    bulbs = Flat.bulbs.copy(
                        tiles = Flat.bulbs.tiles.filter { it.room == "Зал" } +
                            Flat.bulbs.tiles.first().copy(
                                id = "unknown-lamp",
                                name = "Приток",
                                room = "Зал",
                                isOn = null,
                                brightnessPercent = null,
                                brightnessBounds = null,
                            ),
                        error = "Нет сети",
                    ),
                    launchers = emptyList(),
                    now = Flat.NOW,
                    yandexInterval = Flat.YANDEX_INTERVAL,
                    tuyaInterval = Flat.TUYA_INTERVAL,
                )
            }
        }
        val group = compose.onNode(hasText("Свет") and hasText("Зал") and hasClickAction()).getUnclippedBoundsInRoot()
        val compact = compose.onNode(hasText("Приток") and hasText("Зал") and hasClickAction()).getUnclippedBoundsInRoot()
        assertTrue(group.bottom <= compact.top)
    }

    @Test
    fun `a launcher does not reserve space for absent controls and readings`() {
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                LauncherTile(Flat.launchers.first())
            }
        }

        val bounds = compose.onNode(hasText("Домофон") and hasClickAction()).getUnclippedBoundsInRoot()
        assertTrue(bounds.bottom - bounds.top <= 180.dp)
    }

    @Test
    fun `a power-only sheet fits its content`() {
        val bulb = Flat.bulbs.tiles.first().copy(brightnessBounds = null, brightnessPercent = null)
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                DeviceSheet(sheet(bulb, Flat.NOW, null), onDismiss = {})
            }
        }

        val bounds = compose.onNode(
            androidx.compose.ui.test.SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.PaneTitle,
                bulb.name,
            ),
        ).getUnclippedBoundsInRoot()
        assertTrue(bounds.bottom - bounds.top <= 400.dp)
    }

    @Test
    fun `a long reading leaves its age visible without overlap`() {
        val reading = SheetReading("Цвет", "4500 K · только просмотр", "4 мин назад")
        val strip = sheet(Flat.strips.tiles.first(), Flat.NOW, null).copy(readings = listOf(reading))
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                DeviceSheet(strip, onDismiss = {})
            }
        }

        val valueBounds = compose.onNodeWithText(reading.value).getUnclippedBoundsInRoot()
        val ageBounds = compose.onNodeWithText(reading.age).getUnclippedBoundsInRoot()
        assertTrue(valueBounds.right + 16.dp <= ageBounds.left)
        assertTrue(ageBounds.right - ageBounds.left >= 80.dp)
        compose.onNodeWithText(reading.age).assertIsDisplayed()
    }

    @Test
    fun `the temperature control names the setting and shows the vendor range`() {
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                AcTile(Flat.acs.tiles.first(), Flat.NOW)
            }
        }

        compose.onNodeWithText("Температура").assertIsDisplayed()
        compose.onNodeWithText("16–30 °C").assertIsDisplayed()
    }

    @Test
    fun `pending commands are visible and disable duplicate power taps`() {
        val waiting = mutableStateOf(true)
        val ac = Flat.acs.tiles.first()
        val openSheet = mutableStateOf<String?>(ac.id)
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                PanelRooms(
                    acs = AcPanelState(
                        tiles = listOf(ac),
                        pending = if (waiting.value) setOf(ac.id) else emptySet(),
                        lastPolledAt = Flat.NOW,
                    ),
                    curtains = CurtainPanelState(lastPolledAt = Flat.NOW),
                    strips = LightStripPanelState(lastPolledAt = Flat.NOW),
                    recuperators = RecuperatorPanelState(lastPolledAt = Flat.NOW),
                    bulbs = BulbPanelState(lastPolledAt = Flat.NOW),
                    launchers = emptyList(),
                    now = Flat.NOW,
                    yandexInterval = Flat.YANDEX_INTERVAL,
                    tuyaInterval = Flat.TUYA_INTERVAL,
                    openSheet = openSheet,
                )
            }
        }

        compose.onNodeWithText("Ждём подтверждения…").assertIsDisplayed()
        compose.onNodeWithText("Ждём подтверждения…").assert(
            androidx.compose.ui.test.SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.LiveRegion,
                androidx.compose.ui.semantics.LiveRegionMode.Polite,
            ),
        )
        compose.onNodeWithContentDescription("Питание: ${ac.name}, ${ac.room}").assertIsNotEnabled()
        compose.onNodeWithText("Охлаждение").assertIsNotEnabled()
        compose.runOnIdle { waiting.value = false }
        compose.onNodeWithText("Ждём подтверждения…").assertDoesNotExist()
        compose.onNodeWithContentDescription("Питание: ${ac.name}, ${ac.room}").assertIsEnabled()
    }

    @Test
    fun `secondary actions use the whole wall touch target`() {
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                AcTile(Flat.acs.tiles.first(), Flat.NOW)
            }
        }
        val bounds = compose.onNodeWithContentDescription("Скорость вентилятора").getUnclippedBoundsInRoot()
        assertTrue(bounds.right - bounds.left >= 64.dp)
        assertTrue(bounds.bottom - bounds.top >= 64.dp)
    }

    @Test
    fun `a collapsed light group still shows its lamps command failure`() {
        val lamp = Flat.bulbs.tiles.first().copy(room = "Спальня", isOn = true)
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                PanelRooms(
                    acs = AcPanelState(lastPolledAt = Flat.NOW),
                    curtains = CurtainPanelState(lastPolledAt = Flat.NOW),
                    strips = LightStripPanelState(lastPolledAt = Flat.NOW),
                    recuperators = RecuperatorPanelState(lastPolledAt = Flat.NOW),
                    bulbs = BulbPanelState(
                        tiles = listOf(lamp),
                        lastPolledAt = Flat.NOW,
                        commandErrors = mapOf(lamp.id to "Нет связи"),
                    ),
                    launchers = emptyList(),
                    now = Flat.NOW,
                    yandexInterval = Flat.YANDEX_INTERVAL,
                    tuyaInterval = Flat.TUYA_INTERVAL,
                )
            }
        }
        compose.onNodeWithText("${lamp.name}: Нет связи").assertIsDisplayed()
    }

    @Test
    fun `home keeps each rooms lights separate and names their room`() {
        val bulbs = BulbPanelState(
            tiles = listOf("Коридор", "Зал").flatMapIndexed { roomIndex, room ->
                (1..2).map { index ->
                    Flat.bulbs.tiles.first().copy(id = "$roomIndex-$index", room = room, isOn = true)
                }
            },
            lastPolledAt = Flat.NOW,
        )
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                Surface {
                    PanelRooms(
                        acs = AcPanelState(lastPolledAt = Flat.NOW),
                        curtains = CurtainPanelState(lastPolledAt = Flat.NOW),
                        strips = LightStripPanelState(lastPolledAt = Flat.NOW),
                        recuperators = RecuperatorPanelState(lastPolledAt = Flat.NOW),
                        bulbs = bulbs,
                        launchers = emptyList(),
                        now = Flat.NOW,
                        yandexInterval = Flat.YANDEX_INTERVAL,
                        tuyaInterval = Flat.TUYA_INTERVAL,
                    )
                }
            }
        }

        compose.onNode(hasText("Свет") and hasText("Коридор") and hasClickAction()).assertIsDisplayed()
        compose.onNode(hasText("Свет") and hasText("Зал") and hasClickAction()).assertIsDisplayed()
    }
}
