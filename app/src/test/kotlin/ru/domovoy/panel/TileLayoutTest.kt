package ru.domovoy.panel

import org.junit.jupiter.api.Test
import ru.domovoy.R
import ru.domovoy.core.Bounds
import ru.domovoy.core.Reading
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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

    private fun ac(isOn: Boolean?) = AcTileState(
        id = "ac-01",
        name = "Кондиционер",
        room = "Зал",
        isOn = isOn,
        powerLastUpdated = Reading.At(now),
        targetTemperature = 22.0,
        bounds = Bounds(min = 16.0, max = 30.0, precision = 1.0),
        unit = "unit.temperature.celsius",
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

    private fun strip(isOn: Boolean?) = LightStripTileState(
        id = "strip-01",
        name = "Лента",
        room = "Коридор",
        isOn = isOn,
        powerLastUpdated = Reading.At(now),
        brightnessPercent = 60.0,
        bounds = Bounds(min = 1.0, max = 100.0, precision = 1.0),
        unit = "unit.percent",
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
