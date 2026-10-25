package ru.domovoy.panel

import org.junit.jupiter.api.Test
import ru.domovoy.core.Reading
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class PanelCopyTest {
    @Test
    fun `numeric values appear once on a tile`() {
        val ac = Flat.acs.tiles.first()
        val strip = Flat.strips.tiles.first()
        val curtain = Flat.curtains.tiles.first()
        assertFalse(statusLine(ac, Flat.NOW).contains("22 °C"))
        assertFalse(statusLine(strip, Flat.NOW).contains("60%"))
        assertFalse(statusLine(curtain, Flat.NOW).contains("40%"))
    }

    @Test
    fun `app cards give a recovery step without exposing a package name`() {
        val missing = Flat.launchers.last()
        assertEquals("Нет приложения", statusLine(missing))
        assertEquals("Установите его", detailLine(missing))
        assertFalse(anatomy(missing).detail.orEmpty().contains(missing.packageName))
    }

    @Test
    fun `recuperator geometry does not jump when readings arrive or fail`() {
        val known = Flat.recuperators.tiles.first()
        val unknown = known.copy(temperature = null, humidity = null)
        assertEquals(span(known), span(unknown))
        assertEquals(span(unknown), span(unknown.copy(error = "Нет связи")))
    }

    @Test
    fun `device identity carries the room outside a room section`() {
        val ac = Flat.acs.tiles.first()
        assertEquals("Зал", anatomy(ac, Flat.NOW, error = null).room)
        assertEquals("Коридор", anatomy(Flat.strips.tiles.first(), Flat.NOW, error = null).room)
    }

    @Test
    fun `unknown reading time does not turn confirmed off lamps into unknown power`() {
        val lamps = Flat.bulbs.tiles.take(2).map { it.copy(isOn = false, lastUpdated = ru.domovoy.core.Reading.Never) }
        val group = bulbGroup(lamps)
        assertEquals("0 из 2", promoted(group))
        assertEquals("Время неизвестно", bulbGroupLine(group, Flat.NOW))
        assertEquals(2, group.lamps.size)
        assertEquals(0, group.brokenOut.size)
        assertEquals("Нет данных", bulbGroupLine(bulbGroup(emptyList()), Flat.NOW))
    }

    @Test
    fun `an unread recuperator says no data only once and retains any actual reading age`() {
        val unread = Flat.recuperators.tiles.first().copy(
            isOn = null,
            powerLastUpdated = Reading.Never,
            speeds = emptyList(),
            speedLastUpdated = Reading.Never,
            temperature = null,
            temperatureLastUpdated = Reading.Never,
            humidity = null,
            humidityLastUpdated = Reading.Never,
            online = null,
        )
        assertEquals("Нет данных", statusLine(unread, Flat.NOW))
        val measuredHumidity = unread.copy(
            humidity = 41.0,
            humidityLastUpdated = Reading.At(Flat.NOW.minusSeconds(3 * 86_400)),
        )
        assertEquals("Нет данных · 3 дн. назад", statusLine(measuredHumidity, Flat.NOW))
        assertEquals("Выключено · Нет данных · Время неизвестно", statusLine(unread.copy(isOn = false), Flat.NOW))
    }

    @Test
    fun `capability labels use the language of the panel`() {
        assertEquals("Режим", capabilityLabel("thermostat"))
        assertEquals("Охлаждение", capabilityLabel("cool"))
        assertEquals("Ионизация", capabilityLabel("ionization"))
        assertEquals("Future mode", capabilityLabel("future_mode"))
    }

    @Test
    fun `tile names omit only their own trailing room context`() {
        val examples = listOf(
            Triple("Кондиционер в зале", "Зал", "Кондиционер"),
            Triple("Подсветка в зале", "Зал", "Подсветка"),
            Triple("Бризер зал", "Зал", "Бризер"),
            Triple("Споты в коридоре", "Коридор", "Споты"),
            Triple("Бризер спальня", "Спальня", "Бризер"),
            Triple("Бризер В СПАЛЬНЕ", "Спальня", "Бризер"),
            Triple("Лампа на балконе", "Балкон", "Лампа"),
            Triple("Лампа сауна", "Сауна", "Лампа"),
        )
        examples.forEach { (name, room, expected) ->
            val original = Flat.acs.tiles.first().copy(name = name, room = room)
            val tile = anatomy(original, Flat.NOW, error = null)
            assertEquals(expected, tile.name, name)
            assertEquals(room, tile.room)
            assertEquals(name, sheet(original, Flat.NOW, error = null).name)
            assertEquals(name, original.name)
        }
    }

    @Test
    fun `room removal preserves custom names and complete words`() {
        val examples = listOf(
            "Бризер данина комната" to "Маленькая детская",
            "Бризер данина комната" to "Маленькаядетская",
            "Бризер в зале" to "Спальня",
            "Лампа зальчик" to "Зал",
            "Лампа суперзал" to "Зал",
            "Свет в коридоре справа" to "Коридор",
            "Зал" to "Зал",
            "Кондиционер в зале" to null,
        )
        examples.forEach { (name, room) ->
            val original = Flat.acs.tiles.first().copy(name = name, room = room)
            assertEquals(name, anatomy(original, Flat.NOW, error = null).name, name)
        }
    }

    @Test
    fun `tile room names are shortened consistently for every device type`() {
        val strip = Flat.strips.tiles.first().copy(name = "Подсветка в зале", room = "Зал")
        val bulb = Flat.bulbs.tiles.first().copy(name = "Споты в коридоре", room = "Коридор")
        val curtain = Flat.curtains.tiles.first().copy(name = "Шторы в зале", room = "Зал")
        val recuperator = Flat.recuperators.tiles.first().copy(name = "Бризер спальня", room = "Спальня")
        val launcher = Flat.launchers.first().copy(name = "Домофон коридор", room = "Коридор")
        assertEquals("Подсветка", anatomy(strip, Flat.NOW, error = null).name)
        assertEquals("Споты", anatomy(bulb, Flat.NOW, error = null).name)
        assertEquals("Шторы", anatomy(curtain, Flat.NOW, error = null).name)
        assertEquals("Бризер", anatomy(recuperator, Flat.NOW, groupError = null).name)
        assertEquals("Домофон", anatomy(launcher).name)
    }

    @Test
    fun `ordinary light tiles omit colour metadata but preserve it in their sheets and retain errors`() {
        val strip = Flat.strips.tiles.first()
        val bulb = Flat.bulbs.tiles.first { it.color != null }
        assertNull(anatomy(strip, Flat.NOW, error = null).detail)
        assertNull(anatomy(bulb, Flat.NOW, error = null).detail)
        assertEquals("Нет связи", anatomy(strip, Flat.NOW, error = "Нет связи").detail)
        assertEquals("Нет связи", anatomy(bulb, Flat.NOW, error = "Нет связи").detail)
        assertEquals(colorLine(strip), sheet(strip, Flat.NOW, error = null).readings.single { it.label == "Цвет" }.value)
        assertEquals(colorDescription(bulb.color!!), sheet(bulb, Flat.NOW, error = null).readings.single { it.label == "Цвет" }.value)
    }
}
