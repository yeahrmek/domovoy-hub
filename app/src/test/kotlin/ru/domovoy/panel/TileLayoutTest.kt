package ru.domovoy.panel

import org.junit.jupiter.api.Test
import ru.domovoy.R
import ru.domovoy.core.Bounds
import ru.domovoy.core.ColorSetting
import ru.domovoy.core.Reading
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The three decisions in the mosaic that have a right and a wrong answer. They live out here rather
 * than inside a `@Composable` because a decision no test can reach is a decision nobody checks.
 *
 * There is a Compose test dependency now — [PanelScreenshotTest] draws the panel and records what
 * it looks like — and it does not replace any of this. A screenshot says the recuperator came out
 * half-width; only the assertions below say it did so *because it reports a climate line*, and only
 * they fail with the reason written out rather than as a rectangle that moved.
 */
class TileLayoutTest {
    private val now = Instant.ofEpochSecond(1_786_000_000)

    @Test
    fun `a recuperator reporting a temperature is a wide tile`() {
        // Four columns of the twelve, because it has a second line to put there — see climateLine,
        // which is the same condition rather than a second one that can drift away from it.
        val tile = recuperator(temperature = 29.3, humidity = 32.2)

        assertNotNull(climateLine(tile))
        assertEquals(4, span(tile))
    }

    @Test
    fun `a recuperator reporting only a humidity is still a wide tile`() {
        // Either value on its own is a climate line, so either value on its own is a wide tile.
        val tile = recuperator(temperature = null, humidity = 32.2)

        assertNotNull(climateLine(tile))
        assertEquals(4, span(tile))
    }

    @Test
    fun `a recuperator reporting neither is a narrow tile`() {
        // A wide tile holding one line of "on · no speed" is a hole in the wall.
        val tile = recuperator(temperature = null, humidity = null)

        assertNull(climateLine(tile))
        assertEquals(3, span(tile))
    }

    @Test
    fun `a recuperator with nothing to report but a failure is wide anyway`() {
        // The reason it stopped updating is this tile's second line, exactly as the climate is —
        // and the tile with a second line gets the width to say it, which is this function's whole
        // rule. The reason is four words long now rather than a vendor string of unbounded length
        // (see BulbTilesTest), and the width is still the honest answer to having something to say.
        val tile = recuperator(temperature = null, humidity = null, isOn = null, error = "timed out")

        assertNull(climateLine(tile))
        assertEquals(4, span(tile))
    }

    @Test
    fun `the group's failure does not move a recuperator's span`() {
        // It fails all five at once, so letting it move spans would re-lay the whole room out every
        // time Tuya blinked. The span answers from the tile and from nothing passed beside it.
        val tile = recuperator(temperature = null, humidity = null)

        assertEquals(3, span(tile))
        assertEquals("timed out", anatomy(tile, now, groupError = "timed out").detail)
        assertEquals(3, span(tile))
    }

    @Test
    fun `a tile whose poll failed is failing, whatever it last said about being on`() {
        // Failing outranks everything: the tile is showing a value nobody has confirmed, and
        // painting it as merely "on" is the panel asserting something it does not know.
        assertEquals(TileMood.Failing, mood(isOn = true, error = "HTTP 500"))
        assertEquals(TileMood.Failing, mood(isOn = false, error = "HTTP 500"))
        assertEquals(TileMood.Failing, mood(isOn = null, error = "timeout"))
    }

    @Test
    fun `a tile that never reported is unknown, and never off`() {
        // 33 of the 116 recorded capabilities have never reported, and the tiles have always said
        // "unknown" rather than "off" for them. The colours must not undo what the strings were
        // careful about.
        assertEquals(TileMood.Unknown, mood(isOn = null, error = null))
    }

    @Test
    fun `a tile that reported says on or off`() {
        assertEquals(TileMood.On, mood(isOn = true, error = null))
        assertEquals(TileMood.Off, mood(isOn = false, error = null))
    }

    @Test
    fun `an air conditioner and a recuperator are climate`() {
        // The two things in the flat that move air. They are the same family on the wall whatever
        // vendor is behind them — one is Yandex and the other Tuya, and nobody standing in the
        // hallway cares which.
        assertEquals(TileHue.Climate, hue(ac(isOn = true)))
        assertEquals(TileHue.Climate, hue(recuperator(temperature = 29.3, humidity = 32.2)))
    }

    @Test
    fun `a bulb and a light strip are light`() {
        assertEquals(TileHue.Light, hue(bulb(isOn = true)))
        assertEquals(TileHue.Light, hue(strip(isOn = true)))
    }

    @Test
    fun `a curtain and a launcher are neutral`() {
        // Three families and no more: a fourth hue on a wall read from four metres is decoration
        // rather than information, so everything that is neither air nor light shares the quiet one.
        assertEquals(TileHue.Neutral, hue(curtain(openPercent = 100.0)))
        assertEquals(TileHue.Neutral, hue(launcher(openable = true)))
    }

    @Test
    fun `a tile's hue is its type's and does not move with its state`() {
        // The two axes are separate on purpose: hue says what kind of thing this is, mood says
        // whether the hue gets used at all. A lamp that is off is still a lamp — it is mood that
        // paints it neutral, and hue must not quietly agree by turning into something else.
        listOf(true, false, null).forEach { state ->
            assertEquals(TileHue.Light, hue(bulb(isOn = state)))
            assertEquals(TileHue.Light, hue(strip(isOn = state)))
            assertEquals(TileHue.Climate, hue(ac(isOn = state)))
            assertEquals(TileHue.Climate, hue(recuperator(temperature = 29.3, humidity = 32.2, isOn = state)))
        }
        listOf(0.0, 100.0, null).forEach { position ->
            assertEquals(TileHue.Neutral, hue(curtain(openPercent = position)))
        }
        // Failing is a state too, and the recuperator is the one tile carrying its own error.
        val failing = recuperator(temperature = null, humidity = null, isOn = null, error = "timeout")
        assertEquals(TileMood.Failing, mood(failing.isOn, failing.error))
        assertEquals(TileHue.Climate, hue(failing))
        // The launcher's failure is the app being gone, and it is not a hue either.
        assertEquals(TileHue.Neutral, hue(launcher(openable = false)))
    }

    @Test
    fun `only a shut curtain gets the closed glyph`() {
        // The one glyph on the wall that carries state rather than labelling a type. A curtain 40 %
        // open is open; only a shut one is shut, and the threshold is this one comparison.
        assertEquals(R.drawable.ic_vertical_shades_closed, curtainGlyph(0.0))
        assertEquals(R.drawable.ic_vertical_shades, curtainGlyph(40.0))
        assertEquals(R.drawable.ic_vertical_shades, curtainGlyph(100.0))
    }

    @Test
    fun `a curtain the panel has no position for gets the open glyph, not the closed one`() {
        // The closed glyph is a positive claim that the curtain is shut, and the panel does not
        // know. Same rule the strings have always followed — unknown is not off — and the paint
        // must not undo what the words were careful about.
        assertEquals(R.drawable.ic_vertical_shades, curtainGlyph(null))
    }

    @Test
    fun `a room's lamps are one light tile, whatever they are doing`() {
        // The group tile is in the family of the seven it stands for, and its hue does not move
        // with them any more than a single lamp's does.
        assertEquals(TileHue.Light, hue(lamps(on = 7, off = 0)))
        assertEquals(TileHue.Light, hue(lamps(on = 0, off = 7)))
    }

    @Test
    fun `a group with any lamp lit is on, and one with none is off`() {
        // What somebody walking past reads off the colour is whether there is light in that room;
        // how many of the seven are lit is said exactly, in words, at wall size.
        assertEquals(TileMood.On, mood(lamps(on = 1, off = 6)))
        assertEquals(TileMood.On, mood(lamps(on = 7, off = 0)))
        assertEquals(TileMood.Off, mood(lamps(on = 0, off = 7)))
    }

    @Test
    fun `a group whose poll failed keeps its count's mood and takes the outline`() {
        // It used to be Failing, which meant a filled red card. One `/v1.0/user/info` is behind
        // every lamp in the flat, so that one failure filled every light tile on the wall red at
        // once — the wall stopped saying which family anything belonged to at exactly the moment
        // somebody needed to work out what broke. The group's bad news outlines now.
        assertEquals(TilePaint(TileMood.On, groupFailing = true), paint(lamps(on = 7, off = 0), "HTTP 500"))
        assertEquals(TilePaint(TileMood.Off, groupFailing = true), paint(lamps(on = 0, off = 7), "HTTP 500"))
    }

    @Test
    fun `a group tile promotes how many of its lamps are on`() {
        assertEquals("5 on", promoted(lamps(on = 5, off = 2)))
        // Not an absence: every lamp in the group reported, and they reported off. The only group
        // with nothing to say is the one with no lamps, and that one has no tile.
        assertEquals("0 on", promoted(lamps(on = 0, off = 3)))
    }

    @Test
    fun `a group tile is named for how many lamps it holds`() {
        assertEquals("7 lamps", anatomy(lamps(on = 5, off = 2), now, null, notUpdating = false, open = false).name)
        assertEquals("1 lamp", anatomy(lamps(on = 1, off = 0), now, null, notUpdating = false, open = false).name)
    }

    @Test
    fun `a group tile says what its tap will do`() {
        // The one detail line on the wall that describes a gesture rather than a reading, and the
        // only mark the closed tile carries that it has seven devices behind it.
        val group = lamps(on = 5, off = 2)
        assertEquals("tap to see them", anatomy(group, now, null, notUpdating = false, open = false).detail)
        assertEquals("tap to close", anatomy(group, now, null, notUpdating = false, open = true).detail)
    }

    @Test
    fun `opening a group changes nothing it says about the lamps`() {
        // The count, the value and the age stay on the wall while the seven are open — the tile is
        // not replaced by what it opens, and no reading is behind the tap.
        val group = lamps(on = 5, off = 2)
        val closed = anatomy(group, now, null, notUpdating = false, open = false)
        val open = anatomy(group, now, null, notUpdating = false, open = true)

        assertEquals(closed.copy(detail = open.detail), open)
    }

    @Test
    fun `a group tile that stopped updating says so once, for all of its lamps`() {
        // One `/v1.0/user/info` call is behind every bulb in the flat, so this is one tile's line
        // rather than seven — and it is what the seven discs' shared line used to say.
        val tile = anatomy(lamps(on = 5, off = 2), now, "timed out", notUpdating = true, open = false)

        assertEquals(bulbGroupLine(lamps(on = 5, off = 2), now), tile.status)
        // On the second line, where it takes the place of what the tap does. A gesture the tile can
        // still perform is the one thing on this wall worth giving up for a reason it cannot.
        assertEquals("timed out", tile.detail)
    }

    @Test
    fun `a group tile offers a finger nothing but the tap that opens it`() {
        // Deliberately no master switch: Yandex has no group action, so one would be seven requests
        // behind one finger with one status line to report the mixture. The lamps keep their own
        // switches, one tap further in.
        assertEquals(TileControls.None, controls(lamps(on = 5, off = 2)))
    }

    @Test
    fun `a bulb tile's glyph is the lamp, and only ever the lamp`() {
        // The bulb is not the curtain: its glyph labels a type and carries no state. A lamp is a
        // lamp whether it is standing on its own or opened out of its room's group, and the group
        // tile wears the same one — so the two cannot come out as different lamps.
        assertEquals(R.drawable.ic_bulb, glyph(bulb(isOn = null)))
        assertEquals(R.drawable.ic_bulb, glyph(lamps(on = 5, off = 2)))
    }

    @Test
    fun `every tile on the wall resolves to a glyph, and every launcher to its own`() {
        // Seven tile types and two apps, and the failure this catches is a device arriving with no
        // art: `painterResource(0)` throws, and the tile it was going to be drawn on takes the whole
        // panel down with it. Asserted as ids rather than as pictures — what the drawables look like
        // is the screenshots' job, that there *is* one for everything is this one's.
        assertNotEquals(0, glyph(ac(isOn = true)))
        assertNotEquals(0, glyph(recuperator(temperature = 29.3, humidity = 32.2)))
        assertNotEquals(0, glyph(bulb(isOn = true)))
        assertNotEquals(0, glyph(lamps(on = 5, off = 2)))
        assertNotEquals(0, glyph(strip(isOn = true)))
        assertNotEquals(0, glyph(curtain(openPercent = 100.0)))

        // The launcher is the one whose glyph is keyed on an id, so it is the one that can drift:
        // the catalogue is where a third app gets added and `glyph(LauncherTileState)` is where it
        // would be forgotten. Driven through `launcherTiles` so the two cannot be listed apart.
        val launchers = launcherTiles { true }.associateBy { it.packageName }
        assertEquals(R.drawable.ic_video_camera_front, glyph(launchers.getValue("com.domonap.app")))
        assertEquals(R.drawable.ic_vacuum, glyph(launchers.getValue("com.xiaomi.smarthome")))
    }

    // --- What the surface carries, and what the marks do -------------------------------------
    //
    // A tile's card is on the neutral ramp and on nothing else: the family is an accent now — the
    // glyph, the promoted value, the slider fill, the on mark — and the surface is free to carry
    // one thing. What it carries is the mood, so the table below is every mood against the step it
    // takes, and the failure it catches is two moods quietly sharing a step again.

    @Test
    fun `a tile's surface is decided by its mood and by nothing else`() {
        // The signature is half the assertion — `surface` cannot see a hue — and the rest is that
        // the four moods come out as four different steps. Off and Unknown shared `surfaceContainer`
        // until now, so a lamp the panel knew nothing about was the same colour as one it knew was
        // off, and only the words told them apart.
        val steps = TileMood.entries.map { surface(it) }

        assertEquals(steps.size, steps.toSet().size, "two moods on one step: $steps")
    }

    @Test
    fun `a lit tile is raised, a tile nobody has read is sunk, and a failing one is above both`() {
        // One ordering, and it is how much the tile is asserting: a lit device is the exception
        // worth seeing on a wall of off ones, a failing one wants the eye more than a quiet one,
        // and a tile with no reading at all asserts the least of the four.
        assertTrue(surface(TileMood.On) > surface(TileMood.Failing))
        assertTrue(surface(TileMood.Failing) > surface(TileMood.Off))
        assertTrue(surface(TileMood.Off) > surface(TileMood.Unknown))
    }

    @Test
    fun `the marks are the on indicator and the failure, and nothing else is marked`() {
        // The whole colour budget of a tile, after the fields went neutral: a small saturated mark
        // for a device that is on, a filled one for a device whose own poll failed, and nothing at
        // all for the two states where the panel has no news.
        assertEquals(TileMark.Family, mark(TileMood.On))
        assertEquals(TileMark.Failure, mark(TileMood.Failing))
        assertEquals(TileMark.None, mark(TileMood.Off))
        assertEquals(TileMark.None, mark(TileMood.Unknown))
    }

    // --- The two kinds of bad news ------------------------------------------------------------
    //
    // `docs/design/panel-redesign.md` item 4. A group's failure outlines and a tile's own failure
    // fills, and the reason is arithmetic: one Yandex call feeds every ac, curtain, strip and bulb
    // in the flat, so one failed `/v1.0/user/info` used to repaint about 34 of the 35 tiles in a
    // single frame. The recuperator had the split right already and was the only tile that did.

    @Test
    fun `a group failure outlines every kind of tile and leaves its mood alone`() {
        // Every type, not only the recuperator: the hue has to survive a group failure, because
        // "which of these is the air conditioner" is the question somebody is asking when they walk
        // up to a wall that has gone wrong.
        assertEquals(TilePaint(TileMood.On, groupFailing = true), paint(ac(isOn = true), "HTTP 500"))
        assertEquals(TilePaint(TileMood.Off, groupFailing = true), paint(bulb(isOn = false), "HTTP 500"))
        assertEquals(TilePaint(TileMood.On, groupFailing = true), paint(strip(isOn = true), "HTTP 500"))
        assertEquals(
            TilePaint(TileMood.On, groupFailing = true),
            paint(curtain(openPercent = 40.0), "HTTP 500"),
        )
        assertEquals(
            TilePaint(TileMood.On, groupFailing = true),
            paint(recuperator(temperature = 29.3, humidity = 32.2), "HTTP 500"),
        )
        assertEquals(TilePaint(TileMood.On, groupFailing = true), paint(lamps(on = 3, off = 2), "HTTP 500"))
    }

    @Test
    fun `a tile's own failure fills and does not outline`() {
        // The recuperator is the only device with a per-device error — state costs one Tuya call
        // each — and the launcher's missing app is the other tile-sized failure on the wall.
        assertEquals(
            TilePaint(TileMood.Failing, groupFailing = false),
            paint(recuperator(temperature = null, humidity = null, isOn = true, error = "timeout"), null),
        )
        assertEquals(TilePaint(TileMood.Failing, groupFailing = false), paint(launcher(openable = false)))
        assertEquals(TilePaint(TileMood.Unknown, groupFailing = false), paint(launcher(openable = true)))
    }

    @Test
    fun `a recuperator with both kinds of bad news says both`() {
        // Filled and outlined at once. Five outlined tiles is one vendor; the filled one among them
        // is the unit that actually stopped answering, and burying it would be the worse mistake.
        assertEquals(
            TilePaint(TileMood.Failing, groupFailing = true),
            paint(recuperator(temperature = null, humidity = null, isOn = true, error = "timeout"), "HTTP 500"),
        )
    }

    @Test
    fun `a curtain that is open at all is on, whatever is failing around it`() {
        // The curtain has no switch to read a mood off, so its position is the mood — the same
        // three answers its status line gives, in the same order. It used to be worked out inside
        // the composable, where no test could reach it.
        assertEquals(TileMood.On, paint(curtain(openPercent = 40.0), null).mood)
        assertEquals(TileMood.Off, paint(curtain(openPercent = 0.0), null).mood)
        assertEquals(TileMood.Unknown, paint(curtain(openPercent = null), null).mood)
        assertEquals(TileMood.On, paint(curtain(openPercent = 40.0), "HTTP 500").mood)
    }

    // --- What one tile promotes ------------------------------------------------------------
    //
    // One value per tile, at the size the wall is read at, and the rest of the line demoted. The
    // table below is every tile type against every state of the value it would promote, because
    // the failure this catches is a tile quietly having nothing to promote — which is a tile with
    // a hole where the number goes.

    @Test
    fun `an air conditioner promotes its target temperature`() {
        assertEquals("22 °C", promoted(ac(isOn = true)))
    }

    @Test
    fun `an air conditioner in a unit nobody named promotes the bare number`() {
        // The degree sign is printed only for the unit the vendor actually named; hanging it on a
        // number whose unit was not reported would be the panel inventing it.
        assertEquals("22", promoted(ac(isOn = true, unit = "unit.temperature.kelvin")))
    }

    @Test
    fun `an air conditioner with no target promotes nothing`() {
        // Null rather than the word "unknown" set at display size. Nothing is dropped — the status
        // line still says "unknown", which is where it has always been said — but a tile does not
        // shout a value nobody has taken.
        assertNull(promoted(ac(isOn = true, targetTemperature = null)))
    }

    @Test
    fun `a curtain promotes how far open it is`() {
        assertEquals("40% open", promoted(curtain(openPercent = 40.0)))
        assertEquals("0% open", promoted(curtain(openPercent = 0.0)))
    }

    @Test
    fun `a curtain with no position promotes nothing`() {
        assertNull(promoted(curtain(openPercent = null)))
    }

    @Test
    fun `a light strip promotes its brightness`() {
        assertEquals("60%", promoted(strip(isOn = true)))
        assertEquals("60", promoted(strip(isOn = true, unit = "unit.lux")))
    }

    @Test
    fun `a light strip with no brightness promotes nothing`() {
        assertNull(promoted(strip(isOn = true, brightnessPercent = null)))
    }

    @Test
    fun `a recuperator promotes the temperature it measures`() {
        // The temperature and not the humidity: it is the one of the two a person standing in the
        // hallway is deciding something about.
        assertEquals("29.3 °C", promoted(recuperator(temperature = 29.3, humidity = 32.2)))
    }

    @Test
    fun `a recuperator reporting only humidity promotes nothing`() {
        // Still a half tile — span asks climateLine, which either value satisfies — and still with
        // an empty promoted slot, because the value it promotes is the one it does not have.
        assertNull(promoted(recuperator(temperature = null, humidity = 32.2)))
        assertNull(promoted(recuperator(temperature = null, humidity = null)))
    }

    @Test
    fun `a bulb promotes nothing, in every state it has`() {
        // A bulb is on or off and carries no number, and a *named* bulb tile is by construction the
        // one the panel has no state for at all. There is nothing here to promote and inventing one
        // would be the wall's loudest type spent on the least it knows.
        listOf(true, false, null).forEach { state -> assertNull(promoted(bulb(isOn = state))) }
    }

    @Test
    fun `a launcher promotes nothing, installed or not`() {
        // It reads nothing about the flat, so it has no reading to promote — the same reason it is
        // the one tile that takes no `now`.
        assertNull(promoted(launcher(openable = true)))
        assertNull(promoted(launcher(openable = false)))
    }

    @Test
    fun `what a tile promotes does not move with its mood`() {
        // The same split the hue/mood pair already makes: what a tile promotes is a property of the
        // reading, and whether that reading is trustworthy is mood's answer. A failing poll leaves
        // the last value on the wall — that is the whole of why the tile keeps showing it — so the
        // promoted value must not empty out underneath it.
        assertEquals("22 °C", promoted(ac(isOn = null)))
        assertEquals(
            "29.3 °C",
            promoted(recuperator(temperature = 29.3, humidity = 32.2, isOn = null, error = "timeout")),
        )
    }

    @Test
    fun `the promoted value is the same string the status line prints for it`() {
        // The two cannot drift apart, because they are one function: the status line is what ages
        // the value, so it has to name the value it is aging, and a tile printing "22 °C" over
        // "on · 3 min ago · 23 °C · 81 d ago" would be one tile disagreeing with itself.
        val ac = ac(isOn = true)
        assertTrue(statusLine(ac, now).contains(promoted(ac)!!))
        val curtain = curtain(openPercent = 40.0)
        assertTrue(statusLine(curtain, now).contains(promoted(curtain)!!))
        val strip = strip(isOn = true)
        assertTrue(statusLine(strip, now).contains(promoted(strip)!!))
        val recuperator = recuperator(temperature = 29.3, humidity = 32.2)
        assertTrue(climateLine(recuperator)!!.contains(promoted(recuperator)!!))
    }

    @Test
    fun `a tile with no value to promote still says unknown in words`() {
        // Null promotes nothing; it does not take the word away. The status line is where "unknown"
        // has always been said and it goes on saying it.
        assertTrue(statusLine(ac(isOn = true, targetTemperature = null), now).contains("unknown"))
        assertTrue(statusLine(curtain(openPercent = null), now).contains("unknown"))
        assertTrue(statusLine(strip(isOn = true, brightnessPercent = null), now).contains("unknown"))
    }

    // --- The five slots ----------------------------------------------------------------------
    //
    // One anatomy, six tile types, and the failure this catches is a kind quietly re-flowing: a
    // tile type that stops answering for a slot is a tile that lays itself out around what it
    // happens to have, which is the four ragged heights the anatomy replaced. So the table below is
    // every type against every state that could empty a slot, and it asserts what each one answers
    // rather than that it answered at all.

    @Test
    fun `every tile type fills the art, the name and the status slot, in every state it has`() {
        // These three are the slots that can never be empty: something drew the tile, something
        // named it, and CLAUDE.md requires every tile to be able to say how old its reading is.
        everyTile().forEach { anatomy ->
            assertNotEquals(0, anatomy.art)
            assertTrue(anatomy.name.isNotBlank(), "a tile with no name: $anatomy")
            assertTrue(anatomy.status.isNotBlank(), "a tile with no status line: $anatomy")
        }
    }

    @Test
    fun `an empty promoted slot is an answer and stays an answer`() {
        // A bulb is on or off and carries no number; a launcher reads nothing about the flat at
        // all. Both leave the slot empty rather than setting the word "unknown" at 44sp — and the
        // card reserves it either way, which is what keeps their bottom edges on the strip's line.
        assertNull(anatomy(bulb(isOn = true), now, error = null).promoted)
        assertNull(anatomy(launcher(openable = true)).promoted)
        assertEquals("22 °C", anatomy(ac(isOn = true), now, error = null).promoted)
        assertEquals("40% open", anatomy(curtain(openPercent = 40.0), now, error = null).promoted)
        assertEquals("60%", anatomy(strip(isOn = true), now, error = null).promoted)
        assertEquals(
            "29.3 °C",
            anatomy(recuperator(temperature = 29.3, humidity = 32.2), now, groupError = null).promoted,
        )
    }

    @Test
    fun `a tile with nothing wrong and nothing else to report has no second line`() {
        // The second line is for a tile that has something *more* to say than its state and its
        // age: the strip's colour, the recuperator's climate, the group's gesture, the launcher's
        // "no state to read" — or, ahead of any of them, why the panel is not reading it. A tile
        // with none of those leaves the line empty, and the slot it leaves empty is the same slot,
        // capped to the same two lines.
        assertNotNull(anatomy(strip(isOn = true, color = warmWhite), now, error = null).detail)
        assertNotNull(
            anatomy(recuperator(temperature = 29.3, humidity = 32.2), now, groupError = null).detail,
        )
        assertEquals("no state to read", anatomy(launcher(openable = true)).detail)
        assertNull(anatomy(ac(isOn = true), now, error = null).detail)
        assertNull(anatomy(curtain(openPercent = 40.0), now, error = null).detail)
        assertNull(anatomy(bulb(isOn = true), now, error = null).detail)
        // And a strip that reported no colour at all has no second line either, which is the one
        // case where a kind's second line is absent rather than the kind's answer being null.
        assertNull(anatomy(strip(isOn = true), now, error = null).detail)
    }

    // --- One age per tile ---------------------------------------------------------------------
    //
    // The wall printed an age per value and the values shared them: the recuperator carried four
    // timestamps across two lines and three were the same number. What a tile has to be able to say
    // is how old its state is — once, and only when it is old enough to be worth the line.

    @Test
    fun `a tile whose readings are all fresh prints no age at all`() {
        // Every fixture in this file is read at `now`, so this is the whole table asserting the
        // quiet case: nothing on a working wall says "3 min ago" any more.
        everyTile().forEach { anatomy ->
            assertEquals(emptyList(), agesOn(anatomy), "a fresh tile still printing an age: $anatomy")
        }
    }

    @Test
    fun `no tile prints the same age twice, however many readings are behind it`() {
        // The air conditioner has two readings, the strip three, the recuperator four, and a lights
        // group as many as the room has lamps. One line each, and one age on it.
        everyStaleTile().forEach { anatomy ->
            val ages = agesOn(anatomy)
            assertEquals(1, ages.size, "a tile saying its age $ages times: $anatomy")
        }
    }

    /**
     * Every age printed on one tile, on both of its lines. The panel's whole vocabulary for an age
     * is hours, days and "never read" — see `ageLine` — so a match here is a timestamp and nothing
     * else is.
     */
    private fun agesOn(anatomy: TileAnatomy): List<String> {
        val words = anatomy.status.split(" · ") + anatomy.detail.orEmpty().split(" · ")
        return words.filter { it == "never read" || Regex("""^\d+ [hd] ago$""").matches(it) }
    }

    /**
     * One tile of every kind that has a reading, with every reading behind it old enough to speak —
     * and deliberately *not* all the same age, so a tile that printed one per value would come out
     * of [agesOn] with several.
     */
    private fun everyStaleTile(): List<TileAnatomy> {
        val weeks = Reading.At(now.minusSeconds(20 * 86_400))
        val months = Reading.At(now.minusSeconds(81 * 86_400))
        val hours = Reading.At(now.minusSeconds(5 * 3600))
        return listOf(
            anatomy(
                ac(isOn = true).copy(powerLastUpdated = hours, temperatureLastUpdated = months),
                now,
                error = "timed out",
            ),
            anatomy(curtain(openPercent = 40.0).copy(lastUpdated = weeks), now, error = null),
            anatomy(
                strip(isOn = true, color = warmWhite).copy(
                    powerLastUpdated = hours,
                    brightnessLastUpdated = weeks,
                    color = warmWhite.copy(lastUpdated = Reading.Never),
                ),
                now,
                error = null,
            ),
            anatomy(
                recuperator(temperature = 29.3, humidity = 32.2).copy(
                    powerLastUpdated = months,
                    speedLastUpdated = weeks,
                    temperatureLastUpdated = hours,
                    humidityLastUpdated = hours,
                ),
                now,
                groupError = "timed out",
            ),
            anatomy(bulb(isOn = true).copy(lastUpdated = weeks), now, error = null),
            anatomy(
                bulbGroup(
                    listOf(
                        bulb(isOn = true).copy(id = "light-01", lastUpdated = hours),
                        bulb(isOn = false).copy(id = "light-02", lastUpdated = months),
                    ),
                ),
                now,
                error = null,
                notUpdating = true,
                open = false,
            ),
        )
    }

    @Test
    fun `what a tile offers a finger is its type's, and its vendor's bounds`() {
        // The switch and the slider are two axes of one answer, because they sit in two different
        // places on the card. A curtain is the pairing a single "has controls" boolean could not
        // have said: a slider and no switch, since "shut" is a position and not a power state.
        assertEquals(TileControls.ToggleAndLevel, controls(ac(isOn = true)))
        assertEquals(TileControls.ToggleAndLevel, controls(strip(isOn = true)))
        assertEquals(TileControls.Level, controls(curtain(openPercent = 40.0)))
        assertEquals(TileControls.Toggle, controls(recuperator(temperature = 29.3, humidity = 32.2)))
        assertEquals(TileControls.Toggle, controls(bulb(isOn = true)))
        assertEquals(TileControls.None, controls(launcher(openable = true)))
    }

    @Test
    fun `a tile whose vendor never sent bounds gets no slider`() {
        // A slider over an invented range is the same lie as "unknown" at display size: the panel
        // would be offering to set a value between limits nobody reported.
        assertEquals(TileControls.Toggle, controls(ac(isOn = true, bounds = null)))
        assertEquals(TileControls.Toggle, controls(strip(isOn = true, bounds = null)))
        assertEquals(TileControls.None, controls(curtain(openPercent = 40.0, bounds = null)))
    }

    @Test
    fun `what a tile offers a finger does not move with its mood`() {
        // Same split as hue and mood: a failing air conditioner is still a switch and a slider. The
        // tile goes rose and keeps its controls, because the poll failing is not the vendor
        // withdrawing the capability.
        listOf(true, false, null).forEach { state ->
            assertEquals(TileControls.ToggleAndLevel, controls(ac(isOn = state)))
            assertEquals(TileControls.Toggle, controls(bulb(isOn = state)))
        }
        assertEquals(
            TileControls.Toggle,
            controls(recuperator(temperature = null, humidity = null, isOn = null, error = "timeout")),
        )
        assertEquals(TileControls.None, controls(launcher(openable = false)))
    }

    @Test
    fun `the anatomy says exactly what the tile's own functions say`() {
        // It composes them rather than re-deriving them, so a tile cannot print one string at the
        // top of the card and a differently-rounded one underneath.
        val ac = ac(isOn = true)
        assertEquals(statusLine(ac, now), anatomy(ac, now, "timed out").status)
        assertEquals(promoted(ac), anatomy(ac, now, error = null).promoted)
        assertEquals(glyph(ac), anatomy(ac, now, error = null).art)
        val recuperator = recuperator(temperature = 29.3, humidity = 32.2)
        assertEquals(climateLine(recuperator), anatomy(recuperator, now, groupError = null).detail)
        val launcher = launcher(openable = false)
        assertEquals(statusLine(launcher), anatomy(launcher).status)
        assertEquals(detailLine(launcher), anatomy(launcher).detail)
    }

    @Test
    fun `a tile's second line is why it stopped updating, before anything else it had to say`() {
        // **The wall's one rule for bad news, and it is about width as much as about news.** A
        // status line is 156 dp on a quarter tile — about sixteen characters — so
        // "on · 20 d ago · not updating: unreachable" was never going to be one line of anything,
        // and the tile it wrapped inside grew taller than the tile beside it.
        //
        // The reason takes the second line outright rather than queueing behind what is already
        // there: a second reading is stale by definition once the poll behind it stopped landing,
        // so the strip's colour and the recuperator's climate give way to it while it lasts.
        assertEquals("timed out", anatomy(ac(isOn = true), now, "timed out").detail)
        assertEquals("timed out", anatomy(curtain(openPercent = 40.0), now, "timed out").detail)
        assertEquals("timed out", anatomy(bulb(isOn = true), now, "timed out").detail)
        assertEquals("timed out", anatomy(strip(isOn = true, color = warmWhite), now, "timed out").detail)
        assertEquals(
            "timed out",
            anatomy(recuperator(temperature = 29.3, humidity = 32.2), now, groupError = "timed out").detail,
        )
        // And it is only while it lasts: the second reading comes straight back when the poll does.
        assertEquals(
            colorLine(strip(isOn = true, color = warmWhite)),
            anatomy(strip(isOn = true, color = warmWhite), now, error = null).detail,
        )
    }

    @Test
    fun `a recuperator names its own failure rather than its group's`() {
        // The one tile with both kinds at once — Tuya charges a call per device — so its own reason
        // is the more specific of the two and is the one it prints.
        val tile = recuperator(temperature = null, humidity = null, error = "timed out")

        assertEquals("timed out", anatomy(tile, now, groupError = "unreachable").detail)
    }

    @Test
    fun `nothing a tile says carries the reason twice`() {
        // It moved from the status line to the second one; a tile printing it in both places would
        // be the run-on this replaces, wearing two lines instead of four.
        everyTile().forEach { tile ->
            val reason = tile.detail ?: return@forEach
            assertFalse(
                tile.status.contains(reason),
                "a tile saying the same thing twice: ${tile.status} / $reason",
            )
        }
    }

    /**
     * Every tile type, in every state that could empty one of its slots: both bounds, all three
     * on/off answers, a failing poll and a missing app.
     */
    private fun everyTile(): List<TileAnatomy> {
        val errors = listOf(null, "timed out")
        val states = listOf(true, false, null)
        return errors.flatMap { error ->
            states.flatMap { state ->
                listOf(true, false).flatMap { hasBounds ->
                    val bounds = if (hasBounds) BOUNDS else null
                    listOf(
                        anatomy(ac(isOn = state, bounds = bounds), now, error),
                        anatomy(ac(isOn = state, targetTemperature = null, bounds = bounds), now, error),
                        anatomy(curtain(openPercent = null, bounds = bounds), now, error),
                        anatomy(curtain(openPercent = 40.0, bounds = bounds), now, error),
                        anatomy(strip(isOn = state, bounds = bounds), now, error),
                        anatomy(strip(isOn = state, color = warmWhite, bounds = bounds), now, error),
                        anatomy(bulb(isOn = state), now, error),
                        anatomy(recuperator(temperature = 29.3, humidity = 32.2, isOn = state), now, error),
                        anatomy(recuperator(temperature = null, humidity = null, isOn = state), now, error),
                        anatomy(launcher(openable = true)),
                        anatomy(launcher(openable = false)),
                        // The group tile in all four of the states it has: open or closed, being
                        // read or not.
                        anatomy(lamps(on = 5, off = 2), now, error, notUpdating = false, open = false),
                        anatomy(lamps(on = 5, off = 2), now, error, notUpdating = true, open = true),
                        anatomy(lamps(on = 0, off = 1), now, error, notUpdating = false, open = true),
                    )
                }
            }
        }
    }

    private val warmWhite = ColorSetting(
        instance = "temperature_k",
        value = 4500.0,
        lastUpdated = Reading.At(now),
        stateChangedAt = Reading.At(now),
    )

    private fun recuperator(
        temperature: Double?,
        humidity: Double?,
        isOn: Boolean? = true,
        error: String? = null,
    ) = RecuperatorTileState(
        id = "xfj-01",
        name = "Бризер",
        room = "Спальня",
        isOn = isOn,
        powerLastUpdated = Reading.At(now),
        speeds = listOf(FanSpeed.Low),
        speedLastUpdated = Reading.At(now),
        temperature = temperature,
        temperatureLastUpdated = if (temperature == null) Reading.Never else Reading.At(now),
        humidity = humidity,
        humidityLastUpdated = if (humidity == null) Reading.Never else Reading.At(now),
        online = true,
        error = error,
    )

    private fun ac(
        isOn: Boolean?,
        targetTemperature: Double? = 22.0,
        unit: String = "unit.temperature.celsius",
        bounds: Bounds? = BOUNDS,
    ) = AcTileState(
        id = "ac-01",
        name = "Кондиционер",
        room = "Зал",
        isOn = isOn,
        powerLastUpdated = Reading.At(now),
        targetTemperature = targetTemperature,
        bounds = bounds,
        unit = unit,
        temperatureLastUpdated = Reading.At(now),
    )

    /**
     * A room's lamps, built through [bulbGroup] rather than by hand: the group tile's whole content
     * is that function's answer, and a fixture that filled in `on` itself could disagree with it.
     */
    private fun lamps(
        on: Int,
        off: Int,
    ) = bulbGroup(
        (1..on).map { bulb(isOn = true) } + (1..off).map { bulb(isOn = false) },
    )

    private fun bulb(isOn: Boolean?) = BulbTileState(
        id = "light-01",
        name = "Лампа",
        room = "Коридор",
        isOn = isOn,
        lastUpdated = Reading.At(now),
        stateChangedAt = Reading.At(now),
    )

    private fun strip(
        isOn: Boolean?,
        brightnessPercent: Double? = 60.0,
        unit: String = "unit.percent",
        color: ColorSetting? = null,
        bounds: Bounds? = BOUNDS,
    ) = LightStripTileState(
        id = "strip-01",
        name = "Лента",
        room = "Коридор",
        isOn = isOn,
        powerLastUpdated = Reading.At(now),
        brightnessPercent = brightnessPercent,
        bounds = bounds,
        unit = unit,
        brightnessLastUpdated = Reading.At(now),
        color = color,
    )

    private fun curtain(
        openPercent: Double?,
        bounds: Bounds? = BOUNDS,
    ) = CurtainTileState(
        id = "curtain-01",
        name = "Штора",
        room = "Зал",
        openPercent = openPercent,
        bounds = bounds,
        lastUpdated = Reading.At(now),
        stateChangedAt = Reading.At(now),
    )

    private fun launcher(openable: Boolean) = LauncherTileState(
        packageName = "com.example.intercom",
        name = "Домофон",
        room = null,
        openable = openable,
    )

    private companion object {
        /**
         * Whatever range a vendor reported. The numbers do not matter to anything here — what
         * matters is the difference between a tile that has one and a tile that does not, which is
         * the difference between a slider and no slider.
         */
        val BOUNDS = Bounds(min = 0.0, max = 100.0, precision = 1.0)
    }
}
