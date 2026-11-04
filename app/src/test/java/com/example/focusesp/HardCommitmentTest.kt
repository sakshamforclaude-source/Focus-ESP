package com.example.focusesp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardCommitmentTest {

    private val session = HardCommitment.Session(
        active = true,
        startEpochMs = 1_000L,
        startElapsedMs = 5_000L,
        durationMs = 60_000L
    )

    @Test
    fun elapsedAndRemainingUseTheMonotonicClock() {
        // Wall clock jumps forward by an hour; only monotonic time should count.
        assertEquals(10_000L, HardCommitment.elapsedMs(session, 1_000L + 3_600_000L, 15_000L))
        assertEquals(50_000L, HardCommitment.remainingMs(session, 1_000L + 3_600_000L, 15_000L))
    }

    @Test
    fun remainsLockedUntilTheThresholdIsReached() {
        assertTrue(HardCommitment.isLocked(session, 1_000L, 64_999L))
        assertFalse(HardCommitment.isLocked(session, 1_000L, 65_000L))
        assertTrue(HardCommitment.hasCompleted(session, 1_000L, 65_000L))
    }

    @Test
    fun stateTransitionsFromLockedToUnlocked() {
        assertEquals(HardCommitment.State.LOCKED, HardCommitment.state(session, 1_000L, 5_000L))
        assertEquals(HardCommitment.State.UNLOCKED, HardCommitment.state(session, 1_000L, 65_000L))
        assertEquals(HardCommitment.State.INACTIVE, HardCommitment.state(HardCommitment.None, 0L, 0L))
    }

    @Test
    fun monotonicDeltaFallsBackToWallClockWhenMonotonicResets() {
        // Reboot: the monotonic reading is before the anchor.
        assertEquals(
            120_000L,
            HardCommitment.monotonicDelta(1_000L, 5_000L, 121_000L, 1_000L)
        )
    }

    @Test
    fun reconcileRebasesAnchorAfterRebootAndIsIdempotent() {
        // 30 seconds of wall time passed before the reboot reset the monotonic clock.
        val reconciled = HardCommitment.reconcile(session, 31_000L, 900L)
        assertEquals(900L - 30_000L, reconciled.startElapsedMs)
        assertTrue(HardCommitment.isLocked(reconciled, 31_000L, 900L))
        assertEquals(30_000L, HardCommitment.elapsedMs(reconciled, 31_000L, 900L))

        // Once rebased, further reconciliation is a no-op.
        assertEquals(reconciled, HardCommitment.reconcile(reconciled, 130_000L, 8_900L))
    }

    @Test
    fun ceilSecondsRoundsUpAndNeverGoesNegative() {
        assertEquals(0, HardCommitment.ceilSeconds(-5L))
        assertEquals(1, HardCommitment.ceilSeconds(1L))
        assertEquals(1, HardCommitment.ceilSeconds(1_000L))
        assertEquals(2, HardCommitment.ceilSeconds(1_001L))
    }

    @Test
    fun validationRejectsNonPositiveCommitments() {
        assertEquals(null, HardCommitment.validateMinutes(1))
        assertTrue(HardCommitment.validateMinutes(0) != null)
        assertTrue(HardCommitment.validateMinutes(-3) != null)
    }

    @Test
    fun pomodoroValidationRejectsCommitmentLongerThanFocus() {
        assertEquals(null, HardCommitment.validatePomodoro(25, 10))
        // A commitment equal to the whole focus session is allowed.
        assertEquals(null, HardCommitment.validatePomodoro(25, 25))
        assertTrue(HardCommitment.validatePomodoro(10, 20) != null)
    }
}
