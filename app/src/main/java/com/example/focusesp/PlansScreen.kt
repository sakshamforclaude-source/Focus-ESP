package com.example.focusesp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.focusesp.ui.theme.SegmentedControl

/**
 * Schedules & Goals: the automation windows and the daily plan in one
 * destination, exposed as two segments, instead of two separate pages.
 */
@Composable
fun PlansScreen() {
    var segment by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        SegmentedControl(
            options = listOf("Schedules", "Goals"),
            selectedIndex = segment,
            onSelect = { segment = it },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)
        )

        Box(modifier = Modifier.padding(top = 8.dp).weight(1f)) {
            if (segment == 0) SchedulesSection() else GoalsSection()
        }
    }
}
