package ru.domovoy.panel

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Null favourites follow the initial selection; an empty list deliberately leaves Home empty. */
@Serializable
data class CardPreferences(
    val favourites: List<String>? = null,
    val orders: Map<String, List<String>> = emptyMap(),
) {
    fun isFavourite(key: String, defaultKeys: List<String>): Boolean = key in (favourites ?: defaultKeys)

    /** Combining cards retains a favourite or position chosen for any of the original cards. */
    fun mergeKeys(replacements: Map<String, String>): CardPreferences = if (replacements.isEmpty()) {
        this
    } else {
        copy(
            favourites = favourites?.map { replacements[it] ?: it }?.distinct(),
            orders = orders.mapValues { (_, keys) -> keys.map { replacements[it] ?: it }.distinct() },
        )
    }

    fun toggleFavourite(key: String, defaultKeys: List<String>): CardPreferences {
        val selected = (favourites ?: defaultKeys).distinct()
        return copy(favourites = if (key in selected) selected - key else selected + key)
    }

    /** Poll response order cannot undo edits, and newly discovered cards append at the end. */
    fun ordered(section: String, availableKeys: List<String>): List<String> {
        val available = availableKeys.toSet()
        return (orders[section].orEmpty().filter { it in available } + availableKeys).distinct()
    }

    /** Reserve existing positions across missing inventory entries and append newly seen cards. */
    fun rememberOrder(section: String, availableKeys: List<String>): CardPreferences {
        if (availableKeys.isEmpty()) return this
        val keys = (orders[section].orEmpty() + availableKeys).distinct()
        return if (orders[section] == keys) this else copy(orders = orders + (section to keys))
    }

    fun move(
        section: String,
        key: String,
        targetKey: String,
        availableKeys: List<String>,
    ): CardPreferences {
        val ordered = ordered(section, availableKeys).toMutableList()
        val from = ordered.indexOf(key)
        val to = ordered.indexOf(targetKey)
        if (from < 0 || to < 0 || from == to) return this
        ordered.add(to, ordered.removeAt(from))
        return reorder(section, ordered)
    }

    /** Commit a drag preview while reserving the positions of temporarily absent cards. */
    fun reorder(section: String, orderedKeys: List<String>): CardPreferences {
        if (orderedKeys.isEmpty()) return this
        val keys = orderedKeys.distinct()
        val visible = keys.toSet()
        val moved = keys.iterator()
        val reserved = rememberOrder(section, keys).orders.getValue(section)
        return copy(orders = orders + (section to reserved.map { if (it in visible) moved.next() else it }))
    }
}

/** One private on-device record; no reading, credential or control command is persisted here. */
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
