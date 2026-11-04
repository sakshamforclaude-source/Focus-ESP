package com.example.focusesp

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.focusesp.ui.theme.BannerTone
import com.example.focusesp.ui.theme.GlassCard
import com.example.focusesp.ui.theme.IconBadge
import com.example.focusesp.ui.theme.IconClock
import com.example.focusesp.ui.theme.IconShield
import com.example.focusesp.ui.theme.IconTimer
import com.example.focusesp.ui.theme.InfoBanner
import com.example.focusesp.ui.theme.NumberStepper
import com.example.focusesp.ui.theme.PrimaryButton
import com.example.focusesp.ui.theme.ProgressRing
import com.example.focusesp.ui.theme.SecondaryButton
import com.example.focusesp.ui.theme.SectionHeader
import com.example.focusesp.ui.theme.SegmentedControl
import com.example.focusesp.ui.theme.SettingToggleRow
import com.example.focusesp.ui.theme.StatTile
import com.example.focusesp.ui.theme.StatusPill
import com.example.focusesp.ui.theme.TimerNumerals
import kotlinx.coroutines.delay

/**
 * The single Focus Timer destination. Replaces the old, separate "Focus" and
 * "Timer" pages: it owns the three timer modes plus the manual Focus blocking
 * controls, so there is exactly one place to start a session.
 */
@Composable
fun FocusTimerScreen(
    serviceEnabled: Boolean,
    onOpenAccessibility: () -> Unit
) {
    // Drives the timer engine itself — one tick loop, now shared by both halves.
    LaunchedEffect(FocusTimer.running.value) {
        while (FocusTimer.running.value) {
            delay(1000)
            FocusTimer.onTick()
        }
    }

    val mode = FocusTimer.mode.value
    val running = FocusTimer.running.value
    val hardFocusLocked = FocusTimer.isLocked()
    val commitmentActive = FocusTimer.commitment.value.active
    val ticking = hardFocusLocked || commitmentActive || running

    // Manual Focus (blocklist) state.
    val focusActive = FocusPrefs.focusActive.value
    val pausedUntil = FocusPrefs.pausedUntil.value
    val paused = pausedUntil > System.currentTimeMillis()
    val schedule = RuleEngine.activeSchedule()
    val blocking = RuleEngine.blockingActive()
    val tickingManual = ticking || paused
    val wallNow = rememberNowTicker(tickingManual)
    val elapsedNow = SystemClock.elapsedRealtime()

    val commitmentLocked = FocusTimer.commitmentLocked(wallNow, elapsedNow)
    val sessionState = FocusTimer.sessionState(wallNow, elapsedNow)
    val lockNoticeText = FocusTimer.lockNotice(wallNow, elapsedNow)
    val hardLocked = hardFocusLocked || FocusTimer.isLocked(wallNow)
    val commitLocked = hardFocusLocked || commitmentLocked
    val sessionLocked = hardLocked || commitLocked

    var lockNotice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sessionState) {
        if (sessionState != FocusTimer.SessionState.RUNNING_LOCKED) lockNotice = null
    }

    val scheme = MaterialTheme.colorScheme
    val accent = if (sessionLocked) scheme.error else scheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- Prerequisite ----
        if (!serviceEnabled) {
            GlassCard(accent = scheme.error) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(accent = scheme.error, size = 42.dp) {
                        IconShield(tint = scheme.error)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        SectionHeader("Accessibility service")
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Disabled — blocking cannot run",
                            style = MaterialTheme.typography.titleMedium,
                            color = scheme.error
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "FocusESP needs the accessibility service to detect the " +
                        "foreground app and enforce the blocklist.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = "Open Accessibility settings",
                    onClick = onOpenAccessibility,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ---- Timer mode ----
        SegmentedControl(
            options = listOf("Stopwatch", "Countdown", "Pomodoro"),
            selectedIndex = when (mode) {
                FocusTimer.Mode.STOPWATCH -> 0
                FocusTimer.Mode.COUNTDOWN -> 1
                FocusTimer.Mode.POMODORO -> 2
            },
            onSelect = { index ->
                FocusTimer.selectMode(
                    when (index) {
                        0 -> FocusTimer.Mode.STOPWATCH
                        1 -> FocusTimer.Mode.COUNTDOWN
                        else -> FocusTimer.Mode.POMODORO
                    }
                )
            },
            enabled = !running && !hardFocusLocked
        )

        // ---- Timer display ----
        GlassCard(accent = accent) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (hardFocusLocked) {
                    StatusPill(
                        text = "HARD FOCUS",
                        containerColor = scheme.errorContainer,
                        contentColor = scheme.onErrorContainer
                    )
                } else if (commitmentLocked) {
                    StatusPill(
                        text = "HARD MODE",
                        containerColor = scheme.errorContainer,
                        contentColor = scheme.onErrorContainer
                    )
                } else if (running) {
                    StatusPill(
                        text = FocusTimer.stateLabel().uppercase(),
                        containerColor = scheme.primaryContainer,
                        contentColor = scheme.onPrimaryContainer
                    )
                } else {
                    StatusPill(text = FocusTimer.stateLabel().uppercase())
                }

                Spacer(Modifier.height(18.dp))
                ProgressRing(
                    progress = timerProgress(mode),
                    size = 236.dp,
                    strokeWidth = 12.dp,
                    color = accent,
                    trackColor = scheme.surfaceVariant
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = FocusTimer.formatTime(FocusTimer.displaySeconds()),
                            style = TimerNumerals,
                            color = if (sessionLocked) scheme.error else scheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        if (mode == FocusTimer.Mode.POMODORO) {
                            Text(
                                text = "cycle ${FocusTimer.completedCycles.intValue + 1}/" +
                                    "${FocusTimer.pomodoroCycles.intValue}" +
                                    "  ·  ${FocusTimer.phase.value.name.lowercase()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = FocusTimer.stateLabel(),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val startBlockReason = FocusTimer.startBlockReason()
                    PrimaryButton(
                        text = if (running) "Pause" else "Start",
                        onClick = {
                            when {
                                commitLocked -> lockNotice = lockNoticeText
                                running -> FocusTimer.pause()
                                else -> if (!FocusTimer.start()) lockNotice = startBlockReason
                            }
                        },
                        enabled = !hardFocusLocked && (running || startBlockReason == null),
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryButton(
                        text = "Stop / Reset",
                        onClick = {
                            if (commitLocked) lockNotice = lockNoticeText else FocusTimer.reset()
                        },
                        enabled = !hardFocusLocked,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (hardFocusLocked) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Hard Focus — " +
                            "${FocusTimer.formatTime(FocusLock.remainingSeconds(wallNow).toInt())} " +
                            "remaining. Pause and Stop are locked until the countdown finishes. " +
                            "The session resumes automatically if FocusESP is closed or the phone " +
                            "restarts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.error
                    )
                } else if (commitmentActive) {
                    Spacer(Modifier.height(12.dp))
                    val commitMinutes = (FocusTimer.commitment.value.durationMs / 60_000L).toInt()
                    val remaining = FocusTimer.formatTime(
                        HardCommitment.ceilSeconds(
                            FocusTimer.commitmentRemainingMs(wallNow, elapsedNow)
                        )
                    )
                    Text(
                        text = if (commitmentLocked) {
                            "Hard Mode — minimum commitment $commitMinutes min. $remaining " +
                                "remaining before Stop and Reset unlock."
                        } else {
                            "Hard Mode — minimum commitment complete. Stop and Reset are available."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (commitmentLocked) scheme.error else scheme.primary
                    )
                }

                if (lockNotice != null) {
                    Spacer(Modifier.height(10.dp))
                    InfoBanner(text = lockNotice ?: "", tone = BannerTone.DANGER)
                }

                if (!running && FocusTimer.startBlockReason() != null) {
                    Spacer(Modifier.height(10.dp))
                    InfoBanner(
                        text = FocusTimer.startBlockReason() ?: "",
                        tone = BannerTone.WARNING
                    )
                }
            }
        }

        // ---- Mode settings ----
        SectionHeader("Settings", modifier = Modifier.padding(start = 4.dp))
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(accent = scheme.primary, size = 38.dp) {
                    when (mode) {
                        FocusTimer.Mode.STOPWATCH -> IconTimer(tint = scheme.primary)
                        FocusTimer.Mode.COUNTDOWN -> IconClock(tint = scheme.primary)
                        FocusTimer.Mode.POMODORO -> IconTimer(tint = scheme.primary)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = modeLabel(mode),
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface
                    )
                    Text(
                        text = modeDescription(mode),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            when (mode) {
                FocusTimer.Mode.STOPWATCH -> {
                    Text(
                        text = "Starting it also enables Focus blocking.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    HardModeSection(
                        checked = FocusTimer.stopwatchHardMode.value,
                        onCheckedChange = { FocusTimer.setStopwatchHardMode(it) },
                        enabled = !running,
                        description = "Set a minimum commitment. Stop and Reset stay locked " +
                            "until it has elapsed. The stopwatch keeps counting past it.",
                        commitmentMinutes = FocusTimer.stopwatchCommitMinutes.intValue,
                        onCommitmentChange = { FocusTimer.setStopwatchCommitMinutes(it) }
                    )
                }
                FocusTimer.Mode.COUNTDOWN -> {
                    NumberRow(
                        label = "Countdown minutes",
                        value = FocusTimer.countdownMinutes.intValue,
                        enabled = !running,
                        onChange = { FocusTimer.setCountdownMinutes(it) }
                    )
                    Spacer(Modifier.height(12.dp))
                    HardModeSection(
                        checked = FocusTimer.hardMode.value,
                        onCheckedChange = { FocusTimer.setHardMode(it) },
                        enabled = !running,
                        description = "Cannot pause or stop. Locks the app list and the " +
                            "blocklist/whitelist mode until the countdown ends.",
                        commitmentMinutes = null,
                        onCommitmentChange = {}
                    )
                }
                FocusTimer.Mode.POMODORO -> {
                    NumberRow(
                        label = "Focus minutes",
                        value = FocusTimer.pomodoroFocusMinutes.intValue,
                        enabled = !running,
                        onChange = { FocusTimer.setPomodoroFocusMinutes(it) }
                    )
                    NumberRow(
                        label = "Break minutes",
                        value = FocusTimer.pomodoroBreakMinutes.intValue,
                        enabled = !running,
                        onChange = { FocusTimer.setPomodoroBreakMinutes(it) }
                    )
                    NumberRow(
                        label = "Cycles",
                        value = FocusTimer.pomodoroCycles.intValue,
                        enabled = !running,
                        onChange = { FocusTimer.setPomodoroCycles(it) }
                    )
                    Spacer(Modifier.height(12.dp))
                    HardModeSection(
                        checked = FocusTimer.pomodoroHardMode.value,
                        onCheckedChange = { FocusTimer.setPomodoroHardMode(it) },
                        enabled = !running,
                        description = "Set a minimum commitment per work session. It can be " +
                            "shorter than the focus session, never longer. Breaks are never " +
                            "locked.",
                        commitmentMinutes = FocusTimer.pomodoroCommitMinutes.intValue,
                        onCommitmentChange = { FocusTimer.setPomodoroCommitMinutes(it) }
                    )
                }
            }
        }

        // ---- Manual Focus blocking ----
        SectionHeader("Blocking", modifier = Modifier.padding(start = 4.dp))
        val statusText = when {
            hardLocked -> "Hard Focus"
            commitLocked -> "Hard Mode"
            paused -> "Paused"
            blocking -> "Focus active"
            else -> "Inactive"
        }
        val focusAccent = when {
            sessionLocked -> scheme.error
            paused -> scheme.tertiary
            blocking -> scheme.primary
            else -> scheme.onSurfaceVariant
        }
        GlassCard(accent = focusAccent) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(accent = focusAccent, size = 42.dp) {
                    IconShield(tint = focusAccent)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleLarge,
                        color = focusAccent
                    )
                    Text(
                        text = "${FocusPrefs.blockedPackages.size} app(s) " +
                            (if (FocusPrefs.mode.value == FocusMode.BLOCKLIST) "selected to block"
                            else "whitelisted (everything else blocked)"),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                if (sessionLocked) {
                    StatusPill(
                        text = "Locked",
                        containerColor = scheme.errorContainer,
                        contentColor = scheme.onErrorContainer
                    )
                } else if (blocking) {
                    StatusPill(
                        text = "Blocking on",
                        containerColor = scheme.primaryContainer,
                        contentColor = scheme.onPrimaryContainer
                    )
                }
            }

            if (schedule != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Active schedule for ${schedule.packages.size} app(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }

            if (hardLocked || commitLocked) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = FocusTimer.formatTime(
                        if (hardLocked) FocusLock.remainingSeconds(wallNow).toInt()
                        else HardCommitment.ceilSeconds(
                            FocusTimer.commitmentRemainingMs(wallNow, elapsedNow)
                        )
                    ),
                    style = TimerNumerals,
                    color = scheme.error
                )
                Text(
                    text = if (hardLocked) {
                        "HARD FOCUS: stopping, pausing and the app list are locked until the " +
                            "countdown ends."
                    } else {
                        "HARD MODE: stopping and pausing Focus are locked until the " +
                            "commitment completes."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error
                )
            } else if (paused) {
                val minutes = (pausedUntil - System.currentTimeMillis()) / 60_000L
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Resumes in about $minutes min",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PrimaryButton(
                    text = "Start Focus",
                    onClick = { FocusPrefs.setFocusActive(true) },
                    enabled = !focusActive && serviceEnabled && !sessionLocked,
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = "Stop Focus",
                    onClick = { FocusPrefs.setFocusActive(false) },
                    enabled = focusActive && !sessionLocked,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SecondaryButton(
                    text = "Pause 30 min",
                    onClick = { FocusPrefs.pauseFor(30 * 60_000L) },
                    enabled = serviceEnabled && !paused && !sessionLocked,
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = "Resume",
                    onClick = { FocusPrefs.resume() },
                    enabled = paused && !sessionLocked,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ---- Last detected app ----
        StatTile(
            label = "Last detected app",
            value = FocusState.lastPackage.value ?: "None yet",
            caption = if (FocusState.lastPackage.value == null) {
                "Foreground apps appear here once the service is running"
            } else {
                "Most recent foreground app"
            },
            accent = scheme.primary,
            icon = { IconShield(tint = scheme.primary) }
        )

        InfoBanner(
            text = "Starting a countdown, Pomodoro or Stopwatch also enables Focus blocking, and " +
                "turns it off when the session ends. Hard Mode is an in-app commitment: it " +
                "prevents ordinary in-app stopping, but it cannot stop you from force-stopping " +
                "the app, clearing its data or revoking permissions.",
            tone = BannerTone.INFO
        )
        Spacer(Modifier.height(8.dp))
    }
}

private fun modeLabel(mode: FocusTimer.Mode): String = when (mode) {
    FocusTimer.Mode.STOPWATCH -> "Stopwatch"
    FocusTimer.Mode.COUNTDOWN -> "Countdown"
    FocusTimer.Mode.POMODORO -> "Pomodoro"
}

private fun modeDescription(mode: FocusTimer.Mode): String = when (mode) {
    FocusTimer.Mode.STOPWATCH -> "Counts up, locks the blocklist while running"
    FocusTimer.Mode.COUNTDOWN -> "Runs down to zero, then ends the session"
    FocusTimer.Mode.POMODORO -> "Alternating focus and break cycles"
}

private fun timerProgress(mode: FocusTimer.Mode): Float {
    val remaining = FocusTimer.remainingSeconds.intValue.toFloat()
    return when (mode) {
        FocusTimer.Mode.COUNTDOWN -> {
            val total = (FocusTimer.countdownMinutes.intValue * 60).coerceAtLeast(1).toFloat()
            (1f - remaining / total).coerceIn(0f, 1f)
        }
        FocusTimer.Mode.POMODORO -> {
            val total = (if (FocusTimer.phase.value == FocusTimer.Phase.BREAK) {
                FocusTimer.pomodoroBreakMinutes.intValue
            } else {
                FocusTimer.pomodoroFocusMinutes.intValue
            } * 60).coerceAtLeast(1).toFloat()
            (1f - remaining / total).coerceIn(0f, 1f)
        }
        FocusTimer.Mode.STOPWATCH -> 0f
    }
}

@Composable
private fun HardModeSection(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
    description: String,
    commitmentMinutes: Int?,
    onCommitmentChange: (Int) -> Unit
) {
    GlassCard(
        accent = if (checked) MaterialTheme.colorScheme.primary else null,
        contentPadding = PaddingValues(14.dp)
    ) {
        SettingToggleRow(
            title = "Hard Mode",
            description = description,
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
        if (checked && commitmentMinutes != null) {
            Spacer(Modifier.height(10.dp))
            NumberRow(
                label = "Minimum commitment (min)",
                value = commitmentMinutes,
                enabled = enabled,
                onChange = onCommitmentChange
            )
        }
    }
}

@Composable
private fun NumberRow(
    label: String,
    value: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        NumberStepper(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            min = 1,
            max = 240,
            valueWidth = 48.dp
        )
    }
}
