package ru.domovoy.panel

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.domovoy.R
import ru.domovoy.panelDarkScheme
import ru.domovoy.panelTypography
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** The art slot is 80 dp regardless of drawable; the power control has a 64 dp touch target. */
@RunWith(RobolectricTestRunner::class)
// Match the wall tablet's portrait size and density.
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TileDeviceArtTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `both status lines fit at increased system font size`() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f)) {
                MaterialTheme(colorScheme = panelDarkScheme, typography = panelTypography) {
                    AcTile(Flat.acs.tiles.first(), Flat.NOW, modifier = Modifier.width(251.dp), error = "failed")
                }
            }
        }

        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("failed").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.size)
        assertFalse(layouts.single().didOverflowHeight, "The second status line must have room for its full height")
    }

    @Test
    fun `tile power switches announce device and room`() {
        compose.setContent {
            MaterialTheme(colorScheme = panelDarkScheme) {
                Row {
                    AcTile(Flat.acs.tiles.first(), Flat.NOW, modifier = Modifier.weight(1f))
                    BulbTile(Flat.bulbs.tiles.first(), Flat.NOW, modifier = Modifier.weight(1f))
                    LightStripTile(Flat.strips.tiles.first(), Flat.NOW, modifier = Modifier.weight(1f))
                    RecuperatorTile(Flat.recuperators.tiles.first(), Flat.NOW, modifier = Modifier.weight(1f))
                }
            }
        }

        listOf("Кондиционер, Зал", "Лампа 1, Коридор", "Лента, Коридор", "Бризер, Зал").forEach { device ->
            compose.onNodeWithContentDescription("Питание: $device").assertIsOn()
        }
    }

    @Test
    fun `sheet power switch announces its device and room`() {
        compose.setContent {
            MaterialTheme(colorScheme = panelDarkScheme) {
                DeviceSheet(sheet(Flat.acs.tiles.first(), Flat.NOW, error = null), onDismiss = {})
            }
        }

        compose.onNodeWithContentDescription("Питание: Кондиционер, Зал").assertIsOn()
    }

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
    fun `power is a button with a wall-sized touch target`() {
        var toggles = 0
        compose.setContent {
            MaterialTheme(colorScheme = panelDarkScheme) {
                TilePowerButton(
                    deviceName = "Лампа",
                    room = null,
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
