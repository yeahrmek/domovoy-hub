package ru.domovoy.integrations.xiaomi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class XiaomiMiioProtocolTest {
    private val protocol = XiaomiMiioProtocol("00112233445566778899aabbccddeeff")
    private val payload = Json.parseToJsonElement("""{"id":1,"method":"get_properties","params":[]}""").jsonObject
    private val golden = "21310050000000000000000700000064d8c01f62b5aaf4a926f57a9bdad00885a5516ec6151955dc2bb2d43e7c84c183d0323c1dc33c264cbefa960d6d193588dbebe63ac9161b1b7e530d602f13f84f".hex()

    @Test
    fun `packet matches independently generated AES and MD5 golden`() {
        assertContentEquals(golden, protocol.encodePayload(7, 100, payload))
        assertEquals(payload, protocol.decode(golden, 7))
    }

    @Test
    fun `corrupt checksum wrong identity and malformed lengths are rejected`() {
        assertFailsWith<IOException> { protocol.decode(golden.copyOf().also { it[40] = (it[40].toInt() xor 1).toByte() }, 7) }
        assertFailsWith<IOException> { protocol.decode(golden, 8) }
        assertFailsWith<IOException> { protocol.decode(golden.copyOf(40), 7) }
        assertFailsWith<IOException> { protocol.decode(byteArrayOf(), 7) }
    }

    @Test
    fun `discovery extracts identity and timestamp without treating hello as authenticated state`() {
        val hello = "21310020000000000000000700000064ffffffffffffffffffffffffffffffff".hex()
        assertEquals(7 to 100, XiaomiMiioProtocol.handshake(hello))
        assertFailsWith<IOException> { protocol.decode(hello, 7) }
        assertContentEquals("21310020".hex() + ByteArray(28) { 0xff.toByte() }, XiaomiMiioProtocol.hello)
    }

    private fun String.hex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
