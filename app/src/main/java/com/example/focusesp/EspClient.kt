package com.example.focusesp

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

data class EspResult(val success: Boolean, val message: String)

object EspClient {

    private const val CONNECT_TIMEOUT_MS = 3000
    private const val READ_TIMEOUT_MS = 3000

    private val lastBuzzAt = AtomicLong(0L)

    fun cooldownRemainingMs(): Long {
        val elapsed = SystemClock.elapsedRealtime() - lastBuzzAt.get()
        val cooldown = EspConfig.cooldownSeconds.value * 1000L
        return (cooldown - elapsed).coerceAtLeast(0L)
    }

    suspend fun health(): EspResult {
        if (EspConfig.mock.value) return EspResult(true, "Mock: /health OK")
        return request("/health")
    }

    suspend fun buzz(force: Boolean): EspResult {
        if (EspConfig.mock.value) {
            lastBuzzAt.set(SystemClock.elapsedRealtime())
            return EspResult(true, "Mock: /buzz sent")
        }
        val remaining = cooldownRemainingMs()
        if (!force && remaining > 0) {
            return EspResult(false, "Cooldown active (${remaining / 1000}s)")
        }
        lastBuzzAt.set(SystemClock.elapsedRealtime())
        return request("/buzz")
    }

    private suspend fun request(path: String): EspResult = withContext(Dispatchers.IO) {
        val host = EspConfig.host.value.trim()
        if (host.isEmpty()) return@withContext EspResult(false, "No IP address configured")
        val url = try {
            URL("http://$host:${EspConfig.port.value}$path")
        } catch (e: Exception) {
            return@withContext EspResult(false, "Invalid address")
        }
        var connection: HttpURLConnection? = null
        try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }?.trim().orEmpty()
            EspResult(code in 200..299, "HTTP $code ${body.take(120)}")
        } catch (e: Exception) {
            EspResult(false, e.message ?: "Network error")
        } finally {
            connection?.disconnect()
        }
    }
}
