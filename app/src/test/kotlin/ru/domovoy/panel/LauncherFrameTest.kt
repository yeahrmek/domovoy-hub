package ru.domovoy.panel

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
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
import ru.domovoy.panelDarkScheme
import ru.domovoy.panelLightScheme
import ru.domovoy.panelTypography
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalRoborazziApi::class)
class LauncherFrameTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `available vacuum has a quiet frame in light theme`() = frame(dark = false)

    @Test
    fun `available vacuum has a quiet frame in dark theme`() = frame(dark = true)

    private fun frame(dark: Boolean) {
        val scheme = if (dark) panelDarkScheme else panelLightScheme
        compose.setContent {
            MaterialTheme(colorScheme = scheme, typography = panelTypography) {
                Surface {
                    LauncherTile(launcherTiles { true }.last(), modifier = Modifier.width(350.dp).testTag("vacuum"), showRoom = true)
                }
            }
        }
        val path = "build/outputs/card-reordering/vacuum-${if (dark) "dark" else "light"}.png"
        compose.onNodeWithTag("vacuum").captureRoboImage(path, roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        val image = ImageIO.read(File(path))
        val edge = 0..2
        assertTrue(edge.any { image.getRGB(it, image.height / 2) == scheme.outlineVariant.toArgb() }, "The card needs the same outline as other devices")
    }
}
