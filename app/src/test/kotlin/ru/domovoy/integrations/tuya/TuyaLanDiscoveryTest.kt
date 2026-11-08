package ru.domovoy.integrations.tuya

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TuyaLanDiscoveryTest {
    @Test
    fun `a recorded discovery shape supplies identity without trusting its advertised address`() {
        assertEquals("test-fan", decodeTuyaAdvertisement(fixture()))
    }

    @Test
    fun `corrupted and truncated advertisements provide no address guidance`() {
        val packet = fixture()
        packet[30] = (packet[30].toInt() xor 1).toByte()
        assertNull(decodeTuyaAdvertisement(packet))
        assertNull(decodeTuyaAdvertisement(fixture().copyOf(23)))
    }

    @Test
    fun `encrypted advertisements can begin with the same byte as a json object`() {
        // TinyTuya-generated packet with fake identity; the AES ciphertext starts with 0x7b.
        val packet = fixture("discovery_encrypted_brace.hex")

        assertEquals('{'.code.toByte(), packet[20])
        assertEquals("xfj-test", decodeTuyaAdvertisement(packet))
    }

    private fun fixture(name: String = "discovery.hex"): ByteArray = requireNotNull(javaClass.getResource("/tuya/lan/$name"))
        .readText().trim().chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
