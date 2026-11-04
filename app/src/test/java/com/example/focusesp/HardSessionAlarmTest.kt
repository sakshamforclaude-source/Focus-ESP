package com.example.focusesp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HardSessionAlarmTest {

    private lateinit var scheduler: FakeAlarmScheduler
    private lateinit var store: FakeKeyValueStore

    @Before
    fun setUp() {
        scheduler = FakeAlarmScheduler()
        store = FakeKeyValueStore()
        FocusPrefs.init(store)
        FocusLock.init(store, now = 0L)
        FocusTimer.init(store)
    }

    @Test
    fun exactAccessMatrixMatchesAndroidVersionRules() {
        // Before Android 12 (API 31) exact alarms are always allowed.
        assertTrue(HardSessionAlarm.canUseExact(sdkInt = 30, canScheduleExact = false))
        assertTrue(HardSessionAlarm.canUseExact(sdkInt = 30, canScheduleExact = true))
        // Android 12+: exact requires the SCHEDULE_EXACT_ALARM grant.
        assertTrue(HardSessionAlarm.canUseExact(sdkInt = 31, canScheduleExact = true))
        assertFalse(HardSessionAlarm.canUseExact(sdkInt = 31, canScheduleExact = false))
        assertTrue(HardSessionAlarm.canUseExact(sdkInt = 37, canScheduleExact = true))
        assertFalse(HardSessionAlarm.canUseExact(sdkInt = 37, canScheduleExact = false))
    }

    @Test
    fun schedulesExactAlarmWhenExactAccessIsAvailable() {
        val end = System.currentTimeMillis() + 25 * 60_000L

        HardSessionAlarm.scheduleWith(end, scheduler, exact = true)

        assertEquals(listOf(end), scheduler.exactAt)
        assertTrue(scheduler.inexactAt.isEmpty())
        assertEquals(0, scheduler.cancelled)
    }

    @Test
    fun fallsBackToInexactAlarmWhenExactIsUnavailable() {
        val end = System.currentTimeMillis() + 25 * 60_000L

        HardSessionAlarm.scheduleWith(end, scheduler, exact = false)

        assertEquals(listOf(end), scheduler.inexactAt)
        assertTrue(scheduler.exactAt.isEmpty())
    }

    @Test
    fun cancelDelegatesToScheduler() {
        HardSessionAlarm.cancelWith(scheduler)
        assertEquals(1, scheduler.cancelled)
    }

    @Test
    fun earlyAlarmDeliveryDoesNotEndSession() {
        val now = System.currentTimeMillis()
        FocusTimer.setHardMode(true)
        FocusTimer.selectMode(FocusTimer.Mode.COUNTDOWN)
        FocusTimer.setCountdownMinutes(25)
        FocusTimer.start(now)
        val end = now + 25 * 60_000L

        // Alarm fired a moment before the deadline (inexact fallback jitter).
        assertFalse(FocusTimer.checkExpiry(end - 1))
        assertTrue(FocusTimer.sessionActive.value)
        assertTrue(FocusLock.active.value)
        assertTrue(FocusTimer.isLocked(end - 1))
    }

    @Test
    fun duplicateLateExpiryDeliveriesEndSessionExactlyOnce() {
        val now = System.currentTimeMillis()
        FocusTimer.setHardMode(true)
        FocusTimer.selectMode(FocusTimer.Mode.COUNTDOWN)
        FocusTimer.setCountdownMinutes(25)
        FocusTimer.start(now)
        val end = now + 25 * 60_000L

        // Service tick and alarm may both deliver the same expiry moment.
        assertTrue(FocusTimer.checkExpiry(end))
        assertFalse(FocusTimer.checkExpiry(end))
        assertFalse(FocusTimer.checkExpiry(end + 500L))

        assertFalse(FocusTimer.sessionActive.value)
        assertFalse(FocusLock.active.value)
        assertFalse(FocusTimer.running.value)
        assertEquals(FocusTimer.Phase.DONE, FocusTimer.phase.value)
        assertTrue(FocusLock.frozenPackages.isEmpty())
    }

    @Test
    fun stateAfterExpiryAllowsStartingFresh() {
        val now = System.currentTimeMillis()
        FocusTimer.setHardMode(true)
        FocusTimer.selectMode(FocusTimer.Mode.COUNTDOWN)
        FocusTimer.setCountdownMinutes(25)
        FocusTimer.start(now)
        assertTrue(FocusTimer.checkExpiry(System.currentTimeMillis() + 60 * 60_000L))

        FocusTimer.reset()
        assertEquals(FocusTimer.Phase.IDLE, FocusTimer.phase.value)
        assertFalse(FocusTimer.running.value)
        assertFalse(FocusLock.active.value)
        assertFalse(FocusTimer.sessionActive.value)
        assertTrue(FocusTimer.isLocked().not())
        assertTrue(FocusLock.canEditList())
    }
}
