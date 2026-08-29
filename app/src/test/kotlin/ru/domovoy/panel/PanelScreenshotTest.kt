package ru.domovoy.panel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
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
import ru.domovoy.panelTypography

/**
 * What the panel actually looks like, recorded as images.
 *
 * Everything else about the mosaic is tested as a pure function — [TileLayoutTest] asks [hue],
 * [mood] and [span] what they answer, and gets an enum back. None of that can see a tile that came
 * out unreadable, a row that wrapped, or a palette that collapsed in one of the two themes. The
 * things this file is here to catch are exactly the ones with no return value to assert on:
 *
 * - **The palette.** `PanelTheme.kt` carries a table of CIE ΔE separations between the three tile
 *   families and a plain off tile, in both schemes. Nothing has ever checked that the wall matches
 *   it, and the failure mode is a colour retouched in light drifting in dark, which is the half of
 *   the day nobody is looking at.
 * - **The geometry.** Six columns against 753 dp, halves and thirds, 22 and 18 dp corners, 72 dp
 *   circles. All of it is in docs/ui.md and in no assertion — and four columns was the first draft,
 *   thrown out only because somebody held the tablet up to it.
 * - **The two group rules that have a shape**: which bulbs leave the row of circles, and what a
 *   room's heading looks like when it has bad news.
 *
 * **Recording and checking.** The reference images live in `src/test/screenshots/` and are
 * committed. `verifyRoborazziDebug` compares against them and fails on a difference, writing the
 * expected, the actual and the diff into `build/outputs/roborazzi/`. `recordRoborazziDebug`
 * rewrites them, and is the thing to run — and then to *look at* — after a deliberate change:
 *
 * ```
 * source scripts/env.sh && ./gradlew verifyRoborazziDebug
 * source scripts/env.sh && ./gradlew recordRoborazziDebug
 * ```
 *
 * A plain `./gradlew test` runs these as ordinary tests and neither records nor compares, so the
 * other 40-odd tests keep costing what they cost.
 *
 * **These render on the JVM, not on the tablet.** Robolectric draws with its own font and its own
 * text layout, so an image recorded here is a picture of the panel's *layout and palette* and not a
 * pixel-exact preview of the Galaxy Tab. There is no CI, so the references are whatever machine
 * last recorded them; a diff that is nothing but text antialiasing is a machine difference rather
 * than a regression. The geometry — spans, corners, circle sizes, the 64 dp touch floor — is what
 * these images are trusted for.
 */
@RunWith(RobolectricTestRunner::class)
// The wall, in numbers: 1600 × 2560 px at 340 dpi is 753 × 1204 dp, portrait, which is the
// orientation the tablet is mounted in. Six columns is sized from that 753 and from nothing else,
// so a screenshot at any other width tests a panel that does not exist. `sdk = 36` is targetSdk;
// the module compiles against 37 deliberately (see app/build.gradle.kts) but the runtime behaviour
// under test is 36's.
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
// Robolectric's legacy mode draws nothing — every canvas operation is a no-op and every screenshot
// comes out blank. NATIVE is what makes these pictures.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the panel on Главная, light`() {
        capture("panel-home-light", panelLightScheme) { Panel() }
    }

    @Test
    fun `the panel on Главная, dark`() {
        // The wall is on this scheme from 19:00 to 07:00 — half of every day, and the half nobody
        // is watching when it switches. It gets the same picture taken of it as light does.
        capture("panel-home-dark", panelDarkScheme) { Panel() }
    }

    @Test
    fun `the tile colours, light`() {
        capture("tiles-light", panelLightScheme) { TileMatrix() }
    }

    @Test
    fun `the tile colours, dark`() {
        capture("tiles-dark", panelDarkScheme) { TileMatrix() }
    }

    @Test
    fun `the lights group`() {
        // Коридор: three lamps the panel has a value for, drawn as circles under one line, and the
        // fourth — which has never reported — broken out as its own named tile above them.
        val koridor = Flat.bulbs.tiles.filter { it.room == "Коридор" }
        val group = bulbGroup(koridor)
        capture("lights-group", panelLightScheme) {
            Column {
                group.brokenOut.forEach { tile ->
                    BulbTile(tile = tile, now = Flat.NOW, error = null)
                }
                BulbCircles(group = group, now = Flat.NOW)
            }
        }
    }

    @Test
    fun `the two states of a heading, and the longest room name in the flat`() {
        // The headings alone, with nothing else in the frame — the same reason [TileMatrix] draws
        // cards rather than tiles. A capture of the whole panel would put the marked room several
        // sections down its own scroll and out of the picture.
        //
        // The mark is a shape as much as a colour: Samsung's blue light filter warms every red on
        // this tablet when it is on, which is what erodes an error colour against a neutral.
        //
        // "Маленькая детская" is the longest name in `ROOM_ORDER` and is here because the tab strip
        // clipped `Гардеробная` mid-word — the failure this whole shape replaces. A heading is
        // 753 dp wide and wraps rather than clips, so what this picture has to show is that the
        // longest name the flat has does not need to.
        capture("headings", panelLightScheme) {
            Column {
                SectionHeading(title = "Коридор", marked = false)
                SectionHeading(title = "Спальня", marked = true)
                SectionHeading(title = "Маленькая детская", marked = false)
            }
        }
    }

    /**
     * The panel as `MainActivity` builds it: one of the two schemes, a [Surface] under it, and the
     * real [PanelRooms] on top — not a stand-in. A screenshot of a composable assembled only in the
     * test would be a picture of the test.
     */
    private fun capture(
        name: String,
        scheme: ColorScheme,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            // Both halves of the theme, as `MainActivity` passes them. The scheme alone would leave
            // these pictures on Material's phone type scale while the wall is on the panel's own,
            // which is a screenshot of a panel that does not exist — the same trap as capturing at
            // a width the tablet is not.
            MaterialTheme(colorScheme = scheme, typography = panelTypography) {
                Surface { content() }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Composable
    private fun Panel() {
        PanelRooms(
            acs = Flat.acs,
            curtains = Flat.curtains,
            strips = Flat.strips,
            recuperators = Flat.recuperators,
            bulbs = Flat.bulbs,
            launchers = Flat.launchers,
            now = Flat.NOW,
            yandexInterval = Flat.YANDEX_INTERVAL,
            tuyaInterval = Flat.TUYA_INTERVAL,
        )
    }

    /**
     * Every colour a tile can be: the three hues across, the four moods down, plus the outlined
     * case that only the recuperators have — a filled red tile is *this device*, an outlined one is
     * all five.
     *
     * [TileCard] directly rather than one real tile of each kind, because the thing being recorded
     * is the twelve-plus-one colour pairs and nothing else. A grid of real tiles would take the
     * same picture with four sliders and eleven status lines in front of it, and would change every
     * time one of those did.
     */
    @Composable
    private fun TileMatrix() {
        Column(modifier = Modifier.padding(8.dp)) {
            TileMood.entries.forEach { mood ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    TileHue.entries.forEach { hue ->
                        TileCard(hue = hue, mood = mood, span = HALF_SPAN, modifier = Modifier.weight(1f)) {
                            TileHeading(
                                glyph = R.drawable.ic_bulb,
                                name = "$hue · $mood",
                                span = HALF_SPAN,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            }
            // The group failure outline, on a tile that is otherwise ordinary. It is the one piece
            // of tile paint that is a border rather than a fill, and the only reason it exists is
            // to be told apart from the filled red above it.
            Row(modifier = Modifier.fillMaxWidth()) {
                TileCard(
                    hue = TileHue.Climate,
                    mood = TileMood.On,
                    span = HALF_SPAN,
                    modifier = Modifier.weight(1f),
                    border = groupFailureBorder("not updating"),
                ) {
                    TileHeading(
                        glyph = R.drawable.ic_bulb,
                        name = "группа не читается",
                        span = HALF_SPAN,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                // The row's other half, left empty: the outline is one case and not three, and a
                // second card here would be a colour pair that does not exist.
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
