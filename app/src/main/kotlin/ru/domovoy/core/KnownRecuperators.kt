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
 * Last successful recuperator inventory, used while a cold-start poll is still failing.
 * Stores only identity: remembered devices have no values or reading timestamps.
 * [prefs] is encrypted on-device; null means storage is unavailable.
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
