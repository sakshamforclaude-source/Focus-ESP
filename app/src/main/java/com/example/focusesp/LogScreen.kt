package com.example.focusesp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.focusesp.ui.theme.BannerTone
import com.example.focusesp.ui.theme.EmptyState
import com.example.focusesp.ui.theme.GlassCard
import com.example.focusesp.ui.theme.IconChevronRight
import com.example.focusesp.ui.theme.InfoBanner
import com.example.focusesp.ui.theme.LogPanel
import com.example.focusesp.ui.theme.SecondaryButton
import com.example.focusesp.ui.theme.SectionHeader
import com.example.focusesp.ui.theme.StatusPill

/**
 * The service event log. Lives inside App Blocking ▸ Content so diagnostics sit
 * next to the restrictions they describe, rather than on their own page.
 */
@Composable
fun DetectionLogSection() {
    var expanded by remember { mutableStateOf(false) }
    val log = FocusState.log

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoBanner(
            text = "Records what the accessibility service observed: foreground app changes, " +
                "blocked apps, content-restriction hits and goal reminders. Duplicate " +
                "consecutive entries are collapsed to keep the list readable.",
            tone = BannerTone.INFO
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SecondaryButton(
                text = if (expanded) "Collapse details" else "Show details",
                onClick = { expanded = !expanded },
                enabled = log.isNotEmpty(),
                modifier = Modifier.weight(1f),
                leadingIcon = if (!expanded && log.isNotEmpty()) {
                    { IconChevronRight(tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else null
            )
            SecondaryButton(
                text = "Clear",
                onClick = { FocusState.clearLog() },
                enabled = log.isNotEmpty(),
                modifier = Modifier.weight(1f)
            )
        }

        when {
            log.isEmpty() -> EmptyState(
                title = "No events yet",
                subtitle = "Activity will appear here once Focus blocking is active."
            )
            !expanded -> GlassCard {
                Row(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader("Latest", modifier = Modifier.weight(1f))
                    StatusPill("${log.size} recorded")
                }
                Spacer(Modifier.height(10.dp))
                LogPanel(entries = listOf(log.first()), emphasizeFirst = true)
            }
            else -> GlassCard {
                Row(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader("All events", modifier = Modifier.weight(1f))
                    StatusPill("${log.size}")
                }
                Spacer(Modifier.height(10.dp))
                LogPanel(entries = log.toList())
            }
        }
    }
}
