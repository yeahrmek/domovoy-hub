package ru.domovoy.panel

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.domovoy.core.Reading
import ru.domovoy.panelDarkScheme
import ru.domovoy.panelTypography
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeControlsTest {
    @get:Rule
    val compose = createComposeRule()

    private val open = mutableStateOf<String?>(null)

    @Test
    fun `temperature can be adjusted on home without opening details`() {
        var sent: Pair<String, Double>? = null
        compose.setContent {
            Panel(acs = Flat.acs.copy(tiles = listOf(Flat.acs.tiles.first())), onTemperature = { id, value -> sent = id to value })
        }
        compose.onNode(hasContentDescription("Температура") and hasAnyAncestor(hasContentDescription("Настроить: Кондиционер, Зал"))).assertIsDisplayed().performSemanticsAction(SemanticsActions.SetProgress) { it(26f) }
        assertEquals("ac-01" to 26.0, sent)
        assertNull(open.value)
    }

    @Test
    fun `curtain position slider sends the home command without opening details`() {
        var sent: Pair<String, Double>? = null
        compose.setContent { Panel(curtains = Flat.curtains, onOpen = { id, value -> sent = id to value }) }
        compose.onNode(hasContentDescription("Открытие") and hasAnyAncestor(hasContentDescription("Настроить: Штора, Зал"))).assertIsDisplayed().performSemanticsAction(SemanticsActions.SetProgress) { it(75f) }
        assertEquals("curtain-01" to 75.0, sent)
        assertNull(open.value)
    }

    @Test
    fun `strip brightness is adjustable on home without opening details`() {
        var sent: Pair<String, Double>? = null
        compose.setContent {
            Panel(strips = Flat.strips.copy(tiles = listOf(Flat.strips.tiles.first())), onBrightness = { id, value -> sent = id to value })
        }
        compose.onNode(hasContentDescription("Яркость") and hasAnyAncestor(hasContentDescription("Настроить: Лента, Коридор"))).assertIsDisplayed().performSemanticsAction(SemanticsActions.SetProgress) { it(35f) }
        assertEquals("strip-01" to 35.0, sent)
        assertNull(open.value)
    }

    @Test
    fun `pending brightness command blocks further home adjustments`() {
        compose.setContent { Panel(strips = Flat.strips.copy(tiles = listOf(Flat.strips.tiles.first()), pending = setOf("strip-01"))) }
        compose.onNode(hasContentDescription("Яркость") and hasAnyAncestor(hasContentDescription("Настроить: Лента, Коридор"))).assertIsNotEnabled()
    }

    @Test
    fun `recuperator speed is selectable directly on home`() {
        var sent: Pair<String, FanSpeed>? = null
        compose.setContent {
            Panel(recuperators = Flat.recuperators.copy(tiles = listOf(Flat.recuperators.tiles.first())), onSpeed = { id, speed -> sent = id to speed })
        }
        compose.onNodeWithText("Высокая").assertIsDisplayed().performClick()
        assertEquals("xfj-01" to FanSpeed.High, sent)
        assertNull(open.value)
    }

    @Test
    fun `recuperator speed is disabled until power is confirmed on`() {
        val power = mutableStateOf<Boolean?>(false)
        compose.setContent {
            Panel(recuperators = Flat.recuperators.copy(tiles = listOf(Flat.recuperators.tiles.first().copy(isOn = power.value))))
        }
        compose.onNodeWithText("Высокая").assertIsNotEnabled()
        compose.runOnIdle { power.value = null }
        compose.onNodeWithText("Высокая").assertIsNotEnabled()
    }

    @Test
    fun `group power changes only lamps that differ from the target`() {
        val lamps = Flat.bulbs.tiles.take(2).mapIndexed { i, tile -> tile.copy(room = "Зал", isOn = i == 0) }
        val sent = mutableListOf<String>()
        compose.setContent { Panel(bulbs = Flat.bulbs.copy(tiles = lamps), onToggleBulb = { sent += it }) }
        compose.onNodeWithContentDescription("Питание: Свет, Зал").performClick()
        assertEquals(listOf(lamps[1].id), sent)
        assertNull(open.value)
    }

    @Test
    fun `brightness is available on expanded home lamps`() {
        val lamp = Flat.bulbs.tiles[1].copy(room = "Зал", isOn = true, brightnessPercent = 40.0)
        var sent: Pair<String, Double>? = null
        compose.setContent { Panel(bulbs = Flat.bulbs.copy(tiles = listOf(lamp)), onBulbBrightness = { id, value -> sent = id to value }) }
        compose.onNodeWithText("Показать лампы").performClick()
        compose.onNodeWithContentDescription("Яркость").performSemanticsAction(SemanticsActions.SetProgress) { it(80f) }
        assertEquals(lamp.id to 80.0, sent)
        assertNull(open.value)
    }

    @Test
    fun `home retains old power age beside a freshly reported temperature`() {
        val tile = Flat.acs.tiles.first().copy(powerLastUpdated = Reading.At(Flat.NOW.minusSeconds(172800)), temperatureLastUpdated = Reading.At(Flat.NOW))
        compose.setContent { Panel(acs = Flat.acs.copy(tiles = listOf(tile), lastPolledAt = Flat.NOW)) }
        assertHomeAge("Настроить: ${tile.name}, ${tile.room}", "2 дн. назад")
    }

    @Test
    fun `home shows unknown power time beside freshly reported strip brightness`() {
        val tile = Flat.strips.tiles.first().copy(powerLastUpdated = Reading.Never, brightnessLastUpdated = Reading.At(Flat.NOW), color = null)
        compose.setContent { Panel(strips = Flat.strips.copy(tiles = listOf(tile), lastPolledAt = Flat.NOW)) }
        assertHomeAge("Настроить: ${tile.name}, ${tile.room}", "Время неизвестно")
    }

    @Test
    fun `home retains the recuperator humidity age beside fresh temperature`() {
        val tile = Flat.recuperators.tiles.first().copy(humidityLastUpdated = Reading.At(Flat.NOW.minusSeconds(259200)), temperatureLastUpdated = Reading.At(Flat.NOW))
        compose.setContent { Panel(recuperators = Flat.recuperators.copy(tiles = listOf(tile), lastPolledAt = Flat.NOW)) }
        assertHomeAge("Настроить: ${tile.name}, ${tile.room}", "3 дн. назад")
    }

    @Test
    fun `home bulb group shows unknown time for a known power value`() {
        val lamp = Flat.bulbs.tiles.first().copy(room = "Зал", lastUpdated = Reading.Never)
        compose.setContent { Panel(bulbs = Flat.bulbs.copy(tiles = listOf(lamp), lastPolledAt = Flat.NOW)) }
        assertHomeAge("Показать лампы: Свет, Зал", "Время неизвестно")
    }

    @Test
    fun `expanded home lamp retains older power age beside fresh brightness`() {
        val lamp = Flat.bulbs.tiles[1].copy(room = "Зал", isOn = true, lastUpdated = Reading.At(Flat.NOW.minusSeconds(172800)), brightnessLastUpdated = Reading.At(Flat.NOW), color = null)
        compose.setContent { Panel(bulbs = Flat.bulbs.copy(tiles = listOf(lamp), lastPolledAt = Flat.NOW)) }
        compose.onNodeWithText("Показать лампы").performClick()
        assertHomeAge("Настроить: ${lamp.name}, ${lamp.room}", "2 дн. назад")
    }

    @Test
    fun `a missing power value does not age a fresh home temperature`() {
        val tile = Flat.acs.tiles.first().copy(isOn = null, powerLastUpdated = Reading.Never, temperatureLastUpdated = Reading.At(Flat.NOW))
        compose.setContent { Panel(acs = Flat.acs.copy(tiles = listOf(tile), lastPolledAt = Flat.NOW)) }
        compose.onNode(
            hasText("Значение: Время неизвестно") and hasAnyAncestor(hasContentDescription("Настроить: ${tile.name}, ${tile.room}")),
            useUnmergedTree = true,
        ).assertDoesNotExist()
    }

    private fun assertHomeAge(card: String, age: String) {
        compose.onNode(hasContentDescription(card) and hasText("Значение: $age")).assertIsDisplayed()
    }

    @Composable
    private fun Panel(
        acs: AcPanelState = AcPanelState(lastPolledAt = Flat.NOW),
        curtains: CurtainPanelState = CurtainPanelState(lastPolledAt = Flat.NOW),
        strips: LightStripPanelState = LightStripPanelState(lastPolledAt = Flat.NOW),
        bulbs: BulbPanelState = BulbPanelState(lastPolledAt = Flat.NOW),
        recuperators: RecuperatorPanelState = RecuperatorPanelState(lastPolledAt = Flat.NOW),
        onTemperature: (String, Double) -> Unit = { _, _ -> },
        onOpen: (String, Double) -> Unit = { _, _ -> },
        onBrightness: (String, Double) -> Unit = { _, _ -> },
        onBulbBrightness: (String, Double) -> Unit = { _, _ -> },
        onSpeed: (String, FanSpeed) -> Unit = { _, _ -> },
        onToggleBulb: (String) -> Unit = {},
    ) {
        MaterialTheme(colorScheme = panelDarkScheme, typography = panelTypography) {
            Surface {
                PanelRooms(
                    acs = acs, curtains = curtains, strips = strips, recuperators = recuperators,
                    bulbs = bulbs, launchers = emptyList(), now = Flat.NOW,
                    yandexInterval = Flat.YANDEX_INTERVAL, tuyaInterval = Flat.TUYA_INTERVAL,
                    openSheet = open, onSetTemperature = onTemperature, onSetOpen = onOpen,
                    onSetBrightness = onBrightness, onSetBulbBrightness = onBulbBrightness,
                    onSetRecuperatorSpeed = onSpeed, onToggleBulb = onToggleBulb,
                )
            }
        }
    }
}
