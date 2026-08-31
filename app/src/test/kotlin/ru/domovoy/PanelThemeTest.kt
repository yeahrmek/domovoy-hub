package ru.domovoy

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The palette, measured rather than described.**
 *
 * `PanelTheme.kt` has always carried its contrast numbers in a KDoc table, and a KDoc table is a
 * claim nobody re-checks: the roles were retuned three times and the table was retyped by hand each
 * time. This computes them. It is the one thing about the wall's colour that can be asserted on the
 * JVM in milliseconds — [ru.domovoy.panel.PanelScreenshotTest] draws the rest, slowly, as pictures a
 * person has to look at.
 *
 * **What it is protecting.** The panel spends colour on three things and no more: the neutral ramp
 * the cards sit on, one violet accent on the two controls a finger uses, and red for a failure. Two
 * of those are worth an assertion apiece — that the ramp is ordered and that the accent and the
 * words clear their contrast bar on every step of it — because both fail silently in the scheme
 * nobody is looking at. The tablet runs light 07:00–19:00 and dark the rest of the day.
 */
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
    fun `the wall is the neutral surfaces and the one violet it was specified with`() {
        // The four values this change is written in. Pinned because they are the whole brief: a
        // neutral background, a neutral card, neutral text, and one accent — and because a palette
        // is the kind of thing a later commit retouches "slightly" in one scheme only.
        assertEquals(Color(0xFFF5F6F8), panelLightScheme.background)
        assertEquals(Color(0xFFE5E7EC), panelLightScheme.surfaceContainer)
        assertEquals(Color(0xFF202228), panelLightScheme.onSurface)
        assertEquals(Color(0xFF7047EB), panelLightScheme.primary)

        assertEquals(Color(0xFF191B23), panelDarkScheme.background)
        assertEquals(Color(0xFF30333D), panelDarkScheme.surfaceContainer)
        assertEquals(Color(0xFFF3F4F7), panelDarkScheme.onSurface)
    }

    @Test
    fun `the tile ramp is ordered, and its lowest step is the one nearest the background`() {
        // Which step a tile sits on is how much it is asserting — see `surface`. That only reads as
        // an ordering if the steps are actually ordered, and "actually" means in luminance and in
        // both schemes: a ramp retouched in light can invert in dark without anybody seeing it
        // until 19:00.
        listOf(panelLightScheme, panelDarkScheme).forEach { scheme ->
            val steps = tileSteps(scheme).map(::luminance)
            val towardsCards = if (scheme == panelLightScheme) -1 else 1
            steps.zipWithNext { lower, higher ->
                assertTrue(
                    (higher - lower) * towardsCards > 0,
                    "the tile ramp is not monotonic: $steps",
                )
            }
            // **A tile nobody has ever read has to be the quietest card on the wall**, and quietest
            // means nearest the background — not past it. This assertion is the one the tablet
            // rewrote: `Lowest` sat on the far side of the background from the cards, which is a
            // hole while the cards are barely off the wall and is the *largest* separation on the
            // wall once they are not. Домофон and Пылесос are `Unknown` for ever, because nothing
            // polls a launcher, so whichever way this is wrong it is wrong permanently and on the
            // two tiles that deserve it least.
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
        // `onSurface` is the whole of the wall's text after the promoted value went neutral: the
        // 44sp value, the device name and both status lines. It is written on all four steps.
        listOf(panelLightScheme, panelDarkScheme).forEach { scheme ->
            tileSteps(scheme).forEach { step ->
                assertAtLeast(4.5, contrast(scheme.onSurface, step), "onSurface on $step")
            }
        }
    }

    @Test
    fun `the accent clears 3 on every step, in both schemes`() {
        // The accent carries no text — it is the power button's fill, the slider's fill and the
        // 20 dp lit dot — so the bar is the 3:1 a graphical object needs and not 4.5:1.
        //
        // **This is the assertion `#7C4DFF` fails**, which is why dark's `primary` is a lighter tone
        // of the same violet: against the card at `#30333D` it comes out at 2.6:1, and nearly all of
        // that is carried by the blue channel the tablet's filter takes away.
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
        // four. It is the only saturated thing on the wall that is not the accent, and it is the one
        // colour whose meaning cannot be recovered from anywhere else on the card.
        listOf(panelLightScheme, panelDarkScheme).forEach { scheme ->
            tileSteps(scheme).forEach { step ->
                assertAtLeast(3.0, contrast(scheme.error, step), "error on $step")
            }
        }
    }

    @Test
    fun `the accent is nowhere near the error, in hue`() {
        // **What this whole change is about, and the one assertion the old palette fails.** The
        // amber that marked a lit lamp sat 29° from the error red — two states a metre apart on a
        // wall, said in two colours that a warm display filter walks together into a pair of browns.
        // The violet is 108° away in both schemes, and a later retouch that walks the accent back
        // towards warm fails here rather than on the wall.
        //
        // _Hue and not contrast, and not a distance in RGB either._ Both of dark's are pale, so any
        // straight-line measure calls a lavender and a pink neighbours; what tells them apart at
        // four metres is which way round the colour wheel they are. It is a proxy — the filter
        // rotates hue as well as draining it, and nothing on the JVM models that. The photograph in
        // docs/ui.md is the measurement; this is the guard rail.
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
