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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
 * - **The geometry.** Twelve columns against 753 dp, thirds and quarters, one 22 dp corner, one
 *   296 dp tile height across every kind. All of it is in docs/ui.md and in no assertion, and the
 *   one thing an image says that no assertion here does is whether the bottom edges of two
 *   different *kinds* of tile actually land on the same line.
 * - **The two group rules that have a shape**: which bulbs stay out of their room's lights group,
 *   and what a room's heading looks like when it has bad news.
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
// orientation the tablet is mounted in. The column widths are sized from that 753 and from nothing
// else, so a screenshot at any other width tests a panel that does not exist. `sdk = 36` is targetSdk;
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

    // **A taller frame than the wall, and only here.** [TileMatrix] is thirteen colour swatches
    // rather than a picture of the panel — the wall's own 1204 dp is what the two Главная captures
    // are for, and it is load-bearing there. A tile is 296 dp tall now that every kind fills the
    // same five slots and the status slot is capped at two lines, so five rows of them — four moods
    // and the outlined case — come to 1480 dp, and in a 1204 dp frame the failing row and the
    // outline simply fell off the bottom and were recorded as nothing at all. The frame stays at
    // 1700 rather than following the tile down: it was 1656 dp of swatch and is 1480, and a height
    // that has to be retuned every time a slot moves is a height that will be wrong once. The width
    // stays 753 — these cards sit three across, which is the wall's own wide column.
    @Test
    @Config(qualifiers = "w753dp-h1700dp-port-340dpi")
    fun `the tile colours, light`() {
        capture("tiles-light", panelLightScheme) { TileMatrix() }
    }

    @Test
    @Config(qualifiers = "w753dp-h1700dp-port-340dpi")
    fun `the tile colours, dark`() {
        capture("tiles-dark", panelDarkScheme) { TileMatrix() }
    }

    // **What a tap opens, over the wall it was tapped on**, in both schemes — because the sheet is a
    // surface of its own and a palette that reads on twelve small cards is not automatically a
    // palette that reads on one 753 dp panel with a scrim behind it.
    //
    // `xfj-01` is the recuperator, and it is the one chosen deliberately: it is the tile whose four
    // separately-timestamped datapoints the wall prints one age for, so its sheet is the four rows
    // and four ages that are the whole argument for a sheet existing. It is drawn over the real
    // Главная rather than on its own, which is the only way to see the two things a picture is
    // needed for at all — that the tiles behind the scrim are still legible, and that the sheet is
    // unmistakably in front of them.
    @Test
    fun `the device sheet, light`() {
        capture("device-sheet-light", panelLightScheme) { Panel(open = "xfj-01") }
    }

    @Test
    fun `the device sheet, dark`() {
        capture("device-sheet-dark", panelDarkScheme) { Panel(open = "xfj-01") }
    }

    @Test
    fun `the lights group`() {
        // Коридор: three lamps the panel has a value for, standing behind one group tile, and the
        // fourth — which has never reported — as its own named tile beside it.
        //
        // Three cards at the quarter width they take on the wall, which is what this picture is for:
        // the group tile has to come out the same 296 dp as the lamp next to it, and the thing that
        // would say otherwise is a picture rather than an assertion. The open and closed states are
        // both here because they differ by one line of text in a slot that is reserved either way —
        // if opening a group ever moved the card's bottom edge, this is where it would show.
        val koridor = Flat.bulbs.tiles.filter { it.room == "Коридор" }
        val group = bulbGroup(koridor)
        capture("lights-group", panelLightScheme) {
            Row {
                group.brokenOut.forEach { tile ->
                    BulbTile(tile = tile, now = Flat.NOW, error = null, modifier = Modifier.weight(1f))
                }
                BulbGroupTile(group = group, now = Flat.NOW, modifier = Modifier.weight(1f))
                BulbGroupTile(group = group, now = Flat.NOW, open = true, modifier = Modifier.weight(1f))
                // The wall's fourth column, left empty: these are quarter-width tiles and a row of
                // three at a third each would be a picture of a width the panel does not use.
                Spacer(modifier = Modifier.weight(1f))
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
    private fun Panel(
        /** Which device's sheet is open over the wall, or null for the wall on its own. */
        open: String? = null,
    ) {
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
            openSheet = remember { mutableStateOf(open) },
        )
    }

    /**
     * Every way a tile can be painted: the three hues across, the four moods down, plus the outlined
     * case — which every kind of tile has now rather than only the recuperators. **The four rows are
     * four steps of one neutral ramp and the columns differ only in their accents**, which is the
     * whole of what this picture is here to hold: a card that goes back to being filled with its
     * family's container shows up here as three coloured rows before it shows up anywhere else.
     *
     * [TileCard] directly rather than one real tile of each kind, because the thing being recorded
     * is the twelve-plus-one pairs and nothing else. A grid of real tiles would take the same
     * picture with four sliders and eleven status lines in front of it, and would change every time
     * one of those did.
     */
    @Composable
    private fun TileMatrix() {
        Column(modifier = Modifier.padding(8.dp)) {
            TileMood.entries.forEach { mood ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    TileHue.entries.forEach { hue ->
                        TileCard(
                            anatomy = swatch("$hue · $mood"),
                            hue = hue,
                            paint = TilePaint(mood, groupFailing = false),
                            modifier = Modifier.weight(1f),
                            // **The power button is here because it is a mark now.** It takes the family
                            // accent when the tile is on and neutral grey otherwise, so a row of
                            // three that is one colour in this picture is Material's `primary`
                            // leaking back in — a lamp with a blue power button on it.
                            //
                            // Checked on the failing row as well as the lit one, and that pair is
                            // the picture: a device that last reported on keeps its power direction,
                            // and it is grey there because nobody can confirm it any more.
                            toggle = {
                                TilePowerButton(
                                    isOn = mood == TileMood.On || mood == TileMood.Failing,
                                    hue = hue,
                                    mood = mood,
                                    onToggle = {},
                                )
                            },
                        )
                    }
                }
            }
            // The group failure outline, on a tile that is otherwise ordinary and stays ordinary:
            // the surface, the glyph, the promoted value and the mark all go on saying what they
            // said, and the border is the only thing that is new. That is the picture — the tile
            // beside it in the `On · Climate` cell above should differ from this one by a red line
            // and by nothing else.
            Row(modifier = Modifier.fillMaxWidth()) {
                TileCard(
                    anatomy = swatch("группа не читается"),
                    hue = TileHue.Climate,
                    paint = TilePaint(TileMood.On, groupFailing = true),
                    modifier = Modifier.weight(1f),
                    // The same power button the `On · Climate` cell above has, so that "differs by a red
                    // line and by nothing else" is still what the pair shows.
                    toggle = {
                        TilePowerButton(
                            isOn = true,
                            hue = TileHue.Climate,
                            mood = TileMood.On,
                            onToggle = {},
                        )
                    },
                )
                // The row's other two thirds, left empty: the outline is one case and not three,
                // and a second card here would be a pair that does not exist.
                Spacer(modifier = Modifier.weight(2f))
            }
        }
    }

    /**
     * One colour pair, in the anatomy every tile on the wall wears. It fills all five slots so that
     * the picture is of the card the panel actually draws — a swatch with an empty promoted value
     * and no status line would be 100 dp shorter than any real tile and would record a shape that
     * does not exist.
     */
    private fun swatch(name: String) = TileAnatomy(
        art = R.drawable.device_art_bulb_on,
        controls = TileControls.Toggle,
        // No button, like every kind but the curtain — see [TileAction]. These cards are a picture
        // of the four moods against the three hues, and a control only one kind has would be a
        // thirteenth variable in a grid that is here to hold two.
        action = null,
        name = name,
        promoted = "22 °C",
        status = "on · 2 h ago",
        detail = null,
    )
}
