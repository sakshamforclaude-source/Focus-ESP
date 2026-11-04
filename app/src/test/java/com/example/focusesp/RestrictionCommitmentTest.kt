package com.example.focusesp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure state-machine tests for the six-hour unlock commitment.
 *
 * All boundaries are exercised with explicit clocks; no test waits six hours.
 */
class RestrictionCommitmentTest {

    private val delay = RestrictionCommitment.UNLOCK_DELAY_MS
    private val window = RestrictionCommitment.UNLOCK_WINDOW_MS
    private val t0 = 1_000_000L
    private val e0 = 5_000L

    private fun request(now: Long = t0, elapsed: Long = e0) =
        RestrictionCommitment.request(RestrictionCommitment.Appointment.None, now, elapsed)

    private fun state(appt: RestrictionCommitment.Appointment, now: Long, elapsed: Long) =
        RestrictionCommitment.state(appt, now, elapsed)

    @Test
    fun noAppointmentIsLocked() {
        assertEquals(
            RestrictionCommitment.State.LOCKED,
            state(RestrictionCommitment.Appointment.None, t0, e0)
        )
        assertTrue(RestrictionCommitment.canRequest(RestrictionCommitment.Appointment.None, t0, e0))
    }

    @Test
    fun requestSchedulesExactlySixHoursAhead() {
        val appt = request()!!
        assertEquals(t0 + delay, RestrictionCommitment.appointmentEpochMs(appt))
        assertEquals(RestrictionCommitment.State.WAITING, state(appt, t0 + delay - 1, e0 + delay - 1))
    }

    @Test
    fun windowOpensExactlyAtAppointmentAndLastsSixtySeconds() {
        val appt = request()!!
        // Exactly at the appointment the window is open.
        assertEquals(
            RestrictionCommitment.State.UNLOCK_WINDOW,
            state(appt, t0 + delay, e0 + delay)
        )
        // One millisecond before the end of the window it is still open.
        assertEquals(
            RestrictionCommitment.State.UNLOCK_WINDOW,
            state(appt, t0 + delay + window - 1, e0 + delay + window - 1)
        )
        // Exactly at the expiry boundary it is missed.
        assertEquals(
            RestrictionCommitment.State.MISSED,
            state(appt, t0 + delay + window, e0 + delay + window)
        )
    }

    @Test
    fun confirmSucceedsInsideWindow() {
        val appt = request()!!
        assertFalse(RestrictionCommitment.canConfirm(appt, t0 + delay - 1, e0 + delay - 1))
        val confirmed = RestrictionCommitment.confirm(appt, t0 + delay + 30_000L, e0 + delay + 30_000L)
        assertNotNull(confirmed)
        assertEquals(
            RestrictionCommitment.State.CONFIRMED,
            state(confirmed!!, t0 + delay + 30_000L, e0 + delay + 30_000L)
        )
        assertTrue(RestrictionCommitment.canWeaken(confirmed, t0 + delay + 30_000L, e0 + delay + 30_000L))
    }

    @Test
    fun confirmAtOrAfterExpiryBoundaryIsRejected() {
        val appt = request()!!
        // Exactly at the boundary.
        assertFalse(RestrictionCommitment.canConfirm(appt, t0 + delay + window, e0 + delay + window))
        assertNull(RestrictionCommitment.confirm(appt, t0 + delay + window, e0 + delay + window))
        // After the boundary.
        assertNull(
            RestrictionCommitment.confirm(
                appt,
                t0 + delay + window + 1,
                e0 + delay + window + 1
            )
        )
    }

    @Test
    fun confirmationCannotExtendTheWindow() {
        val appt = request()!!
        val confirmed = RestrictionCommitment.confirm(appt, t0 + delay + 40_000L, e0 + delay + 40_000L)!!
        // 20 seconds of the original window remain, no fresh minute.
        assertEquals(
            20_000L,
            RestrictionCommitment.windowRemainingMs(confirmed, t0 + delay + 40_000L, e0 + delay + 40_000L)
        )
        // At the original boundary the window is closed despite the confirmation.
        assertFalse(RestrictionCommitment.canWeaken(confirmed, t0 + delay + window, e0 + delay + window))
        assertEquals(
            RestrictionCommitment.State.LOCKED,
            state(confirmed, t0 + delay + window, e0 + delay + window)
        )
    }

    @Test
    fun missedAppointmentRequiresANewRequest() {
        val appt = request()!!
        val missedAt = t0 + delay + window + 1
        assertEquals(RestrictionCommitment.State.MISSED, state(appt, missedAt, e0 + delay + window + 1))
        // A new request is allowed and is scheduled six hours from *now*.
        val fresh = RestrictionCommitment.request(appt, missedAt, e0 + delay + window + 1)
        assertNotNull(fresh)
        assertEquals(missedAt + delay, RestrictionCommitment.appointmentEpochMs(fresh!!))
    }

    @Test
    fun cannotRequestWhilePending() {
        val appt = request()!!
        assertFalse(RestrictionCommitment.canRequest(appt, t0 + 1_000L, e0 + 1_000L))
        assertNull(RestrictionCommitment.request(appt, t0 + 1_000L, e0 + 1_000L))
        // Even inside the open window a second request is refused.
        assertNull(RestrictionCommitment.request(appt, t0 + delay + 10_000L, e0 + delay + 10_000L))
    }

    @Test
    fun forwardClockJumpCannotOpenWindowEarly() {
        val appt = request()!!
        // Wall clock jumps forward six hours, but the monotonic clock barely moved.
        assertEquals(RestrictionCommitment.State.WAITING, state(appt, t0 + delay, e0 + 1_000L))
        assertFalse(RestrictionCommitment.canConfirm(appt, t0 + delay, e0 + 1_000L))
    }

    @Test
    fun rebootFallsBackToWallClock() {
        val appt = request()!!
        // After a reboot the monotonic reading is behind the stored anchor, so the
        // wall clock is authoritative and the appointment is still in the future.
        assertEquals(
            RestrictionCommitment.State.WAITING,
            state(appt, t0 + 1_000L, e0 - 500_000L)
        )
        assertEquals(
            RestrictionCommitment.State.UNLOCK_WINDOW,
            state(appt, t0 + delay + 5_000L, e0 - 500_000L)
        )
    }

    @Test
    fun delayedEvaluationAfterWindowStaysMissed() {
        val appt = request()!!
        // Evaluate long after the window (e.g. a delayed notification or process
        // restart). The state must be MISSED, never UNLOCK_WINDOW.
        val late = t0 + delay + window + 3 * 60 * 60 * 1000L
        assertEquals(RestrictionCommitment.State.MISSED, state(appt, late, e0 + delay + window + 3 * 60 * 60 * 1000L))
        assertFalse(RestrictionCommitment.canConfirm(appt, late, e0 + delay + window + 3 * 60 * 60 * 1000L))
    }
}
