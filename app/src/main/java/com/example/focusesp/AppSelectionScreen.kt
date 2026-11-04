package com.example.focusesp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.focusesp.ui.theme.AppAvatar
import com.example.focusesp.ui.theme.BannerTone
import com.example.focusesp.ui.theme.CountBadge
import com.example.focusesp.ui.theme.GlassCard
import com.example.focusesp.ui.theme.IconBadge
import com.example.focusesp.ui.theme.IconCheck
import com.example.focusesp.ui.theme.IconClose
import com.example.focusesp.ui.theme.IconLock
import com.example.focusesp.ui.theme.IconSearch
import com.example.focusesp.ui.theme.IconShield
import com.example.focusesp.ui.theme.InfoBanner
import com.example.focusesp.ui.theme.SecondaryButton
import com.example.focusesp.ui.theme.SectionHeader
import com.example.focusesp.ui.theme.SegmentedControl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberLaunchableApps(): List<InstalledApp>? {
    val context = LocalContext.current
    val apps = remember { mutableStateOf<List<InstalledApp>?>(null) }
    LaunchedEffect(Unit) {
        apps.value = withContext(Dispatchers.IO) {
            InstalledAppsHelper.loadAndCache(context)
        }
    }
    return apps.value
}

@Composable
fun AppsSection() {
    val apps = rememberLaunchableApps()
    val mode = FocusPrefs.mode.value
    var query by remember { mutableStateOf("") }

    val ticking = FocusLock.isLocked()
    val now = rememberNowTicker(ticking)
    val locked = FocusLock.isLocked(now)
    val remaining = FocusLock.remainingSeconds(now)

    val filtered = remember(apps, query) {
        val base = apps ?: return@remember null
        if (query.isBlank()) {
            base
        } else {
            base.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (locked) {
            HardFocusLockedBanner(
                remainingSeconds = remaining,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        SectionHeader("Focus mode")
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (mode == FocusMode.BLOCKLIST) "Blocklist" else "Whitelist",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconBadge(accent = MaterialTheme.colorScheme.primary, size = 38.dp) {
                        IconShield(tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                SegmentedControl(
                    options = listOf("Blocklist", "Whitelist"),
                    selectedIndex = if (mode == FocusMode.BLOCKLIST) 0 else 1,
                    onSelect = { index ->
                        FocusPrefs.setMode(
                            if (index == 0) FocusMode.BLOCKLIST else FocusMode.WHITELIST
                        )
                    },
                    enabled = !locked
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (mode == FocusMode.BLOCKLIST) {
                        "Selected apps go to Home during Focus. Everything else stays usable."
                    } else {
                        "Everything is blocked during Focus except the apps you allow."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (mode == FocusMode.WHITELIST && FocusPrefs.blockedPackages.isEmpty() && !locked) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "No apps allowed yet. With an empty list in whitelist mode, every " +
                            "app is blocked during Focus.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingIcon = { IconSearch(tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconClose(
                            modifier = Modifier.clickable { query = "" },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else null,
                singleLine = true,
                enabled = apps != null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (mode == FocusMode.BLOCKLIST) "Blocked" else "Allowed",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = 8.dp)
                )
                CountBadge(FocusPrefs.blockedPackages.size)
                Spacer(Modifier.weight(1f))
                SecondaryButton(
                    text = "Select all",
                    onClick = {
                        val all = apps?.map { it.packageName } ?: return@SecondaryButton
                        FocusPrefs.setBlockedBatch(all, true)
                    },
                    enabled = apps != null && !locked,
                    modifier = Modifier.height(40.dp)
                )
                Spacer(Modifier.width(8.dp))
                SecondaryButton(
                    text = "Clear",
                    onClick = { FocusPrefs.clearBlocked() },
                    enabled = FocusPrefs.blockedPackages.isNotEmpty() && !locked,
                    modifier = Modifier.height(40.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        AppChecklist(
            apps = filtered,
            selectedPackages = FocusPrefs.blockedPackages.toList(),
            onToggle = { pkg, checked -> FocusPrefs.setBlocked(pkg, checked) },
            readOnly = locked,
            positiveLabel = if (mode == FocusMode.BLOCKLIST) "Blocked" else "Allowed",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HardFocusLockedBanner(remainingSeconds: Long, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier, accent = MaterialTheme.colorScheme.error) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(accent = MaterialTheme.colorScheme.error, size = 40.dp) {
                IconLock(tint = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                SectionHeader("Hard Focus", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = FocusTimer.formatTime(remainingSeconds.toInt()),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "The app list is frozen for this session. Editing, mode switching and " +
                "Select/Clear are disabled until the countdown ends.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AppChecklist(
    apps: List<InstalledApp>?,
    selectedPackages: List<String>,
    onToggle: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    positiveLabel: String = "Blocked"
) {
    when {
        apps == null -> GlassCard(
            modifier = modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Loading apps…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        apps.isEmpty() -> GlassCard(
            modifier = modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = "No matching apps found.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(apps, key = { it.packageName }) { app ->
                val checked = selectedPackages.contains(app.packageName)
                val shape = RoundedCornerShape(14.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(
                            if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
                        )
                        .border(
                            BorderStroke(
                                1.dp,
                                if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.outlineVariant
                            ),
                            shape
                        )
                        .clickable(enabled = !readOnly) { onToggle(app.packageName, !checked) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppAvatar(label = app.label, icon = app.icon)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (checked) {
                        Text(
                            text = positiveLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    Checkbox(
                        checked = checked,
                        enabled = !readOnly,
                        onCheckedChange = { onToggle(app.packageName, it) }
                    )
                }
            }
        }
    }
}
