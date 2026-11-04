package com.example.focusesp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Returns the current wall-clock time, refreshed every second while [active],
 * so countdown UIs recompose without tying into the timer's own tick loop.
 */
@Composable
fun rememberNowTicker(active: Boolean): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) {
        if (!active) {
            now = System.currentTimeMillis()
            return@LaunchedEffect
        }
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    return now
}
