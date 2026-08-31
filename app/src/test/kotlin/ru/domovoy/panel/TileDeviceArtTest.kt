package ru.domovoy.panel

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.domovoy.R
import ru.domovoy.panelDarkScheme
import kotlin.test.assertEquals

/**
 * How big device art comes out, which is the one thing about the panel's art that a test can hold.
 *
 * [TileLayoutTest] asks which drawable a tile wears and gets a resource id back; a screenshot says
 * what the wall looks like, but it says it as a rectangle that moved. Neither of them fails with
 * "the device art became phone-sized" written on it, and that is the regression this file is for.
 *
 * **80 dp is written out here rather than read from `DEVICE_ART_SIZE`.** A test that asserts a constant
 * against itself passes whatever the constant becomes, which is the one thing this must not do.
 *
 * Robolectric measures rather than draws — no `GraphicsMode` and no image — so this costs a layout
 * pass and not a bitmap.
 */
@RunWith(RobolectricTestRunner::class)
// The wall's own metrics, the same ones the screenshots are taken at: 1600 × 2560 px at 340 dpi is
// 753 × 1204 dp portrait. A glyph asserted at any other density is a glyph on a tablet nobody owns.
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
class TileDeviceArtTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a tile's device art fills the wall's art row`() {
        compose.setContent {
            MaterialTheme(colorScheme = panelDarkScheme) {
                TileDeviceArt(R.drawable.device_art_air_conditioner)
            }
        }

        compose.onRoot().assertHeightIsEqualTo(80.dp)
    }

    @Test
    fun `bulb variants are drawn at the same size`() {
        compose.setContent {
            MaterialTheme(colorScheme = panelDarkScheme) {
                TileDeviceArt(R.drawable.device_art_bulb_on)
            }
        }

        compose.onRoot().assertHeightIsEqualTo(80.dp)
    }

    @Test
    fun `power is a button with a wall-sized touch target`() {
        var toggles = 0
        compose.setContent {
            MaterialTheme(colorScheme = panelDarkScheme) {
                TilePowerButton(
                    isOn = true,
                    mood = TileMood.On,
                    onToggle = { toggles++ },
                )
            }
        }

        compose.onNode(isToggleable())
            .assertHeightIsEqualTo(64.dp)
            .performClick()
        compose.runOnIdle { assertEquals(1, toggles) }
    }
}
