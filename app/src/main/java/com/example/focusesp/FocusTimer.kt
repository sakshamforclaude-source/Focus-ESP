package com.example.focusesp

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

object FocusTimer {

    enum class Mode { STOPWATCH, COUNTDOWN, POMODORO }
    enum class Phase { IDLE, FOCUS, BREAK, DONE }

    /**
     * Overall session state, including the Hard Mode commitment lock.
     * See the feature's state model: IDLE, RUNNING_LOCKED, RUNNING_UNLOCKED, COMPLETED.
     */
    enum class SessionState { IDLE, RUNNING_LOCKED, RUNNING_UNLOCKED, COMPLETED }

    private var lastTickAt = 0L
    private var appContext: Context? = null
    private var store: KeyValueStore? = null

    /** Monotonic + wall clocks; injectable for deterministic unit tests. */
    internal var timeSource: TimeSource = SystemTimeSource

    private const val KEY_COUNTDOWN = "timer_countdown_min"
    private const val KEY_POMODORO_FOCUS = "timer_pomodoro_focus"
    private const val KEY_POMODORO_BREAK = "timer_pomodoro_break"
    private const val KEY_POMODORO_CYCLES = "timer_pomodoro_cycles"
    private const val KEY_HARD_MODE = "timer_hard_mode"
    private const val KEY_MODE = "timer_mode"
    private const val KEY_PHASE = "timer_phase"
    private const val KEY_SESSION_ACTIVE = "timer_session_active"
    private const val KEY_SESSION_END = "timer_session_end"
    private const val KEY_REMAINING = "timer_remaining"

    // Hard Mode (Stopwatch / Pomodoro commitment)
    private const val KEY_STOPWATCH_HARD = "timer_stopwatch_hard"
    private const val KEY_STOPWATCH_COMMIT = "timer_stopwatch_commit"
    private const val KEY_POMODORO_HARD = "timer_pomodoro_hard"
    private const val KEY_POMODORO_COMMIT = "timer_pomodoro_commit"
    private const val KEY_TIMER_RUNNING = "timer_running"
    private const val KEY_COMMIT_ACTIVE = "timer_commit_active"
    private const val KEY_COMMIT_START_EPOCH = "timer_commit_start_epoch"
    private const val KEY_COMMIT_START_ELAPSED = "timer_commit_start_elapsed"
    private const val KEY_COMMIT_DURATION = "timer_commit_duration"
    private const val KEY_COMMIT_MODE = "timer_commit_mode"
    private const val KEY_SW_START_EPOCH = "timer_sw_start_epoch"
    private const val KEY_SW_START_ELAPSED = "timer_sw_start_elapsed"
    private const val KEY_POM_START_EPOCH = "timer_pom_start_epoch"
    private const val KEY_POM_START_ELAPSED = "timer_pom_start_elapsed"
    private const val KEY_POM_DURATION = "timer_pom_duration"

    val mode = mutableStateOf(Mode.STOPWATCH)
    val running = mutableStateOf(false)
    val phase = mutableStateOf(Phase.IDLE)
    val elapsedSeconds = mutableIntStateOf(0)
    val remainingSeconds = mutableIntStateOf(0)
    val completedCycles = mutableIntStateOf(0)

    /** Hard mode for the countdown: an active countdown cannot be paused or stopped until it ends. */
    val hardMode = mutableStateOf(false)

    /** True while a hard-mode countdown session is in progress (persisted). */
    val sessionActive = mutableStateOf(false)

    /** Wall-clock end time (millis) of the hard-mode countdown session, persisted across restarts. */
    val sessionEndEpoch = mutableStateOf(0L)

    val countdownMinutes = mutableIntStateOf(25)
    val pomodoroFocusMinutes = mutableIntStateOf(25)
    val pomodoroBreakMinutes = mutableIntStateOf(5)
    val pomodoroCycles = mutableIntStateOf(4)

    // ---- Hard Mode (commitment) configuration ----------------------------

    /** Hard Mode for the Stopwatch: stop/reset are locked until the commitment elapses. */
    val stopwatchHardMode = mutableStateOf(false)
    val stopwatchCommitMinutes = mutableIntStateOf(30)

    /** Hard Mode for Pomodoro: the active work interval is committed before it can be stopped. */
    val pomodoroHardMode = mutableStateOf(false)
    val pomodoroCommitMinutes = mutableIntStateOf(10)

    /** The active commitment (if any). Read by the UI to show the lock and remaining time. */
    val commitment = mutableStateOf(HardCommitment.None)

    /** Which mode owns the active commitment (used to restore after process death). */
    private var commitmentMode = Mode.POMODORO

    // Monotonic + wall anchors for the interval timers.
    private var stopwatchStartEpoch = 0L
    private var stopwatchStartElapsed = 0L
    private var pomodoroPhaseStartEpoch = 0L
    private var pomodoroPhaseStartElapsed = 0L
    private var pomodoroPhaseDurationMs = 0L

    fun init(context: Context) {
        appContext = context.applicationContext
        LocalStore.init(context)
        init(LocalStore)
    }

    fun init(store: KeyValueStore) {
        init(store, SystemTimeSource)
    }

    fun init(store: KeyValueStore, timeSource: TimeSource) {
        this.store = store
        this.timeSource = timeSource

        countdownMinutes.intValue = store.getInt(KEY_COUNTDOWN, 25)
        pomodoroFocusMinutes.intValue = store.getInt(KEY_POMODORO_FOCUS, 25)
        pomodoroBreakMinutes.intValue = store.getInt(KEY_POMODORO_BREAK, 5)
        pomodoroCycles.intValue = store.getInt(KEY_POMODORO_CYCLES, 4)
        hardMode.value = store.getBoolean(KEY_HARD_MODE, false)
        stopwatchHardMode.value = store.getBoolean(KEY_STOPWATCH_HARD, false)
        stopwatchCommitMinutes.intValue = store.getInt(KEY_STOPWATCH_COMMIT, 30)
        pomodoroHardMode.value = store.getBoolean(KEY_POMODORO_HARD, false)
        pomodoroCommitMinutes.intValue = store.getInt(KEY_POMODORO_COMMIT, 10)
        mode.value = runCatching {
            Mode.valueOf(store.getString(KEY_MODE, Mode.STOPWATCH.name))
        }.getOrDefault(Mode.STOPWATCH)
        phase.value = runCatching {
            Phase.valueOf(store.getString(KEY_PHASE, Phase.IDLE.name))
        }.getOrDefault(Phase.IDLE)
        sessionActive.value = store.getBoolean(KEY_SESSION_ACTIVE, false)
        sessionEndEpoch.value = store.getLong(KEY_SESSION_END, 0L)

        commitmentMode = runCatching {
            Mode.valueOf(store.getString(KEY_COMMIT_MODE, Mode.POMODORO.name))
        }.getOrDefault(Mode.POMODORO)
        commitment.value = HardCommitment.Session(
            active = store.getBoolean(KEY_COMMIT_ACTIVE, false),
            startEpochMs = store.getLong(KEY_COMMIT_START_EPOCH, 0L),
            startElapsedMs = store.getLong(KEY_COMMIT_START_ELAPSED, 0L),
            durationMs = store.getLong(KEY_COMMIT_DURATION, 0L)
        )
        stopwatchStartEpoch = store.getLong(KEY_SW_START_EPOCH, 0L)
        stopwatchStartElapsed = store.getLong(KEY_SW_START_ELAPSED, 0L)
        pomodoroPhaseStartEpoch = store.getLong(KEY_POM_START_EPOCH, 0L)
        pomodoroPhaseStartElapsed = store.getLong(KEY_POM_START_ELAPSED, 0L)
        pomodoroPhaseDurationMs = store.getLong(KEY_POM_DURATION, 0L)

        val nowEpoch = timeSource.wallClockMs()

        if (sessionActive.value) {
            resumeCountdownSession()
            return
        }

        // Only touch the monotonic clock when an interval session or commitment
        // actually needs it (keeps the countdown path free of SystemClock).
        val savedRunning = store.getBoolean(KEY_TIMER_RUNNING, false)
        val intervalMode = mode.value == Mode.STOPWATCH || mode.value == Mode.POMODORO
        if (commitment.value.active || (savedRunning && intervalMode)) {
            val nowElapsed = timeSource.elapsedRealtimeMs()

            // Reconcile a persisted Hard Mode commitment with the current clocks
            // (also rebases the monotonic anchor after a reboot).
            if (commitment.value.active) {
                val reconciled = HardCommitment.reconcile(commitment.value, nowEpoch, nowElapsed)
                commitment.value =
                    if (HardCommitment.isLocked(reconciled, nowEpoch, nowElapsed)) reconciled
                    else HardCommitment.None
            }

            if (savedRunning && intervalMode) {
                resumeIntervalSession(nowEpoch, nowElapsed)
                return
            }
        }

        // Idle / not running.
        running.value = false
        elapsedSeconds.intValue = 0
        remainingSeconds.intValue = 0
        completedCycles.intValue = 0
        commitment.value = HardCommitment.None
        lastTickAt = 0L
        if (FocusLock.active.value && nowEpoch >= FocusLock.endEpoch.value) {
            FocusLock.end()
        }
    }

    private fun resumeCountdownSession() {
        mode.value = Mode.COUNTDOWN
        val remaining = remainingFromEnd()
        if (remaining > 0) {
            if (!FocusLock.active.value) {
                // Self-heal: the session persisted but its lock record is missing.
                FocusLock.begin(
                    FocusPrefs.mode.value,
                    FocusPrefs.blockedPackages,
                    sessionEndEpoch.value
                )
            }
            remainingSeconds.intValue = remaining
            phase.value = Phase.FOCUS
            running.value = true
            lastTickAt = 0L
            FocusPrefs.setFocusActive(true)
            syncExpiryAlarm()
            ViolationCenter.report("Hard-mode session resumed")
        } else {
            finishCountdown()
        }
    }

    private fun resumeIntervalSession(nowEpoch: Long, nowElapsed: Long) {
        running.value = true
        phase.value = Phase.FOCUS
        lastTickAt = 0L
        FocusPrefs.setFocusActive(true)
        // Rebase anchors in case the device rebooted while the session was alive.
        stopwatchStartElapsed = HardCommitment.rebaseAnchor(
            stopwatchStartEpoch, stopwatchStartElapsed, nowEpoch, nowElapsed
        )
        pomodoroPhaseStartElapsed = HardCommitment.rebaseAnchor(
            pomodoroPhaseStartEpoch, pomodoroPhaseStartElapsed, nowEpoch, nowElapsed
        )
        when (mode.value) {
            Mode.STOPWATCH ->
                elapsedSeconds.intValue =
                    HardCommitment.ceilSeconds(
                        HardCommitment.monotonicDelta(
                            stopwatchStartEpoch, stopwatchStartElapsed, nowEpoch, nowElapsed
                        )
                    )
            Mode.POMODORO ->
                remainingSeconds.intValue =
                    HardCommitment.ceilSeconds(pomodoroPhaseRemainingMs(nowEpoch, nowElapsed))
            else -> Unit
        }
        ViolationCenter.report("Session resumed")
    }

    fun setCountdownMinutes(value: Int) {
        countdownMinutes.intValue = value.coerceAtLeast(1)
        store?.putInt(KEY_COUNTDOWN, countdownMinutes.intValue)
    }

    fun setPomodoroFocusMinutes(value: Int) {
        pomodoroFocusMinutes.intValue = value.coerceAtLeast(1)
        store?.putInt(KEY_POMODORO_FOCUS, pomodoroFocusMinutes.intValue)
    }

    fun setPomodoroBreakMinutes(value: Int) {
        pomodoroBreakMinutes.intValue = value.coerceAtLeast(1)
        store?.putInt(KEY_POMODORO_BREAK, pomodoroBreakMinutes.intValue)
    }

    fun setPomodoroCycles(value: Int) {
        pomodoroCycles.intValue = value.coerceAtLeast(1)
        store?.putInt(KEY_POMODORO_CYCLES, pomodoroCycles.intValue)
    }

    fun setHardMode(value: Boolean) {
        if (running.value) return
        hardMode.value = value
        store?.putBoolean(KEY_HARD_MODE, value)
    }

    fun setStopwatchHardMode(value: Boolean) {
        if (running.value) return
        stopwatchHardMode.value = value
        store?.putBoolean(KEY_STOPWATCH_HARD, value)
    }

    fun setStopwatchCommitMinutes(value: Int) {
        if (running.value) return
        stopwatchCommitMinutes.intValue = value.coerceAtLeast(1)
        store?.putInt(KEY_STOPWATCH_COMMIT, stopwatchCommitMinutes.intValue)
    }

    fun setPomodoroHardMode(value: Boolean) {
        if (running.value) return
        pomodoroHardMode.value = value
        store?.putBoolean(KEY_POMODORO_HARD, value)
    }

    fun setPomodoroCommitMinutes(value: Int) {
        if (running.value) return
        pomodoroCommitMinutes.intValue = value.coerceAtLeast(1)
        store?.putInt(KEY_POMODORO_COMMIT, pomodoroCommitMinutes.intValue)
    }

    /** A hard-focus countdown session that is running and therefore locked. */
    fun isLocked(now: Long = timeSource.wallClockMs()): Boolean = FocusLock.isLocked(now)

    /** True while a Hard Mode commitment is still below its threshold. */
    fun commitmentLocked(
        nowEpochMs: Long = timeSource.wallClockMs(),
        nowElapsedMs: Long = timeSource.elapsedRealtimeMs()
    ): Boolean = HardCommitment.isLocked(commitment.value, nowEpochMs, nowElapsedMs)

    /** Locked by either the countdown Hard Focus or a Hard Mode commitment. */
    fun isSessionLocked(
        nowEpochMs: Long = timeSource.wallClockMs(),
        nowElapsedMs: Long = timeSource.elapsedRealtimeMs()
    ): Boolean = isLocked(nowEpochMs) || commitmentLocked(nowEpochMs, nowElapsedMs)

    fun commitmentRemainingMs(
        nowEpochMs: Long = timeSource.wallClockMs(),
        nowElapsedMs: Long = timeSource.elapsedRealtimeMs()
    ): Long = HardCommitment.remainingMs(commitment.value, nowEpochMs, nowElapsedMs)

    /** Explanation shown when a locked control is used. */
    fun lockNotice(
        nowEpochMs: Long = timeSource.wallClockMs(),
        nowElapsedMs: Long = timeSource.elapsedRealtimeMs()
    ): String {
        val remaining = formatTime(HardCommitment.ceilSeconds(commitmentRemainingMs(nowEpochMs, nowElapsedMs)))
        return "Hard Mode is locked. ${remaining} of the minimum commitment remains — " +
            "you can stop or reset once it is complete."
    }

    /** Overall session state including the commitment lock. */
    fun sessionState(
        nowEpochMs: Long = timeSource.wallClockMs(),
        nowElapsedMs: Long = timeSource.elapsedRealtimeMs()
    ): SessionState = when {
        !running.value && phase.value == Phase.DONE -> SessionState.COMPLETED
        !running.value -> SessionState.IDLE
        commitmentLocked(nowEpochMs, nowElapsedMs) -> SessionState.RUNNING_LOCKED
        else -> SessionState.RUNNING_UNLOCKED
    }

    /** Reason the current mode/configuration cannot be started, or null when it can. */
    fun startBlockReason(): String? = when (mode.value) {
        Mode.STOPWATCH ->
            if (stopwatchHardMode.value) HardCommitment.validateMinutes(stopwatchCommitMinutes.intValue)
            else null
        Mode.POMODORO ->
            if (pomodoroHardMode.value) {
                HardCommitment.validatePomodoro(
                    pomodoroFocusMinutes.intValue,
                    pomodoroCommitMinutes.intValue
                )
            } else null
        Mode.COUNTDOWN -> null
    }

    fun selectMode(value: Mode) {
        if (running.value) return
        mode.value = value
        reset()
    }

    fun start(now: Long = timeSource.wallClockMs()): Boolean {
        // Guard against duplicate/repeated Start taps creating overlapping sessions.
        if (running.value) return true

        when (mode.value) {
            Mode.STOPWATCH -> {
                if (stopwatchHardMode.value) {
                    HardCommitment.validateMinutes(stopwatchCommitMinutes.intValue)?.let { return false }
                }
                val nowElapsed = timeSource.elapsedRealtimeMs()
                val resuming = elapsedSeconds.intValue > 0
                if (resuming) {
                    val elapsedMs = elapsedSeconds.intValue * 1000L
                    stopwatchStartEpoch = now - elapsedMs
                    stopwatchStartElapsed = nowElapsed - elapsedMs
                } else {
                    elapsedSeconds.intValue = 0
                    stopwatchStartEpoch = now
                    stopwatchStartElapsed = nowElapsed
                }
                phase.value = Phase.FOCUS
                if (stopwatchHardMode.value && !resuming) {
                    beginCommitment(Mode.STOPWATCH, stopwatchCommitMinutes.intValue, now, nowElapsed)
                }
                FocusPrefs.setFocusActive(true)
            }
            Mode.COUNTDOWN -> {
                if (remainingSeconds.intValue <= 0) {
                    remainingSeconds.intValue = countdownMinutes.intValue * 60
                }
                phase.value = Phase.FOCUS
                FocusPrefs.setFocusActive(true)
                if (hardMode.value) {
                    sessionActive.value = true
                    sessionEndEpoch.value = now + remainingSeconds.intValue * 1000L
                    FocusLock.begin(
                        FocusPrefs.mode.value,
                        FocusPrefs.blockedPackages,
                        sessionEndEpoch.value
                    )
                    syncExpiryAlarm()
                    ViolationCenter.report("Hard-mode session started")
                }
            }
            Mode.POMODORO -> {
                if (pomodoroHardMode.value) {
                    HardCommitment.validatePomodoro(
                        pomodoroFocusMinutes.intValue,
                        pomodoroCommitMinutes.intValue
                    )?.let { return false }
                }
                val nowElapsed = timeSource.elapsedRealtimeMs()
                completedCycles.intValue = 0
                phase.value = Phase.FOCUS
                startPomodoroPhase(Phase.FOCUS, pomodoroFocusMinutes.intValue, now, nowElapsed)
                if (pomodoroHardMode.value) {
                    beginCommitment(Mode.POMODORO, pomodoroCommitMinutes.intValue, now, nowElapsed)
                }
                FocusPrefs.setFocusActive(true)
            }
        }
        running.value = true
        lastTickAt = 0L
        persistState()
        return true
    }

    fun pause() {
        if (isLocked()) return
        if (commitment.value.active && commitmentLocked()) return
        running.value = false
        endCommitment()
        persistState()
    }

    fun reset() {
        if (isLocked()) return
        if (commitment.value.active && commitmentLocked()) return
        running.value = false
        elapsedSeconds.intValue = 0
        remainingSeconds.intValue = 0
        completedCycles.intValue = 0
        phase.value = Phase.IDLE
        lastTickAt = 0L
        sessionActive.value = false
        sessionEndEpoch.value = 0L
        endCommitment()
        stopwatchStartEpoch = 0L
        stopwatchStartElapsed = 0L
        pomodoroPhaseStartEpoch = 0L
        pomodoroPhaseStartElapsed = 0L
        pomodoroPhaseDurationMs = 0L
        FocusPrefs.setFocusActive(false)
        FocusLock.end()
        cancelExpiryAlarm()
        persistState()
    }

    /**
     * Ends the hard countdown session when the persisted deadline has passed.
     * Idempotent: returns true only when a session actually ended.
     */
    fun checkExpiry(now: Long = timeSource.wallClockMs()): Boolean {
        if (!sessionActive.value) return false
        if (now < sessionEndEpoch.value) return false
        return finishCountdown()
    }

    /** Re-arms the expiry alarm (session start, app resume, init after reboot/reinstall). */
    fun syncExpiryAlarm() {
        val ctx = appContext ?: return
        if (!sessionActive.value || timeSource.wallClockMs() >= sessionEndEpoch.value) {
            if (sessionActive.value) checkExpiry()
            return
        }
        HardSessionAlarm.schedule(ctx, sessionEndEpoch.value)
    }

    private fun cancelExpiryAlarm() {
        val ctx = appContext ?: return
        HardSessionAlarm.cancel(ctx)
    }

    fun onTick() {
        if (!running.value) return
        val nowElapsed = timeSource.elapsedRealtimeMs()
        if (nowElapsed - lastTickAt < 950L) return
        lastTickAt = nowElapsed
        advanceTimers(timeSource.wallClockMs(), nowElapsed)
    }

    /**
     * Advances the running timer using the supplied clock readings. Kept
     * separate from [onTick] so the time model can be tested deterministically.
     */
    internal fun advanceTimers(nowEpochMs: Long, nowElapsedMs: Long) {
        if (!running.value) return
        when (mode.value) {
            Mode.STOPWATCH ->
                elapsedSeconds.intValue =
                    HardCommitment.ceilSeconds(
                        HardCommitment.monotonicDelta(
                            stopwatchStartEpoch, stopwatchStartElapsed, nowEpochMs, nowElapsedMs
                        )
                    )
            Mode.COUNTDOWN -> {
                val remaining = if (sessionActive.value) {
                    remainingFromEnd()
                } else {
                    remainingSeconds.intValue - 1
                }
                remainingSeconds.intValue = remaining
                if (remaining <= 0) {
                    finishCountdown()
                }
            }
            Mode.POMODORO -> advancePomodoro(nowEpochMs, nowElapsedMs)
        }
    }

    private fun advancePomodoro(nowEpochMs: Long, nowElapsedMs: Long) {
        val remainingMs = pomodoroPhaseRemainingMs(nowEpochMs, nowElapsedMs)
        remainingSeconds.intValue = HardCommitment.ceilSeconds(remainingMs)
        if (remainingMs > 0L) return

        if (phase.value == Phase.FOCUS) {
            completedCycles.intValue += 1
            if (completedCycles.intValue >= pomodoroCycles.intValue) {
                remainingSeconds.intValue = 0
                running.value = false
                phase.value = Phase.DONE
                endCommitment()
                FocusPrefs.setFocusActive(false)
                ViolationCenter.report("Pomodoro sessions complete")
            } else {
                phase.value = Phase.BREAK
                // The commitment belongs to the work interval only; never a break.
                endCommitment()
                startPomodoroPhase(Phase.BREAK, pomodoroBreakMinutes.intValue, nowEpochMs, nowElapsedMs)
                FocusPrefs.setFocusActive(false)
                ViolationCenter.report("Break started")
            }
        } else {
            phase.value = Phase.FOCUS
            startPomodoroPhase(Phase.FOCUS, pomodoroFocusMinutes.intValue, nowEpochMs, nowElapsedMs)
            if (pomodoroHardMode.value) {
                beginCommitment(Mode.POMODORO, pomodoroCommitMinutes.intValue, nowEpochMs, nowElapsedMs)
            }
            FocusPrefs.setFocusActive(true)
            ViolationCenter.report("Focus session started")
        }
        persistState()
    }

    private fun startPomodoroPhase(phase: Phase, minutes: Int, nowEpochMs: Long, nowElapsedMs: Long) {
        this.phase.value = phase
        pomodoroPhaseStartEpoch = nowEpochMs
        pomodoroPhaseStartElapsed = nowElapsedMs
        pomodoroPhaseDurationMs = minutes.coerceAtLeast(1) * 60_000L
        remainingSeconds.intValue = minutes.coerceAtLeast(1) * 60
    }

    private fun pomodoroPhaseRemainingMs(nowEpochMs: Long, nowElapsedMs: Long): Long {
        if (pomodoroPhaseDurationMs <= 0L) return 0L
        val elapsed = HardCommitment.monotonicDelta(
            pomodoroPhaseStartEpoch, pomodoroPhaseStartElapsed, nowEpochMs, nowElapsedMs
        )
        return (pomodoroPhaseDurationMs - elapsed).coerceAtLeast(0L)
    }

    private fun beginCommitment(owner: Mode, minutes: Int, nowEpochMs: Long, nowElapsedMs: Long) {
        commitmentMode = owner
        commitment.value = HardCommitment.start(nowEpochMs, nowElapsedMs, minutes * 60_000L)
    }

    private fun endCommitment() {
        commitment.value = HardCommitment.None
    }

    private fun finishCountdown(): Boolean {
        val wasSession = sessionActive.value
        val transition = wasSession || running.value || remainingSeconds.intValue > 0
        remainingSeconds.intValue = 0
        running.value = false
        phase.value = Phase.DONE
        sessionActive.value = false
        sessionEndEpoch.value = 0L
        FocusPrefs.setFocusActive(false)
        val unlocked = FocusLock.end()
        cancelExpiryAlarm()
        persistState()
        if (transition || unlocked) {
            if (wasSession) notifyHardFocusEnded()
            ViolationCenter.report("Focus countdown finished")
        }
        return transition || unlocked
    }

    private fun notifyHardFocusEnded() {
        val ctx = appContext ?: return
        Notifications.ensureChannel(ctx)
        Notifications.show(
            ctx,
            HARD_FOCUS_NOTIFICATION_ID,
            "Hard Focus ended",
            "The countdown finished. The app list is unlocked and editing is available again."
        )
    }

    private fun remainingFromEnd(): Int {
        val delta = sessionEndEpoch.value - timeSource.wallClockMs()
        return if (delta <= 0) 0 else ((delta + 999) / 1000).toInt()
    }

    private fun persistState() {
        val s = store ?: return
        s.putBoolean(KEY_HARD_MODE, hardMode.value)
        s.putString(KEY_MODE, mode.value.name)
        s.putString(KEY_PHASE, phase.value.name)
        s.putInt(KEY_REMAINING, remainingSeconds.intValue)
        s.putBoolean(KEY_SESSION_ACTIVE, sessionActive.value)
        s.putLong(KEY_SESSION_END, sessionEndEpoch.value)
        s.putBoolean(KEY_TIMER_RUNNING, running.value)
        s.putBoolean(KEY_STOPWATCH_HARD, stopwatchHardMode.value)
        s.putInt(KEY_STOPWATCH_COMMIT, stopwatchCommitMinutes.intValue)
        s.putBoolean(KEY_POMODORO_HARD, pomodoroHardMode.value)
        s.putInt(KEY_POMODORO_COMMIT, pomodoroCommitMinutes.intValue)
        s.putBoolean(KEY_COMMIT_ACTIVE, commitment.value.active)
        s.putLong(KEY_COMMIT_START_EPOCH, commitment.value.startEpochMs)
        s.putLong(KEY_COMMIT_START_ELAPSED, commitment.value.startElapsedMs)
        s.putLong(KEY_COMMIT_DURATION, commitment.value.durationMs)
        s.putString(KEY_COMMIT_MODE, commitmentMode.name)
        s.putLong(KEY_SW_START_EPOCH, stopwatchStartEpoch)
        s.putLong(KEY_SW_START_ELAPSED, stopwatchStartElapsed)
        s.putLong(KEY_POM_START_EPOCH, pomodoroPhaseStartEpoch)
        s.putLong(KEY_POM_START_ELAPSED, pomodoroPhaseStartElapsed)
        s.putLong(KEY_POM_DURATION, pomodoroPhaseDurationMs)
    }

    fun displaySeconds(): Int = when (mode.value) {
        Mode.STOPWATCH -> elapsedSeconds.intValue
        else -> remainingSeconds.intValue
    }

    fun stateLabel(): String = when {
        !running.value && phase.value == Phase.IDLE -> "Idle"
        !running.value && phase.value == Phase.DONE -> "Finished"
        !running.value -> "Paused"
        running.value && phase.value == Phase.BREAK -> "Break"
        else -> "Running"
    }

    fun formatTime(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val hours = safe / 3600
        val minutes = (safe % 3600) / 60
        val seconds = safe % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private const val HARD_FOCUS_NOTIFICATION_ID = 0x0FE6
}
