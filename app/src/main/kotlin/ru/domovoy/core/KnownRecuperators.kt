package ru.domovoy.core

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One key, one JSON list: this is five short records, not a database. */
private const val RECUPERATORS = "tuya.recuperators"

/**
 * Who the recuperators are, as the last successful inventory said. Only identity — a value would
 * be a reading the panel did not take.
 */
@Serializable
private data class Remembered(
    val id: String,
    val name: String,
    val room: String? = null,
)

/**
 * What the panel knows about the recuperators before it has read anything.
 *
 * Every other tile group heals in seconds: Yandex is one call every 15 s, so a poll that missed the
 * Wi-Fi coming up is retried before anyone reaches the hallway. Tuya is five calls every 6 minutes
 * against a metered allowance, and the tiles exist only once the inventory call has answered — so a
 * tablet that rebooted into a network that was not up yet shows one line of error where five tiles
 * should be, for six minutes. This is what fills that gap: the wall puts up who it read last time,
 * with no values on them, and replaces them the moment a real read lands.
 *
 * **Identity only — id, name, room.** A switch position from before the reboot is not something the
 * panel has read, and a tile printing it would be claiming a poll that never happened. What comes
 * back has no capabilities at all, so every tile made from it says "unknown · never read", which is
 * the truth.
 *
 * Device ids are apartment-identifying, which is why this lives in the panel's encrypted store next
 * to the credentials rather than anywhere that can be committed. [prefs] is null when the tablet has
 * no usable keystore — a restored backup, a wiped key — and that is a panel with no memory rather
 * than a panel that will not start.
 */
class KnownRecuperators(private val prefs: SharedPreferences?) {
    /** The last inventory, or nothing at all — which is what the panel had before it remembered. */
    fun remembered(): List<Device> {
        val stored = prefs?.getString(RECUPERATORS, null)?.takeIf { it.isNotBlank() } ?: return emptyList()
        // Anything unreadable — a half-written file, a downgrade, a format that moved on — is no
        // memory rather than a wall that will not come up. The poll fills it in six minutes.
        return runCatching { Json.decodeFromString<List<Remembered>>(stored) }
            .getOrDefault(emptyList())
            .filter { it.id.isNotBlank() }
            .map { device ->
                Device(
                    id = device.id,
                    name = device.name,
                    kind = DeviceKind.Recuperator,
                    room = device.room,
                    onOff = null,
                )
            }
    }

    /**
     * What the inventory just said, kept for the next cold start. The whole list replaces the whole
     * list: a recuperator taken off the account is gone from the wall, not remembered for ever.
     */
    fun remember(devices: List<Device>) {
        val prefs = prefs ?: return
        val json = Json.encodeToString(devices.map { Remembered(id = it.id, name = it.name, room = it.room) })
        // Written only when it differs. This runs every 6 minutes for months on end, and the
        // inventory almost never changes — there is no reason to touch the file to say so.
        if (prefs.getString(RECUPERATORS, null) == json) return
        prefs.edit().putString(RECUPERATORS, json).apply()
    }
}
