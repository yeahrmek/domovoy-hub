package ru.domovoy.panel

import org.junit.jupiter.api.Test
import ru.domovoy.R
import ru.domovoy.core.Bounds
import ru.domovoy.core.ColorSetting
import ru.domovoy.core.Mode
import ru.domovoy.core.Reading
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TileLayoutTest {
    private val now = Instant.ofEpochSecond(1_786_000_000)

    @Test
    fun `a recuperator reporting a temperature is a wide tile`() {
        val tile = recuperator(temperature = 29.3, humidity = 32.2)

        assertNotNull(climateLine(tile))
        assertEquals(4, span(tile))
    }

    @Test
    fun `a recuperator reporting only a humidity is still a wide tile`() {
        val tile = recuperator(temperature = null, humidity = 32.2)

        assertNotNull(climateLine(tile))
        assertEquals(4, span(tile))
    }

    @Test
    fun `a recuperator keeps its width before climate readings arrive`() {
        val tile = recuperator(temperature = null, humidity = null)

        assertNull(climateLine(tile))
        assertEquals(4, span(tile))
    }

    @Test
    fun `a recuperator with nothing to report but a failure is wide anyway`() {
        val tile = recuperator(temperature = null, humidity = null, isOn = null, error = "timed out")

        assertNull(climateLine(tile))
        assertEquals(4, span(tile))
    }

    @Test
    fun `the group's failure does not move a recuperator's span`() {
        val tile = recuperator(temperature = null, humidity = null)

        assertEquals(4, span(tile))
        assertEquals("timed out", anatomy(tile, now, groupError = "timed out").detail)
        assertEquals(4, span(tile))
    }

    @Test
    fun `a tile whose poll failed is failing, whatever it last said about being on`() {
        assertEquals(TileMood.Failing, mood(isOn = true, error = "HTTP 500"))
        assertEquals(TileMood.Failing, mood(isOn = false, error = "HTTP 500"))
        assertEquals(TileMood.Failing, mood(isOn = null, error = "timeout"))
    }

    @Test
    fun `a tile that never reported is unknown, and never off`() {
        assertEquals(TileMood.Unknown, mood(isOn = null, error = null))
    }

    @Test
    fun `a tile that reported says on or off`() {
        assertEquals(TileMood.On, mood(isOn = true, error = null))
        assertEquals(TileMood.Off, mood(isOn = false, error = null))
    }

    @Test
    fun `curtain art is the same two-half hardware at every position`() {
        listOf(0.0, 40.0, 100.0, null).forEach { position ->
            assertEquals(R.drawable.device_art_curtain, art(curtain(openPercent = position)))
        }
    }

    @Test
    fun `a group with any lamp lit is on, and one with none is off`() {
        assertEquals(TileMood.On, mood(lamps(on = 1, off = 6)))
        assertEquals(TileMood.On, mood(lamps(on = 7, off = 0)))
        assertEquals(TileMood.Off, mood(lamps(on = 0, off = 7)))
    }

    @Test
    fun `bulb and strip art lights only when the device positively reports on`() {
        assertEquals(R.drawable.device_art_bulb_on, art(bulb(isOn = true)))
        assertEquals(R.drawable.device_art_bulb_off, art(bulb(isOn = false)))
        assertEquals(R.drawable.device_art_bulb_off, art(bulb(isOn = null)))

        assertEquals(R.drawable.device_art_light_strip_on, art(strip(isOn = true)))
        assertEquals(R.drawable.device_art_light_strip_off, art(strip(isOn = false)))
        assertEquals(R.drawable.device_art_light_strip_off, art(strip(isOn = null)))
    }

    @Test
    fun `a room's bulb art lights when any lamp in it is on`() {
        assertEquals(R.drawable.device_art_bulb_on, art(lamps(on = 1, off = 6)))
        assertEquals(R.drawable.device_art_bulb_off, art(lamps(on = 0, off = 7)))
    }

    @Test
    fun `every current tile kind resolves to its real device art`() {
        assertEquals(R.drawable.device_art_air_conditioner, art(ac(isOn = true)))
        assertEquals(
            R.drawable.device_art_recuperator,
            art(recuperator(temperature = 29.3, humidity = 32.2)),
        )
        assertEquals(R.drawable.device_art_curtain, art(curtain(openPercent = 40.0)))

        val launchers = launcherTiles { true }.associateBy { it.packageName }
        assertEquals(R.drawable.device_art_intercom, art(launchers.getValue("com.domonap.app")))
        assertEquals(R.drawable.device_art_vacuum, art(launchers.getValue("com.xiaomi.smarthome")))
    }

    @Test
    fun `a group whose poll failed keeps its count's mood and takes the outline`() {
        assertEquals(TilePaint(TileMood.On, groupFailing = true), paint(lamps(on = 7, off = 0), "HTTP 500"))
        assertEquals(TilePaint(TileMood.Off, groupFailing = true), paint(lamps(on = 0, off = 7), "HTTP 500"))
    }

    @Test
    fun `a group tile promotes how many of its lamps are on`() {
        assertEquals("5 из 7", promoted(lamps(on = 5, off = 2)))

        assertEquals("0 из 3", promoted(lamps(on = 0, off = 3)))
    }

    @Test
    fun `a group tile is named for how many lamps it holds`() {
        assertEquals("Свет", anatomy(lamps(on = 5, off = 2), now, null, notUpdating = false, open = false).name)
        assertEquals("Свет", anatomy(lamps(on = 1, off = 0), now, null, notUpdating = false, open = false).name)
    }

    @Test
    fun `a group tile says what its tap will do`() {
        val group = lamps(on = 5, off = 2)
        assertEquals("Показать", anatomy(group, now, null, notUpdating = false, open = false).detail)
        assertEquals("Свернуть", anatomy(group, now, null, notUpdating = false, open = true).detail)
    }

    @Test
    fun `opening a group changes nothing it says about the lamps`() {
        val group = lamps(on = 5, off = 2)
        val closed = anatomy(group, now, null, notUpdating = false, open = false)
        val open = anatomy(group, now, null, notUpdating = false, open = true)

        assertEquals(closed.copy(detail = open.detail), open)
    }

    @Test
    fun `a group tile that stopped updating says so once, for all of its lamps`() {
        val tile = anatomy(lamps(on = 5, off = 2), now, "timed out", notUpdating = true, open = false)

        assertEquals(bulbGroupLine(lamps(on = 5, off = 2), now), tile.status)

        assertEquals("timed out", tile.detail)
    }

    @Test
    fun `a lit tile is raised, a tile nobody has read is sunk, and a failing one is above both`() {
        assertTrue(surface(TileMood.On) > surface(TileMood.Failing))
        assertTrue(surface(TileMood.Failing) > surface(TileMood.Off))
        assertTrue(surface(TileMood.Off) > surface(TileMood.Unknown))
    }

    @Test
    fun `the marks are the on indicator and the failure, and nothing else is marked`() {
        assertEquals(setOf(TileMark.Lit, TileMark.Power), marks(TileMood.On))

        assertEquals(setOf(TileMark.Offline), marks(TileMood.Failing))
        assertEquals(emptySet(), marks(TileMood.Off))
        assertEquals(emptySet(), marks(TileMood.Unknown))
    }

    @Test
    fun `the unreachable mark is a tile's own failure and never its group's`() {
        val ownFailure = paint(recuperator(temperature = null, humidity = null, isOn = true, error = "timed out"), null)
        val groupFailure = paint(recuperator(temperature = 26.4, humidity = 41.0), "HTTP 500")

        assertTrue(TileMark.Offline in marks(ownFailure.mood))
        assertTrue(TileMark.Offline !in marks(groupFailure.mood))
    }

    @Test
    fun `a group failure outlines every kind of tile and leaves its mood alone`() {
        assertEquals(TilePaint(TileMood.On, groupFailing = true), paint(ac(isOn = true), "HTTP 500"))
        assertEquals(TilePaint(TileMood.Off, groupFailing = true), paint(bulb(isOn = false), "HTTP 500"))
        assertEquals(TilePaint(TileMood.On, groupFailing = true), paint(strip(isOn = true), "HTTP 500"))
        assertEquals(
            TilePaint(TileMood.On, groupFailing = true),
            paint(curtain(openPercent = 40.0), now, "HTTP 500"),
        )
        assertEquals(
            TilePaint(TileMood.On, groupFailing = true),
            paint(recuperator(temperature = 29.3, humidity = 32.2), "HTTP 500"),
        )
        assertEquals(TilePaint(TileMood.On, groupFailing = true), paint(lamps(on = 3, off = 2), "HTTP 500"))
    }

    @Test
    fun `a tile's own failure fills and does not outline`() {
        assertEquals(
            TilePaint(TileMood.Failing, groupFailing = false),
            paint(recuperator(temperature = null, humidity = null, isOn = true, error = "timeout"), null),
        )
        assertEquals(TilePaint(TileMood.Failing, groupFailing = false), paint(launcher(openable = false)))
        assertEquals(TilePaint(TileMood.Unknown, groupFailing = false), paint(launcher(openable = true)))
    }

    @Test
    fun `a recuperator with both kinds of bad news says both`() {
        assertEquals(
            TilePaint(TileMood.Failing, groupFailing = true),
            paint(recuperator(temperature = null, humidity = null, isOn = true, error = "timeout"), "HTTP 500"),
        )
    }

    @Test
    fun `a curtain that is open at all is on, whatever is failing around it`() {
        assertEquals(TileMood.On, paint(curtain(openPercent = 40.0), now, null).mood)
        assertEquals(TileMood.Off, paint(curtain(openPercent = 0.0), now, null).mood)
        assertEquals(TileMood.Unknown, paint(curtain(openPercent = null), now, null).mood)
        assertEquals(TileMood.On, paint(curtain(openPercent = 40.0), now, "HTTP 500").mood)
    }

    @Test
    fun `a curtain whose position has aged out reads as unknown, not as the position it had`() {
        val aged = curtain(openPercent = 0.0).copy(lastUpdated = hoursAgo(13))

        assertEquals(TileMood.Unknown, paint(aged, now, null).mood)
        assertEquals(TileMood.Unknown, paint(curtain(openPercent = 40.0).copy(lastUpdated = hoursAgo(13)), now, null).mood)
    }

    @Test
    fun `an air conditioner promotes its target temperature`() {
        assertEquals("22 °C", promoted(ac(isOn = true)))
    }

    @Test
    fun `an air conditioner in a unit nobody named promotes the bare number`() {
        assertEquals("22", promoted(ac(isOn = true, unit = "unit.temperature.kelvin")))
    }

    @Test
    fun `an air conditioner with no target promotes nothing`() {
        assertNull(promoted(ac(isOn = true, targetTemperature = null)))
    }

    @Test
    fun `a curtain promotes how far open it is`() {
        assertEquals("40%", promoted(curtain(openPercent = 40.0)))
        assertEquals("0%", promoted(curtain(openPercent = 0.0)))
    }

    @Test
    fun `a curtain with no position promotes nothing`() {
        assertNull(promoted(curtain(openPercent = null)))
    }

    @Test
    fun `a curtain promotes a position only while the vendor still vouches for it`() {
        assertEquals("0%", anatomy(curtain(openPercent = 0.0), now, error = null).promoted)
        assertNull(anatomy(curtain(openPercent = 0.0).copy(lastUpdated = hoursAgo(13)), now, error = null).promoted)
    }

    @Test
    fun `the freshest of the two commands is the position, and an open or close is an end of travel`() {
        val closed = curtain(openPercent = 50.0).copy(
            lastUpdated = minutesAgo(6),
            openClose = false,
            openCloseLastUpdated = minutesAgo(2),
        )
        val opened = closed.copy(openClose = true)

        assertEquals("0%", anatomy(closed, now, error = null).promoted)
        assertEquals(TileMood.Off, paint(closed, now, null).mood)
        assertEquals(TileAction.Open, action(closed, now))

        assertEquals("100%", anatomy(opened, now, error = null).promoted)
        assertEquals(TileMood.On, paint(opened, now, null).mood)
        assertEquals(TileAction.Close, action(opened, now))
    }

    @Test
    fun `an open or close is read against the bounds the vendor reported, not against 0 and 100`() {
        val narrow = Bounds(min = 10.0, max = 90.0, precision = 1.0)
        val tile = curtain(openPercent = 50.0, bounds = narrow).copy(
            lastUpdated = minutesAgo(6),
            openClose = true,
            openCloseLastUpdated = minutesAgo(2),
        )

        assertEquals("90%", anatomy(tile, now, error = null).promoted)
    }

    @Test
    fun `an open or close the panel cannot place leaves no position at all`() {
        val tile = curtain(openPercent = 50.0, bounds = null).copy(
            lastUpdated = minutesAgo(6),
            openClose = true,
            openCloseLastUpdated = minutesAgo(2),
        )

        assertNull(anatomy(tile, now, error = null).promoted)
        assertEquals("Нет данных", statusLine(tile, now))
        assertEquals(TileMood.Unknown, paint(tile, now, null).mood)
    }

    @Test
    fun `the slider starts from the position the card is printing, not the one it overtook`() {
        val spoken = curtain(openPercent = 50.0).copy(
            lastUpdated = minutesAgo(6),
            openClose = true,
            openCloseLastUpdated = minutesAgo(2),
        )

        assertEquals(100f, sliderStart(spoken, BOUNDS.min))

        assertEquals(0f, sliderStart(curtain(openPercent = null), BOUNDS.min))
    }

    @Test
    fun `an open or close older than the position leaves that position alone`() {
        val driven = curtain(openPercent = 50.0).copy(
            lastUpdated = minutesAgo(2),
            openClose = false,
            openCloseLastUpdated = minutesAgo(6),
        )

        assertEquals("50%", anatomy(driven, now, error = null).promoted)
        assertEquals(TileMood.On, paint(driven, now, null).mood)
        assertEquals("2 мин назад", statusLine(driven, now))
    }

    @Test
    fun `a position taken from an open or close ages by that command's own clock`() {
        val spoken = curtain(openPercent = 50.0).copy(
            lastUpdated = hoursAgo(13),
            openClose = true,
            openCloseLastUpdated = minutesAgo(2),
        )

        assertEquals("2 мин назад", statusLine(spoken, now))
        assertEquals("100%", anatomy(spoken, now, error = null).promoted)
    }

    @Test
    fun `an open or close ages out exactly as a position does`() {
        val old = curtain(openPercent = 50.0).copy(
            lastUpdated = hoursAgo(20),
            openClose = true,
            openCloseLastUpdated = hoursAgo(13),
        )

        assertNull(anatomy(old, now, error = null).promoted)
        assertEquals(TileMood.Unknown, paint(old, now, null).mood)
        assertEquals("100% · 13 ч назад", statusLine(old, now))
    }

    @Test
    fun `a curtain whose position has aged out still says it, in small type, with its age`() {
        val aged = curtain(openPercent = 0.0).copy(lastUpdated = hoursAgo(13))

        assertEquals("0% · 13 ч назад", statusLine(aged, now))
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
        assertEquals("29.3 °C", promoted(recuperator(temperature = 29.3, humidity = 32.2)))
    }

    @Test
    fun `a recuperator reporting only humidity promotes nothing`() {
        assertNull(promoted(recuperator(temperature = null, humidity = 32.2)))
        assertNull(promoted(recuperator(temperature = null, humidity = null)))
    }

    @Test
    fun `a dimmable bulb promotes brightness while a relay promotes nothing`() {
        listOf(true, false, null).forEach { state ->
            assertEquals("72%", promoted(bulb(isOn = state, brightnessPercent = 72.0)))
            assertNull(promoted(bulb(isOn = state)))
        }
    }

    @Test
    fun `a launcher promotes nothing, installed or not`() {
        assertNull(promoted(launcher(openable = true)))
        assertNull(promoted(launcher(openable = false)))
    }

    @Test
    fun `what a tile promotes does not move with its mood`() {
        assertEquals("22 °C", promoted(ac(isOn = null)))
        assertEquals(
            "29.3 °C",
            promoted(recuperator(temperature = 29.3, humidity = 32.2, isOn = null, error = "timeout")),
        )
    }

    @Test
    fun `a promoted value is not repeated in the supporting text`() {
        val ac = ac(isOn = true)
        assertFalse(statusLine(ac, now).contains(promoted(ac)!!))
        val curtain = curtain(openPercent = 40.0)
        assertFalse(statusLine(curtain, now).contains(promoted(curtain)!!))
        val strip = strip(isOn = true)
        assertFalse(statusLine(strip, now).contains(promoted(strip)!!))
        val recuperator = recuperator(temperature = 29.3, humidity = 32.2)
        assertFalse(climateLine(recuperator)!!.contains(promoted(recuperator)!!))
    }

    @Test
    fun `a tile with no value to promote still says unknown in words`() {
        assertTrue(statusLine(ac(isOn = true, targetTemperature = null), now).contains("неизвестна"))
        assertTrue(statusLine(curtain(openPercent = null), now).contains("Нет данных"))
        assertTrue(statusLine(strip(isOn = true, brightnessPercent = null), now).contains("неизвестна"))
    }

    @Test
    fun `every tile type fills the art, the name and the status slot, in every state it has`() {
        everyTile().forEach { anatomy ->
            assertNotEquals(0, anatomy.art)
            assertTrue(anatomy.name.isNotBlank(), "a tile with no name: $anatomy")
            assertTrue(anatomy.status.isNotBlank(), "a tile with no status line: $anatomy")
        }
    }

    @Test
    fun `an empty promoted slot is an answer and stays an answer`() {
        assertNull(anatomy(bulb(isOn = true), now, error = null).promoted)
        assertNull(anatomy(launcher(openable = true)).promoted)
        assertEquals("22 °C", anatomy(ac(isOn = true), now, error = null).promoted)
        assertEquals("40%", anatomy(curtain(openPercent = 40.0), now, error = null).promoted)
        assertEquals("60%", anatomy(strip(isOn = true), now, error = null).promoted)
        assertEquals(
            "29.3 °C",
            anatomy(recuperator(temperature = 29.3, humidity = 32.2), now, groupError = null).promoted,
        )
    }

    @Test
    fun `a tile with nothing wrong and nothing else to report has no second line`() {
        assertNull(anatomy(strip(isOn = true, color = warmWhite), now, error = null).detail)
        assertNotNull(
            anatomy(recuperator(temperature = 29.3, humidity = 32.2), now, groupError = null).detail,
        )
        assertNull(anatomy(launcher(openable = true)).detail)
        assertNull(anatomy(ac(isOn = true), now, error = null).detail)
        assertNull(anatomy(curtain(openPercent = 40.0), now, error = null).detail)
        assertNull(anatomy(bulb(isOn = true), now, error = null).detail)

        assertNull(anatomy(strip(isOn = true), now, error = null).detail)
    }

    @Test
    fun `a tile whose readings are all fresh prints no age at all`() {
        everyTile().forEach { anatomy ->
            assertEquals(emptyList(), agesOn(anatomy), "a fresh tile still printing an age: $anatomy")
        }
    }

    @Test
    fun `no tile prints the same age twice, however many readings are behind it`() {
        everyStaleTile().forEach { anatomy ->
            val ages = agesOn(anatomy)
            assertEquals(1, ages.size, "a tile saying its age $ages times: $anatomy")
        }
    }

    private fun agesOn(anatomy: TileAnatomy): List<String> {
        val words = anatomy.status.split(" · ") + anatomy.detail.orEmpty().split(" · ")
        return words.filter { it == "Время неизвестно" || Regex("""^\d+ (ч|дн\.) назад$""").matches(it) }
    }

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
    fun `which button a tile carries at its top right is its type's, and most types carry none`() {
        assertNull(action(ac(isOn = true)))
        assertNull(action(strip(isOn = true)))
        assertNull(action(bulb(isOn = true)))
        assertEquals(TileAction.Fan, action(recuperator(temperature = 29.3, humidity = 32.2)))
        assertNull(action(lamps(on = 5, off = 2)))

        assertNull(action(launcher(openable = true)))
        assertNull(action(launcher(openable = false)))

        assertEquals(TileAction.Close, action(curtain(openPercent = 40.0), now))
    }

    @Test
    fun `an AC with a fan mode and a wide powered recuperator get the useful fan shortcut`() {
        val fan = Mode("auto", listOf("low", "medium", "high", "auto"), Reading.Never)
        val ac = ac(isOn = true).copy(modes = mapOf("fan_speed" to fan))
        val recuperator = recuperator(temperature = 29.3, humidity = 32.2, isOn = true)

        assertEquals(TileAction.Fan, action(ac))
        assertEquals("low", nextFanMode(ac))
        assertEquals(TileAction.Fan, action(recuperator))
        assertEquals(FanSpeed.Medium, nextFanSpeed(recuperator))
    }

    @Test
    fun `fan shortcuts disappear when they cannot safely do anything`() {
        val noFan = ac(isOn = true)
        val offRecuperator = recuperator(temperature = 29.3, humidity = 32.2, isOn = false)
        val offlineRecuperator = recuperator(temperature = null, humidity = null, isOn = true).copy(online = false)

        assertNull(action(noFan))
        assertNull(action(offRecuperator))
        assertNull(action(offlineRecuperator))
    }

    @Test
    fun `a curtain offers the end of travel it is not already at`() {
        assertEquals(TileAction.Open, action(curtain(openPercent = 0.0), now))
        assertEquals(TileAction.Close, action(curtain(openPercent = 1.0), now))
        assertEquals(TileAction.Close, action(curtain(openPercent = 40.0), now))
        assertEquals(TileAction.Close, action(curtain(openPercent = 100.0), now))

        assertEquals(TileAction.Close, action(curtain(openPercent = null), now))

        assertEquals(TileAction.Close, action(curtain(openPercent = 0.0).copy(lastUpdated = hoursAgo(13)), now))
    }

    @Test
    fun `a curtain whose vendor never sent bounds gets no button`() {
        assertNull(action(curtain(openPercent = 40.0, bounds = null), now))
        assertNull(action(curtain(openPercent = 0.0, bounds = null), now))
    }

    @Test
    fun `the button drives to the end of the range the vendor reported, not to 0 and 100`() {
        val curtain = curtain(openPercent = 40.0)
        assertEquals(0.0, actionTarget(curtain, TileAction.Close))
        assertEquals(100.0, actionTarget(curtain, TileAction.Open))

        val narrow = curtain(openPercent = 40.0, bounds = Bounds(min = 10.0, max = 90.0, precision = 1.0))
        assertEquals(10.0, actionTarget(narrow, TileAction.Close))
        assertEquals(90.0, actionTarget(narrow, TileAction.Open))
        assertNull(actionTarget(curtain(openPercent = 40.0, bounds = null), TileAction.Close))
    }

    @Test
    fun `secondary controls belong to wide powered devices`() {
        val narrow = recuperator(temperature = null, humidity = null)
        assertEquals(WIDE_SPAN, span(narrow))
        assertEquals(TileAction.Fan, action(narrow))
        assertNull(action(bulb(isOn = true)))
        assertNull(action(launcher(openable = true)))
        assertNull(action(lamps(on = 5, off = 2)))
    }

    @Test
    fun `a button is drawn as the state it produces`() {
        assertEquals(R.drawable.ic_vertical_shades, glyph(TileAction.Open))
        assertEquals(R.drawable.ic_vertical_shades_closed, glyph(TileAction.Close))
        assertEquals(R.drawable.ic_mode_fan, glyph(TileAction.Fan))
    }

    @Test
    fun `a tile keeps its button while its poll is failing`() {
        assertEquals(TileAction.Close, anatomy(curtain(openPercent = 40.0), now, "timed out").action)
        assertEquals(TileAction.Open, anatomy(curtain(openPercent = 0.0), now, "timed out").action)
    }

    @Test
    fun `the anatomy says exactly what the tile's own functions say`() {
        val ac = ac(isOn = true)
        assertEquals(statusLine(ac, now), anatomy(ac, now, "timed out").status)
        assertEquals(promoted(ac), anatomy(ac, now, error = null).promoted)
        assertEquals(art(ac), anatomy(ac, now, error = null).art)
        assertEquals(action(ac), anatomy(ac, now, error = null).action)
        val curtain = curtain(openPercent = 40.0)
        assertEquals(action(curtain, now), anatomy(curtain, now, error = null).action)
        val recuperator = recuperator(temperature = 29.3, humidity = 32.2)
        assertEquals(climateLine(recuperator), anatomy(recuperator, now, groupError = null).detail)
        val launcher = launcher(openable = false)
        assertEquals(statusLine(launcher), anatomy(launcher).status)
        assertEquals(detailLine(launcher), anatomy(launcher).detail)
    }

    @Test
    fun `a tile's second line is why it stopped updating, before anything else it had to say`() {
        assertEquals("timed out", anatomy(ac(isOn = true), now, "timed out").detail)
        assertEquals("timed out", anatomy(curtain(openPercent = 40.0), now, "timed out").detail)
        assertEquals("timed out", anatomy(bulb(isOn = true), now, "timed out").detail)
        assertEquals("timed out", anatomy(strip(isOn = true, color = warmWhite), now, "timed out").detail)
        assertEquals(
            "timed out",
            anatomy(recuperator(temperature = 29.3, humidity = 32.2), now, groupError = "timed out").detail,
        )

        assertNull(anatomy(strip(isOn = true, color = warmWhite), now, error = null).detail)
    }

    @Test
    fun `a recuperator names its own failure rather than its group's`() {
        val tile = recuperator(temperature = null, humidity = null, error = "timed out")

        assertEquals("timed out", anatomy(tile, now, groupError = "unreachable").detail)
    }

    @Test
    fun `nothing a tile says carries the reason twice`() {
        everyTile().forEach { tile ->
            val reason = tile.detail ?: return@forEach
            assertFalse(
                tile.status.contains(reason),
                "a tile saying the same thing twice: ${tile.status} / $reason",
            )
        }
    }

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

    private fun lamps(
        on: Int,
        off: Int,
    ) = bulbGroup(
        (1..on).map { bulb(isOn = true) } + (1..off).map { bulb(isOn = false) },
    )

    private fun bulb(
        isOn: Boolean?,
        brightnessPercent: Double? = null,
    ) = BulbTileState(
        id = "light-01",
        name = "Лампа",
        room = "Коридор",
        isOn = isOn,
        lastUpdated = Reading.At(now),
        brightnessPercent = brightnessPercent,
        brightnessBounds = brightnessPercent?.let { BOUNDS },
        brightnessLastUpdated = brightnessPercent?.let { Reading.At(now) } ?: Reading.Never,
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
        openClose = null,
        openCloseLastUpdated = Reading.Never,
    )

    private fun hoursAgo(hours: Long) = Reading.At(now.minusSeconds(hours * 3600))

    private fun minutesAgo(minutes: Long) = Reading.At(now.minusSeconds(minutes * 60))

    private fun launcher(openable: Boolean) = LauncherTileState(
        packageName = "com.example.intercom",
        name = "Домофон",
        room = null,
        openable = openable,
    )

    private companion object {
        val BOUNDS = Bounds(min = 0.0, max = 100.0, precision = 1.0)
    }
}
