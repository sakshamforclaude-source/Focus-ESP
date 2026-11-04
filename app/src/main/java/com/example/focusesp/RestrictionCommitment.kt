package com.example.focusesp

/**
 * Pure, deterministic state machine for the always-on restriction unlock
 * commitment.
 *
 * The three content-restriction features (YouTube Shorts, Instagram Reels and
 * the whole-app fallback) can always be *enabled*, but they can only be
 * *disabled* during a short, user-confirmed unlock window that opens exactly
 * six hours after the user files an unlock request.
 *
 * State machine:
 *
 *  - [State.LOCKED]        No usable appointment. Weakening is impossible.
 *  - [State.WAITING]       An appointment is pending; the six hours have not elapsed.
 *  - [State.UNLOCK_WINDOW] The 60-second window is open; the user may confirm.
 *  - [State.CONFIRMED]     The user confirmed inside the window; weakening is
 *                          allowed until the window closes.
 *  - [State.MISSED]        The window closed without confirmation. A brand new
 *                          six-hour request is required.
 *
 * Everything is derived from persisted timestamps (wall clock **and** a
 * monotonic anchor), never from an in-memory countdown. Reading state is a
 * pure function of [Appointment] plus the current clocks, so a delayed alarm,
 * a backgrounded process, a reboot or a process restart can never open an
 * earlier or extended window.
 *
 * Clock-change handling: while the monotonic anchor is valid for the current
 * boot, the elapsed time is the *minimum* of the wall-clock delta and the
 * monotonic delta. Moving the wall clock forward therefore cannot open the
 * window early. After a reboot the monotonic clock resets and the wall clock
 * becomes authoritative (see docs for the honest limitations).
 */
object RestrictionCommitment {

    /** Exact delay between requesting and the appointment. Six hours. */
    const val UNLOCK_DELAY_MS = 6L * 60L * 60L * 1000L

    /** Exact length of the manual confirmation window. Sixty seconds. */
    const val UNLOCK_WINDOW_MS = 60L * 1000L

    enum class State { LOCKED, WAITING, UNLOCK_WINDOW, CONFIRMED, MISSED }

    data class Appointment(
        val requestedEpochMs: Long,
        val requestedElapsedMs: Long,
        val confirmedEpochMs: Long
    ) {
        val exists: Boolean get() = requestedEpochMs > 0L

        companion object {
            val None = Appointment(0L, 0L, 0L)
        }
    }

    /**
     * Wall-clock time the user will see as the appointment. This is a display
     * value; eligibility is computed from [elapsedMs], not from this alone.
     */
    fun appointmentEpochMs(appointment: Appointment): Long =
        if (appointment.exists) appointment.requestedEpochMs + UNLOCK_DELAY_MS else 0L

    /**
     * Time elapsed since the request began. Uses the monotonic clock while the
     * anchor is valid for this boot, capped by the wall clock so a forward clock
     * jump cannot unlock early. Falls back to the wall clock after a reboot.
     */
    fun elapsedMs(appointment: Appointment, nowEpochMs: Long, nowElapsedMs: Long): Long {
        if (!appointment.exists) return 0L
        val byWall = nowEpochMs - appointment.requestedEpochMs
        val byMonotonic = if (appointment.requestedElapsedMs in 1..nowElapsedMs) {
            nowElapsedMs - appointment.requestedElapsedMs
        } else {
            null
        }
        return if (byMonotonic != null) minOf(byWall, byMonotonic) else byWall
    }

    fun state(
        appointment: Appointment,
        nowEpochMs: Long,
        nowElapsedMs: Long
    ): State {
        if (!appointment.exists) return State.LOCKED
        val elapsed = elapsedMs(appointment, nowEpochMs, nowElapsedMs)
        if (appointment.confirmedEpochMs > 0L) {
            return if (elapsed < UNLOCK_DELAY_MS + UNLOCK_WINDOW_MS) {
                State.CONFIRMED
            } else {
                State.LOCKED
            }
        }
        return when {
            elapsed < UNLOCK_DELAY_MS -> State.WAITING
            elapsed < UNLOCK_DELAY_MS + UNLOCK_WINDOW_MS -> State.UNLOCK_WINDOW
            else -> State.MISSED
        }
    }

    /** A new appointment may only be filed when none is pending or usable. */
    fun canRequest(appointment: Appointment, nowEpochMs: Long, nowElapsedMs: Long): Boolean =
        when (state(appointment, nowEpochMs, nowElapsedMs)) {
            State.WAITING, State.UNLOCK_WINDOW, State.CONFIRMED -> false
            State.LOCKED, State.MISSED -> true
        }

    /**
     * Files a new appointment at [nowEpochMs] + [UNLOCK_DELAY_MS], or returns
     * null when one is already pending. The monotonic anchor is stored so the
     * wait survives clock changes within the boot.
     */
    fun request(
        appointment: Appointment,
        nowEpochMs: Long,
        nowElapsedMs: Long
    ): Appointment? {
        if (!canRequest(appointment, nowEpochMs, nowElapsedMs)) return null
        return Appointment(
            requestedEpochMs = nowEpochMs,
            requestedElapsedMs = nowElapsedMs,
            confirmedEpochMs = 0L
        )
    }

    /** Confirmation is only valid inside the open, unconfirmed 60-second window. */
    fun canConfirm(appointment: Appointment, nowEpochMs: Long, nowElapsedMs: Long): Boolean =
        appointment.exists &&
            appointment.confirmedEpochMs <= 0L &&
            state(appointment, nowEpochMs, nowElapsedMs) == State.UNLOCK_WINDOW

    /**
     * Confirms the appointment. The returned record keeps the *original*
     * appointment markers, so confirming never extends the window: editing ends
     * at [windowEndEpochMs] regardless of when the confirmation happened.
     */
    fun confirm(
        appointment: Appointment,
        nowEpochMs: Long,
        nowElapsedMs: Long
    ): Appointment? {
        if (!canConfirm(appointment, nowEpochMs, nowElapsedMs)) return null
        return appointment.copy(confirmedEpochMs = nowEpochMs)
    }

    /** True only while a confirmed window is still open. Central weakening rule. */
    fun canWeaken(appointment: Appointment, nowEpochMs: Long, nowElapsedMs: Long): Boolean =
        state(appointment, nowEpochMs, nowElapsedMs) == State.CONFIRMED

    /** Wall-clock close time of the window, whether or not it was confirmed. */
    fun windowEndEpochMs(appointment: Appointment): Long =
        if (appointment.exists) appointment.requestedEpochMs + UNLOCK_DELAY_MS + UNLOCK_WINDOW_MS else 0L

    /** Milliseconds remaining until the appointment opens (0 once reached). */
    fun waitRemainingMs(appointment: Appointment, nowEpochMs: Long, nowElapsedMs: Long): Long =
        (UNLOCK_DELAY_MS - elapsedMs(appointment, nowEpochMs, nowElapsedMs)).coerceAtLeast(0L)

    /** Milliseconds remaining in the open window (0 when closed or not open). */
    fun windowRemainingMs(appointment: Appointment, nowEpochMs: Long, nowElapsedMs: Long): Long {
        if (state(appointment, nowEpochMs, nowElapsedMs) !in
            listOf(State.UNLOCK_WINDOW, State.CONFIRMED)
        ) {
            return 0L
        }
        return (UNLOCK_DELAY_MS + UNLOCK_WINDOW_MS - elapsedMs(appointment, nowEpochMs, nowElapsedMs))
            .coerceAtLeast(0L)
    }
}
