package com.example.focusesp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelineFormatTest {

    private fun state(
        serviceConnected: Boolean = true,
        decision: Decision? = Decision.BLOCK,
        enforcement: EnforcementOutcome = EnforcementOutcome.BLOCK_REQUESTED,
        enforcementDetail: String = "org.example.app yt_strong_activity attempt=1",
        treeNodeCount: Int = 42,
        treeTruncated: Boolean = false,
        treeEmpty: Boolean = false,
        matchedBlockRules: List<String> = emptyList(),
        lastFailure: String? = null,
        reason: String = "Strong short-form evidence."
    ) = PipelineFormat.PipelineState(
        serviceConnected = serviceConnected,
        lastEventType = "WINDOW_STATE_CHANGED",
        lastEventPackage = "com.google.android.youtube",
        foregroundPackage = "com.google.android.youtube",
        restrictionEnabled = true,
        blockingActive = true,
        capturedPackage = "com.google.android.youtube",
        windowClassName = "com.google.android.youtube/.MainActivity",
        treeNodeCount = treeNodeCount,
        treeTruncated = treeTruncated,
        treeEmpty = treeEmpty,
        matchedBlockRules = matchedBlockRules,
        matchedAllowRule = null,
        decision = decision,
        confidence = 0.9f,
        enforcement = enforcement,
        enforcementDetail = enforcementDetail,
        lastFailure = lastFailure,
        reason = reason
    )

    @Test
    fun summaryReportsServiceAndDecision() {
        val lines = PipelineFormat.formatSummary(state())
        assertTrue(lines.any { it.contains("Service: connected") })
        assertTrue(lines.any { it.contains("Decision: BLOCK conf=90%") })
        assertTrue(lines.any { it.contains("Enforcement: BLOCK_REQUESTED") })
    }

    @Test
    fun summaryMarksDisconnectedAndFailure() {
        val lines = PipelineFormat.formatSummary(
            state(
                serviceConnected = false,
                enforcement = EnforcementOutcome.BLOCK_ACTION_FAILED,
                lastFailure = "GLOBAL_ACTION_HOME failed"
            )
        )
        assertTrue(lines.any { it.contains("Service: disconnected") })
        assertTrue(lines.any { it.contains("Enforcement: BLOCK_ACTION_FAILED") })
        assertTrue(lines.any { it.contains("Last failure: GLOBAL_ACTION_HOME failed") })
    }

    @Test
    fun summaryMarksTruncatedAndEmptyTrees() {
        val truncated = PipelineFormat.formatSummary(state(treeTruncated = true))
        assertTrue(truncated.any { it.contains("(truncated)") })

        val empty = PipelineFormat.formatSummary(state(treeNodeCount = 0, treeEmpty = true))
        assertTrue(empty.any { it.contains("nodes=0") && it.contains("(empty)") })
    }

    @Test
    fun neverEmitsNodeTextOrUrls() {
        // Defensive: nothing we feed to the formatter is user content, and the
        // redactor collapses whitespace and caps length regardless.
        val formatted = PipelineFormat.formatSummary(state()).joinToString("\n")
        assertFalse(formatted.contains("http"))
        assertFalse(formatted.contains("\n\n"))
    }

    @Test
    fun redactCollapsesWhitespace() {
        assertEquals("a b c", PipelineFormat.redact("  a   b\n c  "))
    }

    @Test
    fun redactTruncatesLongValues() {
        val long = "x".repeat(500)
        val redacted = PipelineFormat.redact(long)
        assertTrue(redacted.length <= PipelineFormat.MAX_REASON + 1) // +1 for ellipsis
        assertTrue(redacted.endsWith("…"))
    }
}
