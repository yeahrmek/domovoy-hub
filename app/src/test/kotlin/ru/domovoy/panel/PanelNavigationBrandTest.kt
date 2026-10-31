package ru.domovoy.panel

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.domovoy.panelDarkScheme
import ru.domovoy.panelTypography
import kotlin.test.assertEquals

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
}
