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
