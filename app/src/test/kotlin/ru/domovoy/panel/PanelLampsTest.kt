package ru.domovoy.panel

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.domovoy.core.Reading
import ru.domovoy.panelDarkScheme
import ru.domovoy.panelLightScheme
import ru.domovoy.panelTypography
import java.io.File
import kotlin.test.assertEquals

/** Individual power controls and group disclosure on the real room and Home layouts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelLampsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a room's lamps show individual power icons instead of a count`() {
        compose.setContent { Panel() }

        compose.onNodeWithText("Свет").assertIsDisplayed()
        compose.onNodeWithText("5 из 7").assertDoesNotExist()
        (1..7).forEach { icon(it).assertIsDisplayed().assertWidthIsAtLeast(64.dp).assertHeightIsAtLeast(64.dp) }
        icon(1).assertIsOn()
        icon(7).assertIsOff()
        expandedLamp(1).assertDoesNotExist()
        compose.onRoot().captureRoboImage(File("build/outputs/lamp-icons/group-light.png"))
    }

    @Test
    fun `each icon toggles only its lamp using confirmed power and leaves the group collapsed`() {
        val bulbs = mutableStateOf(sevenLamps)
        val commands = mutableListOf<String>()
        compose.setContent { Panel(bulbs.value, onToggle = { commands += it }) }
        icon(1).assertIsOn().performClick()
        icon(7).assertIsOff().performClick()
        assertEquals(listOf("light-01", "light-07"), commands)
        icon(1).assertIsOn()
        icon(7).assertIsOff()
        expandedLamp(1).assertDoesNotExist()
        compose.runOnIdle {
            bulbs.value = sevenLamps.copy(tiles = sevenLamps.tiles.map { it.copy(isOn = it.id == "light-07") })
        }
        icon(1).assertIsOff()
        icon(7).assertIsOn()
    }

    @Test
    fun `pending disables only its own lamp while a command error remains local and retryable`() {
        compose.setContent { Panel(sevenLamps.copy(pending = setOf("light-01"), commandErrors = mapOf("light-02" to "Нет ответа"))) }
        icon(1).assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Ждём подтверждения"))
        icon(2).assertIsEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Ошибка команды: Нет ответа"))
        icon(7).assertIsEnabled().assertIsOff()
        compose.onRoot().captureRoboImage(File("build/outputs/lamp-icons/pending-error.png"))
    }

    @Test
    fun `stopped polling marks every icon and disables cached power controls`() {
        compose.setContent { Panel(sevenLamps.copy(error = "Нет сети", lastPolledAt = Flat.NOW.minusSeconds(3600))) }
        (1..7).forEach {
            icon(it).assertIsNotEnabled()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Нет обновления"))
        }
    }

    @Test
    fun `home preserves unknown power and keeps other lamps operable in dark mode`() {
        compose.setContent {
            Panel(
                sevenLamps.copy(tiles = sevenLamps.tiles.map { if (it.id == "light-01") it.copy(isOn = null) else it }),
                dark = true,
                preferences = CardPreferences(favourites = listOf("lamps:Спальня")),
            )
        }
        homeIcon(1).assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Нет данных"))
        homeIcon(7).assertIsEnabled().assertIsOff()
        compose.onRoot().captureRoboImage(File("build/outputs/lamp-icons/home-dark.png"))
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-port-340dpi")
    fun `small screen and large text wrap the controls without shrinking touch targets`() {
        compose.setContent { Panel(fontScale = 1.5f) }
        (1..7).forEach { icon(it).assertIsDisplayed().assertWidthIsAtLeast(64.dp).assertHeightIsAtLeast(64.dp) }
        compose.onRoot().captureRoboImage(File("build/outputs/lamp-icons/narrow-large-text.png"))
    }

    private fun icon(number: Int) = compose.onNodeWithContentDescription("Питание лампы: Лампа $number, Спальня")

    private fun homeIcon(number: Int) = compose.onNode(
        hasContentDescription("Питание лампы: Лампа $number, Спальня") and hasAnyAncestor(hasTestTag("section:Главная")),
    )

    private fun expandedLamp(number: Int) = compose.onNodeWithContentDescription("Настроить: Лампа $number, Спальня")

    @Test
    fun `tapping the group opens the seven, each with its own name`() {
        compose.setContent { Panel() }

        compose.onNodeWithText("Свет").performClick()

        expandedLamp(1).assertIsDisplayed()
        expandedLamp(7).performScrollTo().assertIsDisplayed()
        // The summary stays mounted above the expanded controls.
        compose.onNodeWithText("Свет").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `tapping it again puts them away`() {
        compose.setContent { Panel() }

        compose.onNodeWithText("Свет").performClick()
        expandedLamp(1).assertIsDisplayed()
        compose.onNodeWithText("Свет").performClick()

        expandedLamp(1).assertDoesNotExist()
    }

    @Test
    fun `the lamp the panel has no state for is named without anybody tapping anything`() {
        // It was never in the group — bulbGroup breaks out exactly the ones with no `isOn` — so the
        // one bulb worth reading about is on the wall whether or not the group is open.
        compose.setContent { Panel(bulbs = withUnknownLamp) }

        compose.onNodeWithText("Бра").assertIsDisplayed()
        // Seven reported, one did not: the group counts the seven and says nothing about the eighth.
        compose.onNodeWithText("Свет").assertIsDisplayed()
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
    )

    @Composable
    private fun Panel(
        bulbs: BulbPanelState = sevenLamps,
        dark: Boolean = false,
        fontScale: Float = 1f,
        preferences: CardPreferences = CardPreferences(favourites = emptyList()),
        onToggle: (String) -> Unit = {},
    ) {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
            MaterialTheme(colorScheme = if (dark) panelDarkScheme else panelLightScheme, typography = panelTypography) {
                Surface {
                    PanelRooms(
                        cardPreferences = preferences,
                        acs = AcPanelState(lastPolledAt = Flat.NOW),
                        curtains = CurtainPanelState(lastPolledAt = Flat.NOW),
                        strips = LightStripPanelState(lastPolledAt = Flat.NOW),
                        recuperators = RecuperatorPanelState(lastPolledAt = Flat.NOW),
                        bulbs = bulbs,
                        launchers = emptyList(),
                        now = Flat.NOW,
                        yandexInterval = Flat.YANDEX_INTERVAL,
                        tuyaInterval = Flat.TUYA_INTERVAL,
                        onToggleBulb = onToggle,
                    )
                }
            }
        }
    }
}
