package ru.domovoy.panel

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.domovoy.panelDarkScheme
import ru.domovoy.panelTypography
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelNavigationBrandTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the branded header returns home and the intercom shortcut opens its app`() {
        var returnedHome = false
        var openedApp: String? = null
        compose.setContent {
            MaterialTheme(colorScheme = panelDarkScheme, typography = panelTypography) {
                PanelNavigation(
                    intercom = LauncherTileState("example.intercom", "Домофон", "Коридор", true),
                    rooms = emptyList(),
                    roomsOpen = false,
                    onRoomsOpen = {},
                    onHome = { returnedHome = true },
                    onRoom = {},
                    onOpenApp = { openedApp = it },
                )
            }
        }

        compose.onNodeWithContentDescription("Логотип Домового", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("На главную").performClick()
        compose.onNodeWithContentDescription("Открыть домофон").performClick()

        assertEquals(true, returnedHome)
        assertEquals("example.intercom", openedApp)
    }

    @Test
    fun `quick app buttons open their app in one tap and a missing app says so`() {
        var openedApp: String? = null
        show(onOpenApp = { openedApp = it }, apps = threeApps.mapIndexed { index, app -> if (index == 2) app.copy(openable = false) else app })

        compose.onNodeWithText("Дом с Алисой").assertIsDisplayed()
        compose.onNodeWithContentDescription("Открыть Gemini").performClick()
        compose.onNodeWithText("ChatGPT: нет приложения").assertIsNotEnabled()

        assertEquals("example.gemini", openedApp)
    }

    @Test
    fun `the plus button at the end of the row opens the app picker`() {
        var picking = false
        show(onAddApp = { picking = true })

        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Добавить приложение"))
        compose.onNodeWithContentDescription("Добавить приложение").performClick()

        assertTrue(picking)
    }

    @Test
    fun `long-press dragging an app past its neighbour reorders the row`() {
        var order: List<String>? = null
        var opened: String? = null
        show(onReorderApps = { order = it }, onOpenApp = { opened = it })

        compose.onNodeWithContentDescription("Открыть Gemini").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(20) { moveBy(Offset(-20.dp.toPx(), 0f), delayMillis = 16) }
            up()
        }

        assertEquals(listOf("example.gemini", "example.alice", "example.chatgpt"), order)
        assertNull(opened)
    }

    @Test
    fun `a plain tap never reorders`() {
        var order: List<String>? = null
        show(onReorderApps = { order = it })

        compose.onNodeWithContentDescription("Открыть Gemini").performClick()

        assertNull(order)
    }

    private val threeApps = listOf(
        LauncherTileState("example.alice", "Дом с Алисой", null, true),
        LauncherTileState("example.gemini", "Gemini", null, true),
        LauncherTileState("example.chatgpt", "ChatGPT", null, true),
    )

    private fun show(
        apps: List<LauncherTileState> = threeApps,
        onOpenApp: (String) -> Unit = {},
        onAddApp: () -> Unit = {},
        onReorderApps: (List<String>) -> Unit = {},
    ) {
        compose.setContent {
            MaterialTheme(colorScheme = panelDarkScheme, typography = panelTypography) {
                PanelNavigation(
                    intercom = LauncherTileState("example.intercom", "Домофон", "Коридор", true),
                    apps = apps,
                    onAddApp = onAddApp,
                    onReorderApps = onReorderApps,
                    rooms = emptyList(),
                    roomsOpen = false,
                    onRoomsOpen = {},
                    onHome = {},
                    onRoom = {},
                    onOpenApp = onOpenApp,
                )
            }
        }
    }
}
