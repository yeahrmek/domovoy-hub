package ru.domovoy.panel

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.Test
import ru.domovoy.core.Device
import ru.domovoy.core.EventProperty
import ru.domovoy.core.Reading
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntercomGroupsTest {
    @Test
    fun `membership updates keep the group identity and individual read-only details`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse(body = intercomFixture()))
            val client = server.yandexClient()
            val entries = client.devices().getOrThrow().filter { it.vendorType == "devices.types.openable" }
                .mapIndexed { i, entry -> entry.copy(name = "Домофон ${i + 1}") }
            val poll = YandexPoll(client) { Flat.NOW }
            var id: String? = null
            listOf(entries, entries.drop(1), entries.takeLast(1)).forEach { current ->
                poll.discovered.show(current, Flat.NOW)
                val state = poll.discovered.state.value
                val card = state.tiles.single()
                if (id == null) id = card.id else assertEquals(id, card.id)
                assertEquals(current.toSet(), state.intercomGroups.getValue(card.id).toSet())
                state.intercomGroups.getValue(card.id).forEach { entry ->
                    val details = sheet(entry, Flat.NOW, null)
                    assertEquals(entry.name, details.name)
                    assertTrue(details.readings.any { it.label == "Дверь" && it.value == "Закрыта" })
                    assertTrue(details.actions.isEmpty())
                }
            }
            val state = poll.discovered.state.value
            poll.discovered.showFailure("Нет связи")
            assertEquals(state.intercomGroups, poll.discovered.state.value.intercomGroups)
            poll.discovered.show(emptyList(), Flat.NOW)
            assertTrue(poll.discovered.state.value.tiles.isEmpty())
            assertTrue(poll.discovered.state.value.intercomGroups.isEmpty())
        }
    }

    @Test
    fun `three intercom endpoints form one stable card while the lock remains separate`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse(body = intercomFixture()))
            val client = server.yandexClient()
            val devices = client.devices().getOrThrow()
            val poll = YandexPoll(client) { Flat.NOW }
            poll.discovered.show(devices, Flat.NOW)
            val cards = poll.discovered.state.value.tiles
            assertEquals(3, cards.size)
            val intercom = cards.single { it.vendorType == "devices.types.openable" }
            assertEquals("Домофоны", intercom.name)
            assertEquals("Все закрыты", sheet(intercom, Flat.NOW, null).readings.first().value)
            assertTrue(sheet(intercom, Flat.NOW, null).actions.isEmpty())
            assertEquals(1, cards.count { it.vendorType == "devices.types.openable.door_lock" })
            poll.discovered.show(devices.reversed(), Flat.NOW)
            assertEquals(intercom, poll.discovered.state.value.tiles.single { it.vendorType == "devices.types.openable" })
        }
    }

    @Test
    fun `an open entry outranks missing data and all closed requires every entry to report closed`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse(body = intercomFixture()))
            val client = server.yandexClient()
            val devices = client.devices().getOrThrow().filter { it.vendorType == "devices.types.openable" }
            val poll = YandexPoll(client) { Flat.NOW }
            fun status(entries: List<Device>): String {
                poll.discovered.show(entries, Flat.NOW)
                return sheet(poll.discovered.state.value.tiles.single(), Flat.NOW, null).readings.first().value
            }
            val missing = devices.mapIndexed { i, device -> if (i == 0) device.copy(events = emptyMap()) else device }
            assertEquals("Нет данных", status(missing))
            val opened = missing.mapIndexed { i, device -> if (i == 1) device.copy(events = mapOf("open" to EventProperty("opened", Reading.At(Flat.NOW)))) else device }
            assertEquals("Есть открытая дверь", status(opened))
            assertEquals("Все закрыты", status(devices))
            assertEquals("Нет данных", status(devices.mapIndexed { i, device -> if (i == 0) device.copy(online = false) else device }))
            assertEquals("Нет данных", status(devices.mapIndexed { i, device -> if (i == 0) device.copy(events = mapOf("open" to EventProperty("closed", Reading.Never))) else device }))
        }
    }
}

internal fun intercomFixture(): String = IntercomGroupsTest::class.java.getResource("/yandex/discovered_devices.json")!!.readText()
