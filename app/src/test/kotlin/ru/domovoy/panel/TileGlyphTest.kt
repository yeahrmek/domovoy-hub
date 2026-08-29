package ru.domovoy.panel

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.domovoy.R
import ru.domovoy.panelDarkScheme

/**
 * How big a glyph comes out, which is the one thing about the panel's art that a test can hold.
 *
 * [TileLayoutTest] asks which drawable a tile wears and gets a resource id back; a screenshot says
 * what the wall looks like, but it says it as a rectangle that moved. Neither of them fails with
 * "the glyphs went back to phone size" written on it, and that is the regression this file is for:
 * the whole of `res/drawable/` is line art meant to be read from four metres, and the size it is
 * drawn at is what decides whether it can be.
 *
 * **48 dp is written out here rather than read from `GLYPH_SIZE`.** A test that asserts a constant
 * against itself passes whatever the constant becomes, which is the one thing this must not do.
 *
 * Robolectric measures rather than draws — no `GraphicsMode` and no image — so this costs a layout
 * pass and not a bitmap.
 */
@RunWith(RobolectricTestRunner::class)
// The wall's own metrics, the same ones the screenshots are taken at: 1600 × 2560 px at 340 dpi is
// 753 × 1204 dp portrait. A glyph asserted at any other density is a glyph on a tablet nobody owns.
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
class TileGlyphTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a tile's glyph is drawn at the wall's size and not at a phone's`() {
        // Height and not width: `setContent` gives the content the whole 753 dp to be wide in, so
        // the root wraps only in the axis the glyph actually governs — which is also the axis that
        // decides how tall the tile it sits in comes out.
        compose.setContent {
            MaterialTheme(colorScheme = panelDarkScheme) {
                TileGlyph(R.drawable.ic_ac_unit)
            }
        }

        compose.onRoot().assertHeightIsEqualTo(48.dp)
    }

    @Test
    fun `the lamp on a bulb disc is the same size as every other glyph`() {
        // The disc's lamp was the one piece of art on this wall already sized for it, and the other
        // seven sat at half that beside it. One number now — `BulbCircle` reads the same constant —
        // so the row of lamps and the tiles around it cannot come out as two different sets.
        compose.setContent {
            MaterialTheme(colorScheme = panelDarkScheme) {
                TileGlyph(R.drawable.ic_bulb)
            }
        }

        compose.onRoot().assertHeightIsEqualTo(48.dp)
    }
}
