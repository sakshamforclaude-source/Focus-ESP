package com.example.focusesp

import android.content.Context

object LocalStore : KeyValueStore {

    private const val PREFS_NAME = "focusesp_store"
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun getString(key: String, def: String): String = prefs?.getString(key, def) ?: def

    override fun putString(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply()
    }

    override fun getInt(key: String, def: Int): Int = prefs?.getInt(key, def) ?: def

    override fun putInt(key: String, value: Int) {
        prefs?.edit()?.putInt(key, value)?.apply()
    }

    override fun getLong(key: String, def: Long): Long = prefs?.getLong(key, def) ?: def

    override fun putLong(key: String, value: Long) {
        prefs?.edit()?.putLong(key, value)?.apply()
    }

    override fun getBoolean(key: String, def: Boolean): Boolean =
        prefs?.getBoolean(key, def) ?: def

    override fun putBoolean(key: String, value: Boolean) {
        prefs?.edit()?.putBoolean(key, value)?.apply()
    }

    override fun getStringSet(key: String, def: Set<String>): Set<String> =
        prefs?.getStringSet(key, def) ?: def

    override fun putStringSet(key: String, value: Set<String>) {
        prefs?.edit()?.putStringSet(key, value)?.apply()
    }

    fun getStringList(key: String): List<String> {
        val raw = getString(key, "[]")
        return try {
            val array = org.json.JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun putStringList(key: String, values: List<String>) {
        putString(key, org.json.JSONArray(values).toString())
    }
}
