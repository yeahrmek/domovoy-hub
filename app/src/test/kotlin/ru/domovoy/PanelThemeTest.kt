package ru.domovoy

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Brand roles stay readable on every wall surface in both themes. */
class PanelThemeTest {
    /**
     * The four steps a tile's card can sit on, brightest-asserting last. The order is `surface`'s in
     * `TileLayout.kt`: a tile nobody has read sits on `Lowest`, an off one on `Container`, a failing
     * one on `High` and a lit one on `Highest`.
     */
    private fun tileSteps(scheme: ColorScheme) = listOf(
        scheme.surfaceContainerLowest,
        scheme.surfaceContainer,
        scheme.surfaceContainerHigh,
        scheme.surfaceContainerHighest,
    )

    @Test
    fun `orbit uses lime actions with peach controls on white and graphite`() {
        assertEquals(Color(0xFF4C6D13), panelLightScheme.primary)
        assertEquals(Color(0xFFB3DF5A), panelLightScheme.primaryFixedDim)
        assertEquals(Color(0xFFB3DF5A), panelDarkScheme.primaryFixedDim)
        assertEquals(Color(0xFF4E5D98), panelLightScheme.secondary)
        assertEquals(Color(0xFF9E492E), panelLightScheme.tertiary)
        assertEquals(Color(0xFF232735), panelLightScheme.onSurface)
        assertEquals(Color(0xFFFAFAFC), panelLightScheme.background)
        assertEquals(Color(0xFFF0F1F4), panelLightScheme.surfaceContainer)
        assertEquals(Color(0xFF14171C), panelDarkScheme.background)
        assertEquals(Color(0xFF20252D), panelDarkScheme.surfaceContainer)
        assertEquals(Color(0xFFB3DF5A), panelDarkScheme.primary)
        assertEquals(Color(0xFFAEBFFF), panelDarkScheme.secondary)
        assertEquals(Color(0xFFFF865F), panelDarkScheme.tertiary)
        assertTrue(hueDegreesApart(panelDarkScheme.tertiary, panelDarkScheme.error) > 25.0)
    }

    @Test
    fun `brand foregrounds clear 4 point 5 on action and navigation surfaces`() {
        listOf(panelLightScheme, panelDarkScheme).forEach { scheme ->
            listOf(
                "primary" to (scheme.onPrimary to scheme.primary),
                "primaryFixedDim" to (scheme.onPrimaryFixed to scheme.primaryFixedDim),
                "primaryContainer" to (scheme.onPrimaryContainer to scheme.primaryContainer),
                "secondary" to (scheme.onSecondary to scheme.secondary),
                "secondaryContainer" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
                "tertiary" to (scheme.onTertiary to scheme.tertiary),
                "tertiaryContainer" to (scheme.onTertiaryContainer to scheme.tertiaryContainer),
                "error" to (scheme.onError to scheme.error),
                "errorContainer" to (scheme.onErrorContainer to scheme.errorContainer),
            ).forEach { (role, pair) ->
                assertAtLeast(4.5, contrast(pair.first, pair.second), "content on $role")
            }
            tileSteps(scheme).forEach { step ->
                assertAtLeast(4.5, contrast(scheme.onSurfaceVariant, step), "secondary text on $step")
                assertAtLeast(3.0, contrast(scheme.outline, step), "control outline on $step")
            }
        }
    }

    @Test
    fun `the tile ramp is ordered, and its lowest step is the one nearest the background`() {
        // State surfaces progress away from the background in both themes.
        listOf(panelLightScheme, panelDarkScheme).forEach { scheme ->
            val steps = tileSteps(scheme).map(::luminance)
            val towardsCards = if (scheme == panelLightScheme) -1 else 1
            steps.zipWithNext { lower, higher ->
                assertTrue(
                    (higher - lower) * towardsCards > 0,
                    "the tile ramp is not monotonic: $steps",
                )
            }
            // Unknown readings remain visible without becoming the loudest card.
            val background = luminance(scheme.background)
            val distances = tileSteps(scheme).map { kotlin.math.abs(luminance(it) - background) }
            assertEquals(
                distances.min(),
                distances.first(),
                "the unknown step is not the step nearest the background",
            )
            // And on the cards' side of it, so that it is still a card and not a cut-out.
            assertTrue(
                (luminance(scheme.surfaceContainerLowest) - background) * towardsCards > 0,
                "the unknown step is on the wrong side of the background",
            )
        }
    }

    @Test
    fun `every word on a tile clears 4 point 5 on every step it can be written on`() {
        // Names, promoted values and status text can appear on every state surface.
        listOf(panelLightScheme, panelDarkScheme).forEach { scheme ->
            tileSteps(scheme).forEach { step ->
                assertAtLeast(4.5, contrast(scheme.onSurface, step), "onSurface on $step")
            }
        }
    }

    @Test
    fun `the accent clears 3 on every step, in both schemes`() {
        // Controls and state marks need 3:1 against their adjacent surface.
        listOf(panelLightScheme, panelDarkScheme).forEach { scheme ->
            tileSteps(scheme).forEach { step ->
                assertAtLeast(3.0, contrast(scheme.primary, step), "primary on $step")
            }
            // The power symbol is written *on* the accent, and that one is text.
            assertAtLeast(4.5, contrast(scheme.onPrimary, scheme.primary), "onPrimary on the accent")
        }
    }

    @Test
    fun `red still reads where the panel spends it`() {
        // The offline glyph sits on the failing step and the group-failure outline is drawn on all
        // four. Their shape and adjacent text also communicate the failure.
        listOf(panelLightScheme, panelDarkScheme).forEach { scheme ->
            tileSteps(scheme).forEach { step ->
                assertAtLeast(3.0, contrast(scheme.error, step), "error on $step")
            }
        }
    }

    @Test
    fun `the accent is nowhere near the error, in hue`() {
        // Primary control colour must remain distinct from a failure.
        listOf(panelLightScheme, panelDarkScheme).forEach { scheme ->
            val apart = hueDegreesApart(scheme.primary, scheme.error)
            assertTrue(
                apart > 60.0,
                "the accent ${scheme.primary} is ${apart.toInt()}° from the error ${scheme.error}",
            )
        }
    }

    private fun assertAtLeast(
        bar: Double,
        actual: Double,
        what: String,
    ) = assertTrue(actual >= bar, "$what is ${"%.2f".format(actual)}:1, under $bar:1")

    /** WCAG relative luminance. The channel transfer function is sRGB's, not a gamma of 2.2. */
    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }

    /** WCAG contrast, which is a ratio of luminances and knows nothing about hue. */
    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val (x, y) = luminance(a) to luminance(b)
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }

    /** The short way round the colour wheel between two colours, 0° to 180°. */
    private fun hueDegreesApart(
        a: Color,
        b: Color,
    ): Double {
        val apart = kotlin.math.abs(hue(a) - hue(b))
        return min(apart, 360.0 - apart)
    }

    /** HSL hue in degrees. Grey has no hue and no colour the panel spends is grey. */
    private fun hue(color: Color): Double {
        val (r, g, b) = Triple(color.red.toDouble(), color.green.toDouble(), color.blue.toDouble())
        val high = maxOf(r, g, b)
        val span = high - minOf(r, g, b)
        val degrees = when {
            span == 0.0 -> 0.0
            high == r -> 60.0 * (((g - b) / span) % 6.0)
            high == g -> 60.0 * ((b - r) / span + 2.0)
            else -> 60.0 * ((r - g) / span + 4.0)
        }
        return (degrees + 360.0) % 360.0
    }
}
