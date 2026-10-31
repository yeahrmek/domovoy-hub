package ru.domovoy.panel

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.Test
import ru.domovoy.core.DeviceKind
import ru.domovoy.core.Reading
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DiscoveredDevicesTest {
    private val now = Instant.parse("2026-10-31T12:00:00Z")

    @Test
    fun `new types arrive on the next shared poll and disappear only after a successful inventory read`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse(body = yandexFixture()))
            server.enqueue(MockResponse(body = discoveredFixture()))
            server.enqueue(MockResponse(code = 500))
            server.enqueue(MockResponse(body = """{"status":"ok","devices":[]}"""))
            val poll = YandexPoll(server.yandexClient()) { now }

            poll.refresh()
            assertEquals(5, poll.discovered.state.value.tiles.size)
            poll.refresh()
            val arrived = poll.discovered.state.value
            assertEquals(5, arrived.tiles.size)
            assertEquals(setOf("Датчик климата", "Умный замок", "Домофон"), arrived.tiles.map { it.name }.toSet())
            assertEquals(now, arrived.lastPolledAt)
            assertTrue(poll.bulbs.state.value.tiles.isEmpty())
            poll.refresh()
            assertEquals(arrived.tiles, poll.discovered.state.value.tiles)
            assertEquals(now, poll.discovered.state.value.lastPolledAt)
            assertEquals("Не удалось выполнить запрос", poll.discovered.state.value.error)
            poll.refresh()
            assertTrue(poll.discovered.state.value.tiles.isEmpty())
            assertNull(poll.discovered.state.value.error)
            assertEquals(4, server.requestCount)
        }
    }

    @Test
    fun `unfamiliar future types are retained but devices from another household are not`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val root = Json.parseToJsonElement(discoveredFixture()).jsonObject
            val prototype = root["devices"]!!.jsonArray.first().jsonObject
            val future = JsonObject(prototype + mapOf("id" to JsonPrimitive("future"), "type" to JsonPrimitive("devices.types.future")))
            val foreign = JsonObject(prototype + mapOf("id" to JsonPrimitive("foreign"), "household_id" to JsonPrimitive("other-household")))
            server.enqueue(MockResponse(body = JsonObject(root + ("devices" to JsonArray(listOf(future, foreign)))).toString()))
            val devices = server.yandexClient().devices().getOrThrow()
            assertEquals(listOf("future"), devices.map { it.id })
            assertEquals(DeviceKind.Other, devices.single().kind)
            assertEquals("devices.types.future", devices.single().vendorType)
        }
    }

    @Test
    fun `climate readings retain units and the lock reports a door contact without write actions`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse(body = discoveredFixture()))
            val devices = server.yandexClient().devices().getOrThrow()
            val sensor = devices.single { it.name == "Датчик климата" }
            val lock = devices.single { it.name == "Умный замок" }
            val climate = sheet(sensor, now, null)
            assertEquals("23.5 °C", climate.readings.single { it.label == "Температура" }.value)
            assertEquals("40.9%", climate.readings.single { it.label == "Влажность" }.value)
            assertEquals("100%", climate.readings.single { it.label == "Батарея" }.value)
            assertEquals("Закрыта", sheet(lock, now, null).readings.first().value)
            assertEquals("Дверь", sheet(lock, now, null).readings.first().label)
            assertEquals("closed", lock.events.getValue("door").value)
            assertTrue(lock.events.getValue("door").lastUpdated is Reading.At)
            assertNull(lock.events.getValue("serviceability").value)
            assertEquals("Нет данных", sheet(lock, now, null).readings.single { it.label == "Исправность" }.value)
            assertTrue(devices.all { sheet(it, now, null).actions.isEmpty() })
            assertTrue(devices.all { sheet(it, now, null).level == null })
        }
    }

    @Test
    fun `discovered devices create rooms home entries and visible outage or stale indicators`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse(body = discoveredFixture()))
            val devices = server.yandexClient().devices().getOrThrow()
            val sections = roomSections(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), devices)
            assertEquals(listOf("Коридор"), sections.map { it.room })
            assertEquals(5, sections.single().tileCount())
            val polls = GroupPolls(now, now, now, now, now, now)
            val fresh = panelHeadings(sections, GroupErrors(), polls, now, 15.seconds, 6.minutes)
            assertEquals(listOf("Главная", "Коридор"), fresh.map { it.title })
            assertEquals(devices, fresh.first().section.discovered)
            assertFalse(fresh.last().marked)
            val errors = GroupErrors(discovered = "Нет связи с сервисом")
            val failed = panelHeadings(sections, errors, polls, now, 15.seconds, 6.minutes)
            assertTrue(failed.last().marked)
            assertEquals(listOf("Устройства Яндекса: нет обновления — Нет связи с сервисом"), sectionFailures(sections.single(), errors))
            val stale = panelHeadings(sections, GroupErrors(), polls, now.plusSeconds(1000), 15.seconds, 6.minutes)
            assertTrue(stale.last().marked)
        }
    }

    private fun discoveredFixture(): String = javaClass.getResource("/yandex/discovered_devices.json")!!.readText()
}
