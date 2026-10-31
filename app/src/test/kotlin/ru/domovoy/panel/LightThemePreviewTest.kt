package ru.domovoy.panel

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.domovoy.panelLightScheme
import ru.domovoy.panelTypography
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.assertTrue

/** Candidate palettes use the actual card controls; alternatives remain test-only until selected. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LightThemePreviewTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `neutral white with mint and peach`() = capture("white", panelLightScheme)

    @Test
    fun `warm porcelain with plum and peach`() = capture(
        "porcelain",
        panelLightScheme.copy(
            primary = Color(0xFF735073), onPrimary = Color.White,
            primaryContainer = Color(0xFFF0E2EF), onPrimaryContainer = Color(0xFF4C354B),
            surfaceTint = Color(0xFF735073),
            secondary = Color(0xFF006956), onSecondary = Color.White,
            secondaryContainer = Color(0xFFDDF2EB), onSecondaryContainer = Color(0xFF174E42),
            background = Color(0xFFFFFBF8), surface = Color(0xFFFFFBF8),
            onBackground = Color(0xFF3C312D), onSurface = Color(0xFF3C312D),
            onSurfaceVariant = Color(0xFF6A5B55),
            surfaceContainerLowest = Color(0xFFFCF6F1), surfaceContainerLow = Color(0xFFF9F1EB),
            surfaceContainer = Color(0xFFF5ECE5), surfaceContainerHigh = Color(0xFFEDE1D8),
            surfaceContainerHighest = Color(0xFFE5D7D0),
            outline = Color(0xFF7B6A65), outlineVariant = Color(0xFFD6C5BB),
        ),
    )

    @Test
    fun `cool silver with blue graphite and peach`() = capture(
        "silver",
        panelLightScheme.copy(
            primary = Color(0xFF245B77), onPrimary = Color.White,
            primaryContainer = Color(0xFFDCEBF6), onPrimaryContainer = Color(0xFF153D53),
            surfaceTint = Color(0xFF245B77),
            secondary = Color(0xFF5D568A), onSecondary = Color.White,
            secondaryContainer = Color(0xFFE9E3FF), onSecondaryContainer = Color(0xFF40365E),
            background = Color(0xFFF7F9FC), surface = Color(0xFFF7F9FC),
            onBackground = Color(0xFF243548), onSurface = Color(0xFF243548),
            onSurfaceVariant = Color(0xFF4C5E70),
            surfaceContainerLowest = Color(0xFFF1F4F9), surfaceContainerLow = Color(0xFFECF1F7),
            surfaceContainer = Color(0xFFE7EDF4), surfaceContainerHigh = Color(0xFFDEE6EF),
            surfaceContainerHighest = Color(0xFFD4DCE7),
            outline = Color(0xFF627385), outlineVariant = Color(0xFFB6C6D6),
        ),
    )

    private fun capture(name: String, scheme: ColorScheme) {
        val surfaces = listOf(scheme.surfaceContainerLowest, scheme.surfaceContainer, scheme.surfaceContainerHigh, scheme.surfaceContainerHighest)
        surfaces.forEach { surface ->
            assertTrue(contrast(scheme.onSurface, surface) >= 4.5, "$name: primary text on $surface")
            assertTrue(contrast(scheme.onSurfaceVariant, surface) >= 4.5, "$name: secondary text on $surface")
            assertTrue(contrast(scheme.primary, surface) >= 3.0, "$name: controls on $surface")
            assertTrue(contrast(scheme.tertiary, surface) >= 3.0, "$name: peach values on $surface")
            assertTrue(contrast(scheme.error, surface) >= 3.0, "$name: errors on $surface")
        }
        assertTrue(contrast(scheme.onPrimary, scheme.primary) >= 4.5, "$name: power glyph")
        compose.setContent {
            MaterialTheme(colorScheme = scheme, typography = panelTypography) {
                Surface {
                    PanelRooms(
                        acs = Flat.acs, curtains = Flat.curtains, strips = Flat.strips,
                        recuperators = Flat.recuperators, bulbs = Flat.bulbs, launchers = Flat.launchers,
                        now = Flat.NOW, yandexInterval = Flat.YANDEX_INTERVAL, tuyaInterval = Flat.TUYA_INTERVAL,
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/theme-light-$name.png")
    }

    private fun luminance(color: Color): Double {
        fun channel(c: Float): Double = if (c <= 0.04045f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        return channel(color.red) * 0.2126 + channel(color.green) * 0.7152 + channel(color.blue) * 0.0722
    }

    private fun contrast(a: Color, b: Color): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }
}
