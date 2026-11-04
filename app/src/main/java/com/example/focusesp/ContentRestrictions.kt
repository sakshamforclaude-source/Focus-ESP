package com.example.focusesp

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Thin facade wiring the Android accessibility tree to the pure detection
 * engine ([NodeAdapter] + [RuleEvaluator] + [RuleRepository]).
 *
 * All decision logic lives in the pure, independently unit-tested engine; this
 * object only converts framework types and reads current user configuration.
 */
object ContentRestrictions {

    fun handlesPackage(packageName: String): Boolean =
        RuleRepository.appFor(packageName) != null

    fun currentConfig(): EvalConfig = EvalConfig(
        blockYoutubeShorts = ContentRestrictionStore.blockYoutubeShorts.value,
        blockInstagramReels = ContentRestrictionStore.blockInstagramReels.value,
        browserKeywords = ContentRestrictionStore.browserKeywords.toList(),
        allowedChannels = ContentRestrictionStore.allowedChannels.toList()
    )

    fun capture(
        root: AccessibilityNodeInfo?,
        packageName: String,
        windowClassName: String?
    ): ScreenSnapshot = NodeAdapter.capture(root, packageName, windowClassName)

    fun evaluate(snapshot: ScreenSnapshot): BlockDecision =
        RuleEvaluator.evaluate(snapshot, currentConfig())

    /** Human-readable explanation surfaced in the UI (developer diagnostics). */
    fun diagnose(snapshot: ScreenSnapshot, decision: BlockDecision): String {
        val app = RuleRepository.appFor(snapshot.packageName)
        if (app == null) {
            return "${snapshot.packageName} is not a monitored app (YouTube, Instagram or a known browser)."
        }
        if (snapshot.nodeCount == 0) {
            return "No accessibility content was returned for ${snapshot.packageName}. The app may block content access or the screen may not be loaded yet."
        }
        val pct = (decision.confidence * 100).toInt()
        val rule = decision.ruleId?.let { " rule=$it" } ?: ""
        val trunc = if (snapshot.truncated) " (tree truncated)" else ""
        return "${decision.decision}$rule conf=$pct% - ${decision.reason}$trunc " +
            "[${snapshot.nodeCount} nodes scanned]"
    }
}
