package ru.domovoy.panel

import org.junit.jupiter.api.Test
import ru.domovoy.core.Bounds
import ru.domovoy.core.ColorSetting
import ru.domovoy.core.Mode
import ru.domovoy.core.Reading
import ru.domovoy.core.Toggle
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TileSheetTest {
    private val now = Flat.NOW
    private val ac = Flat.acs.tiles.first()
    private val curtain = Flat.curtains.tiles.first()
    private val strip = Flat.strips.tiles.first()
    private val plainStrip = Flat.strips.tiles.last()
    private val bulb = Flat.bulbs.tiles.first()
    private val recuperator = Flat.recuperators.tiles.first()
    private val offline = Flat.recuperators.tiles.last()

    @Test
    fun `what a sheet offers is its device type's`() {
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
    }

    @Test
    fun `each controllable tile resolves to its sheet type`() {
        assertEquals(SheetSubject.AirConditioner, subject(ac))
        assertEquals(SheetSubject.Curtain, subject(curtain))
        assertEquals(SheetSubject.LightStrip, subject(strip))
        assertEquals(SheetSubject.Bulb, subject(bulb))
        assertEquals(SheetSubject.Recuperator, subject(recuperator))
    }

    @Test
    fun `a device whose vendor named no range is offered neither the level nor its ends`() {
        assertEquals(
            setOf(SheetAction.Power, SheetAction.Mode, SheetAction.Toggle),
            sheet(ac.copy(bounds = null), now, error = null).actions,
        )
        assertEquals(setOf(SheetAction.Power), sheet(strip.copy(bounds = null), now, error = null).actions)
        assertEquals(emptySet(), sheet(curtain.copy(bounds = null), now, error = null).actions)
    }

    @Test
    fun `an AC sheet carries only modes and toggles advertised by that unit`() {
        val fan = Mode("auto", listOf("low", "medium", "high", "auto"), Reading.Never)
        val ion = Toggle(false, Reading.Never)
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

        assertEquals(16.0, sheet(ac.copy(targetTemperature = null), now, error = null).level?.value)
        assertEquals(0.0, sheet(curtain.copy(openPercent = null), now, error = null).level?.value)
    }

    @Test
    fun `a sheet ages every reading separately, which is what the tile stopped doing`() {
        val sheet = sheet(ac, now, error = null)

        assertEquals(listOf("Питание", "Задано", "В комнате"), sheet.readings.map { it.label })
        assertEquals(listOf("Включено", "22 °C", "26.0 °C"), sheet.readings.map { it.value })
        assertEquals(listOf("1 мин назад", "81 дн. назад", "1 мин назад"), sheet.readings.map { it.age })
    }

    @Test
    fun `how old a reading is, said in full`() {
        assertEquals("Время неизвестно", sheetAge(Reading.Never, now))
        assertEquals("Только что", sheetAge(Reading.At(now), now))
        assertEquals("Только что", sheetAge(Reading.At(now.minusSeconds(59)), now))
        assertEquals("1 мин назад", sheetAge(Reading.At(now.minusSeconds(60)), now))
        assertEquals("59 мин назад", sheetAge(Reading.At(now.minusSeconds(59 * 60)), now))
        assertEquals("1 ч назад", sheetAge(Reading.At(now.minusSeconds(60 * 60)), now))
        assertEquals("23 ч назад", sheetAge(Reading.At(now.minusSeconds(23 * 3600)), now))
        assertEquals("1 дн. назад", sheetAge(Reading.At(now.minusSeconds(24 * 3600)), now))

        assertEquals("Только что", sheetAge(Reading.At(now.plusSeconds(600)), now))
    }

    @Test
    fun `no value the tile shows is missing from the sheet`() {
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
    fun `the strip's colour is on its sheet and is still только просмотр`() {
        val colour = sheet(strip, now, error = null).readings.single { it.label == "Цвет" }

        assertEquals(colorLine(strip), colour.value)
        assertTrue("только просмотр" in colour.value)

        assertTrue(sheet(plainStrip, now, error = null).readings.none { it.label == "Цвет" })
    }

    @Test
    fun `a recuperator's sheet is the four ages its tile prints as one`() {
        val sheet = sheet(recuperator, now, groupError = null)

        assertEquals(listOf("Питание", "Скорость", "Температура", "Влажность"), sheet.readings.map { it.label })
        assertEquals("Низкая", sheet.readings[1].value)
        assertEquals("26.4 °C", sheet.readings[2].value)
        assertEquals("41.0 %", sheet.readings[3].value)
    }

    @Test
    fun `an offline recuperator claims neither its switch nor its speed`() {
        val sheet = sheet(offline, now, groupError = null)

        assertEquals("Нет связи", sheet.readings.first().value)
        assertTrue(sheet.readings.none { it.label == "Скорость" })
    }

    @Test
    fun `a sheet says why the panel stopped reading the device, in the words the wall uses`() {
        assertEquals("timed out", sheet(ac, now, error = "timed out").notUpdating)
        assertEquals("timed out", sheet(bulb, now, error = "timed out").notUpdating)
        assertNull(sheet(ac, now, error = null).notUpdating)

        assertEquals("Устройство не отвечает", sheet(offline, now, groupError = "unreachable").notUpdating)
        assertEquals("unreachable", sheet(recuperator, now, groupError = "unreachable").notUpdating)
    }

    @Test
    fun `a failing poll takes nothing off the sheet`() {
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
