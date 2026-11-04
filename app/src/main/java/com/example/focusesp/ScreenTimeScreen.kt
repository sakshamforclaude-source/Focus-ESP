package com.example.focusesp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.focusesp.ui.theme.AppAvatar
import com.example.focusesp.ui.theme.BannerTone
import com.example.focusesp.ui.theme.Dot
import com.example.focusesp.ui.theme.EmptyState
import com.example.focusesp.ui.theme.FilterChip
import com.example.focusesp.ui.theme.GlassCard
import com.example.focusesp.ui.theme.IconChart
import com.example.focusesp.ui.theme.IconEye
import com.example.focusesp.ui.theme.IconRefresh
import com.example.focusesp.ui.theme.InfoBanner
import com.example.focusesp.ui.theme.MiniBarChart
import com.example.focusesp.ui.theme.NumberStepper
import com.example.focusesp.ui.theme.PrimaryButton
import com.example.focusesp.ui.theme.ProgressRing
import com.example.focusesp.ui.theme.SecondaryButton
import com.example.focusesp.ui.theme.SectionHeader
import com.example.focusesp.ui.theme.UsageBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ChartColors = listOf(
    Color(0xFF60A5FA),
    Color(0xFF38BDF8),
    Color(0xFF22D3EE),
    Color(0xFF2DD4BF),
    Color(0xFF818CF8)
)
private val ChartRestColor = Color(0xFF334155)

private enum class UsageFilter(val label: String) {
    ALL("All apps"),
    BLOCKED("Blocked"),
    ALLOWED("Allowed")
}

@Composable
fun ScreenTimeScreen(
    usageAccess: Boolean,
    onOpenUsageAccess: () -> Unit
) {
    val context = LocalContext.current
    var usage by remember { mutableStateOf<List<AppUsage>?>(null) }
    var weekly by remember { mutableStateOf<List<DailyUsage>>(emptyList()) }
    var icons by remember { mutableStateOf<Map<String, android.graphics.drawable.Drawable>>(emptyMap()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var lastUpdated by remember { mutableStateOf<Long?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf(UsageFilter.ALL) }

    LaunchedEffect(usageAccess, refreshKey) {
        if (!usageAccess) {
            usage = null
            return@LaunchedEffect
        }
        refreshing = true
        loadError = null
        try {
            val loaded = withContext(Dispatchers.IO) {
                val list = UsageStatsHelper.todayUsage(context)
                val week = UsageStatsHelper.lastSevenDays(context)
                val iconMap = mutableMapOf<String, android.graphics.drawable.Drawable>()
                list.forEach { entry ->
                    try {
                        iconMap[entry.packageName] =
                            context.packageManager.getApplicationIcon(entry.packageName)
                    } catch (_: Exception) {
                        // Icon unavailable — rows fall back to initials.
                    }
                }
                Triple(list, week, iconMap)
            }
            usage = loaded.first
            weekly = loaded.second
            icons = loaded.third
            lastUpdated = System.currentTimeMillis()
        } catch (e: Exception) {
            loadError = e.message ?: "Unknown error"
            usage = null
        }
        refreshing = false
    }

    if (!usageAccess) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassCard(accent = MaterialTheme.colorScheme.primary) {
                SectionHeader("Usage access required")
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Screen time is computed from Android's Usage Stats, which needs a " +
                        "one-time permission. FocusESP only reads this on your device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                PermissionStep(1, "Open Usage Access settings")
                PermissionStep(2, "Find FocusESP in the list")
                PermissionStep(3, "Turn the toggle on, then come back")
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = "Open Usage Access settings",
                    onClick = onOpenUsageAccess,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        return
    }

    val list = usage
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    SectionHeader("Today")
                    Text(
                        text = lastUpdated?.let { "Updated ${formatClock(it)}" } ?: "Reading usage…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SecondaryRefresh(onClick = { refreshKey++ }, enabled = !refreshing)
            }
        }

        when {
            list == null && loadError == null -> item {
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            Column {
                                Text(
                                    text = "Loading today's usage…",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(10.dp))
                                UsageBar(fraction = 1f)
                            }
                        }
                    }
                }
            }

            loadError != null -> item {
                InfoBanner(
                    tone = BannerTone.DANGER,
                    title = "Couldn't read usage data",
                    text = loadError ?: "Unknown error",
                    icon = { IconChart(tint = MaterialTheme.colorScheme.error) }
                )
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    text = "Try again",
                    onClick = { refreshKey++ },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            list != null && list.isEmpty() -> item {
                GlassCard {
                    EmptyState(
                        title = "No usage recorded yet today",
                        subtitle = "Usage appears here after you use other apps for a while.",
                        icon = { IconEye(tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    )
                }
            }

            list != null -> {
                val total = list.sumOf { it.timeInForegroundMillis }
                val top = list.take(5)
                val topTotal = top.sumOf { it.timeInForegroundMillis }
                val rest = total - topTotal

                item {
                    SummaryCard(
                        total = total,
                        list = list,
                        top = top,
                        rest = rest,
                        lastUpdated = lastUpdated
                    )
                }

                item {
                    GoalCard(total = total)
                }

                item {
                    WeeklyCard(weekly = weekly)
                }

                item {
                    Column {
                        SectionHeader("By app", modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            UsageFilter.entries.forEach { option ->
                                FilterChip(
                                    text = option.label,
                                    selected = filter == option,
                                    onClick = { filter = option }
                                )
                            }
                        }
                    }
                }

                val blocked = FocusPrefs.blockedPackages
                val filtered = when (filter) {
                    UsageFilter.ALL -> list
                    UsageFilter.BLOCKED -> list.filter { it.packageName in blocked }
                    UsageFilter.ALLOWED -> list.filter { it.packageName !in blocked }
                }

                if (filtered.isEmpty()) {
                    item {
                        GlassCard {
                            EmptyState(
                                title = when (filter) {
                                    UsageFilter.ALL -> "No apps to show"
                                    UsageFilter.BLOCKED -> "No blocked apps used today"
                                    UsageFilter.ALLOWED -> "No allowed apps used today"
                                },
                                icon = { IconEye(tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            )
                        }
                    }
                } else {
                    val maxMillis = filtered.firstOrNull()?.timeInForegroundMillis ?: 0L
                    itemsIndexed(filtered, key = { _, app -> app.packageName }) { index, app ->
                        UsageRow(
                            rank = index + 1,
                            app = app,
                            icon = icons[app.packageName],
                            maxMillis = maxMillis,
                            blocked = app.packageName in blocked
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SummaryCard(
    total: Long,
    list: List<AppUsage>,
    top: List<AppUsage>,
    rest: Long,
    lastUpdated: Long?
) {
    val topShare = if (total > 0) {
        (top.firstOrNull()?.timeInForegroundMillis ?: 0L).toFloat() / total
    } else {
        0f
    }
    GlassCard(accent = MaterialTheme.colorScheme.primary) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress = topShare,
                size = 100.dp,
                strokeWidth = 9.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${(topShare * 100).toInt()}%",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "top app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                SectionHeader("Total screen time")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatNaturalDuration(total),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${list.size} app${if (list.size == 1) "" else "s"} used" +
                        (lastUpdated?.let { " · ${formatClock(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (total > 0) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(50))
            ) {
                top.forEachIndexed { index, entry ->
                    Box(
                        modifier = Modifier
                            .weight(entry.timeInForegroundMillis.toFloat())
                            .fillMaxHeight()
                            .background(ChartColors[index % ChartColors.size])
                    )
                }
                if (rest > 0) {
                    Box(
                        modifier = Modifier
                            .weight(rest.toFloat())
                            .fillMaxHeight()
                            .background(ChartRestColor)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            top.forEachIndexed { index, entry ->
                LegendRow(
                    color = ChartColors[index % ChartColors.size],
                    label = entry.label,
                    value = formatNaturalDuration(entry.timeInForegroundMillis)
                )
            }
            if (rest > 0) {
                LegendRow(
                    color = ChartRestColor,
                    label = "All other apps",
                    value = formatNaturalDuration(rest),
                    muted = true
                )
            }
        }
    }
}

@Composable
private fun GoalCard(total: Long) {
    val goalMinutes = ScreenTimeStore.dailyGoalMinutes.value
    val goalMillis = goalMinutes * 60_000L
    val over = goalMinutes > 0 && total > goalMillis

    GlassCard(accent = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SectionHeader("Daily screen-time goal")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        goalMinutes <= 0 -> "No goal set"
                        over -> "Over by ${formatNaturalDuration(total - goalMillis)}"
                        else -> "${formatNaturalDuration(goalMillis - total)} left today"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (goalMinutes > 0) {
            Spacer(Modifier.height(12.dp))
            UsageBar(
                fraction = (total.toFloat() / goalMillis.toFloat()).coerceIn(0f, 1f),
                barColor = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${formatNaturalDuration(total)} of ${formatNaturalDuration(goalMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Goal",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            NumberStepper(
                value = goalMinutes,
                onValueChange = { ScreenTimeStore.setDailyGoalMinutes(it) },
                min = 0,
                max = 24 * 60,
                step = 15,
                valueWidth = 72.dp,
                valueFormatter = { if (it <= 0) "Off" else formatNaturalDuration(it * 60_000L) }
            )
        }

        if (goalMinutes > 0) {
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                text = "Turn off goal",
                onClick = { ScreenTimeStore.setDailyGoalMinutes(0) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun WeeklyCard(weekly: List<DailyUsage>) {
    if (weekly.isEmpty()) return
    val maxMillis = weekly.maxOfOrNull { it.totalMillis } ?: 0L
    val values = weekly.map { if (maxMillis > 0) it.totalMillis.toFloat() / maxMillis else 0f }
    val labels = weekly.map { dayLabel(it.dayStartMillis) }
    val totalWeek = weekly.sumOf { it.totalMillis }
    val todayIndex = weekly.lastIndex

    GlassCard(accent = MaterialTheme.colorScheme.tertiary) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                SectionHeader("Last 7 days")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatNaturalDuration(totalWeek),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "${labels.getOrElse(todayIndex) { "" }} · today",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(16.dp))
        MiniBarChart(
            values = values,
            labels = labels,
            barColor = MaterialTheme.colorScheme.tertiary,
            highlightIndex = todayIndex
        )
    }
}

@Composable
private fun UsageRow(
    rank: Int,
    app: AppUsage,
    icon: android.graphics.drawable.Drawable?,
    maxMillis: Long,
    blocked: Boolean
) {
    GlassCard(contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = rank.toString().padStart(2, '0'),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(22.dp)
            )
            AppAvatar(label = app.label, icon = icon, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Text(
                        text = formatNaturalDuration(app.timeInForegroundMillis),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (blocked) {
                        Text(
                            text = "Blocked",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(8.dp))
                UsageBar(
                    fraction = if (maxMillis > 0) {
                        app.timeInForegroundMillis.toFloat() / maxMillis
                    } else {
                        0f
                    },
                    barColor = if (rank - 1 < ChartColors.size) ChartColors[rank - 1] else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SecondaryRefresh(onClick: () -> Unit, enabled: Boolean) {
    TextButton(onClick = onClick, enabled = enabled) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconRefresh(tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(if (enabled) "Refresh" else "Refreshing…")
        }
    }
}

@Composable
private fun LegendRow(
    color: Color,
    label: String,
    value: String,
    muted: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Dot(color = color)
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PermissionStep(number: Int, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatClock(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

private fun dayLabel(dayStartMillis: Long): String =
    SimpleDateFormat("EEE", Locale.getDefault()).format(Date(dayStartMillis))
