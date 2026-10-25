package ru.domovoy.panel

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LevelControlsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `completed command discards an unconfirmed slider draft`() {
        val enabled = mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                LevelControl(
                    id = "ac",
                    value = 22.0,
                    bounds = ru.domovoy.core.Bounds(16.0, 30.0, 1.0),
                    onSetValue = {},
                    stepButtons = true,
                    enabled = enabled.value,
                )
            }
        }
        compose.onNodeWithContentDescription("Увеличить").performClick()
        assertEquals(23f, sliderValue())
        compose.runOnIdle { enabled.value = false }

        compose.runOnIdle { enabled.value = true }

        assertEquals(22f, sliderValue())
    }

    @Test
    fun `AC slider follows a new target from the poll`() = followsPoll(22f, 26f) { value ->
        AcTile(Flat.acs.tiles.first().copy(targetTemperature = value.toDouble()), Flat.NOW)
    }

    @Test
    fun `curtain slider follows a new position from the poll`() = followsPoll(40f, 80f) { value ->
        CurtainTile(Flat.curtains.tiles.single().copy(openPercent = value.toDouble()), Flat.NOW)
    }

    @Test
    fun `strip slider follows a new brightness from the poll`() = followsPoll(60f, 80f) { value ->
        LightStripTile(Flat.strips.tiles.first().copy(brightnessPercent = value.toDouble()), Flat.NOW)
    }

    @Test
    fun `bulb slider follows a new brightness from the poll`() = followsPoll(48f, 80f) { value ->
        BulbTile(Flat.bulbs.tiles[1].copy(brightnessPercent = value.toDouble()), Flat.NOW)
    }

    @Test
    fun `sheet step buttons use the latest polled target`() {
        val target = mutableFloatStateOf(22f)
        var sent: Double? = null
        compose.setContent {
            MaterialTheme {
                DeviceSheet(
                    sheet = sheet(Flat.acs.tiles.first().copy(targetTemperature = target.floatValue.toDouble()), Flat.NOW, null),
                    onDismiss = {},
                    onSetLevel = { sent = it },
                )
            }
        }
        compose.runOnIdle { target.floatValue = 26f }

        compose.onNodeWithContentDescription("Увеличить").performClick()

        compose.runOnIdle { assertEquals(27.0, sent) }
    }

    @Test
    fun `a poll does not move the slider under a dragging finger`() {
        val target = mutableFloatStateOf(22f)
        var sent: Double? = null
        compose.setContent {
            MaterialTheme {
                AcTile(
                    Flat.acs.tiles.first().copy(targetTemperature = target.floatValue.toDouble()),
                    Flat.NOW,
                    onSetTemperature = { _, value -> sent = value },
                )
            }
        }
        slider().performTouchInput {
            down(center)
            moveTo(Offset(width * 0.8f, centerY))
        }
        val dragged = sliderValue()

        compose.runOnIdle { target.floatValue = 18f }
        assertEquals(dragged, sliderValue())
        slider().performTouchInput { up() }
        compose.runOnIdle { assertEquals(dragged.toDouble(), sent) }
        compose.runOnIdle { target.floatValue = 28f }
        assertEquals(28f, sliderValue())
    }

    @Test
    fun `switching same-name devices discards the previous device draft`() {
        val device = mutableStateOf(Flat.acs.tiles.first())
        compose.setContent {
            MaterialTheme { DeviceSheet(sheet(device.value, Flat.NOW, null), onDismiss = {}) }
        }
        compose.onNodeWithContentDescription("Увеличить").performClick()
        assertEquals(23f, sliderValue())

        compose.runOnIdle { device.value = device.value.copy(id = "another-ac") }

        assertEquals(22f, sliderValue())
    }

    @Test
    fun `a cancelled drag does not prevent later poll updates`() {
        val target = mutableFloatStateOf(22f)
        compose.setContent {
            MaterialTheme {
                AcTile(Flat.acs.tiles.first().copy(targetTemperature = target.floatValue.toDouble()), Flat.NOW)
            }
        }
        slider().performTouchInput {
            down(center)
            moveTo(Offset(width * 0.8f, centerY))
            cancel()
        }

        compose.runOnIdle { target.floatValue = 28f }

        assertEquals(28f, sliderValue())
    }

    private fun followsPoll(initial: Float, updated: Float, content: @Composable (Float) -> Unit) {
        val value = mutableFloatStateOf(initial)
        compose.setContent { MaterialTheme { content(value.floatValue) } }
        assertEquals(initial, sliderValue())

        compose.runOnIdle { value.floatValue = updated }

        assertEquals(updated, sliderValue())
    }

    private fun slider() = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))

    private fun sliderValue(): Float = slider().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
}
