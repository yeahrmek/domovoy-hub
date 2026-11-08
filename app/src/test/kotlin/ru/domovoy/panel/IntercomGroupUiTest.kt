package ru.domovoy.panel

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
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
import ru.domovoy.core.Device
import ru.domovoy.panelDarkScheme
import ru.domovoy.panelLightScheme
import ru.domovoy.panelTypography
import java.io.File
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IntercomGroupUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `intercoms expand into individual cards and collapse like other device groups`() = runTest {
        val state = state()
        val apps = mutableListOf<String>()
        render(state, onOpenApp = { apps += it })
        compose.onAllNodesWithText("Домофоны").assertCountEquals(2)
        compose.onAllNodesWithText("Все закрыты").assertCountEquals(2)
        compose.onNodeWithText("Домофон — Вход 1").assertDoesNotExist()
        compose.onNodeWithContentDescription("Открыть домофон").performClick()
        assertEquals(listOf("com.domonap.app"), apps)
        capture("cards-light")
        val home = hasAnyAncestor(hasTestTag("section:Главная"))
        compose.onNode(hasText("Домофоны") and home)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Свёрнуто"))
            .performClick()
        listOf("Домофон — Вход 1", "Домофон — Вход 2", "Домофон — Этаж").forEach {
            compose.onNode(hasText(it) and home).assertExists()
            compose.onAllNodesWithText(it).assertCountEquals(1)
        }
        compose.onNode(hasText("Домофоны") and home)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Развёрнуто"))
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).assertCountEquals(0)
        compose.onNodeWithText("Открыть").assertDoesNotExist()
        capture("expanded-light")
        compose.onNode(hasText("Домофоны") and home).performClick()
        compose.onNodeWithText("Домофон — Вход 1").assertDoesNotExist()
        compose.onNode(hasText("Домофоны") and home).performClick()
        compose.onNode(hasText("Домофон — Вход 1") and home).performClick()
        compose.onNodeWithText("Домофон — Вход 1").assertExists()
        compose.onNodeWithText("Домофон — Вход 2").assertDoesNotExist()
        compose.onNodeWithText("Дверь").assertExists()
        compose.onNodeWithText("Закрыта").assertExists()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).assertCountEquals(0)
        compose.onNodeWithText("Открыть").assertDoesNotExist()
        capture("details-light")
    }

    @Test
    fun `a favourite on any old entry keeps the expandable group on Home`() = runTest {
        val state = state()
        val oldId = state.intercomGroups.values.single().last().id
        render(state, dark = true, preferences = CardPreferences(favourites = listOf("discovered:$oldId")))
        compose.onNode(hasText("Домофоны") and hasAnyAncestor(hasTestTag("section:Главная"))).assertExists()
        compose.onAllNodesWithText("Все закрыты").assertCountEquals(2)
        capture("cards-dark")
        compose.onNode(hasText("Домофоны") and hasAnyAncestor(hasTestTag("section:Главная"))).performClick()
        capture("expanded-dark")
    }

    @Test
    fun `a missing entrance reading cannot produce an all-closed summary`() = runTest {
        val state = state { entries -> entries.mapIndexed { i, entry -> if (i == 0) entry.copy(events = emptyMap()) else entry } }
        render(state)
        compose.onAllNodesWithText("Все закрыты").assertCountEquals(0)
        compose.onAllNodesWithText("—").assertCountEquals(2)
    }

    private suspend fun state(change: (List<Device>) -> List<Device> = { it }): DiscoveredPanelState = MockWebServer().use { server ->
        server.start()
        server.enqueue(MockResponse(body = intercomFixture()))
        val client = server.yandexClient()
        val entries = client.devices().getOrThrow().filter { it.vendorType == "devices.types.openable" }
            .mapIndexed { i, entry -> entry.copy(name = listOf("Домофон — Вход 1", "Домофон — Вход 2", "Домофон — Этаж")[i]) }
        val poll = YandexPoll(client) { Flat.NOW }
        poll.discovered.show(change(entries), Flat.NOW)
        poll.discovered.state.value
    }

    private fun render(state: DiscoveredPanelState, dark: Boolean = false, preferences: CardPreferences = CardPreferences(), onOpenApp: (String) -> Unit = {}) {
        compose.setContent {
            MaterialTheme(colorScheme = if (dark) panelDarkScheme else panelLightScheme, typography = panelTypography) {
                Surface {
                    PanelRooms(
                        acs = AcPanelState(), curtains = CurtainPanelState(), strips = LightStripPanelState(),
                        recuperators = RecuperatorPanelState(), bulbs = BulbPanelState(),
                        launchers = launcherTiles { true }.filter { it.packageName == "com.domonap.app" },
                        discovered = state, cardPreferences = preferences, onOpenApp = onOpenApp,
                        now = Flat.NOW, yandexInterval = 1.minutes, tuyaInterval = 6.minutes,
                    )
                }
            }
        }
    }

    private fun capture(name: String) {
        compose.onRoot().captureRoboImage(File("build/outputs/intercom-group/$name.png"))
    }
}
