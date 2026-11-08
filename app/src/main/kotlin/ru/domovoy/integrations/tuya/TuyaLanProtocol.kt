package ru.domovoy.integrations.tuya

import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** One authenticated, decrypted protocol 3.4 frame; response correlation belongs to the client. */
internal data class TuyaLanFrame(
    val sequence: Int,
    val command: Int,
    val payload: ByteArray,
    val returnCode: Int?,
)

internal data class TuyaLanSession(
    val remoteNonce: ByteArray,
    val finishPayload: ByteArray,
    val key: ByteArray,
)

/** Tuya's 55AA framing, session authentication and AES/HMAC format for protocol 3.4 only. */
internal class TuyaLanProtocol(localKey: ByteArray) {
    private val localKey = localKey.copyOf().also { require(it.size == KEY_SIZE) { "Tuya local key must be 16 bytes" } }

    /** Adds the protocol header inside encryption, except for queries and negotiation commands. */
    fun encode(
        sequence: Int,
        command: Int,
        payload: ByteArray,
        key: ByteArray = localKey,
        returnCode: Int? = null,
    ): ByteArray {
        require(key.size == KEY_SIZE) { "Tuya session key must be 16 bytes" }
        require(payload.size <= MAX_FRAME_SIZE - HEADER_SIZE - TRAILER_SIZE - VERSION_HEADER_SIZE - KEY_SIZE - 4) {
            "Tuya payload is too large"
        }
        val plain = if (command in NO_VERSION_HEADER || payload.isEmpty()) payload else VERSION_HEADER + payload
        val encrypted = if (plain.isEmpty()) plain else encrypt(plain, key)
        val returnCodeSize = if (returnCode == null) 0 else 4
        val length = returnCodeSize + encrypted.size + TRAILER_SIZE
        require(length + HEADER_SIZE <= MAX_FRAME_SIZE) { "Tuya frame is too large" }
        val authenticated = ByteBuffer.allocate(HEADER_SIZE + returnCodeSize + encrypted.size)
            .putInt(PREFIX)
            .putInt(sequence)
            .putInt(command)
            .putInt(length)
            .apply { if (returnCode != null) putInt(returnCode) }
            .put(encrypted)
            .array()
        return authenticated + hmac(key, authenticated) + ByteBuffer.allocate(4).putInt(SUFFIX).array()
    }

    /** Reads one bounded frame, authenticates its entire wire body, then decrypts its payload. */
    fun read(
        input: InputStream,
        key: ByteArray = localKey,
        hasReturnCode: Boolean = true,
    ): TuyaLanFrame {
        require(key.size == KEY_SIZE) { "Tuya session key must be 16 bytes" }
        val stream = DataInputStream(input)
        val header = ByteArray(HEADER_SIZE).also(stream::readFully)
        val fields = ByteBuffer.wrap(header)
        if (fields.int != PREFIX) throw IOException("Invalid Tuya frame prefix")
        val sequence = fields.int
        val command = fields.int
        val length = fields.int
        val returnCodeSize = if (hasReturnCode) 4 else 0
        if (length < TRAILER_SIZE + returnCodeSize || length > MAX_FRAME_SIZE - HEADER_SIZE) {
            throw IOException("Invalid Tuya frame length")
        }
        val body = ByteArray(length).also(stream::readFully)
        if (ByteBuffer.wrap(body, length - 4, 4).int != SUFFIX) throw IOException("Invalid Tuya frame suffix")
        val signedSize = length - TRAILER_SIZE
        val receivedMac = body.copyOfRange(signedSize, length - 4)
        if (!MessageDigest.isEqual(hmac(key, header + body.copyOfRange(0, signedSize)), receivedMac)) {
            throw IOException("Tuya frame authentication failed")
        }
        val returnCode = if (hasReturnCode) ByteBuffer.wrap(body, 0, 4).int else null
        val encrypted = body.copyOfRange(returnCodeSize, signedSize)
        if (encrypted.size % KEY_SIZE != 0) throw IOException("Invalid Tuya encrypted payload length")
        var plain = if (encrypted.isEmpty()) encrypted else decrypt(encrypted, key)
        // Reports can use a source header with nonzero fields after the ASCII version.
        if (command !in HANDSHAKE_COMMANDS && plain.size >= VERSION_HEADER_SIZE && plain.startsWith(VERSION)) {
            plain = plain.copyOfRange(VERSION_HEADER_SIZE, plain.size)
        }
        return TuyaLanFrame(sequence, command, plain, returnCode)
    }

    /** Verifies the server's proof of the client nonce before deriving or returning any key. */
    fun acceptChallenge(localNonce: ByteArray, response: TuyaLanFrame): TuyaLanSession {
        require(localNonce.size == KEY_SIZE) { "Tuya client nonce must be 16 bytes" }
        if (response.command != 4 || response.returnCode != 0 || response.payload.size != 48) {
            throw IOException("Invalid Tuya session challenge")
        }
        val remoteNonce = response.payload.copyOfRange(0, KEY_SIZE)
        val proof = response.payload.copyOfRange(KEY_SIZE, response.payload.size)
        if (!MessageDigest.isEqual(hmac(localKey, localNonce), proof)) {
            throw IOException("Tuya session challenge authentication failed")
        }
        val nonceXor = ByteArray(KEY_SIZE) { index -> (localNonce[index].toInt() xor remoteNonce[index].toInt()).toByte() }
        val sessionKey = crypt(Cipher.ENCRYPT_MODE, nonceXor, localKey, padding = false)
        return TuyaLanSession(remoteNonce, hmac(localKey, remoteNonce), sessionKey)
    }

    private fun encrypt(payload: ByteArray, key: ByteArray): ByteArray = crypt(Cipher.ENCRYPT_MODE, payload, key)

    private fun decrypt(payload: ByteArray, key: ByteArray): ByteArray = crypt(Cipher.DECRYPT_MODE, payload, key)

    private fun crypt(mode: Int, payload: ByteArray, key: ByteArray, padding: Boolean = true): ByteArray = try {
        Cipher.getInstance(if (padding) "AES/ECB/PKCS5Padding" else "AES/ECB/NoPadding").run {
            init(mode, SecretKeySpec(key, "AES"))
            doFinal(payload)
        }
    } catch (failure: GeneralSecurityException) {
        throw IOException("Invalid Tuya encrypted payload", failure)
    }

    private fun hmac(key: ByteArray, payload: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(payload)
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean = indices.take(prefix.size).all { this[it] == prefix[it] }

    private companion object {
        const val PREFIX = 0x000055aa
        const val SUFFIX = 0x0000aa55
        const val HEADER_SIZE = 16
        const val KEY_SIZE = 16
        const val TRAILER_SIZE = 36
        const val MAX_FRAME_SIZE = 65_536
        const val VERSION_HEADER_SIZE = 15
        val VERSION = "3.4".toByteArray(Charsets.US_ASCII)
        val VERSION_HEADER = VERSION + ByteArray(12)
        val HANDSHAKE_COMMANDS = setOf(3, 4, 5)
        val NO_VERSION_HEADER = setOf(3, 4, 5, 9, 10, 16, 18, 64)
    }
}
