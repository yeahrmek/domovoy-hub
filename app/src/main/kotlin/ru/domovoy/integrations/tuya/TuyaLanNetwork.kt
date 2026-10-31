package ru.domovoy.integrations.tuya

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.system.ErrnoException
import android.system.OsConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Uses the Wi-Fi Network explicitly so a tablet VPN cannot redirect local device traffic. */
class TuyaLanNetwork(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    private fun wifi(): Network = connectivity.allNetworks.firstOrNull { network ->
        connectivity.getNetworkCapabilities(network)?.let {
            it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } == true
    } ?: throw IOException("Планшет не подключён к Wi-Fi")

    fun connect(host: String, port: Int): Socket = Socket().also { socket ->
        try {
            bindWhenAllowed { wifi().bindSocket(socket) }
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(host, port), 1_500)
        } catch (failure: Exception) {
            socket.close()
            throw failure
        }
    }

    /** Periodic advertisements survive DHCP changes; cached address hints cover missed broadcasts. */
    suspend fun discover(): Map<String, String> = coroutineScope {
        val network = wifi()
        listOf(6666, 6667, 7000).map { port ->
            async(Dispatchers.IO) {
                try {
                    advertisements(network, port)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: IOException) {
                    // One occupied discovery port must not cancel the other listeners.
                    emptyMap()
                }
            }
        }.awaitAll().fold(emptyMap()) { found, values -> found + values }
    }

    private suspend fun advertisements(network: Network, port: Int): Map<String, String> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val socket = DatagramSocket(null)
            continuation.invokeOnCancellation { socket.close() }
            try {
                socket.use {
                    socket.reuseAddress = true
                    socket.bind(InetSocketAddress(port))
                    bindWhenAllowed { network.bindSocket(socket) }
                    socket.soTimeout = 500
                    val end = System.nanoTime() + 5_000_000_000L
                    val found = mutableMapOf<String, String>()
                    while (continuation.isActive && System.nanoTime() < end) {
                        val packet = DatagramPacket(ByteArray(4096), 4096)
                        try {
                            socket.receive(packet)
                            decodeTuyaAdvertisement(packet.data.copyOf(packet.length))?.let { id ->
                                found[id] = packet.address.hostAddress ?: return@let
                            }
                        } catch (_: SocketTimeoutException) {
                            // Keep the absolute discovery deadline even if unrelated packets arrive.
                        }
                    }
                    if (continuation.isActive) continuation.resume(found)
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (continuation.isActive) continuation.resumeWithException(failure)
            }
        }
    }

    private fun bindWhenAllowed(bind: () -> Unit) {
        try {
            bind()
        } catch (failure: SocketException) {
            val cause = failure.cause
            if (cause !is ErrnoException || cause.errno != OsConstants.EPERM) throw failure
            // A non-bypassable VPN can still route LAN destinations outside its tunnel.
            // An ordinary socket follows that system policy without changing VPN settings.
        }
    }
}

/** Discovery is untrusted address guidance; the TCP handshake still authenticates the device key. */
internal fun decodeTuyaAdvertisement(packet: ByteArray): String? = runCatching {
    require(packet.size in 28..4096)
    val fields = ByteBuffer.wrap(packet)
    require(fields.int == 0x000055aa)
    fields.int
    require(fields.int in setOf(19, 35))
    val length = fields.int
    require(length >= 12 && length + 16 == packet.size)
    require(ByteBuffer.wrap(packet, packet.size - 4, 4).int == 0x0000aa55)
    val crc = CRC32().apply { update(packet, 0, packet.size - 8) }.value
    require(crc == ByteBuffer.wrap(packet, packet.size - 8, 4).int.toLong().and(0xffffffffL))
    val payload = packet.copyOfRange(20, packet.size - 8)
    val clear = runCatching { Json.parseToJsonElement(payload.toString(Charsets.UTF_8)).jsonObject }.getOrNull()
    val data = clear ?: run {
        val key = MessageDigest.getInstance("MD5").digest("yGAdlopoPVldABfn".toByteArray(Charsets.US_ASCII))
        val plain = Cipher.getInstance("AES/ECB/PKCS5Padding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
            doFinal(payload)
        }
        Json.parseToJsonElement(plain.toString(Charsets.UTF_8)).jsonObject
    }
    require(data["version"]?.jsonPrimitive?.content == "3.4")
    data["gwId"]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
}.getOrNull()
