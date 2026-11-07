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
    val children: List<DetectedNode> = emptyList(),
    val isSelected: Boolean = false
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

enum class ReelsEvidenceOutcome {
    /** The conservative block combination matched; this is not device-verified proof. */
    CONFIRMED,

    /** Some Reels-related evidence exists, but not enough to block safely. */
    AMBIGUOUS,

    /** No Reels-specific evidence was found. */
    NONE
}

/**
 * Result of a conservative Instagram Reels inspection.
 *
 * [ReelsEvidenceOutcome.CONFIRMED] means the detector's conservative block
 * combination matched. It is not an on-device verified playback signal: the
 * available evidence does not prove that `clips_viewer` is showing Reels rather
 * than another Instagram video surface. Conflicting Story/DM evidence is always
 * reported as [ReelsEvidenceOutcome.AMBIGUOUS]. Truncation only forces
 * [ReelsEvidenceOutcome.AMBIGUOUS] when the required positive pair is
 * incomplete; both observed signals remain authoritative.
 *
 * [signals] contains only resource-ID / UI-state labels used by the detector;
 * it never contains user content such as captions, names or messages.
 */
data class InstagramReelsResult(
    val outcome: ReelsEvidenceOutcome,
    val confidence: Float,
    val reason: String,
    val signals: List<String> = emptyList()
)
