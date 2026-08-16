package ru.domovoy.panel

import org.junit.jupiter.api.Test
import ru.domovoy.core.Reading
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The two decisions in the mosaic that have a right and a wrong answer. They live out here rather
 * than inside a `@Composable` because there is no Compose test dependency and adding one is an
 * "ask first" — and because a decision no test can reach is a decision nobody checks.
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

    private fun recuperator(
        temperature: Double?,
        humidity: Double?,
    ) = RecuperatorTileState(
        id = "xfj-01",
        name = "Бризер",
        room = "Спальня",
        isOn = true,
        powerLastUpdated = Reading.At(now),
        speeds = listOf(FanSpeed.Low),
        speedLastUpdated = Reading.At(now),
        temperature = temperature,
        temperatureLastUpdated = if (temperature == null) Reading.Never else Reading.At(now),
        humidity = humidity,
        humidityLastUpdated = if (humidity == null) Reading.Never else Reading.At(now),
        online = true,
    )
}
