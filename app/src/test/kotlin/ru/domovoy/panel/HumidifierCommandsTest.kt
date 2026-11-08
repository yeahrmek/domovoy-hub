package ru.domovoy.panel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HumidifierCommandsTest {
    @Test
    fun `humidifier power posts the advertised command and confirms with a fresh read`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse(body = humidifierFixture()))
            server.enqueue(MockResponse(body = """{"status":"ok"}"""))
            server.enqueue(MockResponse(body = humidifierFixture()))
            val poll = YandexPoll(server.yandexClient()) { Flat.NOW }
            poll.refresh()

            poll.discovered.toggle("humidifier-01")

            assertEquals(3, server.requestCount)
            server.takeRequest()
            val state = sentYandexAction(server.takeRequest().body?.utf8(), "humidifier-01", "devices.capabilities.on_off")
            assertEquals("true", state["value"]!!.jsonPrimitive.content)
            assertEquals("/v1.0/user/info", server.takeRequest().target)
        }
    }

    @Test
    fun `humidifier speed uses that device's advertised modes and rereads`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse(body = humidifierFixture()))
            val poll = YandexPoll(server.yandexClient()) { Flat.NOW }
            poll.refresh()
            poll.discovered.setMode("humidifier-01", "fan_speed", "turbo")
            assertEquals(1, server.requestCount)
            assertEquals("Режим недоступен", poll.discovered.state.value.commandErrors["humidifier-01"])

            server.enqueue(MockResponse(body = """{"status":"ok"}"""))
            server.enqueue(MockResponse(body = humidifierFixture()))
            poll.discovered.setMode("humidifier-02", "fan_speed", "turbo")

            assertEquals(3, server.requestCount)
            server.takeRequest()
            val state = sentYandexAction(server.takeRequest().body?.utf8(), "humidifier-02", "devices.capabilities.mode")
            assertEquals("fan_speed", state["instance"]!!.jsonPrimitive.content)
            assertEquals("turbo", state["value"]!!.jsonPrimitive.content)
            assertEquals("/v1.0/user/info", server.takeRequest().target)
        }
    }

    @Test
    fun `speed command keeps the reading until confirmation and blocks every command for that humidifier`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse(body = humidifierFixture()))
            val devices = server.yandexClient().devices().getOrThrow()
            val gate = CompletableDeferred<Unit>()
            val confirming = CompletableDeferred<Unit>()
            lateinit var discovered: DiscoveredDevices
            discovered = DiscoveredDevices(server.yandexClient()) {
                confirming.complete(Unit)
                gate.await()
                discovered.show(
                    devices.map { device ->
                        if (device.id == "humidifier-01") {
                            device.copy(modes = device.modes + ("fan_speed" to device.modes.getValue("fan_speed").copy(current = "high")))
                        } else {
                            device
                        }
                    },
                    Flat.NOW,
                )
            }
            discovered.show(devices, Flat.NOW)
            server.enqueue(MockResponse(body = """{"status":"ok"}"""))
            val command = launch { discovered.setMode("humidifier-01", "fan_speed", "high") }
            confirming.await()
            assertEquals("medium", discovered.state.value.tiles.single { it.id == "humidifier-01" }.modes.getValue("fan_speed").current)
            assertTrue("humidifier-01" in discovered.state.value.pending)
            discovered.setMode("humidifier-01", "fan_speed", "low")
            discovered.toggle("humidifier-01")
            gate.complete(Unit)
            command.join()

            assertEquals("high", discovered.state.value.tiles.single { it.id == "humidifier-01" }.modes.getValue("fan_speed").current)
            assertTrue(discovered.state.value.pending.isEmpty())
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun `failed speed command keeps state and error until a successful retry`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse(body = humidifierFixture()))
            val poll = YandexPoll(server.yandexClient()) { Flat.NOW }
            poll.refresh()
            val before = poll.discovered.state.value.tiles
            server.enqueue(MockResponse(code = 500))
            poll.discovered.setMode("humidifier-01", "fan_speed", "high")
            assertEquals(before, poll.discovered.state.value.tiles)
            assertEquals("Не удалось выполнить запрос", poll.discovered.state.value.commandErrors["humidifier-01"])
            assertTrue(poll.discovered.state.value.pending.isEmpty())
            server.enqueue(MockResponse(body = humidifierFixture()))
            poll.refresh()
            assertTrue("humidifier-01" in poll.discovered.state.value.commandErrors)
            server.enqueue(MockResponse(body = """{"status":"ok"}"""))
            server.enqueue(MockResponse(body = humidifierFixture()))
            poll.discovered.setMode("humidifier-01", "fan_speed", "high")
            assertTrue(poll.discovered.state.value.commandErrors.isEmpty())
        }
    }

    @Test
    fun `locks entry relays and other devices never gain humidifier controls`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse(body = humidifierFixture()))
            val template = server.yandexClient().devices().getOrThrow().single { it.id == "humidifier-01" }
            val discovered = DiscoveredDevices(server.yandexClient()) { error("Unsupported commands must not reread") }
            val devices = listOf("devices.types.openable.door_lock", "devices.types.openable", "devices.types.future").mapIndexed { index, type ->
                template.copy(id = "unsupported-$index", vendorType = type)
            } + template.copy(id = "no-power", onOff = null)
            discovered.show(devices, Flat.NOW)
            devices.forEach { device -> discovered.toggle(device.id) }
            devices.filter { it.id != "no-power" }.forEach { device -> discovered.setMode(device.id, "fan_speed", "high") }
            discovered.setMode("no-power", "thermostat", "high")

            assertEquals(1, server.requestCount)
            assertTrue(discovered.state.value.pending.isEmpty())
        }
    }
}

/** Move the recorded humidifiers into the test client's household without changing their capabilities. */
private fun humidifierFixture(): String {
    val root = Json.parseToJsonElement(yandexFixture()).jsonObject
    val devices = root.getValue("devices").jsonArray.map { device ->
        val data = device.jsonObject
        if (data["type"]?.jsonPrimitive?.content == "devices.types.humidifier") {
            JsonObject(data + ("household_id" to JsonPrimitive("household-flat")))
        } else {
            device
        }
    }
    return JsonObject(root + ("devices" to JsonArray(devices))).toString()
}
