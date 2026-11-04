package com.example.focusesp

/**
 * Pure detection-engine models. No Android framework types so the engine is
 * independently unit-testable on the JVM.
 */

enum class Decision { BLOCK, ALLOW, UNKNOWN }

data class BlockDecision(
    val decision: Decision,
    val confidence: Float,
    val ruleId: String?,
    val reason: String,
    val matchedRuleIds: List<String> = emptyList()
)

data class DetectedNode(
    val viewId: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val className: String? = null,
    val children: List<DetectedNode> = emptyList()
)

data class ScreenSnapshot(
    val packageName: String,
    val windowClassName: String?,
    val roots: List<DetectedNode>,
    val nodeCount: Int,
    val truncated: Boolean,
    val capturedAtMs: Long
)

data class EvalConfig(
    val blockYoutubeShorts: Boolean,
    val blockInstagramReels: Boolean,
    val browserKeywords: List<String>,
    val allowedChannels: List<String>
)
