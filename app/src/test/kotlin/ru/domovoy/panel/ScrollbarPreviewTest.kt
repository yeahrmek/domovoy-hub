package ru.domovoy.panel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.domovoy.panelLightScheme
import ru.domovoy.panelTypography
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Real card variants keep the same range interaction and command callback. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w376dp-h480dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScrollbarPreviewTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `round knob changes brightness without opening the card`() = capture("round", LevelThumbShape.Round)

    @Test
    fun `oval knob changes brightness without opening the card`() = capture("oval", LevelThumbShape.Oval)

    @Test
    fun `gradient regulator follows confirmed readings and remains disabled while pending`() {
        val reading = mutableStateOf(40.0)
        val enabled = mutableStateOf(true)
        val sent = mutableListOf<Double>()
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                LevelControl("test-strip", reading.value, checkNotNull(Flat.strips.tiles.first().bounds), sent::add, enabled = enabled.value, label = "Яркость")
            }
        }
        compose.onNodeWithContentDescription("Яркость").performSemanticsAction(SemanticsActions.SetProgress) { it(80f) }
        assertEquals(listOf(80.0), sent)
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithContentDescription("Яркость").assertIsNotEnabled()
        compose.runOnIdle {
            reading.value = 45.0
            enabled.value = true
        }
        assertEquals(45f, compose.onNodeWithContentDescription("Яркость").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current)
    }

    private fun capture(name: String, shape: LevelThumbShape) {
        val tile = Flat.strips.tiles.first()
        val sent = mutableListOf<Double>()
        var opened = false
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                Surface {
                    Column(Modifier.padding(10.dp)) {
                        HomeDeviceTile(
                            anatomy = anatomy(tile, Flat.NOW, null).copy(status = power(tile.isOn)),
                            paint = paint(tile, null),
                            polledAt = Flat.NOW,
                            now = Flat.NOW,
                            interval = Flat.YANDEX_INTERVAL,
                            onOpen = { opened = true },
                            toggle = { TilePowerButton(tile.name, tile.room, true, TileMood.On, {}) },
                            controls = {
                                LevelControl(
                                    id = tile.id,
                                    value = tile.brightnessPercent,
                                    bounds = checkNotNull(tile.bounds),
                                    label = "Яркость",
                                    onSetValue = { sent += it },
                                    thumbShape = shape,
                                )
                            },
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/scrollbar-$name.png")
        compose.onNodeWithContentDescription("Яркость").performTouchInput {
            down(center)
            moveTo(Offset(width * 0.8f, centerY))
        }
        assertTrue(sent.isEmpty())
        compose.onNodeWithContentDescription("Яркость").performTouchInput { up() }
        assertEquals(1, sent.size)
        assertTrue(sent.single() in 1.0..100.0)
        assertFalse(opened)
    }
}
