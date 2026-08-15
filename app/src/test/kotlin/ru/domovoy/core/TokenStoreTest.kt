package ru.domovoy.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The store against a fake [android.content.SharedPreferences] — see [FakeSharedPreferences] for
 * why the real encrypted one is not what these tests drive.
 */
class TokenStoreTest {
    @Test
    fun `a token written to the store reads back`() {
        val store = TokenStore(FakeSharedPreferences())

        store.seedYandexToken("y0_first")

        assertEquals("y0_first", store.yandexToken())
    }

    @Test
    fun `an empty store answers with no token rather than throwing`() {
        assertEquals("", TokenStore(FakeSharedPreferences()).yandexToken())
    }

    @Test
    fun `a fresh install takes the token from the build, so the first poll has one`() {
        val prefs = FakeSharedPreferences()

        val seeded = TokenStore(prefs).seedYandexToken("y0_from_local_properties")

        assertTrue(seeded)
        // Written, not just remembered: the next launch builds a new store over the same file.
        assertEquals("y0_from_local_properties", TokenStore(prefs).yandexToken())
    }

    @Test
    fun `a token already stored survives a launch whose build carries an older one`() {
        // The point of moving the token out of BuildConfig: once something else has written a
        // fresher token, the value baked into the APK must not overwrite it on the next launch.
        val prefs = FakeSharedPreferences()
        TokenStore(prefs).seedYandexToken("y0_fresh")

        val seeded = TokenStore(prefs).seedYandexToken("y0_stale_from_the_apk")

        assertEquals(false, seeded)
        assertEquals("y0_fresh", TokenStore(prefs).yandexToken())
    }

    @Test
    fun `a build with no token in local properties seeds nothing`() {
        val store = TokenStore(FakeSharedPreferences())

        val seeded = store.seedYandexToken("")

        assertEquals(false, seeded)
        assertEquals("", store.yandexToken())
    }

    @Test
    fun `the Tuya credentials written to the store read back, all three of them`() {
        val prefs = FakeSharedPreferences()

        TokenStore(prefs).seedTuyaCredentials(
            clientId = "client-from-local-properties",
            clientSecret = "secret-from-local-properties",
            uid = "uid-from-local-properties",
        )

        // Written, not just remembered: the next launch builds a new store over the same file.
        val store = TokenStore(prefs)
        assertEquals("client-from-local-properties", store.tuyaClientId())
        assertEquals("secret-from-local-properties", store.tuyaClientSecret())
        assertEquals("uid-from-local-properties", store.tuyaUid())
    }

    @Test
    fun `an empty store answers with blank Tuya credentials rather than throwing`() {
        val store = TokenStore(FakeSharedPreferences())

        assertEquals("", store.tuyaClientId())
        assertEquals("", store.tuyaClientSecret())
        assertEquals("", store.tuyaUid())
    }

    @Test
    fun `a Tuya secret already stored survives a launch whose build carries an older one`() {
        // Same rule as the Yandex token, and it matters more here: a rotated client secret written
        // into the store would otherwise be undone by the stale one still baked into the APK, and
        // every call after that reboot would come back 1004 sign invalid.
        val prefs = FakeSharedPreferences()
        TokenStore(prefs).seedTuyaCredentials("client-rotated", "secret-rotated", "uid-1")

        TokenStore(prefs).seedTuyaCredentials("client-stale", "secret-stale", "uid-1")

        assertEquals("client-rotated", TokenStore(prefs).tuyaClientId())
        assertEquals("secret-rotated", TokenStore(prefs).tuyaClientSecret())
    }

    @Test
    fun `a build carrying only some of the Tuya credentials seeds those and leaves the rest`() {
        // Each key is seeded on its own, so a local.properties missing one value does not stop the
        // other two from reaching the store.
        val prefs = FakeSharedPreferences()

        TokenStore(prefs).seedTuyaCredentials(clientId = "client-1", clientSecret = "", uid = "uid-1")

        val store = TokenStore(prefs)
        assertEquals("client-1", store.tuyaClientId())
        assertEquals("", store.tuyaClientSecret())
        assertEquals("uid-1", store.tuyaUid())
    }

    @Test
    fun `a token that is only whitespace counts as no token at all`() {
        // local.properties keeps whatever trails the value, and a token pasted with a stray
        // newline would otherwise be sent as a Bearer header and rejected as Forbidden.
        val store = TokenStore(FakeSharedPreferences())

        assertEquals(false, store.seedYandexToken("   "))
        assertEquals("", store.yandexToken())

        store.seedYandexToken("  y0_padded  ")
        assertEquals("y0_padded", store.yandexToken())
    }
}
