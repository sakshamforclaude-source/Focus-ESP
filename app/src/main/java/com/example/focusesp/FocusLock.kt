package com.example.focusesp

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

/**
 * Frozen configuration of an active Hard Focus session.
 *
 * When a hard session starts, the selected package list and the blocklist/whitelist
 * mode are snapshotted here and persisted. The accessibility rule engine reads this
 * snapshot for the whole session, and the app-selection UI becomes read-only.
 *
 * The persisted end timestamp is the single source of truth: every lock evaluation
 * compares against `now`, so state that is stale past its deadline reports unlocked
 * immediately even if [end] has not run yet. An alarm can complete a session but can
 * never extend it.
 */
object FocusLock {

    private const val KEY_ACTIVE = "lock_active"
    private const val KEY_HARD = "lock_hard"
    private const val KEY_END = "lock_end"
    private const val KEY_MODE = "lock_mode"
    private const val KEY_PACKAGES = "lock_packages"

    private var store: KeyValueStore? = null

    val active = mutableStateOf(false)
    val hard = mutableStateOf(false)
    val endEpoch = mutableStateOf(0L)
    val frozenMode = mutableStateOf(FocusMode.BLOCKLIST)
    val frozenPackages = mutableStateListOf<String>()

    /**
     * Loads persisted lock state. If the persisted session is already past its
     * deadline, the lock is released and persisted as unlocked.
     */
    @Synchronized
    fun init(store: KeyValueStore, now: Long = System.currentTimeMillis()) {
        this.store = store
        active.value = store.getBoolean(KEY_ACTIVE, false)
        hard.value = store.getBoolean(KEY_HARD, false)
        endEpoch.value = store.getLong(KEY_END, 0L)
        frozenMode.value = runCatching {
            FocusMode.valueOf(store.getString(KEY_MODE, FocusMode.BLOCKLIST.name))
        }.getOrDefault(FocusMode.BLOCKLIST)
        frozenPackages.clear()
        frozenPackages.addAll(store.getStringSet(KEY_PACKAGES, emptySet()))
        if (active.value && now >= endEpoch.value) {
            end()
        }
    }

    /** Snapshots the current configuration for the duration of a hard session. */
    fun begin(mode: FocusMode, packages: Collection<String>, endEpochMs: Long) {
        active.value = true
        hard.value = true
        endEpoch.value = endEpochMs
        frozenMode.value = mode
        frozenPackages.clear()
        frozenPackages.addAll(packages)
        persist()
    }

    /** Releases the lock. Idempotent; returns true only when a lock was actually released. */
    fun end(): Boolean {
        val wasActive = active.value
        active.value = false
        hard.value = false
        endEpoch.value = 0L
        frozenPackages.clear()
        if (wasActive) persist()
        return wasActive
    }

    fun isLocked(now: Long = System.currentTimeMillis()): Boolean =
        active.value && now < endEpoch.value

    fun canEditList(now: Long = System.currentTimeMillis()): Boolean = !isLocked(now)

    fun remainingSeconds(now: Long = System.currentTimeMillis()): Long {
        if (!active.value) return 0L
        val delta = endEpoch.value - now
        return if (delta <= 0) 0L else (delta + 999) / 1000
    }

    private fun persist() {
        val s = store ?: return
        s.putBoolean(KEY_ACTIVE, active.value)
        s.putBoolean(KEY_HARD, hard.value)
        s.putLong(KEY_END, endEpoch.value)
        s.putString(KEY_MODE, frozenMode.value.name)
        s.putStringSet(KEY_PACKAGES, HashSet(frozenPackages))
    }
}
