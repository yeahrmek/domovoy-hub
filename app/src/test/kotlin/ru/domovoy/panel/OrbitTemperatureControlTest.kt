package ru.domovoy.panel

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.domovoy.core.Bounds
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OrbitTemperatureControlTest {
    @get:Rule
    val compose = createComposeRule()

    private val bounds = Bounds(16.0, 30.0, 1.0)

    @Test
    fun `accessible steps use the vendor precision and latest polled value`() {
        val target = mutableStateOf<Double?>(22.0)
        val sent = mutableListOf<Double>()
        compose.setContent {
            MaterialTheme {
                OrbitTemperatureControl("ac", target.value, bounds, true, sent::add)
            }
        }
        compose.runOnIdle { target.value = 26.0 }

        assertEquals(26f, current())
        val increased = current() + bounds.precision.toFloat()
        dial().performSemanticsAction(SemanticsActions.SetProgress) { it(increased) }
        val decreased = current() - bounds.precision.toFloat()
        dial().performSemanticsAction(SemanticsActions.SetProgress) { it(decreased) }

        assertEquals(listOf(27.0, 26.0), sent)
    }

    @Test
    fun `accessible adjustment snaps and clamps to the vendor range`() {
        val sent = mutableListOf<Double>()
        compose.setContent {
            MaterialTheme {
                OrbitTemperatureControl("ac", 22.0, Bounds(18.0, 28.0, 0.5), true, sent::add)
            }
        }

        dial().performSemanticsAction(SemanticsActions.SetProgress) { it(23.3f) }
        dial().performSemanticsAction(SemanticsActions.SetProgress) { it(99f) }

        assertEquals(listOf(23.5, 28.0), sent)
    }

    @Test
    fun `drag sends only on release and a poll does not move the draft under the finger`() {
        val target = mutableStateOf<Double?>(22.0)
        val sent = mutableListOf<Double>()
        compose.setContent {
            MaterialTheme { OrbitTemperatureControl("ac", target.value, bounds, true, sent::add) }
        }
        dial().performTouchInput {
            down(Offset(width * 0.9f, centerY))
            moveTo(Offset(centerX, height * 0.1f))
        }
        val draft = current()
        assertTrue(sent.isEmpty())

        compose.runOnIdle { target.value = 18.0 }
        assertEquals(draft, current())
        dial().performTouchInput { up() }

        assertEquals(listOf(draft.toDouble()), sent)
    }

    @Test
    fun `cancelled drag sends nothing and still accepts later readings`() {
        val target = mutableStateOf<Double?>(22.0)
        val sent = mutableListOf<Double>()
        compose.setContent {
            MaterialTheme { OrbitTemperatureControl("ac", target.value, bounds, true, sent::add) }
        }
        dial().performTouchInput {
            down(Offset(width * 0.9f, centerY))
            moveTo(Offset(centerX, height * 0.1f))
            cancel()
        }
        compose.runOnIdle { target.value = 28.0 }

        assertTrue(sent.isEmpty())
        assertEquals(28f, current())
    }

    @Test
    fun `pending blocks adjustment and completion discards an unconfirmed draft`() {
        val enabled = mutableStateOf(true)
        val sent = mutableListOf<Double>()
        compose.setContent {
            MaterialTheme { OrbitTemperatureControl("ac", 22.0, bounds, enabled.value, sent::add) }
        }
        dial().performSemanticsAction(SemanticsActions.SetProgress) { it(23f) }
        assertEquals(23f, current())
        compose.runOnIdle { enabled.value = false }
        dial().assertIsNotEnabled()
        assertTrue(SemanticsActions.SetProgress !in dial().fetchSemanticsNode().config)
        dial().performTouchInput {
            down(Offset(width * 0.9f, centerY))
            up()
        }
        assertEquals(listOf(23.0), sent)

        compose.runOnIdle { enabled.value = true }

        assertEquals(22f, current())
        assertEquals(listOf(23.0), sent)
    }

    @Test
    fun `unknown reading stays unknown until the user chooses a value`() {
        val sent = mutableListOf<Double>()
        compose.setContent {
            MaterialTheme { OrbitTemperatureControl("ac", null, bounds, true, sent::add) }
        }
        compose.onNodeWithText("Нет данных", useUnmergedTree = true).assertExists()
        assertTrue(dial().fetchSemanticsNode().config[SemanticsProperties.StateDescription].contains("Нет данных"))

        dial().performSemanticsAction(SemanticsActions.SetProgress) { it(24f) }

        assertEquals(24f, current())
        assertEquals(listOf(24.0), sent)
    }

    @Test
    fun `temperature is adjusted on the circle without step buttons`() {
        compose.setContent {
            MaterialTheme { OrbitTemperatureControl("ac", 22.0, bounds, true, {}) }
        }

        compose.onNodeWithContentDescription("Увеличить температуру").assertDoesNotExist()
        compose.onNodeWithContentDescription("Уменьшить температуру").assertDoesNotExist()
        assertTrue(SemanticsActions.SetProgress in dial().fetchSemanticsNode().config)
    }

    private fun dial() = compose.onNodeWithContentDescription("Температура")

    private fun current() = dial().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
}
