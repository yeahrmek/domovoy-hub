package ru.domovoy.panel

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Null favourites include every card; an explicit empty list deliberately leaves Home empty. */
@Serializable
data class CardPreferences(
    val favourites: List<String>? = null,
    val orders: Map<String, List<String>> = emptyMap(),
) {
    fun isFavourite(key: String, defaultKeys: List<String>): Boolean {
        val selected = favourites ?: defaultKeys
        return key in selected || (key.startsWith("recuperator:") && "breezers" in selected)
    }

    fun ordered(section: String, availableKeys: List<String>): List<String> {
        val available = availableKeys.toSet()
        return (orders[section].orEmpty().filter { it in available } + availableKeys).distinct()
    }

    /** Preserve reserved positions for absent cards and append newly discovered cards. */
    fun rememberOrder(section: String, availableKeys: List<String>): CardPreferences {
        if (availableKeys.isEmpty()) return this
        val keys = (orders[section].orEmpty() + availableKeys).distinct()
        return if (orders[section] == keys) this else copy(orders = orders + (section to keys))
    }
}

/** Uses the panel's encrypted preferences; no readings or credentials are stored here. */
class CardPreferencesStore(private val prefs: SharedPreferences?) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): CardPreferences {
        val stored = prefs?.getString(CARD_PREFERENCES, null) ?: return CardPreferences()
        return runCatching { json.decodeFromString<CardPreferences>(stored) }.getOrDefault(CardPreferences())
    }

    fun save(preferences: CardPreferences) {
        prefs?.edit()?.putString(CARD_PREFERENCES, json.encodeToString(preferences))?.apply()
    }
}

private const val CARD_PREFERENCES = "panel.cards"
