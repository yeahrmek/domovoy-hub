package ru.domovoy.panel

import org.junit.jupiter.api.Test
import ru.domovoy.core.FakeSharedPreferences
import kotlin.test.assertEquals

class QuickAppsTest {
    private val alice = "com.yandex.iot"
    private val gemini = "com.google.android.apps.bard"
    private val chatgpt = "com.openai.chatgpt"

    @Test
    fun `a fresh panel offers Alice home, Gemini and ChatGPT`() {
        assertEquals(listOf(alice, gemini, chatgpt), QuickAppStore(FakeSharedPreferences()).packages.value)
    }

    @Test
    fun `an added app goes to the end and survives a restart`() {
        val prefs = FakeSharedPreferences()
        QuickAppStore(prefs).toggle("com.example.notes")

        assertEquals(listOf(alice, gemini, chatgpt, "com.example.notes"), QuickAppStore(prefs).packages.value)
    }

    @Test
    fun `toggling a shown app removes it, even a default one`() {
        val prefs = FakeSharedPreferences()
        QuickAppStore(prefs).toggle(gemini)

        assertEquals(listOf(alice, chatgpt), QuickAppStore(prefs).packages.value)
    }

    @Test
    fun `removing every app leaves the row empty rather than restoring defaults`() {
        val prefs = FakeSharedPreferences()
        val store = QuickAppStore(prefs)
        listOf(alice, gemini, chatgpt).forEach(store::toggle)

        assertEquals(emptyList(), QuickAppStore(prefs).packages.value)
    }

    @Test
    fun `a dragged order survives a restart`() {
        val prefs = FakeSharedPreferences()
        QuickAppStore(prefs).reorder(listOf(chatgpt, alice, gemini))

        assertEquals(listOf(chatgpt, alice, gemini), QuickAppStore(prefs).packages.value)
    }

    @Test
    fun `defaults keep their wall names and other apps use their installed label`() {
        val apps = quickApps(listOf(alice, "com.example.notes", "com.example.gone")) { packageName ->
            when (packageName) {
                alice -> "Дом с Алисой (Яндекс)"
                "com.example.notes" -> "Заметки"
                else -> null
            }
        }

        assertEquals(listOf("Дом с Алисой", "Заметки", "com.example.gone"), apps.map { it.name })
        assertEquals(listOf(true, true, false), apps.map { it.openable })
    }

    @Test
    fun `moving an item lands it at the target slot`() {
        assertEquals(listOf("b", "a", "c"), listOf("a", "b", "c").moved(0, 1))
        assertEquals(listOf("c", "a", "b"), listOf("a", "b", "c").moved(2, 0))
        assertEquals(listOf("a", "b", "c"), listOf("a", "b", "c").moved(1, 1))
    }
}
