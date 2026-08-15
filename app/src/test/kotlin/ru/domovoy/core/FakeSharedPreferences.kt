package ru.domovoy.core

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener

/**
 * An in-memory [SharedPreferences], so [TokenStore] can be tested on the JVM.
 *
 * The real store on the tablet is `EncryptedSharedPreferences`, which needs the Android keystore
 * and therefore a device — but everything [TokenStore] decides (seed or keep, blank or not) is
 * decided above that line. This keeps Robolectric out of the build for what is, underneath, a map.
 */
class FakeSharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(
        key: String?,
        defValue: String?,
    ): String? = values[key] as? String ?: defValue

    override fun getStringSet(
        key: String?,
        defValues: MutableSet<String>?,
    ): MutableSet<String>? {
        @Suppress("UNCHECKED_CAST")
        return values[key] as? MutableSet<String> ?: defValues
    }

    override fun getInt(
        key: String?,
        defValue: Int,
    ): Int = values[key] as? Int ?: defValue

    override fun getLong(
        key: String?,
        defValue: Long,
    ): Long = values[key] as? Long ?: defValue

    override fun getFloat(
        key: String?,
        defValue: Float,
    ): Float = values[key] as? Float ?: defValue

    override fun getBoolean(
        key: String?,
        defValue: Boolean,
    ): Boolean = values[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor(values)

    // Nothing in the panel listens for changes; a token is read when a poll needs one.
    override fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener?) = Unit
}

private object Removed

private class FakeEditor(private val values: MutableMap<String, Any?>) : SharedPreferences.Editor {
    private val pending = mutableMapOf<String, Any?>()
    private var clearFirst = false

    override fun putString(
        key: String,
        value: String?,
    ): SharedPreferences.Editor = put(key, value)

    override fun putStringSet(
        key: String,
        values: MutableSet<String>?,
    ): SharedPreferences.Editor = put(key, values)

    override fun putInt(
        key: String,
        value: Int,
    ): SharedPreferences.Editor = put(key, value)

    override fun putLong(
        key: String,
        value: Long,
    ): SharedPreferences.Editor = put(key, value)

    override fun putFloat(
        key: String,
        value: Float,
    ): SharedPreferences.Editor = put(key, value)

    override fun putBoolean(
        key: String,
        value: Boolean,
    ): SharedPreferences.Editor = put(key, value)

    override fun remove(key: String): SharedPreferences.Editor = put(key, Removed)

    override fun clear(): SharedPreferences.Editor {
        clearFirst = true
        return this
    }

    override fun commit(): Boolean {
        write()
        return true
    }

    override fun apply() = write()

    private fun put(
        key: String,
        value: Any?,
    ): SharedPreferences.Editor {
        pending[key] = value
        return this
    }

    private fun write() {
        if (clearFirst) values.clear()
        pending.forEach { (key, value) ->
            if (value == Removed || value == null) values.remove(key) else values[key] = value
        }
        pending.clear()
        clearFirst = false
    }
}
