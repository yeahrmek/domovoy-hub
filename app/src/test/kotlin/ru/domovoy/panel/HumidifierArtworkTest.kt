package ru.domovoy.panel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.domovoy.R
import ru.domovoy.panelDarkScheme
import ru.domovoy.panelLightScheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HumidifierArtworkTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `light artwork changes only mist and indicator and returns to the identical off image`() = verify(dark = false)

    @Test
    fun `dark artwork changes only mist and indicator and returns to the identical off image`() = verify(dark = true)

    private fun verify(dark: Boolean) {
        val mood = mutableStateOf(TileMood.Off)
        val sizes = listOf(48, 80)
        compose.setContent {
            MaterialTheme(colorScheme = if (dark) panelDarkScheme else panelLightScheme) {
                Surface {
                    Column {
                        sizes.forEach { size ->
                            if (size == 48) {
                                TileArt(R.drawable.device_art_humidifier, mood.value, Modifier.testTag("art-$size"))
                            } else {
                                TileDeviceArt(R.drawable.device_art_humidifier, Modifier.size(size.dp).testTag("art-$size"), active = mood.value == TileMood.On)
                            }
                        }
                        Row(Modifier.testTag("comparison")) {
                            listOf(false to "Выключен", true to "Работает").forEach { (active, label) ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    TileDeviceArt(R.drawable.device_art_humidifier, Modifier.size(160.dp), active = active)
                                    Text(label)
                                }
                            }
                        }
                    }
                }
            }
        }
        val off = sizes.associateWith { capture(it, dark, "off") }
        compose.runOnIdle { mood.value = TileMood.On }
        sizes.forEach { size ->
            val on = capture(size, dark, "on")
            val before = off.getValue(size)
            assertEquals(before.width, on.width)
            assertEquals(before.height, on.height)
            var mistChanges = 0
            var lightChanges = 0
            for (y in 0 until on.height) {
                for (x in 0 until on.width) {
                    if (before.getRGB(x, y) == on.getRGB(x, y)) continue
                    val px = (x + 0.5f) / on.width
                    val py = (y + 0.5f) / on.height
                    when {
                        py < 0.34f && px in 0.2f..0.8f -> mistChanges++
                        py in 0.49f..0.80f && px in 0.29f..0.41f -> lightChanges++
                        else -> error("Body or position changed at ($px, $py), size $size")
                    }
                }
            }
            assertTrue(mistChanges > 0, "Working humidifier needs visible mist at $size dp")
            assertTrue(lightChanges > 0, "Working humidifier needs a lit indicator at $size dp")
        }
        listOf(TileMood.Off, TileMood.Unknown, TileMood.Failing).forEach { state ->
            compose.runOnIdle { mood.value = state }
            sizes.forEach { size ->
                val restored = capture(size, dark, state.name)
                val before = off.getValue(size)
                assertTrue(before.getRGB(0, 0, before.width, before.height, null, 0, before.width).contentEquals(restored.getRGB(0, 0, restored.width, restored.height, null, 0, restored.width)))
            }
        }
        captureComparison(dark)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun captureComparison(dark: Boolean) {
        val file = File("build/outputs/humidifier/artwork/comparison-${if (dark) "dark" else "light"}.png")
        compose.onNodeWithTag("comparison", useUnmergedTree = true).captureRoboImage(file, roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(size: Int, dark: Boolean, state: String): BufferedImage {
        val file = File("build/outputs/humidifier/artwork/${if (dark) "dark" else "light"}-$size-$state.png")
        compose.onNodeWithTag("art-$size", useUnmergedTree = true).captureRoboImage(file, roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        return ImageIO.read(file)
    }
}
