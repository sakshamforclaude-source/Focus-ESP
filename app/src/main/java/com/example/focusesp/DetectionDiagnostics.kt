package com.example.focusesp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf

/**
 * Privacy-safe detection diagnostics.
 *
 * Only metadata is stored: timestamp, package, decision, rule id, confidence
 * and a coarse note. No node text, titles or descriptions are recorded.
 * Recording is a no-op unless enabled in the developer options
 * (see [ContentRestrictionStore.diagnosticsEnabled]).
 */
object DetectionDiagnostics {

    private const val MAX_ENTRIES = 25

    data class Entry(
        val timeMs: Long,
        val packageName: String,
        val decision: Decision,
        val ruleId: String?,
        val confidence: Float,
        val note: String
    )

    /** Observable so developer UI can recompose. */
    val entries = mutableStateListOf<Entry>()

    /** Bumped on every record; lets callers cheaply observe changes. */
    val revision = mutableStateOf(0)

    fun record(
        packageName: String,
        decision: Decision,
        ruleId: String?,
        confidence: Float,
        note: String = ""
    ) {
        entries.add(
            0,
            Entry(System.currentTimeMillis(), packageName, decision, ruleId, confidence, note)
        )
        while (entries.size > MAX_ENTRIES) {
            entries.removeAt(entries.size - 1)
        }
        revision.value += 1
    }

    fun summary(): String =
        entries.firstOrNull()?.let { e ->
            val pct = (e.confidence * 100).toInt()
            "${e.decision.name} pkg=${e.packageName} rule=${e.ruleId ?: "-"} conf=$pct% ${e.note}"
        } ?: "No content checks recorded yet."

    fun clear() {
        entries.clear()
        revision.value += 1
    }
}
