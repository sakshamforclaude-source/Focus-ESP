package com.example.focusesp

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.focusesp.ui.theme.BannerTone
import com.example.focusesp.ui.theme.EmptyState
import com.example.focusesp.ui.theme.GlassCard
import com.example.focusesp.ui.theme.IconCheck
import com.example.focusesp.ui.theme.IconClose
import com.example.focusesp.ui.theme.IconTarget
import com.example.focusesp.ui.theme.InfoBanner
import com.example.focusesp.ui.theme.PrimaryButton
import com.example.focusesp.ui.theme.SecondaryButton
import com.example.focusesp.ui.theme.SectionHeader
import com.example.focusesp.ui.theme.SettingToggleRow
import com.example.focusesp.ui.theme.StatusPill
import com.example.focusesp.ui.theme.UsageBar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun GoalsSection() {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf<Long?>(null) }
    var remind by remember { mutableStateOf(false) }
    var speechError by remember { mutableStateOf<String?>(null) }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            spoken?.firstOrNull()?.let { text = it }
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val goals = GoalStore.goals
        val doneCount = goals.count { it.done }
        val fraction = if (goals.isEmpty()) 0f else doneCount.toFloat() / goals.size

        GlassCard(accent = MaterialTheme.colorScheme.primary) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    SectionHeader("Today's progress")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (goals.isEmpty()) {
                            "No goals yet"
                        } else {
                            "$doneCount of ${goals.size} completed"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconTarget(tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                }
            }
            if (goals.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                UsageBar(fraction = fraction)
            }
        }

        InfoBanner(
            text = "FocusESP cannot verify real-world completion. Mark goals Done yourself. " +
                "Reminders need notifications; if exact alarms are not allowed, reminders may " +
                "arrive a little late.",
            tone = BannerTone.INFO
        )

        SectionHeader("New goal", modifier = Modifier.padding(start = 4.dp))
        GlassCard {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Goal") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            speechError?.let {
                Spacer(Modifier.height(8.dp))
                InfoBanner(text = it, tone = BannerTone.DANGER)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SecondaryButton(
                    text = "Speak",
                    onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your goal")
                        }
                        try {
                            speechLauncher.launch(intent)
                        } catch (e: ActivityNotFoundException) {
                            speechError = "Speech recognition is not available on this device."
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = if (deadline == null) "Set deadline" else "Change deadline",
                    onClick = { pickDeadline(context, deadline) { deadline = it } },
                    modifier = Modifier.weight(1f)
                )
            }

            if (deadline != null) {
                Spacer(Modifier.height(10.dp))
                StatusPill(
                    text = "Due ${formatDateTime(deadline!!)}",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(Modifier.height(12.dp))
            SettingToggleRow(
                title = "Remind me at deadline",
                description = if (deadline == null) "Set a deadline first." else "Notify at the chosen time.",
                checked = remind,
                onCheckedChange = { remind = it },
                enabled = deadline != null
            )

            Spacer(Modifier.height(14.dp))
            PrimaryButton(
                text = "Add goal",
                onClick = {
                    if (text.isBlank()) return@PrimaryButton
                    val goal = Goal(
                        id = UUID.randomUUID().toString(),
                        text = text.trim(),
                        deadline = deadline,
                        done = false,
                        reminderEnabled = remind && deadline != null
                    )
                    GoalStore.add(goal)
                    if (remind && deadline != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        GoalStore.scheduleReminder(context, goal)
                    }
                    text = ""
                    deadline = null
                    remind = false
                },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { IconCheck(tint = MaterialTheme.colorScheme.onPrimary) }
            )
        }

        if (GoalStore.goals.isEmpty()) {
            EmptyState(
                title = "No goals yet",
                subtitle = "Add a goal above to track it here."
            )
        } else {
            SectionHeader("Your goals", modifier = Modifier.padding(start = 4.dp))
            GoalStore.goals.toList().forEach { goal ->
                GlassCard(accent = if (goal.done) MaterialTheme.colorScheme.primary else null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Checkbox(
                            checked = goal.done,
                            onCheckedChange = { checked ->
                                GoalStore.setDone(goal.id, checked)
                                if (checked) GoalStore.cancelReminder(context, goal)
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = goal.text,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (goal.done) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (goal.deadline != null) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Due ${formatDateTime(goal.deadline)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            StatusPill(
                                text = if (goal.done) "Done" else "Not done",
                                containerColor = if (goal.done) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                contentColor = if (goal.done) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        Surface(
                            onClick = {
                                GoalStore.cancelReminder(context, goal)
                                GoalStore.remove(goal.id)
                            },
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
                    Spacer(Modifier.height(12.dp))
                    SettingToggleRow(
                        title = "Reminder at deadline",
                        description = if (goal.deadline == null) {
                            "Set a deadline to enable reminders."
                        } else {
                            "Notify when this goal is due."
                        },
                        checked = goal.reminderEnabled,
                        enabled = goal.deadline != null,
                        onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            GoalStore.setReminderEnabled(context, goal.id, enabled)
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun pickDeadline(context: Context, current: Long?, onPicked: (Long) -> Unit) {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = current ?: System.currentTimeMillis()
    }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, day)
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    calendar.set(Calendar.HOUR_OF_DAY, hour)
                    calendar.set(Calendar.MINUTE, minute)
                    calendar.set(Calendar.SECOND, 0)
                    onPicked(calendar.timeInMillis)
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}

private fun formatDateTime(millis: Long): String {
    val format = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
    return format.format(Date(millis))
}
