package com.example.focusesp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FocusLockTest {

    private lateinit var store: FakeKeyValueStore

    @Before
    fun setUp() {
        store = FakeKeyValueStore()
        FocusPrefs.init(store)
        FocusLock.init(store, now = 0L)
    }

    @Test
    fun hardStartFreezesListAndModeAndRejectsEdits() {
        val now = System.currentTimeMillis()
        FocusPrefs.setBlocked("com.example.app", true)
        FocusPrefs.setMode(FocusMode.BLOCKLIST)

        FocusLock.begin(
            mode = FocusMode.BLOCKLIST,
            packages = listOf("com.example.app"),
            endEpochMs = now + 25 * 60_000L
        )

        assertTrue(FocusLock.isLocked(now))
        assertFalse(FocusLock.canEditList(now))
        assertEquals(listOf("com.example.app"), FocusLock.frozenPackages.toList())
        assertEquals(FocusMode.BLOCKLIST, FocusLock.frozenMode.value)

        assertFalse(FocusPrefs.setBlocked("com.example.other", true))
        assertFalse(FocusPrefs.clearBlocked())
        assertFalse(FocusPrefs.setMode(FocusMode.WHITELIST))
        assertFalse(FocusPrefs.setBlockedBatch(listOf("com.example.third"), true))

        assertEquals(listOf("com.example.app"), FocusPrefs.blockedPackages.toList())
        assertEquals(FocusMode.BLOCKLIST, FocusPrefs.mode.value)
    }

    @Test
    fun normalFocusKeepsEditingEnabled() {
        FocusPrefs.setFocusActive(true)

        assertTrue(FocusLock.canEditList())
        assertTrue(FocusPrefs.setBlocked("com.example.app", true))
        assertTrue(FocusPrefs.setMode(FocusMode.WHITELIST))
        assertTrue(FocusPrefs.clearBlocked())
        assertFalse(FocusTimer.isLocked())
    }

    @Test
    fun restartRestoresLockAndFrozenConfigFromStore() {
        val now = System.currentTimeMillis()
        val endEpoch = now + 25 * 60_000L
        FocusLock.begin(
            mode = FocusMode.WHITELIST,
            packages = listOf("com.example.allowed"),
            endEpochMs = endEpoch
        )

        // Simulate app restart: fresh in-memory state reloaded from the same store.
        FocusLock.init(store, now = now)

        assertTrue(FocusLock.active.value)
        assertTrue(FocusLock.isLocked(now))
        assertEquals(endEpoch, FocusLock.endEpoch.value)
        assertEquals(FocusMode.WHITELIST, FocusLock.frozenMode.value)
        assertEquals(listOf("com.example.allowed"), FocusLock.frozenPackages.toList())
        assertFalse(FocusLock.canEditList(now))
    }

    @Test
    fun expiryAtInitAutomaticallyUnlocks() {
        val now = System.currentTimeMillis()
        FocusLock.begin(
            mode = FocusMode.BLOCKLIST,
            packages = listOf("com.example.app"),
            endEpochMs = now - 1_000L
        )

        // Simulate reopening the app after the deadline.
        FocusLock.init(store, now = now)

        assertFalse(FocusLock.active.value)
        assertTrue(FocusLock.canEditList(now))
        assertTrue(FocusLock.frozenPackages.isEmpty())
        assertTrue(FocusPrefs.setBlocked("com.example.app", true))
    }

    @Test
    fun staleStatePastDeadlineReportsUnlockedWithoutReinit() {
        val now = System.currentTimeMillis()
        FocusLock.begin(
            mode = FocusMode.BLOCKLIST,
            packages = listOf("com.example.app"),
            endEpochMs = now + 1_000L
        )
        assertTrue(FocusLock.isLocked(now))

        // endEpoch reached but end() has not run yet (app not resumed, no tick).
        val later = now + 5_000L
        assertFalse(FocusLock.isLocked(later))
        assertTrue(FocusLock.canEditList(later))
        assertEquals(0L, FocusLock.remainingSeconds(later))
        // Editing via FocusPrefs uses real wall-clock time internally, so the
        // restored-editing path is covered by expiryAtInitAutomaticallyUnlocks,
        // where the lock is fully released (active=false).
    }

    @Test
    fun endIsIdempotentAndOnlyReportsRealTransitions() {
        FocusLock.begin(
            mode = FocusMode.BLOCKLIST,
            packages = listOf("com.example.app"),
            endEpochMs = System.currentTimeMillis() + 60_000L
        )
        assertTrue(FocusLock.end())
        assertFalse(FocusLock.end())
        assertFalse(FocusLock.active.value)
    }

    @Test
    fun remainingSecondsCountsDownTowardsZero() {
        val now = System.currentTimeMillis()
        FocusLock.begin(
            mode = FocusMode.BLOCKLIST,
            packages = listOf("com.example.app"),
            endEpochMs = now + 60_000L
        )
        assertEquals(60L, FocusLock.remainingSeconds(now))
        assertEquals(30L, FocusLock.remainingSeconds(now + 30_000L))
        assertEquals(1L, FocusLock.remainingSeconds(now + 59_001L))
        assertEquals(0L, FocusLock.remainingSeconds(now + 60_001L))
    }
}
