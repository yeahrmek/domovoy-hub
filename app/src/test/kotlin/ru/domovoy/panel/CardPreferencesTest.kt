package ru.domovoy.panel

import org.junit.jupiter.api.Test
import ru.domovoy.core.FakeSharedPreferences
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardPreferencesTest {
    @Test
    fun `committing a preview keeps absent positions and the order of other sections`() {
        val before = CardPreferences(orders = mapOf("Главная" to listOf("a", "missing", "b"), "Зал" to listOf("b", "a")))
        val committed = before.reorder("Главная", listOf("b", "new", "a", "b"))
        assertEquals(listOf("b", "missing", "new", "a"), committed.orders["Главная"])
        assertEquals(before.orders["Зал"], committed.orders["Зал"])
        assertEquals(before, before.reorder("Главная", emptyList()))
    }

    @Test
    fun `automatic order survives restart inventory reordering and temporary absence`() {
        val prefs = FakeSharedPreferences()
        val initial = CardPreferences().rememberOrder("Главная", listOf("curtain", "humidifier", "lights"))
        CardPreferencesStore(prefs).save(initial)
        val restored = CardPreferencesStore(prefs).load()
            .rememberOrder("Главная", listOf("lights", "curtain", "new"))
        assertEquals(listOf("curtain", "lights", "new"), restored.ordered("Главная", listOf("lights", "curtain", "new")))
        assertEquals(listOf("curtain", "humidifier", "lights", "new"), restored.ordered("Главная", listOf("new", "lights", "humidifier", "curtain")))
        assertEquals(restored, restored.rememberOrder("Главная", emptyList()))
    }

    @Test
    fun `moving a visible card preserves the reserved position of an absent card`() {
        val preferences = CardPreferences(orders = mapOf("Главная" to listOf("curtain", "humidifier", "lights")))
        val moved = preferences.move("Главная", "lights", "curtain", listOf("curtain", "lights"))
        assertEquals(listOf("lights", "humidifier", "curtain"), moved.ordered("Главная", listOf("curtain", "humidifier", "lights")))
    }

    @Test
    fun `combining cards preserves favourites and the first chosen position without duplicates`() {
        val preferences = CardPreferences(
            favourites = listOf("entry-b", "light"),
            orders = mapOf("Главная" to listOf("light", "entry-b", "entry-a", "lock")),
        )
        val replacements = mapOf("entry-a" to "intercom", "entry-b" to "intercom")
        val combined = preferences.mergeKeys(replacements)
        assertEquals(listOf("intercom", "light"), combined.favourites)
        assertEquals(listOf("light", "intercom", "lock"), combined.orders["Главная"])
        assertEquals(combined, combined.mergeKeys(replacements))
        assertEquals(CardPreferences(), CardPreferences().mergeKeys(replacements))
        assertEquals(emptyList(), CardPreferences(favourites = emptyList()).mergeKeys(replacements).favourites)
    }

    @Test
    fun `defaults remain live until the first favourite edit`() {
        val preferences = CardPreferences()
        assertTrue(preferences.isFavourite("curtain", listOf("curtain")))
        assertFalse(preferences.isFavourite("curtain", emptyList()))

        val edited = preferences.toggleFavourite("curtain", listOf("curtain", "lights"))
        assertFalse(edited.isFavourite("curtain", listOf("curtain")))
        assertTrue(edited.isFavourite("lights", emptyList()))
        assertFalse(edited.isFavourite("new", listOf("new")))
        assertTrue(edited.toggleFavourite("curtain", emptyList()).isFavourite("curtain", emptyList()))
    }

    @Test
    fun `order preserves chosen positions and appends new cards without resurrecting absent ones`() {
        val preferences = CardPreferences(orders = mapOf("Главная" to listOf("lights", "gone", "curtain", "lights")))
        assertEquals(listOf("lights", "curtain", "fan"), preferences.ordered("Главная", listOf("curtain", "lights", "fan", "fan")))
        assertEquals(listOf("curtain", "lights"), preferences.ordered("Зал", listOf("curtain", "lights")))
    }

    @Test
    fun `moving both directions changes only the selected section`() {
        val keys = listOf("curtain", "climate", "lights", "fan")
        val preferences = CardPreferences(orders = mapOf("Зал" to listOf("fan", "lights")))
        val later = preferences.move("Главная", "curtain", "lights", keys)
        assertEquals(listOf("climate", "lights", "curtain", "fan"), later.ordered("Главная", keys))
        val earlier = later.move("Главная", "fan", "climate", keys)
        assertEquals(listOf("fan", "climate", "lights", "curtain"), earlier.ordered("Главная", keys))
        assertEquals(preferences.orders["Зал"], earlier.orders["Зал"])
        assertEquals(earlier, earlier.move("Главная", "missing", "fan", keys))
        assertEquals(earlier, earlier.move("Главная", "fan", "missing", keys))
    }

    @Test
    fun `an intentionally empty favourites list and card orders survive restart`() {
        val prefs = FakeSharedPreferences()
        val chosen = CardPreferences(favourites = emptyList(), orders = mapOf("Главная" to listOf("lights", "curtain")))
        CardPreferencesStore(prefs).save(chosen)
        assertEquals(chosen, CardPreferencesStore(prefs).load())
        assertFalse(CardPreferencesStore(prefs).load().isFavourite("curtain", listOf("curtain")))
    }

    @Test
    fun `missing or unreadable storage keeps defaults available`() {
        val prefs = FakeSharedPreferences()
        assertEquals(CardPreferences(), CardPreferencesStore(prefs).load())
        prefs.edit().putString("panel.cards", "{bad json").apply()
        assertEquals(CardPreferences(), CardPreferencesStore(prefs).load())
        CardPreferencesStore(null).save(CardPreferences(favourites = emptyList()))
        assertEquals(CardPreferences(), CardPreferencesStore(null).load())
    }
}
