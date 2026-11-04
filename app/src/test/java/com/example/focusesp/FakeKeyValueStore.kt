package com.example.focusesp

class FakeKeyValueStore : KeyValueStore {

    private val strings = mutableMapOf<String, String>()
    private val ints = mutableMapOf<String, Int>()
    private val longs = mutableMapOf<String, Long>()
    private val booleans = mutableMapOf<String, Boolean>()
    private val sets = mutableMapOf<String, Set<String>>()

    override fun getString(key: String, def: String): String = strings[key] ?: def

    override fun putString(key: String, value: String) {
        strings[key] = value
    }

    override fun getInt(key: String, def: Int): Int = ints[key] ?: def

    override fun putInt(key: String, value: Int) {
        ints[key] = value
    }

    override fun getLong(key: String, def: Long): Long = longs[key] ?: def

    override fun putLong(key: String, value: Long) {
        longs[key] = value
    }

    override fun getBoolean(key: String, def: Boolean): Boolean = booleans[key] ?: def

    override fun putBoolean(key: String, value: Boolean) {
        booleans[key] = value
    }

    override fun getStringSet(key: String, def: Set<String>): Set<String> = sets[key] ?: def

    override fun putStringSet(key: String, value: Set<String>) {
        sets[key] = value
    }
}
