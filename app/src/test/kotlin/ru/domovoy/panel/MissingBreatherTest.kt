package ru.domovoy.panel

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.domovoy.integrations.tuya.TuyaLanClient
import ru.domovoy.integrations.tuya.TuyaLanDevice
import ru.domovoy.integrations.tuya.TuyaLanDeviceNotFoundException
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MissingBreatherTest {
    @Test
    fun `an undiscovered breather reports a useful safe connection failure`() = runTest {
        val client = TuyaLanClient(
            configuration = { listOf(TuyaLanDevice("test-breather", "Test breather", "0123456789abcdef")) },
            connect = { _, _ -> error("An undiscovered device has no address to connect to") },
            discover = { emptyMap() },
        )
        val device = client.devices().getOrThrow().single()
        val failure = client.read(device).exceptionOrNull()

        assertIs<TuyaLanDeviceNotFoundException>(failure)
        assertEquals("Бризер не найден в локальной сети", reason(failure))
    }

    @Test
    fun `unstructured exceptions retain a generic label regardless of their message`() {
        listOf(
            IOException("Бризер не найден в локальной сети"),
            IllegalStateException("Failed to connect to 192.0.2.10:6668"),
        ).forEach { failure ->
            assertEquals("Не удалось выполнить запрос", reason(failure))
        }
    }
}
