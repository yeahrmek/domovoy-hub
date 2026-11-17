package ru.domovoy.panel

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.domovoy.panelDarkScheme
import ru.domovoy.panelTypography
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
class QuickAppPickerTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the picker marks shown apps and toggles any installed app`() {
        val toggled = mutableListOf<String>()
        var closed = false
        compose.setContent {
            MaterialTheme(colorScheme = panelDarkScheme, typography = panelTypography) {
                QuickAppPicker(
                    installed = listOf(InstalledApp("example.notes", "Заметки"), InstalledApp("example.gemini", "Gemini")),
                    shown = listOf("example.gemini"),
                    onToggle = { toggled += it },
                    onDismiss = { closed = true },
                )
            }
        }

        compose.onNodeWithText("Gemini").assertIsOn()
        compose.onNodeWithText("Заметки").assertIsOff().performClick()
        compose.onNodeWithText("Готово").performClick()

        assertEquals(listOf("example.notes"), toggled)
        assertEquals(true, closed)
    }
}
