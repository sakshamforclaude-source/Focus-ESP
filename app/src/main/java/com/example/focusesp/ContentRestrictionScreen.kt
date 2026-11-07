package com.example.focusesp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.focusesp.ui.theme.BannerTone
import com.example.focusesp.ui.theme.GlassCard
import com.example.focusesp.ui.theme.IconClose
import com.example.focusesp.ui.theme.InfoBanner
import com.example.focusesp.ui.theme.LogPanel
import com.example.focusesp.ui.theme.PrimaryButton
import com.example.focusesp.ui.theme.SecondaryButton
import com.example.focusesp.ui.theme.SectionHeader
import com.example.focusesp.ui.theme.SettingToggleRow
import com.example.focusesp.ui.theme.StatusPill
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val diagnosticTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

private fun formatDiagnosticEntry(entry: DetectionDiagnostics.Entry): String {
    val time = diagnosticTimeFormat.format(Date(entry.timeMs))
    val pct = (entry.confidence * 100).toInt()
    return "$time  ${entry.decision}  ${entry.packageName}  rule=${entry.ruleId ?: "-"}  ($pct%)"
}

private fun formatClock(epochMs: Long): String =
    diagnosticTimeFormat.format(Date(epochMs))

private fun formatCountdown(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) + 999L) / 1000L
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

/**
 * Reads the observable [DetectionPipeline] fields during composition so the
 * screen recomposes whenever the service records a new decision or outcome.
 */
@Composable
private fun pipelineSummary(): List<String> = DetectionPipeline.summaryLines()

/**
 * Content Restrictions: one unified control panel for the three always-on
 * restriction features.
 *
 * The screen is a thin view over the shared state:
 *  - [ContentRestrictionStore] holds the restriction toggles.
 *  - [ContentRestrictionCommitment] holds the unlock appointment and owns the
 *    single validation rule. The switches here are only a convenience; the
 *    store rejects any weakening change the commitment does not permit.
 */
@Composable
fun ContentSection(serviceEnabled: Boolean) {
    var keywordInput by remember { mutableStateOf("") }
    var channelInput by remember { mutableStateOf("") }
    var messageDraft by remember(ContentRestrictionStore.shortsBlockMessage.value) {
        mutableStateOf(ContentRestrictionStore.effectiveShortsBlockMessage())
    }

    // One-second display tick. Eligibility is always recomputed from persisted
    // timestamps; this tick only refreshes the countdown and the switch state.
    var nowWall by remember { mutableLongStateOf(ContentRestrictionCommitment.nowEpochMs()) }
    var nowElapsed by remember { mutableLongStateOf(ContentRestrictionCommitment.nowElapsedMs()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowWall = System.currentTimeMillis()
            nowElapsed = android.os.SystemClock.elapsedRealtime()
            delay(500L)
        }
    }

    val appointment = ContentRestrictionCommitment.appointment.value
    val state = RestrictionCommitment.state(appointment, nowWall, nowElapsed)
    val canWeaken = state == RestrictionCommitment.State.CONFIRMED
    val context = LocalContext.current
    val anyEnabled = AlwaysOnRestrictions.anyRestrictionEnabled(
        ContentRestrictionStore.blockYoutubeShorts.value,
        ContentRestrictionStore.blockInstagramReels.value,
        ContentRestrictionStore.blockYoutubeApp.value,
        ContentRestrictionStore.blockInstagramApp.value
    )
    val enforcementSummary = AlwaysOnRestrictions.enforcementSummary(serviceEnabled, anyEnabled)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!serviceEnabled) {
            InfoBanner(
                text = enforcementSummary + " Enable it in Android Accessibility settings. " +
                    "FocusESP reports the real service state and never claims blocking is " +
                    "working when it is not.",
                tone = BannerTone.DANGER,
                title = "Not enforcing"
            )
        } else {
            InfoBanner(
                text = enforcementSummary + " They keep working when Focus Mode is off, when " +
                    "the timer is stopped, in the background and after a restart. Detection is " +
                    "best-effort and can miss screens when an app changes.",
                tone = BannerTone.INFO,
                title = "Always-on"
            )
        }

        // ---- Unlock commitment ------------------------------------------
        SectionHeader("Unlock commitment", modifier = Modifier.padding(start = 4.dp))
        UnlockCommitmentPanel(
            state = state,
            appointment = appointment,
            nowWall = nowWall,
            nowElapsed = nowElapsed,
            onRequest = {
                if (ContentRestrictionCommitment.request()) {
                    ContentRestrictionCommitment.syncReminder(context)
                }
            },
            onConfirm = {
                if (ContentRestrictionCommitment.confirm()) {
                    ContentRestrictionCommitment.syncReminder(context)
                }
            }
        )

        // ---- The three primary restriction features ----------------------
        SectionHeader("Content restrictions", modifier = Modifier.padding(start = 4.dp))

        GlassCard {
            RestrictionControl(
                title = "Block YouTube Shorts",
                description = "Restricts the Shorts player while leaving normal watch, search " +
                    "and home screens usable. Best effort — depends on visible screen signals.",
                checked = ContentRestrictionStore.blockYoutubeShorts.value,
                serviceEnabled = serviceEnabled,
                canWeaken = canWeaken,
                onCheckedChange = { ContentRestrictionStore.setBlockYoutubeShorts(it) }
            )
        }

        GlassCard {
            RestrictionControl(
                title = "Block Instagram Reels",
                description = "Restricts the Reels viewer while leaving feed, stories and direct " +
                    "messages usable. Detection stays conservative when a screen is unclear.",
                checked = ContentRestrictionStore.blockInstagramReels.value,
                serviceEnabled = serviceEnabled,
                canWeaken = canWeaken,
                onCheckedChange = { ContentRestrictionStore.setBlockInstagramReels(it) }
            )
        }

        GlassCard {
            RestrictionControl(
                title = "Whole-App Fallback (YouTube)",
                description = "Send the entire YouTube app Home instead of relying on Shorts " +
                    "detection. Use this only when content-level blocking is unreliable.",
                checked = ContentRestrictionStore.blockYoutubeApp.value,
                serviceEnabled = serviceEnabled,
                canWeaken = canWeaken,
                onCheckedChange = { ContentRestrictionStore.setBlockYoutubeApp(it) }
            )
            Spacer(Modifier.height(14.dp))
            RestrictionControl(
                title = "Whole-App Fallback (Instagram)",
                description = "Send the entire Instagram app Home instead of relying on Reels " +
                    "detection. Use this only when content-level blocking is unreliable.",
                checked = ContentRestrictionStore.blockInstagramApp.value,
                serviceEnabled = serviceEnabled,
                canWeaken = canWeaken,
                onCheckedChange = { ContentRestrictionStore.setBlockInstagramApp(it) }
            )
            Spacer(Modifier.height(12.dp))
            InfoBanner(
                text = "Content-level blocking targets only the Shorts/Reels surface. " +
                    "Whole-app fallback blocks everything in that app, including ordinary " +
                    "videos and messages. It is never enabled for you automatically.",
                tone = BannerTone.WARNING,
                title = "Which should I use?"
            )
        }

        ContentRestrictionStore.lastControlNotice.value?.let { notice ->
            InfoBanner(text = notice, tone = BannerTone.WARNING, title = "Locked")
        }

        // ---- Customizable short-form blocking message ---------------------
        SectionHeader("Blocking Message", modifier = Modifier.padding(start = 4.dp))
        GlassCard {
            Text(
                text = "This message appears whenever FocusESP blocks YouTube Shorts or Instagram Reels.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = messageDraft,
                onValueChange = {
                    messageDraft = it.take(ContentRestrictionStore.MAX_SHORTS_BLOCK_MESSAGE_LENGTH)
                },
                label = { Text("Message") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${messageDraft.length} / " +
                    ContentRestrictionStore.MAX_SHORTS_BLOCK_MESSAGE_LENGTH,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Preview",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = messageDraft.trim().ifBlank {
                    ContentRestrictionStore.DEFAULT_SHORTS_BLOCK_MESSAGE
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    text = "Save message",
                    onClick = {
                        ContentRestrictionStore.setShortsBlockMessage(messageDraft)
                        messageDraft = ContentRestrictionStore.effectiveShortsBlockMessage()
                    },
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = "Reset",
                    onClick = {
                        ContentRestrictionStore.resetShortsBlockMessage()
                        messageDraft = ContentRestrictionStore.effectiveShortsBlockMessage()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ---- Diagnostics -------------------------------------------------
        SectionHeader("Detection & blocking", modifier = Modifier.padding(start = 4.dp))
        GlassCard {
            LogPanel(entries = listOf(ContentRestrictionStore.lastDiagnostic.value), emphasizeFirst = true)
        }

        SectionHeader("Live pipeline", modifier = Modifier.padding(start = 4.dp))
        GlassCard {
            Text(
                text = "Last decision and enforcement outcome from the accessibility service. " +
                    "Only metadata is shown — never screen text, titles or URLs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            // Reading the observable fields here also drives recomposition.
            val lines = pipelineSummary()
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader("Pipeline events", modifier = Modifier.weight(1f))
                StatusPill("${DetectionPipeline.events.size}")
            }
            Spacer(Modifier.height(8.dp))
            if (DetectionPipeline.events.isEmpty()) {
                Text(
                    text = "No pipeline events yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LogPanel(entries = DetectionPipeline.events.toList())
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = "Clear pipeline log",
                onClick = { DetectionPipeline.clear() },
                modifier = Modifier.fillMaxWidth()
            )
        }

        SectionHeader("Developer diagnostics", modifier = Modifier.padding(start = 4.dp))
        GlassCard {
            SettingToggleRow(
                title = "Record detection outcomes",
                description = "Stores only timestamp, app, decision, rule and confidence. " +
                    "No screen text, titles or URLs are recorded.",
                checked = ContentRestrictionStore.diagnosticsEnabled.value,
                onCheckedChange = { ContentRestrictionStore.setDiagnosticsEnabled(it) }
            )
            if (ContentRestrictionStore.diagnosticsEnabled.value) {
                Spacer(Modifier.height(12.dp))
                val revision = DetectionDiagnostics.revision.value
                val entries = remember(revision) { DetectionDiagnostics.entries.toList() }
                if (entries.isEmpty()) {
                    Text(
                        text = "No checks recorded yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader("Recent checks", modifier = Modifier.weight(1f))
                        StatusPill("${entries.size}")
                    }
                    Spacer(Modifier.height(8.dp))
                    LogPanel(entries = entries.map { formatDiagnosticEntry(it) })
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        text = "Clear log",
                        onClick = { DetectionDiagnostics.clear() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        SectionHeader("Browser keywords", modifier = Modifier.padding(start = 4.dp))
        GlassCard {
            Text(
                text = "If a browser address shows a URL containing a keyword, FocusESP sends you Home.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = keywordInput,
                    onValueChange = { keywordInput = it },
                    label = { Text("Keyword") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.width(10.dp))
                PrimaryButton(
                    text = "Add",
                    onClick = {
                        ContentRestrictionStore.addBrowserKeyword(keywordInput)
                        keywordInput = ""
                    },
                    enabled = keywordInput.isNotBlank()
                )
            }
            ContentRestrictionStore.browserKeywords.toList().forEach { keyword ->
                ChipRow(text = keyword, locked = !canWeaken) {
                    ContentRestrictionStore.removeBrowserKeyword(keyword)
                }
            }
        }

        SectionHeader("Allowed YouTube channels", modifier = Modifier.padding(start = 4.dp))
        GlassCard {
            Text(
                text = "When a matching channel name is visible, Shorts blocking is skipped. " +
                    "Adding a channel weakens protection, so it obeys the unlock commitment.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = channelInput,
                    onValueChange = { channelInput = it },
                    label = { Text("Channel name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.width(10.dp))
                PrimaryButton(
                    text = "Add",
                    onClick = {
                        ContentRestrictionStore.addAllowedChannel(channelInput)
                        channelInput = ""
                    },
                    enabled = channelInput.isNotBlank()
                )
            }
            ContentRestrictionStore.allowedChannels.toList().forEach { channel ->
                ChipRow(text = channel, locked = false) {
                    ContentRestrictionStore.removeAllowedChannel(channel)
                }
            }
        }

        InfoBanner(
            text = "An ordinary Android app cannot reliably disable split-screen/floating " +
                "windows or prevent uninstalls, and the user can always turn off Accessibility " +
                "access or uninstall FocusESP from system settings. Those are device/OS " +
                "behaviors this app does not and should not try to trap.",
            tone = BannerTone.INFO
        )

        SectionHeader("Detection log", modifier = Modifier.padding(start = 4.dp))
        DetectionLogSection()
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun UnlockCommitmentPanel(
    state: RestrictionCommitment.State,
    appointment: RestrictionCommitment.Appointment,
    nowWall: Long,
    nowElapsed: Long,
    onRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    val waitRemaining = RestrictionCommitment.waitRemainingMs(appointment, nowWall, nowElapsed)
    val windowRemaining = RestrictionCommitment.windowRemainingMs(appointment, nowWall, nowElapsed)
    val windowEnd = RestrictionCommitment.windowEndEpochMs(appointment)

    GlassCard {
        when (state) {
            RestrictionCommitment.State.LOCKED -> {
                Text(
                    text = "Restrictions are locked. You can always turn more on, but turning " +
                        "one off requires a 6-hour appointment and a manual confirmation inside " +
                        "a 1-minute window.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = "Request unlock in 6 hours",
                    onClick = onRequest,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            RestrictionCommitment.State.WAITING -> {
                Text(
                    text = "Unlock appointment set for ${formatClock(ContentRestrictionCommitment.appointmentEpochMs())}. " +
                        "Every restriction stays active until then.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Opens in ${formatCountdown(waitRemaining)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "You cannot move the appointment earlier, restart the wait for an " +
                        "earlier unlock, or cancel it. Another request is blocked while this one is pending.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            RestrictionCommitment.State.UNLOCK_WINDOW -> {
                Text(
                    text = "The 1-minute unlock window is open. Confirm before ${formatClock(windowEnd)}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Closes in ${formatCountdown(windowRemaining)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = "Confirm unlock",
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Confirming does not turn anything off automatically. It only makes " +
                        "the controls editable until the window closes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            RestrictionCommitment.State.CONFIRMED -> {
                Text(
                    text = "Confirmed. Controls are editable until ${formatClock(windowEnd)}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Window closes in ${formatCountdown(windowRemaining)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "When this window closes the controls lock again. Confirming does not " +
                        "start a fresh minute.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            RestrictionCommitment.State.MISSED -> {
                Text(
                    text = "You missed the appointment window. All restrictions are still " +
                        "enabled and the window cannot be reopened.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "To try again you must file a completely new request and wait a full " +
                        "six hours. There is no grace period and no late unlock.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = "Request another 6 hours",
                    onClick = onRequest,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun RestrictionControl(
    title: String,
    description: String,
    checked: Boolean,
    serviceEnabled: Boolean,
    canWeaken: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val locked = checked && !canWeaken
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = !checked || canWeaken
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(
                text = if (checked) "Enabled" else "Off",
                containerColor = if (checked) scheme.primaryContainer else scheme.surfaceVariant,
                contentColor = if (checked) scheme.onPrimaryContainer else scheme.onSurfaceVariant
            )
            StatusPill(
                text = if (serviceEnabled) "Service on" else "Service off",
                containerColor = if (serviceEnabled) scheme.primaryContainer else scheme.errorContainer,
                contentColor = if (serviceEnabled) scheme.onPrimaryContainer else scheme.onErrorContainer
            )
            if (locked) {
                StatusPill(
                    text = "Locked",
                    containerColor = scheme.errorContainer,
                    contentColor = scheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun ChipRow(text: String, locked: Boolean, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(8.dp))
        Surface(
            onClick = onRemove,
            enabled = !locked,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(
                modifier = Modifier.size(34.dp),
                contentAlignment = Alignment.Center
            ) {
                IconClose(tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
