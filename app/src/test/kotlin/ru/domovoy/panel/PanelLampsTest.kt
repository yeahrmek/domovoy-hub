package ru.domovoy.panel

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.domovoy.core.Reading
import ru.domovoy.panelLightScheme
import ru.domovoy.panelTypography

/**
 * What the lights group does when somebody touches it — the one thing about it that is Compose state
 * rather than a pure function, and therefore the one thing [BulbGroupTest] cannot reach.
 *
 * Seven lamps in Спальня, which is not one of Главная's rooms and is not failing, so they appear
 * once on the wall and the counts below are unambiguous. Nothing else is polled, so the whole panel
 * is a heading, a group tile and — when it is open — seven named tiles, all of which compose inside
 * the wall's own 1204 dp without scrolling.
 *
 * Driven against the real [PanelRooms] at the tablet's own width, for the reason [PanelScrollTest]
 * gives: a fake grid would be a test of the fake.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelLampsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a room's lamps are one tile, and the tile says how many`() {
        // The wall a person walks up to: one card for seven devices, saying seven and saying five
        // of them are lit. What it does not say is which is which — that is what the tap is for,
        // and it is the only thing behind it.
        compose.setContent { Panel() }

        compose.onNodeWithText("7 lamps").assertIsDisplayed()
        compose.onNodeWithText("5 on").assertIsDisplayed()
        compose.onNodeWithText("Лампа 1").assertDoesNotExist()
        compose.onNodeWithText("Лампа 7").assertDoesNotExist()
    }

    @Test
    fun `tapping the group opens the seven, each with its own name`() {
        // Which lamp is which was not recoverable from the row of discs at all, at any number of
        // taps. It is one tap now, and what arrives is an ordinary tile per lamp — name, age and a
        // switch of its own.
        compose.setContent { Panel() }

        compose.onNodeWithText("7 lamps").performClick()

        compose.onNodeWithText("Лампа 1").assertIsDisplayed()
        compose.onNodeWithText("Лампа 7").assertIsDisplayed()
        // And the group tile is still there above them, still carrying the count and the age: the
        // tile is not replaced by what it opens.
        compose.onNodeWithText("7 lamps").assertIsDisplayed()
    }

    @Test
    fun `tapping it again puts them away`() {
        compose.setContent { Panel() }

        compose.onNodeWithText("7 lamps").performClick()
        compose.onNodeWithText("Лампа 1").assertIsDisplayed()
        compose.onNodeWithText("7 lamps").performClick()

        compose.onNodeWithText("Лампа 1").assertDoesNotExist()
    }

    @Test
    fun `the lamp the panel has no state for is named without anybody tapping anything`() {
        // It was never in the group — bulbGroup breaks out exactly the ones with no `isOn` — so the
        // one bulb worth reading about is on the wall whether or not the group is open.
        compose.setContent { Panel(bulbs = withUnknownLamp) }

        compose.onNodeWithText("Бра").assertIsDisplayed()
        // Seven reported, one did not: the group counts the seven and says nothing about the eighth.
        compose.onNodeWithText("7 lamps").assertIsDisplayed()
    }

    /** Спальня's seven lamps, five of them lit. Fixed readings, as everything in [Flat] is. */
    private val sevenLamps =
        BulbPanelState(
            tiles = (1..7).map { lamp(id = "light-0$it", name = "Лампа $it", isOn = it <= 5) },
            lastPolledAt = Flat.NOW.minusSeconds(12),
        )

    /** The same seven, plus the one that has never reported and is therefore a tile of its own. */
    private val withUnknownLamp =
        sevenLamps.copy(tiles = sevenLamps.tiles + lamp(id = "light-08", name = "Бра", isOn = null))

    private fun lamp(
        id: String,
        name: String,
        isOn: Boolean?,
    ) = BulbTileState(
        id = id,
        name = name,
        room = "Спальня",
        isOn = isOn,
        lastUpdated = if (isOn == null) Reading.Never else Reading.At(Flat.NOW.minusSeconds(600)),
        stateChangedAt = Reading.Never,
    )

    @Composable
    private fun Panel(bulbs: BulbPanelState = sevenLamps) {
        MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
            Surface {
                PanelRooms(
                    // Nothing but the lamps: every other group is empty, so the wall is one room
                    // and there is no Главная section to show them twice on.
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
}
