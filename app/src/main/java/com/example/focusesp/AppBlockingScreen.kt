package com.example.focusesp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.focusesp.ui.theme.GlassCard
import com.example.focusesp.ui.theme.PrimaryButton
import com.example.focusesp.ui.theme.SegmentedControl

/**
 * App Blocking: the app list and the content restrictions live in one
 * destination, exposed as two segments, instead of two separate pages.
 */
@Composable
fun AppBlockingScreen(
    serviceEnabled: Boolean,
    onOpenAccessibility: () -> Unit
) {
    var segment by remember { mutableIntStateOf(0) }
    val scheme = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxSize()) {
        SegmentedControl(
            options = listOf("Apps", "Content"),
            selectedIndex = segment,
            onSelect = { segment = it },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)
        )

        Box(modifier = Modifier.padding(top = 8.dp).weight(1f)) {
            if (segment == 0) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (!serviceEnabled) {
                        GlassCard(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            accent = scheme.error
                        ) {
                            Text(
                                text = "Accessibility service is off, so nothing is blocked yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.error
                            )
                            Spacer(Modifier.height(10.dp))
                            PrimaryButton(
                                text = "Open Accessibility settings",
                                onClick = onOpenAccessibility,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        AppsSection()
                    }
                }
            } else {
                ContentSection(serviceEnabled = serviceEnabled)
            }
        }
    }
}
