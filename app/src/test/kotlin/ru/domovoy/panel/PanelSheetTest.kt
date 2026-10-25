package ru.domovoy.panel

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.domovoy.core.Reading
import ru.domovoy.panelLightScheme
import ru.domovoy.panelTypography
import kotlin.test.assertEquals

/**
 * **What a tap on a tile does**, which is the one part of the device sheet that is Compose state
 * rather than a pure function and therefore the one part [TileSheetTest] cannot reach.
 *
 * One air conditioner in Спальня — not one of Главная's rooms, and neither failing nor stale, so it
 * appears once on the wall and every count below is unambiguous — plus the intercom's launcher tile,
 * which is the kind that opens an app instead.
 *
 * Driven against the real [PanelRooms] at the tablet's own width, for the reason [PanelLampsTest]
 * gives: a fake grid would be a test of the fake.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `tapping a tile opens the device, with an age for every reading`() {
        // The wall says "off · 24 °C · 3 дн. назад": one age, the oldest of the two readings behind it,
        // because four timestamps in one paragraph is what made the panel unreadable. The tap is
        // where the pair comes back apart — the on/off was read a minute ago and the target three
        // days ago, and until now the panel held that difference and had nowhere to say it.
        compose.setContent { Panel() }
        compose.onNodeWithText("Задано").assertDoesNotExist()

        compose.onNodeWithText("Кондиционер", useUnmergedTree = true).performClick()

        compose.onNodeWithText("Питание").assertIsDisplayed()
        compose.onNodeWithText("Задано").assertIsDisplayed()
        compose.onAllNodesWithText("1 мин назад").assertCountEquals(2)
        compose.onNodeWithText("3 дн. назад").assertIsDisplayed()
    }

    @Test
    fun `the AC sheet exposes its advertised modes and button-style options`() {
        compose.setContent { Panel() }

        compose.onNodeWithText("Кондиционер", useUnmergedTree = true).performClick()

        compose.onNodeWithText("Режим").assertIsDisplayed()
        compose.onNodeWithText("Вентилятор").assertIsDisplayed()
        compose.onNodeWithText("Направление воздуха").assertIsDisplayed()
        compose.onNodeWithText("Ионизация").assertIsDisplayed()
        compose.onNodeWithText("Поддержание тепла").assertIsDisplayed()
        compose.onNodeWithText("Подсветка").assertIsDisplayed()
    }

    @Test
    fun `the sheet repeats the tile reading and restores it on dismiss`() {
        compose.setContent { Panel() }
        compose.onAllNodesWithText("24 °C").assertCountEquals(1)

        compose.onNodeWithText("Кондиционер", useUnmergedTree = true).performClick()

        // The background remains drawn, but its semantics are hidden while the sheet is modal.
        compose.onNodeWithText("24 °C").assertIsDisplayed()
        compose.onNodeWithText("Готово").performClick()
        compose.onAllNodesWithText("24 °C").assertCountEquals(1)
    }

    @Test
    fun `the sheet goes away when it is done with`() {
        compose.setContent { Panel() }
        compose.onNodeWithText("Кондиционер", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Готово").assertIsDisplayed()

        compose.onNodeWithText("Готово").performClick()

        compose.onNodeWithText("Готово").assertDoesNotExist()
        compose.onNodeWithText("Задано").assertDoesNotExist()
    }

    @Test
    fun `system back closes the sheet without leaving the panel`() {
        lateinit var back: OnBackPressedDispatcher
        compose.setContent {
            back = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            Panel()
        }
        compose.onNodeWithText("Кондиционер", useUnmergedTree = true).performClick()

        compose.runOnIdle { back.onBackPressed() }

        compose.onNodeWithText("Готово").assertDoesNotExist()
        compose.onNodeWithText("Кондиционер", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `the sheet takes focus and hides the background from accessibility`() {
        compose.setContent { Panel() }
        compose.onNodeWithText("Кондиционер", useUnmergedTree = true).performClick()

        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Кондиционер"))
            .assertIsFocused()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
            .assertExists()

        val pane = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Кондиционер")
        repeat(30) {
            compose.onNode(isFocused()).performKeyInput { pressKey(Key.Tab) }
            compose.onNode(isFocused()).assert(pane or hasAnyAncestor(pane))
        }

        compose.onNodeWithText("Готово").performClick()

        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
            .assertDoesNotExist()
    }

    @Test
    fun `the idle reset closes it, so the next person gets the wall and not one device`() {
        // A wall panel is walked up to by somebody who did not leave it there. After two minutes of
        // quiet it goes back to Главная, and a sheet somebody left open is the one thing that would
        // still be in front of it — see [returnToHome], which is what the reset calls.
        val openSheet = mutableStateOf<String?>(null)
        lateinit var scroll: LazyGridState
        compose.setContent {
            scroll = rememberLazyGridState()
            Panel(scroll = scroll, openSheet = openSheet)
        }
        compose.onNodeWithText("Кондиционер", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Готово").assertIsDisplayed()

        runBlocking { returnToHome(scroll, openSheet) }

        compose.onNodeWithText("Готово").assertDoesNotExist()
        compose.onNodeWithText("Задано").assertDoesNotExist()
    }

    @Test
    fun `a launcher tile opens its app and never a sheet`() {
        // Xiaomi issues no credentials and Domonap has no API the panel calls, so there is no
        // reading behind either tile for a sheet to show — the tap is the app. It appears twice on
        // this wall, once on Главная and once in Коридор, which is the launcher rule and not this
        // change's doing.
        var opened: String? = null
        compose.setContent { Panel(onOpenApp = { opened = it }) }

        compose.onAllNodesWithText("Домофон")[0].performClick()

        compose.onNodeWithText("Готово").assertDoesNotExist()
        compose.onNodeWithText("Питание").assertDoesNotExist()
        assertEquals(Flat.launchers.first().packageName, opened)
    }

    @Test
    fun `the lights group still opens its lamps rather than a sheet`() {
        // The one tile whose tap was already spoken for. A group stands for a room's lamps rather
        // than for a device, so there is nothing behind it for a sheet to be about — and what the
        // tap opens is the lamps, each of which is a bulb with a sheet of its own.
        compose.setContent { Panel(bulbs = twoLamps) }

        compose.onNodeWithText("Свет").performClick()

        compose.onNodeWithText("Готово").assertDoesNotExist()
        compose.onNodeWithText("Лампа 1").assertIsDisplayed()

        compose.onNodeWithText("Лампа 1", useUnmergedTree = true).performClick()

        compose.onNodeWithText("Готово").assertIsDisplayed()
        compose.onNodeWithText("Питание").assertIsDisplayed()
    }

    /** Спальня's two lamps: a group tile, and the two it opens. */
    private val twoLamps =
        BulbPanelState(
            tiles = (1..2).map { lamp ->
                BulbTileState(
                    id = "light-0$lamp",
                    name = "Лампа $lamp",
                    room = "Спальня",
                    isOn = lamp == 1,
                    lastUpdated = Reading.At(Flat.NOW.minusSeconds(600)),
                )
            },
            lastPolledAt = Flat.NOW.minusSeconds(12),
        )

    /** `ac-02`, which is in Спальня and therefore on the wall exactly once. */
    private val oneAc =
        AcPanelState(tiles = listOf(Flat.acs.tiles.last()), lastPolledAt = Flat.NOW.minusSeconds(12))

    @Composable
    private fun Panel(
        bulbs: BulbPanelState = BulbPanelState(lastPolledAt = Flat.NOW),
        scroll: LazyGridState = rememberLazyGridState(),
        openSheet: MutableState<String?> = remember { mutableStateOf(null) },
        onOpenApp: (String) -> Unit = {},
    ) {
        MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
            Surface {
                PanelRooms(
                    acs = oneAc,
                    curtains = CurtainPanelState(lastPolledAt = Flat.NOW),
                    strips = LightStripPanelState(lastPolledAt = Flat.NOW),
                    recuperators = RecuperatorPanelState(lastPolledAt = Flat.NOW),
                    bulbs = bulbs,
                    launchers = listOf(Flat.launchers.first()),
                    now = Flat.NOW,
                    yandexInterval = Flat.YANDEX_INTERVAL,
                    tuyaInterval = Flat.TUYA_INTERVAL,
                    scroll = scroll,
                    openSheet = openSheet,
                    onOpenApp = onOpenApp,
                )
            }
        }
    }
}
