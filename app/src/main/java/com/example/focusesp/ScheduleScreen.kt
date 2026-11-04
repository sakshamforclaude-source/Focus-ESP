package com.example.focusesp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.focusesp.ui.theme.EmptyState
import com.example.focusesp.ui.theme.GlassCard
import com.example.focusesp.ui.theme.IconPlus
import com.example.focusesp.ui.theme.InfoBanner
import com.example.focusesp.ui.theme.PrimaryButton
import com.example.focusesp.ui.theme.SecondaryButton
import com.example.focusesp.ui.theme.SectionHeader
import com.example.focusesp.ui.theme.SettingToggleRow
import com.example.focusesp.ui.theme.StatusPill
import com.example.focusesp.ui.theme.BannerTone
import java.util.Calendar
import java.util.UUID

private val DAY_LABELS = listOf(
    Calendar.MONDAY to "Mon",
    Calendar.TUESDAY to "Tue",
    Calendar.WEDNESDAY to "Wed",
    Calendar.THURSDAY to "Thu",
    Calendar.FRIDAY to "Fri",
    Calendar.SATURDAY to "Sat",
    Calendar.SUNDAY to "Sun"
)

@Composable
fun SchedulesSection() {
    var editing by remember { mutableStateOf<Schedule?>(null) }

    val current = editing
    if (current != null) {
        ScheduleEditor(
            initial = current,
            onSave = {
                if (ScheduleStore.schedules.any { it.id == current.id }) {
                    ScheduleStore.update(it)
                } else {
                    ScheduleStore.add(it)
                }
                editing = null
            },
            onCancel = { editing = null }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        InfoBanner(
            text = "During an active schedule, the selected apps are sent to Home. " +
                "Overnight windows (end before start) are supported.",
            tone = BannerTone.INFO
        )

        PrimaryButton(
            text = "Add schedule",
            onClick = {
                editing = Schedule(
                    id = UUID.randomUUID().toString(),
                    enabled = true,
                    startMinutes = 9 * 60,
                    endMinutes = 17 * 60,
                    days = setOf(
                        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                        Calendar.THURSDAY, Calendar.FRIDAY
                    ),
                    packages = emptySet()
                )
            },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { IconPlus(tint = MaterialTheme.colorScheme.onPrimary) }
        )

        if (ScheduleStore.schedules.isEmpty()) {
            EmptyState(
                title = "No schedules yet",
                subtitle = "Add a time window to block apps automatically."
            )
        }

        ScheduleStore.schedules.forEach { schedule ->
            GlassCard(accent = if (schedule.enabled) MaterialTheme.colorScheme.primary else null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${minutesToLabel(schedule.startMinutes)} – ${minutesToLabel(schedule.endMinutes)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = daySummary(schedule.days),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusPill(
                        text = if (schedule.enabled) "Enabled" else "Disabled",
                        containerColor = if (schedule.enabled) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (schedule.enabled) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
                StatusPill(text = "${schedule.packages.size} app(s)")
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SecondaryButton(
                        text = "Edit",
                        onClick = { editing = schedule },
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryButton(
                        text = "Delete",
                        onClick = { ScheduleStore.remove(schedule.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ScheduleEditor(
    initial: Schedule,
    onSave: (Schedule) -> Unit,
    onCancel: () -> Unit
) {
    var startText by remember { mutableStateOf(minutesToLabel(initial.startMinutes)) }
    var endText by remember { mutableStateOf(minutesToLabel(initial.endMinutes)) }
    var enabled by remember { mutableStateOf(initial.enabled) }
    var days by remember { mutableStateOf(initial.days) }
    var packages by remember { mutableStateOf(initial.packages) }
    val apps = rememberLaunchableApps()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader("Edit schedule", modifier = Modifier.padding(start = 4.dp))

        GlassCard {
            SettingToggleRow(
                title = "Enabled",
                description = "Apply this schedule when its window is active.",
                checked = enabled,
                onCheckedChange = { enabled = it }
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = startText,
                onValueChange = { startText = it },
                label = { Text("Start time (HH:MM)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = endText,
                onValueChange = { endText = it },
                label = { Text("End time (HH:MM)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        SectionHeader("Days", modifier = Modifier.padding(start = 4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DAY_LABELS.forEach { (value, label) ->
                val selected = days.contains(value)
                Text(
                    text = label.take(2),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                        .clickable {
                            days = if (selected) days - value else days + value
                        }
                        .padding(vertical = 10.dp)
                )
            }
        }

        SectionHeader("Blocked apps", modifier = Modifier.padding(start = 4.dp))
        GlassCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
            Column(modifier = Modifier.height(320.dp)) {
                AppChecklist(
                    apps = apps,
                    selectedPackages = packages.toList(),
                    onToggle = { pkg, checked ->
                        packages = if (checked) packages + pkg else packages - pkg
                    }
                )
            }
        }

        val valid = parseTime(startText) != null && parseTime(endText) != null
        if (!valid) {
            InfoBanner(
                text = "Enter times as HH:MM (24-hour).",
                tone = BannerTone.DANGER
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PrimaryButton(
                text = "Save",
                onClick = {
                    val start = parseTime(startText) ?: return@PrimaryButton
                    val end = parseTime(endText) ?: return@PrimaryButton
                    onSave(
                        initial.copy(
                            startMinutes = start,
                            endMinutes = end,
                            enabled = enabled,
                            days = days,
                            packages = packages
                        )
                    )
                },
                enabled = valid,
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(
                text = "Cancel",
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun parseTime(text: String): Int? {
    val parts = text.trim().split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

private fun daySummary(days: Set<Int>): String {
    if (days.isEmpty()) return "No days"
    if (days.size == 7) return "Every day"
    return DAY_LABELS.filter { days.contains(it.first) }.joinToString(", ") { it.second }
}
