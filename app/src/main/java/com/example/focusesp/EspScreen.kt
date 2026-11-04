package com.example.focusesp

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.focusesp.ui.theme.BannerTone
import com.example.focusesp.ui.theme.EmptyState
import com.example.focusesp.ui.theme.GlassCard
import com.example.focusesp.ui.theme.IconBadge
import com.example.focusesp.ui.theme.IconBolt
import com.example.focusesp.ui.theme.IconRefresh
import com.example.focusesp.ui.theme.InfoBanner
import com.example.focusesp.ui.theme.LogPanel
import com.example.focusesp.ui.theme.NumberStepper
import com.example.focusesp.ui.theme.PrimaryButton
import com.example.focusesp.ui.theme.SecondaryButton
import com.example.focusesp.ui.theme.SectionHeader
import com.example.focusesp.ui.theme.SettingToggleRow
import com.example.focusesp.ui.theme.StatusPill
import kotlinx.coroutines.launch

@Composable
fun EspScreen() {
    var portText by remember { mutableStateOf(EspConfig.port.value.toString()) }
    var result by remember { mutableStateOf<EspResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme

    val connected = EspConfig.connected.value
    val mock = EspConfig.mock.value
    val host = EspConfig.host.value
    val events = EspConfig.events.toList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- Connection status ----
        val statusText = when {
            mock -> "Mock mode"
            connected -> "Connected"
            else -> "Not connected"
        }
        val statusContainer = when {
            mock -> scheme.surfaceVariant
            connected -> scheme.primaryContainer
            EspConfig.enabled.value -> scheme.errorContainer
            else -> scheme.surfaceVariant
        }
        val statusContent = when {
            mock -> scheme.onSurfaceVariant
            connected -> scheme.onPrimaryContainer
            EspConfig.enabled.value -> scheme.onErrorContainer
            else -> scheme.onSurfaceVariant
        }
        GlassCard(accent = if (connected && !mock) scheme.primary else null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    accent = if (connected && !mock) scheme.primary else scheme.onSurfaceVariant,
                    size = 42.dp
                ) {
                    IconBolt(tint = if (connected && !mock) scheme.primary else scheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    SectionHeader("Wearable")
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (host.isBlank()) "No address set" else "$host:${EspConfig.port.value}",
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface
                    )
                }
                StatusPill(
                    text = statusText,
                    containerColor = statusContainer,
                    contentColor = statusContent
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Connection reflects the most recent /health check. Mock mode answers " +
                    "locally and never touches the network.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PrimaryButton(
                    text = if (connected) "Test again" else "Connect",
                    onClick = {
                        busy = true
                        result = null
                        scope.launch {
                            val r = EspClient.health()
                            EspConfig.setConnected(r.success)
                            EspConfig.logEvent(
                                if (r.success) "Connect OK (${r.message})"
                                else "Connect failed (${r.message})"
                            )
                            result = r
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    leadingIcon = { IconRefresh(tint = scheme.onPrimary) }
                )
                SecondaryButton(
                    text = "Disconnect",
                    onClick = {
                        EspConfig.disconnect()
                        result = null
                    },
                    enabled = connected,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ---- Settings ----
        SectionHeader("Settings", modifier = Modifier.padding(start = 4.dp))
        GlassCard {
            OutlinedTextField(
                value = host,
                onValueChange = { EspConfig.setHost(it) },
                label = { Text("IP address (e.g. 192.168.1.50)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = portText,
                onValueChange = {
                    portText = it
                    it.toIntOrNull()?.let { value -> EspConfig.setPort(value) }
                },
                label = { Text("Port") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(14.dp))
            SettingToggleRow(
                title = "Buzzer on violations",
                description = "Send a buzz command when a blocked app is detected.",
                checked = EspConfig.enabled.value,
                onCheckedChange = { EspConfig.setEnabled(it) }
            )
            Spacer(Modifier.height(8.dp))
            SettingToggleRow(
                title = "Mock mode",
                description = "Simulate the device without sending network requests.",
                checked = EspConfig.mock.value,
                onCheckedChange = { EspConfig.setMock(it) }
            )

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cooldown",
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurface
                    )
                    Text(
                        text = "Minimum seconds between buzz commands.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                NumberStepper(
                    value = EspConfig.cooldownSeconds.value,
                    onValueChange = { EspConfig.setCooldown(it) },
                    min = 0,
                    max = 300,
                    step = 1,
                    valueWidth = 44.dp,
                    valueFormatter = { "${it}s" }
                )
            }
        }

        // ---- Test ----
        SectionHeader("Test", modifier = Modifier.padding(start = 4.dp))
        GlassCard {
            PrimaryButton(
                text = "Test buzzer",
                onClick = {
                    busy = true
                    result = null
                    scope.launch {
                        val r = EspClient.buzz(force = true)
                        EspConfig.logEvent(
                            if (r.success) "Test buzz sent (${r.message})"
                            else "Test buzz failed (${r.message})"
                        )
                        result = r
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { IconBolt(tint = scheme.onPrimary) }
            )
            result?.let {
                Spacer(Modifier.height(12.dp))
                InfoBanner(
                    text = it.message,
                    tone = if (it.success) BannerTone.SUCCESS else BannerTone.DANGER
                )
            }
        }

        // ---- Diagnostics ----
        SectionHeader("Diagnostics", modifier = Modifier.padding(start = 4.dp))
        if (events.isEmpty()) {
            EmptyState(
                title = "No activity yet",
                subtitle = "Connect, test or trigger a violation to see events here."
            )
        } else {
            GlassCard {
                LogPanel(entries = events.take(12))
                Spacer(Modifier.height(12.dp))
                SecondaryButton(
                    text = "Clear log",
                    onClick = { EspConfig.clearEvents() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        InfoBanner(
            text = "The device is reachable only on the same local network. FocusESP sends a " +
                "single /buzz command per violation; there is no cloud relay.",
            tone = BannerTone.INFO
        )
        Spacer(Modifier.height(8.dp))
    }
}
