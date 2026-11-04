package com.example.focusesp

/**
 * Pure, Compose-free formatting and redaction for [DetectionPipeline].
 *
 * Kept separate so it can be unit-tested on the JVM without initializing any
 * Compose runtime state (the observable pipeline itself lives in
 * [DetectionPipeline]).
 */
object PipelineFormat {

    const val MAX_REASON = 120

    /** Immutable snapshot of the pipeline state, decoupled from Compose. */
    data class PipelineState(
        val serviceConnected: Boolean,
        val lastEventType: String?,
        val lastEventPackage: String?,
        val foregroundPackage: String?,
        val restrictionEnabled: Boolean,
        val blockingActive: Boolean,
        val capturedPackage: String?,
        val windowClassName: String?,
        val treeNodeCount: Int,
        val treeTruncated: Boolean,
        val treeEmpty: Boolean,
        val matchedBlockRules: List<String>,
        val matchedAllowRule: String?,
        val decision: Decision?,
        val confidence: Float,
        val enforcement: EnforcementOutcome,
        val enforcementDetail: String,
        val lastFailure: String?,
        val reason: String
    )

    /** Pure formatter so the diagnostics layout is unit-testable without Compose. */
    fun formatSummary(s: PipelineState): List<String> {
        val pct = (s.confidence * 100).toInt()
        return listOf(
            "Service: ${if (s.serviceConnected) "connected" else "disconnected"}",
            "Last event: ${s.lastEventType ?: "-"}  pkg=${s.lastEventPackage ?: "-"}",
            "Restriction enabled: ${s.restrictionEnabled}",
            "Blocking active: ${s.blockingActive}",
            "Foreground: ${s.foregroundPackage ?: "-"}",
            "Capture: pkg=${s.capturedPackage ?: "-"} nodes=${s.treeNodeCount}" +
                (if (s.treeTruncated) " (truncated)" else "") +
                (if (s.treeEmpty) " (empty)" else ""),
            "Window class: ${s.windowClassName ?: "-"}",
            "Matched block: ${if (s.matchedBlockRules.isEmpty()) "-" else s.matchedBlockRules.joinToString("+")}",
            "Matched allow: ${s.matchedAllowRule ?: "-"}",
            "Decision: ${s.decision ?: "-"} conf=$pct%",
            "Enforcement: ${s.enforcement.name}" +
                (if (s.enforcementDetail.isBlank()) "" else " — ${s.enforcementDetail}"),
            "Last failure: ${s.lastFailure ?: "-"}",
            "Reason: ${s.reason}"
        )
    }

    /**
     * Collapses whitespace and bounds length. Diagnostics must never carry
     * screen text, titles or URLs; this is a defensive guard on top of only
     * ever passing rule ids, package names and short reasons.
     */
    fun redact(value: String): String {
        val collapsed = value.replace(Regex("\\s+"), " ").trim()
        return if (collapsed.length > MAX_REASON) collapsed.take(MAX_REASON) + "…" else collapsed
    }
}
