package com.example.focusesp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.focusesp.ui.theme.IconBadge
import com.example.focusesp.ui.theme.IconBolt
import com.example.focusesp.ui.theme.IconCalendar
import com.example.focusesp.ui.theme.IconChart
import com.example.focusesp.ui.theme.IconShield
import com.example.focusesp.ui.theme.IconTimer
import com.example.focusesp.ui.theme.StatusPill

/**
 * The five top-level destinations. Labels are deliberately short; the header
 * carries the longer title + supporting line so the bottom bar stays quiet.
 */
enum class Screen(val title: String, val subtitle: String, val tabLabel: String) {
    SCREEN_TIME("Screen time", "Today's usage and your daily goal", "Screen time"),
    TIMER("Focus timer", "Stopwatch, countdown and Pomodoro", "Timer"),
    BLOCKING("App blocking", "Blocklist, whitelist and content", "Blocking"),
    PLANS("Schedules & goals", "Automatic windows and daily plan", "Plans"),
    ESP("ESP32", "Local Wi-Fi wearable", "ESP32")
}

@Composable
fun FocusEspApp(
    serviceEnabled: Boolean,
    usageAccess: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenUsageAccess: () -> Unit
) {
    var screen by remember { mutableStateOf(Screen.SCREEN_TIME) }

    BackHandler(enabled = screen != Screen.SCREEN_TIME) {
        screen = Screen.SCREEN_TIME
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            FocusBackdrop()
            Column(modifier = Modifier.fillMaxSize()) {
                AppHeader(
                    screen = screen,
                    serviceEnabled = serviceEnabled,
                    usageAccess = usageAccess,
                    modifier = Modifier.statusBarsPadding()
                )
                Box(modifier = Modifier.weight(1f)) {
                    when (screen) {
                        Screen.SCREEN_TIME -> ScreenTimeScreen(
                            usageAccess = usageAccess,
                            onOpenUsageAccess = onOpenUsageAccess
                        )
                        Screen.TIMER -> FocusTimerScreen(
                            serviceEnabled = serviceEnabled,
                            onOpenAccessibility = onOpenAccessibility
                        )
                        Screen.BLOCKING -> AppBlockingScreen(
                            serviceEnabled = serviceEnabled,
                            onOpenAccessibility = onOpenAccessibility
                        )
                        Screen.PLANS -> PlansScreen()
                        Screen.ESP -> EspScreen()
                    }
                }
                BottomBar(
                    current = screen,
                    onSelect = { screen = it },
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        }
    }
}

/**
 * Very restrained backdrop: a soft accent wash at the top with a single faint
 * cyan glow, so the primary content still reads as the focus of the screen.
 */
@Composable
private fun FocusBackdrop() {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize().background(scheme.background)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to scheme.primary.copy(alpha = 0.06f),
                        560f to Color.Transparent
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(scheme.tertiary.copy(alpha = 0.04f), Color.Transparent),
                        center = Offset(120f, 1700f),
                        radius = 800f
                    )
                )
        )
    }
}

@Composable
private fun AppHeader(
    screen: Screen,
    serviceEnabled: Boolean,
    usageAccess: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(accent = MaterialTheme.colorScheme.primary, size = 40.dp) {
            ScreenIcon(screen = screen, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = screen.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = screen.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (screen == Screen.SCREEN_TIME) {
            StatusPill(
                text = if (usageAccess) "Access on" else "No access",
                containerColor = if (usageAccess) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
                contentColor = if (usageAccess) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )
        } else {
            StatusPill(
                text = if (serviceEnabled) "Service on" else "Service off",
                containerColor = if (serviceEnabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
                contentColor = if (serviceEnabled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )
        }
    }
}

@Composable
private fun BottomBar(
    current: Screen,
    onSelect: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = scheme.surfaceContainer.copy(alpha = 0.97f)
    ) {
        NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
            Screen.entries.forEach { destination ->
                NavigationBarItem(
                    selected = destination == current,
                    onClick = { onSelect(destination) },
                    icon = { ScreenIcon(destination, LocalContentColor.current) },
                    label = { Text(destination.tabLabel, style = MaterialTheme.typography.labelSmall) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = scheme.primary,
                        selectedTextColor = scheme.primary,
                        indicatorColor = scheme.primaryContainer,
                        unselectedIconColor = scheme.onSurfaceVariant,
                        unselectedTextColor = scheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun ScreenIcon(screen: Screen, tint: Color) {
    when (screen) {
        Screen.SCREEN_TIME -> IconChart(tint = tint)
        Screen.TIMER -> IconTimer(tint = tint)
        Screen.BLOCKING -> IconShield(tint = tint)
        Screen.PLANS -> IconCalendar(tint = tint)
        Screen.ESP -> IconBolt(tint = tint)
    }
}

// ---------------------------------------------------------------------------
// Shared formatting helpers
// ---------------------------------------------------------------------------

internal fun minutesToLabel(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return String.format("%02d:%02d", h, m)
}

internal fun formatNaturalDuration(millis: Long): String {
    val totalMinutes = millis / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "$hours hr $minutes min"
        hours > 0 -> "$hours hr"
        minutes > 0 -> "$minutes min"
        else -> "under 1 min"
    }
}
