package com.example.focusesp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FocusTimerHardModeTest {

    private lateinit var store: FakeKeyValueStore
    private lateinit var time: FakeTimeSource

    @Before
    fun setUp() {
        store = FakeKeyValueStore()
        FocusPrefs.init(store)
        FocusLock.init(store, now = 0L)
        time = FakeTimeSource()
        FocusTimer.init(store, time)
    }

    private fun wall() = time.wallClockMs()
    private fun elapsed() = time.elapsedRealtimeMs()

    // ---- Stopwatch -------------------------------------------------------

    @Test
    fun stopwatchCommitmentLocksStopAndResetUntilElapsed() {
        FocusTimer.selectMode(FocusTimer.Mode.STOPWATCH)
        FocusTimer.setStopwatchHardMode(true)
        FocusTimer.setStopwatchCommitMinutes(2)
        assertTrue(FocusTimer.start(wall()))

        assertTrue(FocusTimer.commitment.value.active)
        assertTrue(FocusTimer.commitmentLocked(wall(), elapsed()))
        assertEquals(FocusTimer.SessionState.RUNNING_LOCKED, FocusTimer.sessionState(wall(), elapsed()))

        // Locked: pause and reset are ignored.
        FocusTimer.pause()
        assertTrue(FocusTimer.running.value)
        FocusTimer.reset()
        assertTrue(FocusTimer.running.value)

        // Unlock after the commitment elapses.
        time.advance(120_000L)
        assertFalse(FocusTimer.commitmentLocked(wall(), elapsed()))
        assertEquals(FocusTimer.SessionState.RUNNING_UNLOCKED, FocusTimer.sessionState(wall(), elapsed()))

        FocusTimer.reset()
        assertFalse(FocusTimer.running.value)
        assertFalse(FocusTimer.commitment.value.active)
        assertEquals(FocusTimer.Phase.IDLE, FocusTimer.phase.value)
    }

    @Test
    fun stopwatchKeepsCountingWhilePastTheCommitment() {
        FocusTimer.selectMode(FocusTimer.Mode.STOPWATCH)
        FocusTimer.setStopwatchHardMode(true)
        FocusTimer.setStopwatchCommitMinutes(1)
        FocusTimer.start(wall())

        time.advance(150_000L)
        FocusTimer.advanceTimers(wall(), elapsed())

        assertEquals(150, FocusTimer.elapsedSeconds.intValue)
        assertFalse(FocusTimer.commitmentLocked(wall(), elapsed()))
        assertTrue(FocusTimer.running.value)
    }

    @Test
    fun stopwatchWithoutHardModeNeverLocks() {
        FocusTimer.selectMode(FocusTimer.Mode.STOPWATCH)
        FocusTimer.setStopwatchHardMode(false)
        FocusTimer.start(wall())

        assertFalse(FocusTimer.commitment.value.active)
        assertFalse(FocusTimer.isSessionLocked(wall(), elapsed()))

        FocusTimer.pause()
        assertFalse(FocusTimer.running.value)
    }

    @Test
    fun stopwatchCommitmentSurvivesProcessDeathAndReboot() {
        FocusTimer.selectMode(FocusTimer.Mode.STOPWATCH)
        FocusTimer.setStopwatchHardMode(true)
        FocusTimer.setStopwatchCommitMinutes(5)
        FocusTimer.start(wall())

        time.advance(60_000L)
        val wallAtReboot = wall()

        // Reboot: the monotonic clock restarts near zero while wall time is intact.
        time.elapsedNow = 1_000L
        FocusTimer.init(store, time)

        assertTrue(FocusTimer.running.value)
        assertTrue(FocusTimer.commitment.value.active)
        assertTrue(FocusTimer.commitmentLocked(wallAtReboot, 1_000L))
        assertEquals(60, FocusTimer.elapsedSeconds.intValue)
        assertEquals(240_000L, FocusTimer.commitmentRemainingMs(wallAtReboot, 1_000L))
    }

    // ---- Pomodoro --------------------------------------------------------

    @Test
    fun pomodoroCommitmentRejectsLongerThanFocus() {
        FocusTimer.selectMode(FocusTimer.Mode.POMODORO)
        FocusTimer.setPomodoroFocusMinutes(5)
        FocusTimer.setPomodoroHardMode(true)
        FocusTimer.setPomodoroCommitMinutes(10)

        assertNotNull(FocusTimer.startBlockReason())
        assertFalse(FocusTimer.start(wall()))
        assertFalse(FocusTimer.running.value)
    }

    @Test
    fun pomodoroCommitmentUnlocksAtBreakAndIsNotCarriedIntoIt() {
        FocusTimer.selectMode(FocusTimer.Mode.POMODORO)
        FocusTimer.setPomodoroFocusMinutes(1)
        FocusTimer.setPomodoroBreakMinutes(1)
        FocusTimer.setPomodoroCycles(3)
        FocusTimer.setPomodoroHardMode(true)
        FocusTimer.setPomodoroCommitMinutes(1)
        FocusTimer.start(wall())

        assertTrue(FocusTimer.commitmentLocked(wall(), elapsed()))

        // Focus interval ends -> break begins, commitment is released.
        time.advance(60_000L)
        FocusTimer.advanceTimers(wall(), elapsed())

        assertEquals(FocusTimer.Phase.BREAK, FocusTimer.phase.value)
        assertFalse(FocusTimer.commitment.value.active)
        assertFalse(FocusTimer.commitmentLocked(wall(), elapsed()))

        // Break is never locked: pause is allowed.
        FocusTimer.pause()
        assertFalse(FocusTimer.running.value)
    }

    @Test
    fun pomodoroCommitmentShorterThanFocusStillLocksUntilElapsed() {
        FocusTimer.selectMode(FocusTimer.Mode.POMODORO)
        FocusTimer.setPomodoroFocusMinutes(5)
        FocusTimer.setPomodoroBreakMinutes(1)
        FocusTimer.setPomodoroCycles(2)
        FocusTimer.setPomodoroHardMode(true)
        FocusTimer.setPomodoroCommitMinutes(2)
        FocusTimer.start(wall())

        time.advance(120_000L)
        FocusTimer.advanceTimers(wall(), elapsed())
        // Work session still running, commitment already satisfied.
        assertEquals(FocusTimer.Phase.FOCUS, FocusTimer.phase.value)
        assertTrue(FocusTimer.running.value)
        assertFalse(FocusTimer.commitmentLocked(wall(), elapsed()))
        assertEquals(FocusTimer.SessionState.RUNNING_UNLOCKED, FocusTimer.sessionState(wall(), elapsed()))
    }

    // ---- Cross-cutting ---------------------------------------------------

    @Test
    fun lockNoticeExplainsTheRemainingCommitment() {
        FocusTimer.selectMode(FocusTimer.Mode.STOPWATCH)
        FocusTimer.setStopwatchHardMode(true)
        FocusTimer.setStopwatchCommitMinutes(1)
        FocusTimer.start(wall())

        time.advance(30_000L)
        val notice = FocusTimer.lockNotice(wall(), elapsed())
        assertTrue(notice.contains("Hard Mode is locked"))
        assertTrue(notice.contains("00:30"))
    }

    @Test
    fun countdownHardFocusIsUnaffectedByHardModeCommitment() {
        // Countdown Hard Focus still uses its own wall-clock session lock.
        FocusTimer.setHardMode(true)
        FocusTimer.selectMode(FocusTimer.Mode.COUNTDOWN)
        FocusTimer.setCountdownMinutes(25)
        FocusTimer.start(wall())

        assertTrue(FocusTimer.isLocked(wall()))
        assertTrue(FocusTimer.sessionActive.value)
        // No commitment is created for the countdown path.
        assertFalse(FocusTimer.commitment.value.active)
        assertTrue(FocusTimer.isSessionLocked(wall(), elapsed()))
    }
}
