package ru.domovoy.panel

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.domovoy.panelDarkScheme
import ru.domovoy.panelLightScheme
import ru.domovoy.panelTypography
import java.io.File
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SwitchSocketUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `wardrobe switch card and details control the wardrobe light using its confirmed state`() = runTest {
        val lamp = Flat.bulbs.tiles.first().copy(id = "wardrobe-light", room = "Гардероб", isOn = true)
        val lightCommands = mutableListOf<String>()
        val otherCommands = mutableListOf<String>()
        render(
            state(),
            bulbs = BulbPanelState(tiles = listOf(lamp), lastPolledAt = Flat.NOW),
            onToggleBulb = { lightCommands += it },
            onToggle = { otherCommands += it },
        )
        compose.onNodeWithContentDescription("Питание: Выключатель, Гардероб").assertIsOn().performClick()
        compose.onNode(hasText("Выключатель") and hasText("Включено")).assertDoesNotExist()
        assertEquals(listOf("wardrobe-light"), lightCommands)
        assertEquals(emptyList(), otherCommands)
        capture("linked-light")
        compose.onNodeWithText("Выключатель").performClick()
        compose.onNodeWithContentDescription("Питание: Выключатель, Гардероб").assertIsOn().performClick()
        assertEquals(listOf("wardrobe-light", "wardrobe-light"), lightCommands)
        compose.onNodeWithText("Верхняя клавиша").assertExists()
        compose.onNodeWithText("Нижняя клавиша").assertExists()
        capture("linked-light-details")
    }

    @Test
    fun `a pending light command also disables the linked switch power`() = runTest {
        val lamp = Flat.bulbs.tiles.first().copy(id = "wardrobe-light", room = "Гардероб", isOn = false)
        render(
            state(),
            bulbs = BulbPanelState(tiles = listOf(lamp), lastPolledAt = Flat.NOW, pending = setOf(lamp.id)),
            dark = true,
        )
        compose.onNodeWithContentDescription("Питание: Выключатель, Гардероб").assertIsOff().assertIsNotEnabled()
        compose.onNode(hasText("Выключатель") and hasText("Выключено")).assertDoesNotExist()
        capture("linked-light-pending-dark")
    }

    @Test
    fun `one wardrobe switch is visible and iron power works from its card and details`() = runTest {
        val state = state()
        val commands = mutableListOf<String>()
        render(state, onToggle = { commands += it })
        compose.onNodeWithText("Верхняя кнопка").assertDoesNotExist()
        compose.onNodeWithText("Нижняя кнопка").assertDoesNotExist()
        compose.onAllNodesWithText("Выключатель")[0].assertExists()
        compose.onNodeWithText("Верхняя клавиша: Нажатие").assertExists()
        compose.onNodeWithText("Нижняя клавиша: Нажатие").assertExists()
        compose.onNodeWithText("—").assertDoesNotExist()
        compose.onNodeWithContentDescription("Питание: Утюг, Гардероб").assertIsOff().performClick()
        assertEquals(listOf("socket-iron"), commands)
        capture("cards-light")
        compose.onNodeWithText("Утюг").performClick()
        compose.onNodeWithContentDescription("Питание: Утюг, Гардероб").assertIsOff().performClick()
        assertEquals(listOf("socket-iron", "socket-iron"), commands)
        capture("details-light")
    }

    @Test
    fun `pending socket power is disabled while the switch events remain readable in dark mode`() = runTest {
        val state = state()
        render(state.copy(pending = setOf("socket-iron")), dark = true)
        compose.onNodeWithContentDescription("Питание: Утюг, Гардероб").assertIsNotEnabled()
        compose.onNodeWithText("Ждём подтверждения…").assertExists()
        compose.onNodeWithText("Верхняя клавиша: Нажатие").assertExists()
        capture("pending-dark")
    }

    @Test
    fun `a confirmed powered socket has an on control`() = runTest {
        val state = state()
        render(state.copy(tiles = state.tiles.map { it.copy(onOff = it.onOff?.copy(isOn = true)) }))
        compose.onNodeWithContentDescription("Питание: Утюг, Гардероб").assertIsOn()
    }

    private suspend fun state(): DiscoveredPanelState = MockWebServer().use { server ->
        server.start()
        server.enqueue(MockResponse(body = switchSocketFixture()))
        val poll = YandexPoll(server.yandexClient()) { Flat.NOW }
        poll.refresh()
        poll.discovered.state.value
    }

    private fun render(
        state: DiscoveredPanelState,
        dark: Boolean = false,
        bulbs: BulbPanelState = BulbPanelState(),
        onToggleBulb: (String) -> Unit = {},
        onToggle: (String) -> Unit = {},
    ) {
        compose.setContent {
            MaterialTheme(colorScheme = if (dark) panelDarkScheme else panelLightScheme, typography = panelTypography) {
                Surface {
                    PanelRooms(
                        cardPreferences = CardPreferences(favourites = emptyList()),
                        acs = AcPanelState(), curtains = CurtainPanelState(), strips = LightStripPanelState(),
                        recuperators = RecuperatorPanelState(), bulbs = bulbs, launchers = emptyList(),
                        discovered = state, now = Flat.NOW, yandexInterval = 1.minutes, tuyaInterval = 6.minutes,
                        onToggleDiscovered = onToggle,
                        onToggleBulb = onToggleBulb,
                    )
                }
            }
        }
    }

    private fun capture(name: String) {
        compose.onRoot().captureRoboImage(File("build/outputs/switch-socket/$name.png"))
    }
}
