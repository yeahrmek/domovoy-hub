package ru.domovoy.core

import android.content.SharedPreferences

private const val YANDEX_OAUTH_TOKEN = "yandex.oauth.token"
private const val TUYA_CLIENT_ID = "tuya.client.id"
private const val TUYA_CLIENT_SECRET = "tuya.client.secret"
private const val TUYA_UID = "tuya.uid"
private const val TUYA_LOCAL_DEVICES = "tuya.local.devices"

/** Vendor credentials in encrypted on-device preferences, read again on each request. */
class TokenStore(private val prefs: SharedPreferences) {
    /** The stored Yandex token, or `""` when nothing usable is stored. Never null. */
    fun yandexToken(): String = prefs.getString(YANDEX_OAUTH_TOKEN, null).orEmpty().trim()

    /** Seeds an empty store from BuildConfig without overwriting fresher runtime credentials. */
    fun seedYandexToken(token: String): Boolean {
        val seed = token.trim()
        if (seed.isEmpty() || yandexToken().isNotEmpty()) return false
        prefs.edit().putString(YANDEX_OAUTH_TOKEN, seed).apply()
        return true
    }

    /** Tuya project credentials and linked Smart Life uid; empty when absent. */
    fun tuyaClientId(): String = read(TUYA_CLIENT_ID)

    fun tuyaClientSecret(): String = read(TUYA_CLIENT_SECRET)

    fun tuyaUid(): String = read(TUYA_UID)

    /** Local device identifiers, addresses and keys share the encrypted credential store. */
    fun tuyaLanDevices(): String = read(TUYA_LOCAL_DEVICES)

    /** An APK seed never overwrites keys or connection details already stored on the tablet. */
    fun seedTuyaLanDevices(devicesJson: String) {
        seed(TUYA_LOCAL_DEVICES, devicesJson)
    }

    /** Seeds missing fields without overwriting existing credentials. */
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
