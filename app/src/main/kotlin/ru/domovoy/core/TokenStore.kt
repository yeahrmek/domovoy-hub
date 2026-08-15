package ru.domovoy.core

import android.content.SharedPreferences

private const val YANDEX_OAUTH_TOKEN = "yandex.oauth.token"
private const val TUYA_CLIENT_ID = "tuya.client.id"
private const val TUYA_CLIENT_SECRET = "tuya.client.secret"
private const val TUYA_UID = "tuya.uid"

/**
 * Where the panel keeps vendor credentials at runtime. On the tablet the [prefs] handed in are
 * `EncryptedSharedPreferences`; nothing here knows that, which is what lets it be tested off a
 * device.
 *
 * A token in `BuildConfig` is baked into the APK, so an expired one costs a rebuild and a walk to
 * the wall. Reading it from here instead means anything that can write to the store — the OAuth
 * refresh flow, when it exists — fixes the panel in place.
 */
class TokenStore(private val prefs: SharedPreferences) {
    /** The stored Yandex token, or `""` when nothing usable is stored. Never null, never blank. */
    fun yandexToken(): String = prefs.getString(YANDEX_OAUTH_TOKEN, null).orEmpty().trim()

    /**
     * First-run seeding from `local.properties` via `BuildConfig`: writes [token] only when the
     * store holds none, and answers whether it wrote. Deliberately not an overwrite — once the
     * store holds a fresher token, the value baked into the APK is the stale one, and a launch
     * after a reinstall must not put it back.
     */
    fun seedYandexToken(token: String): Boolean {
        val seed = token.trim()
        if (seed.isEmpty() || yandexToken().isNotEmpty()) return false
        prefs.edit().putString(YANDEX_OAUTH_TOKEN, seed).apply()
        return true
    }

    /**
     * The Tuya cloud project's credentials, and the uid of the Smart Life account linked to it.
     * `""` for anything not stored; the client refuses to send a request rather than signing one
     * with a blank secret, which would come back `1004 sign invalid` and blame the signature.
     *
     * Three strings rather than the client's own credentials type: `core` does not depend on
     * `integrations`, and this is the shape a `SharedPreferences` holds anyway.
     */
    fun tuyaClientId(): String = read(TUYA_CLIENT_ID)

    fun tuyaClientSecret(): String = read(TUYA_CLIENT_SECRET)

    fun tuyaUid(): String = read(TUYA_UID)

    /**
     * First-run seeding from `local.properties`, on the same terms as [seedYandexToken]: each value
     * is written only when the store holds none, so a credential rotated into the store is not
     * undone by the stale one still baked into the APK.
     */
    fun seedTuyaCredentials(
        clientId: String,
        clientSecret: String,
        uid: String,
    ) {
        seed(TUYA_CLIENT_ID, clientId)
        seed(TUYA_CLIENT_SECRET, clientSecret)
        seed(TUYA_UID, uid)
    }

    private fun read(key: String): String = prefs.getString(key, null).orEmpty().trim()

    private fun seed(
        key: String,
        value: String,
    ) {
        val seed = value.trim()
        if (seed.isEmpty() || read(key).isNotEmpty()) return
        prefs.edit().putString(key, seed).apply()
    }
}
