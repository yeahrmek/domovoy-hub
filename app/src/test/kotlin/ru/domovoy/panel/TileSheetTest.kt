package ru.domovoy.panel

import org.junit.jupiter.api.Test
import ru.domovoy.core.Bounds
import ru.domovoy.core.ColorSetting
import ru.domovoy.core.Mode
import ru.domovoy.core.Reading
import ru.domovoy.core.Toggle
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **What a tap opens, per device type** — the whole of it, as a table.
 *
 * The panel is glanceable and a phone app is not, so the sheet is not the reference's split: nothing
 * moves off a tile into it. What it is is the rest of the detail and the actions a 251 dp card had
 * no room for, and *which* of those a kind gets is a decision with a right and a wrong answer. Two
 * of the rows below are refusals rather than features and are the reason this file exists at all:
 *
 * - **The lock's action set is empty**, and it is written out as its own case rather than left out
 *   of the table. `CLAUDE.md` and `docs/aqara.md` both say the lock reports and does not act; the
 *   rule most likely to be broken by anybody building "a detail sheet per device" is exactly the one
 *   an omission would not catch.
 * - **The launcher and the lights group open no sheet at all.** Xiaomi has no readable state and
 *   Domonap has no API worth calling, so a sheet over either would be an empty card with a scrim
 *   behind it; the lights group's tap already opens the room's lamps and keeps doing so.
 */
class TileSheetTest {
    private val now = Flat.NOW
    private val ac = Flat.acs.tiles.first()
    private val curtain = Flat.curtains.tiles.first()
    private val strip = Flat.strips.tiles.first()
    private val plainStrip = Flat.strips.tiles.last()
    private val bulb = Flat.bulbs.tiles.first()
    private val recuperator = Flat.recuperators.tiles.first()
    private val offline = Flat.recuperators.tiles.last()
    private val launcher = Flat.launchers.first()
    private val lamps = bulbGroup(Flat.bulbs.tiles.filter { it.room == "Коридор" })

    @Test
    fun `what a sheet offers is its device type's, and the lock's is nothing`() {
        // The whole table, and every row of it is either an endpoint this panel has actually sent or
        // a refusal. Power is `on_off`, verified end to end on Yandex and on the tablet; the level is
        // `range`, verified the same way. What is *not* here is everything the reference sheet has
        // that rests on an endpoint nobody has called: the `Color` section and `Modes` need a
        // `color_setting` and a `mode` action body that docs/yandex.md still lists as open, `reset`
        // is not a capability any vendor here reported, and the air conditioner's fan mode is the
        // same unanswered `mode` question. None of them is guessed at.
        assertEquals(
            setOf(SheetAction.Power, SheetAction.Level, SheetAction.Mode, SheetAction.Toggle),
            sheetActions(SheetSubject.AirConditioner),
        )
        assertEquals(setOf(SheetAction.Power, SheetAction.Level), sheetActions(SheetSubject.LightStrip))
        assertEquals(
            setOf(SheetAction.Level, SheetAction.Open, SheetAction.Close),
            sheetActions(SheetSubject.Curtain),
        )
        assertEquals(setOf(SheetAction.Power, SheetAction.Level, SheetAction.Color), sheetActions(SheetSubject.Bulb))
        assertEquals(setOf(SheetAction.Power, SheetAction.Speed), sheetActions(SheetSubject.Recuperator))
        assertEquals(emptySet(), sheetActions(SheetSubject.Lock))
    }

    @Test
    fun `the lock's sheet reports and does not act, and there is no exception to that`() {
        // **The rule this task is most likely to break**, so it is asserted on its own and not only
        // as one cell of the table above. A door lock reachable from a hallway panel is a door lock
        // anybody standing in the hallway can open — no unlock, no open, no door release, and not a
        // power switch that could be mistaken for one either.
        //
        // There is no lock tile in `panel/` today: Aqara's developer project is still in review and
        // no key exists, so nothing constructs this subject yet. That is precisely why the row is
        // here — the rule is written down before the tile that would have to obey it, rather than
        // after somebody has given it a button.
        assertTrue(sheetActions(SheetSubject.Lock).isEmpty())
        SheetAction.entries.forEach { action ->
            assertTrue(action !in sheetActions(SheetSubject.Lock), "the lock was given $action")
        }
    }

    @Test
    fun `every device type in the table has an answer, and nothing else opens a sheet`() {
        // The table is total over the subjects, and the subjects are total over the tiles: a kind of
        // tile that quietly stopped answering is a kind whose sheet nobody would notice was gone.
        SheetSubject.entries.forEach { subject -> assertNotNull(sheetActions(subject)) }
        assertEquals(SheetSubject.AirConditioner, subject(ac))
        assertEquals(SheetSubject.Curtain, subject(curtain))
        assertEquals(SheetSubject.LightStrip, subject(strip))
        assertEquals(SheetSubject.Bulb, subject(bulb))
        assertEquals(SheetSubject.Recuperator, subject(recuperator))
        // Xiaomi has no readable state and Domonap no API the panel calls: both are a tile that
        // opens somebody else's app, and there is nothing behind them for a sheet to show.
        assertNull(subject(launcher))
        // The lights group's tap is already spoken for — it opens the room's lamps in the grid, each
        // of which is a bulb with a sheet of its own.
        assertNull(subject(lamps))
    }

    @Test
    fun `a device whose vendor named no range is offered neither the level nor its ends`() {
        // The same refusal `controls` and `action` already make on the tile: with no reported bounds
        // there is no "fully open" to drive to and no grid to snap a value onto, and a control that
        // invents one is the panel making up a position.
        assertEquals(
            setOf(SheetAction.Power, SheetAction.Mode, SheetAction.Toggle),
            sheet(ac.copy(bounds = null), now, error = null).actions,
        )
        assertEquals(setOf(SheetAction.Power), sheet(strip.copy(bounds = null), now, error = null).actions)
        assertEquals(emptySet(), sheet(curtain.copy(bounds = null), now, error = null).actions)
    }

    @Test
    fun `an AC sheet carries only modes and toggles advertised by that unit`() {
        val fan = Mode("auto", listOf("low", "medium", "high", "auto"), Reading.Never, Reading.Never)
        val ion = Toggle(false, Reading.Never, Reading.Never)
        val capable = ac.copy(modes = mapOf("fan_speed" to fan), toggles = mapOf("ionization" to ion))

        val sheet = sheet(capable, now, error = null)

        assertEquals(mapOf("fan_speed" to fan), sheet.modes)
        assertEquals(mapOf("ionization" to ion), sheet.toggles)
        assertTrue(SheetAction.Mode in sheet.actions)
        assertTrue(SheetAction.Toggle in sheet.actions)
    }

    @Test
    fun `a dimmable RGB bulb sheet has brightness RGB and only its advertised scenes`() {
        val color =
            ColorSetting(
                instance = "rgb",
                value = 0xFFAA00.toDouble(),
                scenes = listOf("candle", "movie"),
                lastUpdated = Reading.Never,
                stateChangedAt = Reading.Never,
            )
        val capable =
            bulb.copy(
                brightnessPercent = 42.0,
                brightnessBounds = Bounds(0.0, 100.0, 1.0),
                color = color,
            )

        val sheet = sheet(capable, now, error = null)

        assertEquals(42.0, sheet.level?.value)
        assertEquals(color, sheet.color)
        assertEquals(listOf("candle", "movie"), sheet.color?.scenes)
        assertTrue(SheetAction.Level in sheet.actions)
        assertTrue(SheetAction.Color in sheet.actions)
    }

    @Test
    fun `a relay bulb has no empty brightness or color controls`() {
        val sheet = sheet(bulb, now, error = null)

        assertEquals(setOf(SheetAction.Power), sheet.actions)
        assertNull(sheet.level)
        assertNull(sheet.color)
    }

    @Test
    fun `a recuperator sheet offers its three verified speeds but only while online`() {
        assertEquals(FanSpeed.entries, sheet(recuperator, now, groupError = null).fanSpeeds)
        assertTrue(SheetAction.Speed in sheet(recuperator, now, groupError = null).actions)
        assertTrue(SheetAction.Speed !in sheet(offline, now, groupError = null).actions)
    }

    @Test
    fun `a sheet offers a level exactly when it carries one to drive`() {
        // Two answers to one question would be a sheet drawing a slider over a range it does not
        // have, or holding a range it never offers.
        listOf(
            sheet(ac, now, error = null),
            sheet(ac.copy(bounds = null), now, error = null),
            sheet(curtain, now, error = null),
            sheet(curtain.copy(bounds = null), now, error = null),
            sheet(strip, now, error = null),
            sheet(bulb, now, error = null),
            sheet(recuperator, now, groupError = null),
        ).forEach { sheet ->
            assertEquals(SheetAction.Level in sheet.actions, sheet.level != null, "$sheet")
        }
    }

    @Test
    fun `a sheet starts its level where the last reading left it, and at the bottom when there is none`() {
        assertEquals(22.0, sheet(ac, now, error = null).level?.value)
        assertEquals(40.0, sheet(curtain, now, error = null).level?.value)
        // Never reported is not zero. The bottom of the range is the one value certainly on the
        // grid, which is where every slider on this panel already starts — and the reading beside it
        // still says "unknown".
        assertEquals(16.0, sheet(ac.copy(targetTemperature = null), now, error = null).level?.value)
        assertEquals(0.0, sheet(curtain.copy(openPercent = null), now, error = null).level?.value)
    }

    @Test
    fun `a sheet ages every reading separately, which is what the tile stopped doing`() {
        // **This is what the sheet is for.** The tile prints one age — the oldest of what it shows —
        // because four timestamps in one paragraph is what made the wall unreadable. The two
        // readings behind an air conditioner really are 81 days apart on `ac-01`, so the sheet is
        // where the pair is broken back out: nothing has moved off the tile, and the detail the tile
        // could not spend a line on is one tap away.
        val sheet = sheet(ac, now, error = null)

        assertEquals(listOf("power", "target", "room"), sheet.readings.map { it.label })
        assertEquals(listOf("on", "22 °C", "26.0 °C"), sheet.readings.map { it.value })
        assertEquals(listOf("1 min ago", "81 d ago", "1 min ago"), sheet.readings.map { it.age })
    }

    @Test
    fun `how old a reading is, said in full`() {
        // The sheet's own wording, and deliberately not the wall's: `ageLine` says nothing at all
        // under an hour, because "3 min ago" on twelve tiles is the noise the wall was cleared of.
        // A sheet is opened on purpose by somebody standing at the panel, and the question they
        // opened it with is exactly this one — so it always answers.
        assertEquals("never read", sheetAge(Reading.Never, now))
        assertEquals("just now", sheetAge(Reading.At(now), now))
        assertEquals("just now", sheetAge(Reading.At(now.minusSeconds(59)), now))
        assertEquals("1 min ago", sheetAge(Reading.At(now.minusSeconds(60)), now))
        assertEquals("59 min ago", sheetAge(Reading.At(now.minusSeconds(59 * 60)), now))
        assertEquals("1 h ago", sheetAge(Reading.At(now.minusSeconds(60 * 60)), now))
        assertEquals("23 h ago", sheetAge(Reading.At(now.minusSeconds(23 * 3600)), now))
        assertEquals("1 d ago", sheetAge(Reading.At(now.minusSeconds(24 * 3600)), now))
        // A tablet whose clock jumped back reads as fresh rather than as absurd, which is the rule
        // `ageLine` and `isStale` already follow.
        assertEquals("just now", sheetAge(Reading.At(now.plusSeconds(600)), now))
    }

    @Test
    fun `no value the tile shows is missing from the sheet`() {
        // The load-bearing direction of this whole change: a number that moved off a tile into a
        // sheet turns a glance into a walk. So the sheet is a superset, and the way to see that is
        // that every promoted value is still printed here word for word by the one formatter both
        // use.
        assertTrue(sheet(ac, now, error = null).readings.any { it.value == promoted(ac) })
        assertEquals(promoted(curtain), sheet(curtain, now, error = null).readings.last().value)
        assertTrue(sheet(strip, now, error = null).readings.any { it.value == promoted(strip) })
        assertTrue(
            sheet(recuperator, now, groupError = null).readings.any {
                it.value == promoted(recuperator)
            },
        )
    }

    @Test
    fun `the strip's colour is on its sheet and is still not controllable`() {
        // It is read and shown and there is no colour action to send — docs/yandex.md still lists
        // what a `color_setting` action body looks like as an open question. The sheet is where a
        // reference app would have put a row of swatches; this one prints the refusal instead, in
        // the same words the tile has always used.
        val colour = sheet(strip, now, error = null).readings.single { it.label == "colour" }

        assertEquals(colorLine(strip), colour.value)
        assertTrue("not controllable" in colour.value)
        // And a strip with no `color_setting` at all has no row for one — an absent capability is
        // not a capability that reported nothing.
        assertTrue(sheet(plainStrip, now, error = null).readings.none { it.label == "colour" })
    }

    @Test
    fun `a recuperator's sheet is the four ages its tile prints as one`() {
        val sheet = sheet(recuperator, now, groupError = null)

        assertEquals(listOf("power", "fan", "temperature", "humidity"), sheet.readings.map { it.label })
        assertEquals("low", sheet.readings[1].value)
        assertEquals("26.4 °C", sheet.readings[2].value)
        assertEquals("41.0 %", sheet.readings[3].value)
    }

    @Test
    fun `an offline recuperator claims neither its switch nor its speed`() {
        // The tile's own refusal, kept: a device Tuya says is offline is not confirming either, and
        // a sheet that printed "on" over it would assert what the tile is careful not to.
        val sheet = sheet(offline, now, groupError = null)

        assertEquals("offline", sheet.readings.first().value)
        assertTrue(sheet.readings.none { it.label == "fan" })
    }

    @Test
    fun `a sheet says why the panel stopped reading the device, in the words the wall uses`() {
        // A sheet covers the tile it was opened from, so bad news the tile was carrying has to
        // survive the tap. It is the same four-word reason and not a vendor sentence.
        assertEquals("timed out", sheet(ac, now, error = "timed out").notUpdating)
        assertEquals("timed out", sheet(bulb, now, error = "timed out").notUpdating)
        assertNull(sheet(ac, now, error = null).notUpdating)
        // The recuperator is the one with two kinds of bad news, and its own is the more specific.
        assertEquals("timed out", sheet(offline, now, groupError = "unreachable").notUpdating)
        assertEquals("unreachable", sheet(recuperator, now, groupError = "unreachable").notUpdating)
    }

    @Test
    fun `a failing poll takes nothing off the sheet`() {
        // The same split every other rule on this panel makes: the call behind a tile failing is not
        // the vendor withdrawing the capability, so the readings and the actions stay put and the
        // reason is added beside them.
        val failing = sheet(ac, now, error = "timed out")

        assertEquals(sheet(ac, now, error = null).readings, failing.readings)
        assertEquals(sheet(ac, now, error = null).actions, failing.actions)
    }

    @Test
    fun `a sheet is named for the device and says which room it is in`() {
        assertEquals("Кондиционер", sheet(ac, now, error = null).name)
        assertEquals("Зал", sheet(ac, now, error = null).room)
        assertEquals(art(ac), sheet(ac, now, error = null).art)
    }
}
