package com.example.focusesp

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Final outcome of an enforcement attempt for a single detection decision.
 * Distinct from [Decision]: a [Decision.BLOCK] is a *request* to enforce, not
 * proof that anything happened.
 */
enum class EnforcementOutcome {
    /** No enforcement attempted yet. */
    NONE,

    /** A decision was reached but enforcement did not apply (e.g. ALLOW). */
    NOT_APPLICABLE,

    /** The relevant restriction toggle is off, so content was not inspected. */
    RESTRICTION_DISABLED,

    /** Restrictions are on but no focus session is active, so nothing is enforced. */
    SERVICE_INACTIVE,

    /** Inspection produced UNKNOWN; no action is ever taken on UNKNOWN. */
    DETECTION_UNKNOWN,

    /** An identical action was already issued for this visit/screen. */
    SUPPRESSED_BY_GATE,

    /** The global Home action was requested and the platform accepted it. */
    BLOCK_REQUESTED,

    /** The global Home action reported failure, or the app stayed on screen. */
    BLOCK_ACTION_FAILED,

    /** A follow-up check confirmed the target screen is gone. */
    BLOCK_VERIFIED
}

/**
 * Structured, privacy-safe diagnostics for the content-restriction pipeline.
 *
 * This records *only metadata*: timestamps, package names, event types, tree
 * sizes, matched rule ids, confidences, the final decision and the enforcement
 * outcome. Node text, video titles, usernames, messages and the accessibility
 * tree itself are never stored. Any string that could carry user content is
 * passed through [redact].
 *
 * It is intentionally an app-global observable singleton so the service writes
 * to it and the diagnostics UI reads from it without extra plumbing.
 */
object DetectionPipeline {

    private const val MAX_EVENTS = 40

    /** Service lifecycle. */
    val serviceConnected = mutableStateOf(false)

    /** Last accessibility event observed by the service. */
    val lastEventAtMs = mutableStateOf(0L)
    val lastEventType = mutableStateOf<String?>(null)
    val lastEventPackage = mutableStateOf<String?>(null)

    /** Current gating state at the time of the last evaluation. */
    val foregroundPackage = mutableStateOf<String?>(null)
    val restrictionEnabled = mutableStateOf(false)
    val blockingActive = mutableStateOf(false)

    /** Capture summary (no content). */
    val capturedPackage = mutableStateOf<String?>(null)
    val windowClassName = mutableStateOf<String?>(null)
    val treeNodeCount = mutableStateOf(0)
    val treeTruncated = mutableStateOf(false)
    val treeEmpty = mutableStateOf(false)

    /** Evaluation result. */
    val matchedBlockRules = mutableStateListOf<String>()
    val matchedAllowRule = mutableStateOf<String?>(null)
    val decision = mutableStateOf<Decision?>(null)
    val confidence = mutableStateOf(0f)
    val reason = mutableStateOf("")

    /** Enforcement result. */
    val enforcement = mutableStateOf(EnforcementOutcome.NONE)
    val enforcementDetail = mutableStateOf("")

    /** Most recent failure, if any (exception or failed platform action). */
    val lastFailure = mutableStateOf<String?>(null)

    /** Bounded, human-readable pipeline event log (sanitized). */
    val events = mutableStateListOf<String>()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun onServiceConnected() {
        serviceConnected.value = true
        log("SERVICE_CONNECTED")
    }

    fun onServiceDisconnected() {
        serviceConnected.value = false
        log("SERVICE_DISCONNECTED")
    }

    fun onEvent(type: String, pkg: String) {
        lastEventAtMs.value = System.currentTimeMillis()
        lastEventType.value = type
        lastEventPackage.value = pkg
    }

    fun onGating(foreground: String?, enabled: Boolean, blockingActive: Boolean) {
        foregroundPackage.value = foreground
        restrictionEnabled.value = enabled
        this.blockingActive.value = blockingActive
    }

    /** Records a captured snapshot without recording any of its content. */
    fun onCapture(snapshot: ScreenSnapshot) {
        capturedPackage.value = snapshot.packageName
        windowClassName.value = snapshot.windowClassName
        treeNodeCount.value = snapshot.nodeCount
        treeTruncated.value = snapshot.truncated
        treeEmpty.value = snapshot.nodeCount == 0
    }

    fun onDecision(d: BlockDecision) {
        decision.value = d.decision
        confidence.value = d.confidence
        reason.value = redact(d.reason)
        matchedBlockRules.clear()
        matchedBlockRules.addAll(d.matchedRuleIds.filter { it != d.ruleId }.distinct())
        matchedAllowRule.value = if (d.decision == Decision.ALLOW) d.ruleId else null
    }

    fun onEnforcement(outcome: EnforcementOutcome, detail: String = "") {
        enforcement.value = outcome
        enforcementDetail.value = redact(detail)
        log("ENFORCE ${outcome.name}${if (detail.isBlank()) "" else " ${redact(detail)}"}")
    }

    fun onFailure(message: String) {
        val safe = redact(message)
        lastFailure.value = safe
        log("FAILURE $safe")
    }

    fun clear() {
        events.clear()
    }

    /** Full reset to a fresh, disconnected state (used by tests and sign-out). */
    fun reset() {
        serviceConnected.value = false
        lastEventAtMs.value = 0L
        lastEventType.value = null
        lastEventPackage.value = null
        foregroundPackage.value = null
        restrictionEnabled.value = false
        blockingActive.value = false
        capturedPackage.value = null
        windowClassName.value = null
        treeNodeCount.value = 0
        treeTruncated.value = false
        treeEmpty.value = false
        matchedBlockRules.clear()
        matchedAllowRule.value = null
        decision.value = null
        confidence.value = 0f
        reason.value = ""
        enforcement.value = EnforcementOutcome.NONE
        enforcementDetail.value = ""
        lastFailure.value = null
        events.clear()
    }

    private fun log(message: String) {
        events.add(0, "${timeFormat.format(Date())}  ${redact(message)}")
        while (events.size > MAX_EVENTS) {
            events.removeAt(events.size - 1)
        }
    }

    /** Structured, ready-to-display summary lines for the diagnostics UI. */
    fun summaryLines(): List<String> = PipelineFormat.formatSummary(
        PipelineFormat.PipelineState(
            serviceConnected = serviceConnected.value,
            lastEventType = lastEventType.value,
            lastEventPackage = lastEventPackage.value,
            foregroundPackage = foregroundPackage.value,
            restrictionEnabled = restrictionEnabled.value,
            blockingActive = blockingActive.value,
            capturedPackage = capturedPackage.value,
            windowClassName = windowClassName.value,
            treeNodeCount = treeNodeCount.value,
            treeTruncated = treeTruncated.value,
            treeEmpty = treeEmpty.value,
            matchedBlockRules = matchedBlockRules.toList(),
            matchedAllowRule = matchedAllowRule.value,
            decision = decision.value,
            confidence = confidence.value,
            enforcement = enforcement.value,
            enforcementDetail = enforcementDetail.value,
            lastFailure = lastFailure.value,
            reason = reason.value
        )
    )

    /** Delegates to [PipelineFormat.redact]; kept for internal call sites. */
    fun redact(value: String): String = PipelineFormat.redact(value)
}
