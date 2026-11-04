package com.example.focusesp

import android.content.SharedPreferences

interface KeyValueStore {
    fun getString(key: String, def: String = ""): String
    fun putString(key: String, value: String)
    fun getInt(key: String, def: Int = 0): Int
    fun putInt(key: String, value: Int)
    fun getLong(key: String, def: Long = 0L): Long
    fun putLong(key: String, value: Long)
    fun getBoolean(key: String, def: Boolean = false): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getStringSet(key: String, def: Set<String> = emptySet()): Set<String>
    fun putStringSet(key: String, value: Set<String>)
}

class PrefsStore(private val prefs: SharedPreferences) : KeyValueStore {

    override fun getString(key: String, def: String): String = prefs.getString(key, def) ?: def

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getInt(key: String, def: Int): Int = prefs.getInt(key, def)

    override fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    override fun getLong(key: String, def: Long): Long = prefs.getLong(key, def)

    override fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    override fun getBoolean(key: String, def: Boolean): Boolean = prefs.getBoolean(key, def)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun getStringSet(key: String, def: Set<String>): Set<String> =
        prefs.getStringSet(key, def) ?: def

    override fun putStringSet(key: String, value: Set<String>) {
        prefs.edit().putStringSet(key, value).apply()
    }
}
