package ru.domovoy.panel

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.domovoy.core.Mode
import ru.domovoy.core.Reading
import ru.domovoy.core.Toggle
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CapabilityControlsAccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `mode choices expose the reported selection and selected action`() {
        val current = mutableStateOf("cool")
        var sent: Pair<String, String>? = null
        compose.setContent {
            MaterialTheme {
                ModeControls(
                    modes = mapOf("thermostat" to Mode(current.value, listOf("cool", "heat"), Reading.Never)),
                    onSetMode = { instance, value -> sent = instance to value },
                )
            }
        }

        compose.onNodeWithText("Охлаждение").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        compose.onNodeWithText("Обогрев").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
        compose.onNodeWithText("Обогрев").performClick()
        compose.runOnIdle { assertEquals("thermostat" to "heat", sent) }
        compose.onNodeWithText("Охлаждение").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        compose.runOnIdle { current.value = "heat" }
        compose.onNodeWithText("Обогрев").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    }

    @Test
    fun `boolean options expose switch state and preserve an unread state`() {
        val state = mutableStateOf<Boolean?>(true)
        var sent: Boolean? = null
        compose.setContent {
            MaterialTheme {
                ToggleControls(
                    toggles = mapOf("ionization" to Toggle(state.value, Reading.Never)),
                    onSetToggle = { _, on -> sent = on },
                )
            }
        }

        compose.onNodeWithText("Ионизация")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
            .performClick()
        compose.runOnIdle { assertEquals(false, sent) }
        compose.runOnIdle { state.value = false }
        compose.onNodeWithText("Ионизация")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        compose.runOnIdle { state.value = null }
        compose.onNodeWithText("Ионизация")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Indeterminate))
    }

    @Test
    fun `disabled scene choices expose their unselected and disabled state`() {
        compose.setContent {
            MaterialTheme {
                ColorControls(scenes = listOf("movie"), onSetRgb = {}, onSetScene = {}, enabled = false)
            }
        }

        compose.onNodeWithText("Кино")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
            .assertIsNotEnabled()
    }
}
