package com.example.focusesp

/**
 * Abstraction over the two clocks FocusESP needs.
 *
 * - [elapsedRealtimeMs] is monotonic (counts since boot, including deep sleep)
 *   and is the primary source for measuring elapsed/remaining time so that
 *   timer drift and UI refresh delays cannot distort a session.
 * - [wallClockMs] is persisted alongside it so a session can still be
 *   reconciled after a device reboot, when the monotonic clock resets.
 *
 * Injectable so the whole commitment/timer model is unit-testable on the JVM.
 */
interface TimeSource {
    fun elapsedRealtimeMs(): Long
    fun wallClockMs(): Long
}

object SystemTimeSource : TimeSource {
    override fun elapsedRealtimeMs(): Long = android.os.SystemClock.elapsedRealtime()
    override fun wallClockMs(): Long = System.currentTimeMillis()
}

/**
 * Pure minimum-commitment model shared by Stopwatch and Pomodoro Hard Mode.
 *
 * A [Session] records when the commitment began on **both** clocks plus the
 * committed duration. All queries take the current reading of both clocks, so
 * the same persisted session can be evaluated after UI delays, backgrounding,
 * process death, or a reboot. There is intentionally no per-second counter.
 */
object HardCommitment {

    enum class State { INACTIVE, LOCKED, UNLOCKED, COMPLETED }

    data class Session(
        val active: Boolean,
        val startEpochMs: Long,
        val startElapsedMs: Long,
        val durationMs: Long
    )

    val None = Session(active = false, startEpochMs = 0L, startElapsedMs = 0L, durationMs = 0L)

    fun start(nowEpochMs: Long, nowElapsedMs: Long, durationMs: Long): Session {
        require(durationMs > 0L) { "commitment duration must be positive" }
        return Session(true, nowEpochMs, nowElapsedMs, durationMs)
    }

    /**
     * Monotonic delta with a wall-clock fallback. The fallback triggers only
     * when the monotonic reading is before the anchor, which happens after a
     * reboot (the monotonic clock restarts) or if the anchor came from a
     * previous boot and has not yet been reconciled.
     */
    fun monotonicDelta(
        startEpochMs: Long,
        startElapsedMs: Long,
        nowEpochMs: Long,
        nowElapsedMs: Long
    ): Long {
        val monotonic = nowElapsedMs - startElapsedMs
        return if (monotonic >= 0L) monotonic else (nowEpochMs - startEpochMs).coerceAtLeast(0L)
    }

    fun elapsedMs(session: Session, nowEpochMs: Long, nowElapsedMs: Long): Long {
        if (!session.active) return 0L
        return monotonicDelta(session.startEpochMs, session.startElapsedMs, nowEpochMs, nowElapsedMs)
    }

    fun remainingMs(session: Session, nowEpochMs: Long, nowElapsedMs: Long): Long {
        if (!session.active) return 0L
        return (session.durationMs - elapsedMs(session, nowEpochMs, nowElapsedMs)).coerceAtLeast(0L)
    }

    fun isLocked(session: Session, nowEpochMs: Long, nowElapsedMs: Long): Boolean =
        session.active && elapsedMs(session, nowEpochMs, nowElapsedMs) < session.durationMs

    fun hasCompleted(session: Session, nowEpochMs: Long, nowElapsedMs: Long): Boolean =
        session.active && !isLocked(session, nowEpochMs, nowElapsedMs)

    fun state(session: Session, nowEpochMs: Long, nowElapsedMs: Long): State = when {
        !session.active -> State.INACTIVE
        isLocked(session, nowEpochMs, nowElapsedMs) -> State.LOCKED
        else -> State.UNLOCKED
    }

    /**
     * Rebases a persisted session onto the current boot's monotonic clock.
     *
     * If the stored monotonic anchor is *ahead* of the current reading, the
     * device rebooted. The wall-clock anchors are still valid, so the anchor is
     * rewritten so that the monotonic delta equals the wall-clock delta. Safe
     * and idempotent within a boot.
     */
    fun reconcile(session: Session, nowEpochMs: Long, nowElapsedMs: Long): Session {
        if (!session.active) return session
        return session.copy(
            startElapsedMs = rebaseAnchor(
                session.startEpochMs, session.startElapsedMs, nowEpochMs, nowElapsedMs
            )
        )
    }

    /**
     * Rebases any monotonic anchor onto the current boot. When the stored
     * monotonic reading is ahead of the current one the device rebooted, so the
     * wall-clock anchors are used to reconstruct the equivalent monotonic value.
     * Safe and idempotent within a boot.
     */
    fun rebaseAnchor(
        startEpochMs: Long,
        startElapsedMs: Long,
        nowEpochMs: Long,
        nowElapsedMs: Long
    ): Long {
        if (startElapsedMs <= nowElapsedMs) return startElapsedMs
        val elapsedBefore = (nowEpochMs - startEpochMs).coerceAtLeast(0L)
        return nowElapsedMs - elapsedBefore
    }

    fun ceilSeconds(millis: Long): Int = ((millis.coerceAtLeast(0L) + 999L) / 1000L).toInt()

    // ---- Validation ------------------------------------------------------

    /** Returns an error message, or null when the commitment is usable. */
    fun validateMinutes(minutes: Int): String? =
        if (minutes <= 0) "Choose a minimum commitment of at least 1 minute." else null

    /**
     * Pomodoro-specific check: the commitment is a threshold within the focus
     * session, so it may be shorter but never longer than the focus duration.
     */
    fun validatePomodoro(focusMinutes: Int, commitmentMinutes: Int): String? {
        validateMinutes(commitmentMinutes)?.let { return it }
        if (commitmentMinutes > focusMinutes) {
            return "The minimum commitment ($commitmentMinutes min) cannot exceed the " +
                "focus session ($focusMinutes min)."
        }
        return null
    }
}
