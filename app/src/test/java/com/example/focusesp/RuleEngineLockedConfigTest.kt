package com.example.focusesp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RuleEngineLockedConfigTest {

    private lateinit var store: FakeKeyValueStore
    private val selfPackage = "com.example.focusesp"

    @Before
    fun setUp() {
        store = FakeKeyValueStore()
        FocusPrefs.init(store)
        FocusLock.init(store, now = 0L)
        FocusTimer.init(store)
    }

    @Test
    fun usesFrozenListEvenIfLivePrefsAreMutated() {
        val now = System.currentTimeMillis()
        FocusPrefs.setBlocked("com.example.frozen", true)
        FocusPrefs.setFocusActive(true)
        FocusLock.begin(
            mode = FocusMode.BLOCKLIST,
            packages = listOf("com.example.frozen"),
            endEpochMs = now + 60_000L
        )

        // Attempt (and fail) to edit through the guarded API...
        assertFalse(FocusPrefs.setBlocked("com.example.intruder", true))
        // ...and tamper with the live state list directly, as if a bug bypassed the guard.
        FocusPrefs.blockedPackages.add("com.example.intruder")

        assertTrue(RuleEngine.shouldBlock("com.example.frozen", selfPackage, now))
        assertFalse(RuleEngine.shouldBlock("com.example.intruder", selfPackage, now))
    }

    @Test
    fun usesFrozenModeEvenIfLiveModeIsSwitched() {
        val now = System.currentTimeMillis()
        FocusPrefs.setBlocked("com.example.app", true)
        FocusPrefs.setMode(FocusMode.BLOCKLIST)
        FocusPrefs.setFocusActive(true)
        FocusLock.begin(
            mode = FocusMode.BLOCKLIST,
            packages = listOf("com.example.app"),
            endEpochMs = now + 60_000L
        )

        // Tamper with live preferences while the hard session runs.
        FocusPrefs.mode.value = FocusMode.WHITELIST

        // Frozen BLOCKLIST semantics apply: selected app blocked, others allowed.
        assertTrue(RuleEngine.shouldBlock("com.example.app", selfPackage, now))
        assertFalse(RuleEngine.shouldBlock("com.example.other", selfPackage, now))
    }

    @Test
    fun expiredLockFallsBackToLivePreferences() {
        val now = System.currentTimeMillis()
        FocusPrefs.setBlocked("com.example.live", true)
        FocusPrefs.setFocusActive(true)

        // Lock record exists but its deadline has already passed.
        FocusLock.begin(
            mode = FocusMode.BLOCKLIST,
            packages = listOf("com.example.stale"),
            endEpochMs = now - 1_000L
        )

        assertFalse(FocusLock.isLocked(now))
        // Live preferences apply again: selected live app blocked, stale frozen entry not.
        assertTrue(RuleEngine.shouldBlock("com.example.live", selfPackage, now))
        assertFalse(RuleEngine.shouldBlock("com.example.stale", selfPackage, now))
    }

    @Test
    fun nothingIsBlockedWithoutFocusOrSchedule() {
        val now = System.currentTimeMillis()
        assertFalse(RuleEngine.shouldBlock("com.example.app", selfPackage, now))
        // Self package is never blocked.
        FocusPrefs.setFocusActive(true)
        FocusPrefs.setBlocked(selfPackage, true)
        assertFalse(RuleEngine.shouldBlock(selfPackage, selfPackage, now))
    }
}
