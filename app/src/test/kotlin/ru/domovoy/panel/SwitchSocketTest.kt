package ru.domovoy.panel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.Test
import ru.domovoy.core.Reading
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwitchSocketTest {
    @Test
    fun `only the paired wardrobe switch binds to one wardrobe light`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse(body = switchSocketFixture()))
            val devices = combineSwitchButtons(server.yandexClient().devices().getOrThrow())
            val switch = devices.single { it.name == "Выключатель" }
            val wardrobe = Flat.bulbs.tiles.first().copy(id = "wardrobe-light", room = "Гардероб", isOn = true)
            val bedroom = wardrobe.copy(id = "bedroom-light", room = "Спальня", isOn = false)
            assertEquals(wardrobe, switchLight(switch, listOf(bedroom, wardrobe)))
            assertEquals(null, switchLight(switch, listOf(bedroom)))
            assertEquals(null, switchLight(switch, listOf(wardrobe, wardrobe.copy(id = "another-light"))))
            val linked = sheet(switch, Flat.NOW, null, wardrobe)
            assertEquals(true, linked.isOn)
            assertEquals("Включено", linked.readings.single { it.label == "Свет гардероба" }.value)
            assertEquals(setOf(SheetAction.Power), linked.actions)
            listOf("devices.types.openable.door_lock", "devices.types.openable", "devices.types.future").forEach { type ->
                assertTrue(sheet(switch.copy(vendorType = type), Flat.NOW, null, wardrobe).actions.isEmpty())
            }
        }
    }

    @Test
    fun `a socket command waits for the shared confirmation read and ignores another press while pending`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse(body = switchSocketFixture()))
            val devices = server.yandexClient().devices().getOrThrow()
            val gate = CompletableDeferred<Unit>()
            val confirming = CompletableDeferred<Unit>()
            lateinit var discovered: DiscoveredDevices
            discovered = DiscoveredDevices(server.yandexClient()) {
                confirming.complete(Unit)
                gate.await()
                discovered.show(devices.map { if (it.id == "socket-iron") it.copy(onOff = it.onOff!!.copy(isOn = true)) else it }, Flat.NOW)
            }
            discovered.show(devices, Flat.NOW)
            server.enqueue(MockResponse(body = """{"status":"ok"}"""))
            val command = launch { discovered.toggle("socket-iron") }
            confirming.await()
            assertTrue("socket-iron" in discovered.state.value.pending)
            assertEquals(false, discovered.state.value.tiles.single { it.id == "socket-iron" }.onOff?.isOn)
            discovered.toggle("socket-iron")
            gate.complete(Unit)
            command.join()
            assertEquals(true, discovered.state.value.tiles.single { it.id == "socket-iron" }.onOff?.isOn)
            assertTrue(discovered.state.value.pending.isEmpty())
            assertEquals(2, server.requestCount)
            server.takeRequest()
            val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
            val action = body["devices"]!!.jsonArray.single().jsonObject
            assertEquals("socket-iron", action["id"]!!.jsonPrimitive.content)
            assertEquals("true", action["actions"]!!.jsonArray.single().jsonObject["state"]!!.jsonObject["value"]!!.jsonPrimitive.content)
        }
    }

    @Test
    fun `command failure keeps the socket state and is cleared only by the next command`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse(body = switchSocketFixture()))
            val poll = YandexPoll(server.yandexClient()) { Flat.NOW }
            poll.refresh()
            server.enqueue(MockResponse(code = 500))
            poll.discovered.toggle("socket-iron")
            assertEquals(false, poll.discovered.state.value.tiles.single { it.id == "socket-iron" }.onOff?.isOn)
            assertEquals("Не удалось выполнить запрос", poll.discovered.state.value.commandErrors["socket-iron"])
            assertTrue(poll.discovered.state.value.pending.isEmpty())
            server.enqueue(MockResponse(body = switchSocketFixture()))
            poll.refresh()
            assertTrue("socket-iron" in poll.discovered.state.value.commandErrors)
            server.enqueue(MockResponse(body = """{"status":"ok"}"""))
            server.enqueue(MockResponse(body = switchSocketFixture()))
            poll.discovered.toggle("socket-iron")
            assertTrue(poll.discovered.state.value.commandErrors.isEmpty())
            val before = server.requestCount
            poll.discovered.state.value.tiles.filter { it.id != "socket-iron" }.forEach { poll.discovered.toggle(it.id) }
            assertEquals(before, server.requestCount)
        }
    }

    @Test
    fun `pairing is limited to the complete unambiguous wardrobe switch`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse(body = switchSocketFixture()))
            val devices = server.yandexClient().devices().getOrThrow()
            val expected = combineSwitchButtons(devices)
            assertEquals(expected.sortedBy { it.id }, combineSwitchButtons(devices.reversed()).sortedBy { it.id })
            listOf(
                devices.filterNot { it.id == "button-upper" },
                devices.map { if (it.id == "button-upper") it.copy(room = "Зал") else it },
                devices + devices.first().copy(id = "another-lower"),
            ).forEach { input -> assertEquals(input, combineSwitchButtons(input)) }
            assertFalse(expected.any { it.name == "Верхняя кнопка" || it.name == "Нижняя кнопка" })
        }
    }

    @Test
    fun `the two wardrobe keys form one stable card retaining both events and the shared battery`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse(body = switchSocketFixture()))
            val poll = YandexPoll(server.yandexClient()) { Flat.NOW }
            poll.refresh()
            val cards = poll.discovered.state.value.tiles
            assertEquals(listOf("Выключатель", "Утюг"), cards.map { it.name })
            val switch = cards.single { it.name == "Выключатель" }
            assertEquals("button-lower", switch.id)
            val details = sheet(switch, Flat.NOW, null)
            assertEquals("Нажатие", details.readings.single { it.label == "Верхняя клавиша" }.value)
            assertEquals("Время неизвестно", details.readings.single { it.label == "Верхняя клавиша" }.age)
            assertEquals("Нажатие", details.readings.single { it.label == "Нижняя клавиша" }.value)
            assertEquals("100%", details.readings.single { it.label == "Батарея" }.value)
            assertTrue(details.actions.isEmpty())
            assertTrue(switch.events.getValue("lower_button").lastUpdated is Reading.At)
        }
    }

    @Test
    fun `only the advertised socket gains power controls`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse(body = switchSocketFixture()))
            val client = server.yandexClient()
            val devices = client.devices().getOrThrow()
            val socket = devices.single { it.name == "Утюг" }
            assertEquals(setOf(SheetAction.Power), sheet(socket, Flat.NOW, null).actions)
            listOf("devices.types.openable.door_lock", "devices.types.openable", "devices.types.switch", "devices.types.future").forEach { type ->
                assertTrue(sheet(socket.copy(vendorType = type), Flat.NOW, null).actions.isEmpty(), type)
            }
            assertTrue(sheet(socket.copy(onOff = null), Flat.NOW, null).actions.isEmpty())
        }
    }
}

internal fun switchSocketFixture(): String = SwitchSocketTest::class.java.getResource("/yandex/switch_socket.json")!!.readText()
