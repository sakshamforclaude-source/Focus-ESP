package com.example.focusesp

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * Persisted, observable owner of the always-on restriction unlock commitment.
 *
 * This is the single shared component through which every restriction change
 * is validated. It does **not** depend on Focus Mode, the focus timer or any
 * focus-session lifecycle: the appointment lives in its own storage keys and
 * is reconciled from timestamps on every read.
 *
 * [canWeaken] is the one rule all disabling code paths call. The stateful
 * setters in [ContentRestrictionStore] and the accessibility service all defer
 * to it, so hiding a control in the UI is never the only protection.
 */
object ContentRestrictionCommitment {

    private const val KEY_REQUESTED_EPOCH = "restrict_appt_requested_epoch"
    private const val KEY_REQUESTED_ELAPSED = "restrict_appt_requested_elapsed"
    private const val KEY_CONFIRMED_EPOCH = "restrict_appt_confirmed_epoch"

    /** Injectable clocks for deterministic unit tests. */
    internal var timeSource: TimeSource = SystemTimeSource

    private var store: KeyValueStore? = null

    val appointment = mutableStateOf(RestrictionCommitment.Appointment.None)

    fun init(context: Context) {
        LocalStore.init(context)
        init(LocalStore)
    }

    fun init(store: KeyValueStore, timeSource: TimeSource = SystemTimeSource) {
        this.store = store
        this.timeSource = timeSource
        appointment.value = RestrictionCommitment.Appointment(
            requestedEpochMs = store.getLong(KEY_REQUESTED_EPOCH, 0L),
            requestedElapsedMs = store.getLong(KEY_REQUESTED_ELAPSED, 0L),
            confirmedEpochMs = store.getLong(KEY_CONFIRMED_EPOCH, 0L)
        )
    }

    fun nowEpochMs(): Long = timeSource.wallClockMs()

    fun nowElapsedMs(): Long = timeSource.elapsedRealtimeMs()

    fun state(
        nowEpochMs: Long = nowEpochMs(),
        nowElapsedMs: Long = nowElapsedMs()
    ): RestrictionCommitment.State =
        RestrictionCommitment.state(appointment.value, nowEpochMs, nowElapsedMs)

    fun canRequest(
        nowEpochMs: Long = nowEpochMs(),
        nowElapsedMs: Long = nowElapsedMs()
    ): Boolean = RestrictionCommitment.canRequest(appointment.value, nowEpochMs, nowElapsedMs)

    /** Files a six-hour appointment. Returns false when one is already pending. */
    fun request(): Boolean {
        val next = RestrictionCommitment.request(appointment.value, nowEpochMs(), nowElapsedMs())
            ?: return false
        appointment.value = next
        persist()
        return true
    }

    fun canConfirm(
        nowEpochMs: Long = nowEpochMs(),
        nowElapsedMs: Long = nowElapsedMs()
    ): Boolean = RestrictionCommitment.canConfirm(appointment.value, nowEpochMs, nowElapsedMs)

    /** Confirms inside the open window. Returns false outside the exact window. */
    fun confirm(): Boolean {
        val next = RestrictionCommitment.confirm(appointment.value, nowEpochMs(), nowElapsedMs())
            ?: return false
        appointment.value = next
        persist()
        return true
    }

    /**
     * The single central validation rule: only a confirmed, still-open window
     * permits a restriction to be weakened (disabled or made less strict).
     */
    fun canWeaken(
        nowEpochMs: Long = nowEpochMs(),
        nowElapsedMs: Long = nowElapsedMs()
    ): Boolean = RestrictionCommitment.canWeaken(appointment.value, nowEpochMs, nowElapsedMs)

    fun waitRemainingMs(): Long =
        RestrictionCommitment.waitRemainingMs(appointment.value, nowEpochMs(), nowElapsedMs())

    fun windowRemainingMs(): Long =
        RestrictionCommitment.windowRemainingMs(appointment.value, nowEpochMs(), nowElapsedMs())

    fun windowEndEpochMs(): Long = RestrictionCommitment.windowEndEpochMs(appointment.value)

    fun appointmentEpochMs(): Long = RestrictionCommitment.appointmentEpochMs(appointment.value)

    /**
     * Re-evaluates the persisted appointment against the current clocks and
     * keeps the reminder alarm in sync. State is always derived from the
     * timestamps, so this is safe and idempotent to call often.
     */
    fun refresh(context: Context? = null) {
        if (context != null) syncReminder(context)
    }

    /** (Re)arms the reminder notification when waiting; clears it otherwise. */
    fun syncReminder(context: Context) {
        when (state()) {
            RestrictionCommitment.State.WAITING ->
                RestrictionUnlockAlarm.schedule(context, appointmentEpochMs())
            else ->
                RestrictionUnlockAlarm.cancel(context)
        }
    }

    private fun persist() {
        val s = store ?: return
        s.putLong(KEY_REQUESTED_EPOCH, appointment.value.requestedEpochMs)
        s.putLong(KEY_REQUESTED_ELAPSED, appointment.value.requestedElapsedMs)
        s.putLong(KEY_CONFIRMED_EPOCH, appointment.value.confirmedEpochMs)
    }
}
