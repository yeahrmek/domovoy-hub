package ru.domovoy.panel

import org.junit.jupiter.api.Test
import ru.domovoy.R
import ru.domovoy.core.Bounds
import ru.domovoy.core.Reading
import java.time.Instant
import kotlin.test.assertEquals
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
    fun `a recuperator reporting a temperature is a hero tile`() {
        // Three columns of the six, because it has a second line to put there — see climateLine,
        // which is the same condition rather than a second one that can drift away from it.
        val tile = recuperator(temperature = 29.3, humidity = 32.2)

        assertNotNull(climateLine(tile, now))
        assertEquals(3, span(tile))
    }

    @Test
    fun `a recuperator reporting only a humidity is still a hero tile`() {
        // Either value on its own is a climate line, so either value on its own is a hero tile.
        val tile = recuperator(temperature = null, humidity = 32.2)

        assertNotNull(climateLine(tile, now))
        assertEquals(3, span(tile))
    }

    @Test
    fun `a recuperator reporting neither is a medium tile`() {
        // A hero tile holding one line of "on · 2 min ago" is a hole in the wall.
        val tile = recuperator(temperature = null, humidity = null)

        assertNull(climateLine(tile, now))
        assertEquals(2, span(tile))
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
    fun `a bulb tile's glyph is the lamp, and only ever the lamp`() {
        // The bulb is not the curtain: its glyph labels a type and carries no state. A named bulb
        // tile is by construction the bulb the panel has no state for — bulbGroup breaks out
        // exactly the null ones — and the state of every other lamp is said by the disc it sits on.
        assertEquals(R.drawable.ic_bulb, glyph(bulb(isOn = null)))
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
        assertTrue(statusLine(ac, now, error = null).contains(promoted(ac)!!))
        val curtain = curtain(openPercent = 40.0)
        assertTrue(statusLine(curtain, now, error = null).contains(promoted(curtain)!!))
        val strip = strip(isOn = true)
        assertTrue(statusLine(strip, now, error = null).contains(promoted(strip)!!))
        val recuperator = recuperator(temperature = 29.3, humidity = 32.2)
        assertTrue(climateLine(recuperator, now)!!.contains(promoted(recuperator)!!))
    }

    @Test
    fun `a tile with no value to promote still says unknown in words`() {
        // Null promotes nothing; it does not take the word away. The status line is where "unknown"
        // has always been said and it goes on saying it.
        assertTrue(statusLine(ac(isOn = true, targetTemperature = null), now, error = null).contains("unknown"))
        assertTrue(statusLine(curtain(openPercent = null), now, error = null).contains("unknown"))
        assertTrue(statusLine(strip(isOn = true, brightnessPercent = null), now, error = null).contains("unknown"))
    }

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
    ) = AcTileState(
        id = "ac-01",
        name = "Кондиционер",
        room = "Зал",
        isOn = isOn,
        powerLastUpdated = Reading.At(now),
        targetTemperature = targetTemperature,
        bounds = Bounds(min = 16.0, max = 30.0, precision = 1.0),
        unit = unit,
        temperatureLastUpdated = Reading.At(now),
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
    ) = LightStripTileState(
        id = "strip-01",
        name = "Лента",
        room = "Коридор",
        isOn = isOn,
        powerLastUpdated = Reading.At(now),
        brightnessPercent = brightnessPercent,
        bounds = Bounds(min = 1.0, max = 100.0, precision = 1.0),
        unit = unit,
        brightnessLastUpdated = Reading.At(now),
        color = null,
    )

    private fun curtain(openPercent: Double?) = CurtainTileState(
        id = "curtain-01",
        name = "Штора",
        room = "Зал",
        openPercent = openPercent,
        bounds = Bounds(min = 0.0, max = 100.0, precision = 1.0),
        lastUpdated = Reading.At(now),
        stateChangedAt = Reading.At(now),
    )

    private fun launcher(openable: Boolean) = LauncherTileState(
        packageName = "com.example.intercom",
        name = "Домофон",
        room = null,
        openable = openable,
    )
}
