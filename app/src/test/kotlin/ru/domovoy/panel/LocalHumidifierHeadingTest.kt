package ru.domovoy.panel

import org.junit.jupiter.api.Test
import ru.domovoy.core.Device
import ru.domovoy.core.DeviceKind
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class LocalHumidifierHeadingTest {
    private val now = Instant.parse("2026-11-08T12:00:00Z")
    private val tile = Device("native", "Увлажнитель", DeviceKind.Other, "Спальня", null, vendorType = "devices.types.humidifier")
    private val section = RoomSection("Спальня", discovered = listOf(tile))
    private val polls = GroupPolls(null, null, null, null, null)

    @Test
    fun `healthy native room has no Yandex outage marker or banner`() {
        val errors = GroupErrors(discovered = "Yandex unavailable", localDiscovered = mapOf(tile.id to false))
        assertFalse(panelHeadings(listOf(section), errors, polls, now, 1.minutes, 1.minutes).single { it.title == "Спальня" }.marked)
        assertTrue(sectionFailures(section, errors).isEmpty())
    }

    @Test
    fun `native failure marks its room while mixed room still reports Yandex outage`() {
        val failed = GroupErrors(localDiscovered = mapOf(tile.id to true))
        assertTrue(panelHeadings(listOf(section), failed, polls, now, 1.minutes, 1.minutes).single { it.title == "Спальня" }.marked)
        val mixed = section.copy(discovered = listOf(tile, tile.copy(id = "cloud")))
        val errors = GroupErrors(discovered = "Yandex unavailable", localDiscovered = mapOf(tile.id to false))
        assertTrue(panelHeadings(listOf(mixed), errors, polls, now, 1.minutes, 1.minutes).single { it.title == "Спальня" }.marked)
        assertTrue(sectionFailures(mixed, errors).isNotEmpty())
    }
}
