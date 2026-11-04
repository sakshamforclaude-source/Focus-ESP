package com.example.focusesp

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

enum class FocusMode { BLOCKLIST, WHITELIST }

object FocusPrefs {

    private const val PREFS_NAME = "focus_prefs"
    private const val KEY_BLOCKED = "blocked_packages"
    private const val KEY_FOCUS_ACTIVE = "focus_active"
    private const val KEY_PAUSED_UNTIL = "paused_until"
    private const val KEY_MODE = "focus_mode"

    private var prefs: KeyValueStore? = null

    val blockedPackages = mutableStateListOf<String>()
    val focusActive = mutableStateOf(false)
    val pausedUntil = mutableStateOf(0L)
    val mode = mutableStateOf(FocusMode.BLOCKLIST)

    fun init(context: Context) {
        init(
            PrefsStore(
                context.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            )
        )
    }

    @Synchronized
    fun init(store: KeyValueStore) {
        prefs = store
        blockedPackages.clear()
        blockedPackages.addAll(store.getStringSet(KEY_BLOCKED, emptySet()))
        focusActive.value = store.getBoolean(KEY_FOCUS_ACTIVE, false)
        pausedUntil.value = store.getLong(KEY_PAUSED_UNTIL, 0L)
        mode.value = runCatching {
            FocusMode.valueOf(store.getString(KEY_MODE, FocusMode.BLOCKLIST.name))
        }.getOrDefault(FocusMode.BLOCKLIST)
    }

    /** Returns false (and changes nothing) while a Hard Focus session locks the list. */
    @Synchronized
    fun setMode(value: FocusMode): Boolean {
        if (!FocusLock.canEditList()) return false
        mode.value = value
        prefs?.putString(KEY_MODE, value.name)
        return true
    }

    fun isBlocked(packageName: String): Boolean = blockedPackages.contains(packageName)

    @Synchronized
    fun setBlocked(packageName: String, blocked: Boolean): Boolean {
        if (!FocusLock.canEditList()) return false
        if (blocked) {
            if (!blockedPackages.contains(packageName)) {
                blockedPackages.add(packageName)
            }
        } else {
            blockedPackages.remove(packageName)
        }
        persistBlocked()
        return true
    }

    @Synchronized
    fun setBlockedBatch(packages: Collection<String>, blocked: Boolean): Boolean {
        if (!FocusLock.canEditList()) return false
        if (blocked) {
            packages.forEach { if (!blockedPackages.contains(it)) blockedPackages.add(it) }
        } else {
            blockedPackages.removeAll(packages.toSet())
        }
        persistBlocked()
        return true
    }

    @Synchronized
    fun clearBlocked(): Boolean {
        if (!FocusLock.canEditList()) return false
        blockedPackages.clear()
        persistBlocked()
        return true
    }

    private fun persistBlocked() {
        prefs?.putStringSet(KEY_BLOCKED, HashSet(blockedPackages))
    }

    fun setFocusActive(active: Boolean) {
        focusActive.value = active
        prefs?.putBoolean(KEY_FOCUS_ACTIVE, active)
    }

    fun pauseFor(millis: Long) {
        setPausedUntil(System.currentTimeMillis() + millis)
    }

    fun resume() {
        setPausedUntil(0L)
    }

    private fun setPausedUntil(value: Long) {
        pausedUntil.value = value
        prefs?.putLong(KEY_PAUSED_UNTIL, value)
    }
}
