package com.example.focusesp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FocusTimerHardSessionTest {

    private lateinit var store: FakeKeyValueStore

    @Before
    fun setUp() {
        store = FakeKeyValueStore()
        FocusPrefs.init(store)
        FocusLock.init(store, now = 0L)
        FocusTimer.init(store)
    }

    private fun startHardSession(now: Long) {
        FocusTimer.setHardMode(true)
        FocusTimer.selectMode(FocusTimer.Mode.COUNTDOWN)
        FocusTimer.setCountdownMinutes(25)
        FocusPrefs.setBlocked("com.example.app", true)
        FocusTimer.start(now)
    }

    @Test
    fun startingHardFocusLocksSessionListAndTimer() {
        val now = System.currentTimeMillis()
        startHardSession(now)
        val end = now + 25 * 60_000L

        assertTrue(FocusTimer.sessionActive.value)
        assertTrue(FocusTimer.running.value)
        assertEquals(end, FocusTimer.sessionEndEpoch.value)
        assertTrue(FocusTimer.isLocked(now))
        assertEquals(end, FocusLock.endEpoch.value)
        assertEquals(listOf("com.example.app"), FocusLock.frozenPackages.toList())
        assertFalse(FocusLock.canEditList(now))
    }

    @Test
    fun pauseAndResetCannotCancelHardSession() {
        val now = System.currentTimeMillis()
        startHardSession(now)

        FocusTimer.pause()
        assertTrue(FocusTimer.running.value)
        assertEquals(FocusTimer.Mode.COUNTDOWN, FocusTimer.mode.value)

        FocusTimer.reset()
        assertTrue(FocusTimer.running.value)
        assertTrue(FocusTimer.sessionActive.value)
        assertTrue(FocusLock.active.value)
        assertTrue(FocusTimer.isLocked(now))

        assertFalse(FocusPrefs.setBlocked("com.example.another", true))

        FocusTimer.selectMode(FocusTimer.Mode.STOPWATCH)
        assertEquals(FocusTimer.Mode.COUNTDOWN, FocusTimer.mode.value)
    }

    @Test
    fun earlyExpiryCheckDoesNotEndSession() {
        val now = System.currentTimeMillis()
        startHardSession(now)
        val end = now + 25 * 60_000L

        assertFalse(FocusTimer.checkExpiry(end - 1))
        assertTrue(FocusTimer.sessionActive.value)
        assertTrue(FocusTimer.running.value)
        assertTrue(FocusTimer.isLocked(end - 1))
        assertTrue(FocusLock.active.value)
    }

    @Test
    fun automaticExpiryEndsSessionOnceAndRestoresEditing() {
        val now = System.currentTimeMillis()
        startHardSession(now)
        val end = now + 25 * 60_000L

        assertTrue(FocusTimer.checkExpiry(end))
        assertFalse(FocusTimer.sessionActive.value)
        assertFalse(FocusTimer.running.value)
        assertFalse(FocusTimer.isLocked(end))
        assertFalse(FocusLock.active.value)
        assertEquals(0, FocusTimer.remainingSeconds.intValue)
        assertEquals(FocusTimer.Phase.DONE, FocusTimer.phase.value)
        assertFalse(FocusPrefs.focusActive.value)
        assertTrue(FocusLock.frozenPackages.isEmpty())
        assertTrue(FocusPrefs.setBlocked("com.example.editable", true))

        // Duplicate alarm delivery / repeated checks are idempotent no-ops.
        assertFalse(FocusTimer.checkExpiry(end + 1_000L))
        assertFalse(FocusTimer.checkExpiry(end + 2_000L))
        assertFalse(FocusLock.active.value)
    }

    @Test
    fun restartResumesPersistedSessionAndStaysLocked() {
        val now = System.currentTimeMillis()
        startHardSession(now)

        // Simulate process death + relaunch while the session is still running.
        FocusLock.init(store, now = now)
        FocusTimer.init(store)

        assertTrue(FocusTimer.sessionActive.value)
        assertTrue(FocusTimer.running.value)
        assertEquals(FocusTimer.Mode.COUNTDOWN, FocusTimer.mode.value)
        assertTrue(FocusTimer.isLocked(now))
        assertTrue(FocusLock.active.value)
        val remaining = FocusTimer.remainingSeconds.intValue
        assertTrue("remaining should be positive", remaining > 0)
        assertTrue("remaining should not exceed session length", remaining <= 25 * 60)
        assertFalse(FocusPrefs.setBlocked("com.example.another", true))
    }

    @Test
    fun restartAfterDeadlineAutomaticallyUnlocks() {
        // Session started 26 minutes ago with a 25 minute countdown -> deadline passed.
        val pastStart = System.currentTimeMillis() - 26 * 60_000L
        startHardSession(pastStart)
        val end = pastStart + 25 * 60_000L
        assertTrue(end < System.currentTimeMillis())

        // Simulate process death and reopening after the deadline passed.
        FocusLock.init(store, now = System.currentTimeMillis())
        FocusTimer.init(store)

        assertFalse(FocusTimer.sessionActive.value)
        assertFalse(FocusLock.active.value)
        assertFalse(FocusTimer.running.value)
        assertEquals(FocusTimer.Phase.DONE, FocusTimer.phase.value)
        assertFalse(FocusTimer.isLocked())
        assertTrue(FocusPrefs.setBlocked("com.example.editable", true))
    }

    @Test
    fun normalCountdownKeepsEditingAndTimerControls() {
        FocusTimer.setHardMode(false)
        FocusTimer.selectMode(FocusTimer.Mode.COUNTDOWN)
        FocusTimer.start()

        assertTrue(FocusTimer.running.value)
        assertFalse(FocusTimer.isLocked())
        assertTrue(FocusPrefs.setBlocked("com.example.editable", true))

        // No hard session -> expiry machinery must not touch the running timer.
        assertFalse(FocusTimer.checkExpiry(System.currentTimeMillis() + 60 * 60_000L))
        assertTrue(FocusTimer.running.value)
        assertFalse(FocusLock.active.value)

        FocusTimer.pause()
        assertFalse(FocusTimer.running.value)

        FocusTimer.reset()
        assertEquals(FocusTimer.Phase.IDLE, FocusTimer.phase.value)
        assertFalse(FocusPrefs.focusActive.value)
        assertTrue(FocusLock.canEditList())
    }
}
