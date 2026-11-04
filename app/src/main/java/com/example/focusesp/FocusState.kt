package com.example.focusesp

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FocusState {

    private const val MAX_LOG_ENTRIES = 40

    val lastPackage = mutableStateOf<String?>(null)
    val log = mutableStateListOf<String>()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun onForegroundAppChanged(pkg: String, className: String) {
        if (lastPackage.value == pkg) return
        lastPackage.value = pkg
        addLog("Foreground: $pkg")
    }

    fun onServiceConnected() {
        addLog("Service connected")
    }

    fun onServiceInterrupt() {
        addLog("Service interrupted")
    }

    fun onBlocked(pkg: String) {
        addLog("Blocked: $pkg")
    }

    fun onViolation(reason: String) {
        addLog("Violation: $reason")
    }

    fun addEvent(message: String) {
        addLog(message)
    }

    fun clearLog() {
        log.clear()
    }

    private fun addLog(message: String) {
        val entry = "${timeFormat.format(Date())}  $message"
        val top = log.firstOrNull()
        if (top != null && top.substringAfter("  ") == message) {
            log[0] = entry
            return
        }
        log.add(0, entry)
        while (log.size > MAX_LOG_ENTRIES) {
            log.removeAt(log.size - 1)
        }
    }
}
