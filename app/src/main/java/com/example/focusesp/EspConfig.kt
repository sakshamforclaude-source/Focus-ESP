package com.example.focusesp

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object EspConfig {

    private const val KEY_HOST = "esp_host"
    private const val KEY_PORT = "esp_port"
    private const val KEY_ENABLED = "esp_enabled"
    private const val KEY_MOCK = "esp_mock"
    private const val KEY_COOLDOWN = "esp_cooldown"
    private const val MAX_EVENTS = 30

    val host = mutableStateOf("")
    val port = mutableStateOf(80)
    val enabled = mutableStateOf(false)
    val mock = mutableStateOf(true)
    val cooldownSeconds = mutableStateOf(10)

    /**
     * Result of the last explicit connection probe. Transient: it reflects the
     * most recent /health check, not a guarantee that the wearable is reachable
     * right now. Never persisted, so a fresh launch starts as "not connected".
     */
    val connected = mutableStateOf(false)
    val lastCheckedAt = mutableStateOf(0L)

    /** Bounded, timestamped diagnostic history for the ESP32 page. */
    val events = mutableStateListOf<String>()

    fun init(context: Context) {
        LocalStore.init(context)
        host.value = LocalStore.getString(KEY_HOST, "")
        port.value = LocalStore.getInt(KEY_PORT, 80)
        enabled.value = LocalStore.getBoolean(KEY_ENABLED, false)
        mock.value = LocalStore.getBoolean(KEY_MOCK, true)
        cooldownSeconds.value = LocalStore.getInt(KEY_COOLDOWN, 10)
    }

    fun setHost(value: String) {
        host.value = value
        LocalStore.putString(KEY_HOST, value)
    }

    fun setPort(value: Int) {
        port.value = value
        LocalStore.putInt(KEY_PORT, value)
    }

    fun setEnabled(value: Boolean) {
        enabled.value = value
        LocalStore.putBoolean(KEY_ENABLED, value)
    }

    fun setMock(value: Boolean) {
        mock.value = value
        LocalStore.putBoolean(KEY_MOCK, value)
    }

    fun setCooldown(value: Int) {
        cooldownSeconds.value = value
        LocalStore.putInt(KEY_COOLDOWN, value)
    }

    fun setConnected(value: Boolean) {
        connected.value = value
        lastCheckedAt.value = System.currentTimeMillis()
    }

    /** Clears the connection state without touching saved settings. */
    fun disconnect() {
        connected.value = false
        lastCheckedAt.value = 0L
        logEvent("Disconnected")
    }

    @Synchronized
    fun logEvent(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        events.add(0, "$time  $message")
        while (events.size > MAX_EVENTS) {
            events.removeAt(events.size - 1)
        }
    }

    fun clearEvents() {
        events.clear()
    }
}
