package ru.domovoy.panel

import org.junit.jupiter.api.Test
import ru.domovoy.core.FakeSharedPreferences
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardPreferencesTest {
    @Test
    fun `reserved order survives restart reordering and temporary absence`() {
        val prefs = FakeSharedPreferences()
        CardPreferencesStore(prefs).save(CardPreferences().rememberOrder("Главная", listOf("curtain", "humidifier", "lights")))
        val restored = CardPreferencesStore(prefs).load().rememberOrder("Главная", listOf("lights", "curtain", "new"))
        assertEquals(listOf("curtain", "lights", "new"), restored.ordered("Главная", listOf("new", "lights", "curtain")))
        assertEquals(listOf("curtain", "humidifier", "lights", "new"), restored.ordered("Главная", listOf("new", "lights", "humidifier", "curtain")))
        assertEquals(restored, restored.rememberOrder("Главная", emptyList()))
    }

    @Test
    fun `existing panel record retains favourites and ignores unrelated fields`() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString("panel.cards", """{"favourites":["breezers","bulb:individual"],"orders":{"Главная":["bulb:individual","breezers"]},"future":true}""").apply()
        val restored = CardPreferencesStore(prefs).load()
        assertTrue(restored.isFavourite("recuperator:bedroom", emptyList()))
        assertTrue(restored.isFavourite("bulb:individual", emptyList()))
        assertFalse(restored.isFavourite("lamps:bedroom", listOf("lamps:bedroom")))
        assertEquals(listOf("bulb:individual", "breezers"), restored.orders["Главная"])
        CardPreferencesStore(prefs).save(restored)
        assertEquals(restored, CardPreferencesStore(prefs).load())
    }

    @Test
    fun `explicitly empty favourites and unreadable storage remain distinct`() {
        val prefs = FakeSharedPreferences()
        CardPreferencesStore(prefs).save(CardPreferences(favourites = emptyList()))
        assertFalse(CardPreferencesStore(prefs).load().isFavourite("curtain", listOf("curtain")))
        prefs.edit().putString("panel.cards", "{invalid").apply()
        assertEquals(CardPreferences(), CardPreferencesStore(prefs).load())
        assertEquals(CardPreferences(), CardPreferencesStore(null).load())
    }
}
