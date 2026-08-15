package ru.domovoy.core

import android.content.SharedPreferences

private const val YANDEX_OAUTH_TOKEN = "yandex.oauth.token"

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
}
