package ru.domovoy.integrations.xiaomi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** miIO packets authenticate their header and AES-CBC payload with the device token. */
internal class XiaomiMiioProtocol(tokenHex: String) {
    private val token = tokenHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray().also {
        require(tokenHex.matches(Regex("[0-9a-fA-F]{32}"))) { "Некорректный токен Xiaomi" }
    }
    private val key = md5(token)
    private val iv = md5(key + token)

    fun encodePayload(deviceId: Int, timestamp: Int, payload: JsonObject): ByteArray {
        val encrypted = cipher(Cipher.ENCRYPT_MODE).doFinal((payload.toString() + '\u0000').toByteArray(Charsets.UTF_8))
        val header = ByteBuffer.allocate(16).putShort(0x2131).putShort((32 + encrypted.size).toShort())
            .putInt(0).putInt(deviceId).putInt(timestamp).array()
        return header + md5(header + token + encrypted) + encrypted
    }

    fun decode(packet: ByteArray, deviceId: Int): JsonObject {
        validateHeader(packet)
        if (packet.size <= 32 || (packet.size - 32) % 16 != 0) throw IOException("Некорректный ответ Xiaomi")
        if (ByteBuffer.wrap(packet, 8, 4).int != deviceId) throw IOException("Ответ другого устройства Xiaomi")
        val ciphertext = packet.copyOfRange(32, packet.size)
        if (!MessageDigest.isEqual(md5(packet.copyOfRange(0, 16) + token + ciphertext), packet.copyOfRange(16, 32))) {
            throw IOException("Не удалось проверить ответ Xiaomi")
        }
        return try {
            val text = cipher(Cipher.DECRYPT_MODE).doFinal(ciphertext).toString(Charsets.UTF_8).trimEnd('\u0000')
            Json.parseToJsonElement(text).jsonObject
        } catch (_: Exception) {
            throw IOException("Не удалось прочитать ответ Xiaomi")
        }
    }

    private fun cipher(mode: Int) = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
        init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(this@XiaomiMiioProtocol.iv))
    }

    companion object {
        val hello: ByteArray get() = byteArrayOf(0x21, 0x31, 0, 0x20) + ByteArray(28) { 0xff.toByte() }

        fun handshake(packet: ByteArray): Pair<Int, Int> {
            validateHeader(packet)
            if (packet.size != 32) throw IOException("Некорректный ответ Xiaomi")
            return ByteBuffer.wrap(packet, 8, 8).let { it.int to it.int }
        }

        private fun validateHeader(packet: ByteArray) {
            if (packet.size < 32) throw IOException("Неполный ответ Xiaomi")
            val header = ByteBuffer.wrap(packet)
            if (header.short.toInt() != 0x2131 || (header.short.toInt() and 0xffff) != packet.size) {
                throw IOException("Некорректный ответ Xiaomi")
            }
        }

        private fun md5(bytes: ByteArray): ByteArray = MessageDigest.getInstance("MD5").digest(bytes)
    }
}
