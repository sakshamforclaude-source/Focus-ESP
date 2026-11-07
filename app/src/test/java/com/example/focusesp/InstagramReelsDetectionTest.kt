package com.example.focusesp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramReelsDetectionTest {

    private val instagram = InstagramReelsDetector.INSTAGRAM_PACKAGE
    private val youtube = "com.google.android.youtube"

    private val detector = InstagramReelsDetector()

    private fun config(reels: Boolean = true) =
        EvalConfig(
            blockYoutubeShorts = true,
            blockInstagramReels = reels,
            browserKeywords = emptyList(),
            allowedChannels = emptyList()
        )

    private fun node(
        id: String? = null,
        text: String? = null,
        desc: String? = null,
        selected: Boolean = false,
        vararg children: DetectedNode
    ) = DetectedNode(
        viewId = id,
        text = text,
        contentDescription = desc,
        children = children.toList(),
        isSelected = selected
    )

    private fun snapshot(
        pkg: String = instagram,
        windowClass: String? = null,
        roots: List<DetectedNode> = emptyList(),
        truncated: Boolean = false
    ): ScreenSnapshot {
        var count = 0
        val queue = ArrayDeque(roots)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            count++
            current.children.forEach { queue.add(it) }
        }
        return ScreenSnapshot(pkg, windowClass, roots, count, truncated, 0L)
    }

    @Test
    fun selectedClipsTabWithViewerIsConfirmedAndBlocks() {
        val s = snapshot(
            roots = listOf(
                node(id = "com.instagram.android:id/clips_tab", selected = true),
                node(id = "com.instagram.android:id/clips_viewer")
            )
        )

        val result = detector.detect(s)
        val decision = RuleEvaluator.evaluate(s, config(), instagramReels = result)

        assertEquals(ReelsEvidenceOutcome.CONFIRMED, result.outcome)
        assertEquals(Decision.BLOCK, decision.decision)
        assertEquals(BlockRouting.INSTAGRAM_REELS_RULE_ID, decision.ruleId)
    }

    @Test
    fun clipsViewerAloneDoesNotBlock() {
        val s = snapshot(
            roots = listOf(node(id = "com.instagram.android:id/clips_viewer"))
        )

        val result = detector.detect(s)
        val decision = RuleEvaluator.evaluate(s, config(), instagramReels = result)

        assertEquals(ReelsEvidenceOutcome.NONE, result.outcome)
        assertEquals(Decision.ALLOW, decision.decision)
    }

    @Test
    fun normalVideoPostUsingClipsViewerDoesNotBlock() {
        val s = snapshot(
            roots = listOf(
                node(id = "com.instagram.android:id/feed_tab", selected = true),
                node(id = "com.instagram.android:id/clips_viewer"),
                node(text = "A normal video post")
            )
        )

        val result = detector.detect(s)
        val decision = RuleEvaluator.evaluate(s, config(), instagramReels = result)

        assertEquals(ReelsEvidenceOutcome.NONE, result.outcome)
        assertEquals(Decision.ALLOW, decision.decision)
    }

    @Test
    fun selectedClipsTabWithoutViewerIsAmbiguousAndUnknown() {
        val s = snapshot(
            roots = listOf(
                node(id = "com.instagram.android:id/clips_tab", selected = true)
            )
        )

        val result = detector.detect(s)
        val decision = RuleEvaluator.evaluate(s, config(), instagramReels = result)

        assertEquals(ReelsEvidenceOutcome.AMBIGUOUS, result.outcome)
        assertEquals(Decision.UNKNOWN, decision.decision)
    }

    @Test
    fun clipsTabPresentButNotSelectedDoesNotBlock() {
        val s = snapshot(
            roots = listOf(
                node(id = "com.instagram.android:id/clips_tab", selected = false),
                node(id = "com.instagram.android:id/clips_viewer")
            )
        )

        val result = detector.detect(s)
        val decision = RuleEvaluator.evaluate(s, config(), instagramReels = result)

        assertEquals(ReelsEvidenceOutcome.NONE, result.outcome)
        assertEquals(Decision.ALLOW, decision.decision)
    }

    @Test
    fun reelsNavLabelAloneDoesNotBlock() {
        val s = snapshot(
            roots = listOf(node(desc = "Reels", selected = true))
        )

        val result = detector.detect(s)
        val decision = RuleEvaluator.evaluate(s, config(), instagramReels = result)

        assertEquals(ReelsEvidenceOutcome.NONE, result.outcome)
        assertEquals(Decision.ALLOW, decision.decision)
    }

    @Test
    fun storySurfaceWithBothReelsSignalsIsAmbiguousNotBlocked() {
        // Both Reels signals plus an exact visible Story label. The label is not
        // a verified Instagram ID; it is only a non-blocking conflict hint.
        val s = snapshot(
            roots = listOf(
                node(id = "com.instagram.android:id/clips_tab", selected = true),
                node(id = "com.instagram.android:id/clips_viewer"),
                node(desc = "Your story")
            )
        )

        val result = detector.detect(s)
        val decision = RuleEvaluator.evaluate(s, config(), instagramReels = result)

        assertEquals(ReelsEvidenceOutcome.AMBIGUOUS, result.outcome)
        assertTrue(result.signals.contains("conflict:story"))
        assertEquals(Decision.UNKNOWN, decision.decision)
    }

    @Test
    fun directMessageLabelWithBothReelsSignalsIsAmbiguousNotBlocked() {
        // Exact visible DM text, not a resource ID.
        val s = snapshot(
            roots = listOf(
                node(id = "com.instagram.android:id/clips_tab", selected = true),
                node(id = "com.instagram.android:id/clips_viewer"),
                node(text = "Messages")
            )
        )

        val result = detector.detect(s)
        val decision = RuleEvaluator.evaluate(s, config(), instagramReels = result)

        assertEquals(ReelsEvidenceOutcome.AMBIGUOUS, result.outcome)
        assertTrue(result.signals.contains("conflict:direct_message"))
        assertEquals(Decision.UNKNOWN, decision.decision)
    }

    @Test
    fun directMessageSurfaceWithBothReelsSignalsIsAmbiguousNotBlocked() {
        // The window hint comes from this repository's former ig_allow_direct
        // rule; it is not claimed to be a verified DM identifier.
        val s = snapshot(
            windowClass = "com.instagram.android.direct.DirectInboxActivity",
            roots = listOf(
                node(id = "com.instagram.android:id/clips_tab", selected = true),
                node(id = "com.instagram.android:id/clips_viewer"),
                node(text = "Messages")
            )
        )

        val result = detector.detect(s)
        val decision = RuleEvaluator.evaluate(s, config(), instagramReels = result)

        assertEquals(ReelsEvidenceOutcome.AMBIGUOUS, result.outcome)
        assertTrue(result.signals.contains("conflict:direct_message"))
        assertEquals(Decision.UNKNOWN, decision.decision)
    }

    @Test
    fun missingFeedTabIsNotUsedAsReelsEvidence() {
        // clips_viewer without any feed_tab must still be allowed.
        val s = snapshot(
            roots = listOf(
                node(id = "com.instagram.android:id/clips_viewer"),
                node(id = "com.instagram.android:id/some_other_container")
            )
        )

        val result = detector.detect(s)

        assertFalse(result.signals.contains("feed_tab:present"))
        assertEquals(ReelsEvidenceOutcome.NONE, result.outcome)
    }

    @Test
    fun truncatedTreeWithSelectedTabAndNoViewerIsAmbiguous() {
        val s = snapshot(
            roots = listOf(
                node(id = "com.instagram.android:id/clips_tab", selected = true)
            ),
            truncated = true
        )

        val result = detector.detect(s)
        val decision = RuleEvaluator.evaluate(s, config(), instagramReels = result)

        assertEquals(ReelsEvidenceOutcome.AMBIGUOUS, result.outcome)
        assertEquals(Decision.UNKNOWN, decision.decision)
    }

    @Test
    fun truncatedTreeWithBothReelsSignalsStillBlocks() {
        // Regression for the real screen: both required signals were observed
        // even though the snapshot itself was marked truncated.
        val s = snapshot(
            roots = listOf(
                node(id = "com.instagram.android:id/clips_tab", selected = true),
                node(id = "com.instagram.android:id/clips_viewer")
            ),
            truncated = true
        )

        val result = detector.detect(s)
        val decision = RuleEvaluator.evaluate(s, config(), instagramReels = result)

        assertEquals(ReelsEvidenceOutcome.CONFIRMED, result.outcome)
        assertTrue(result.signals.contains("tree:truncated"))
        assertEquals(Decision.BLOCK, decision.decision)
        assertEquals(BlockRouting.INSTAGRAM_REELS_RULE_ID, decision.ruleId)
    }

    @Test
    fun liveConfirmedResultOverridesSeparatelyTruncatedSnapshot() {
        // The production service passes the live-tree result independently of
        // the bounded snapshot. A truncated snapshot must not downgrade it.
        val s = snapshot(
            roots = listOf(node(id = "com.instagram.android:id/clips_viewer")),
            truncated = true
        )
        val live = InstagramReelsResult(
            outcome = ReelsEvidenceOutcome.CONFIRMED,
            confidence = 0.6f,
            reason = "Live traversal found clips_tab selected and clips_viewer.",
            signals = listOf("clips_tab:selected", "clips_viewer")
        )

        val decision = RuleEvaluator.evaluate(s, config(), instagramReels = live)

        assertEquals(Decision.BLOCK, decision.decision)
        assertEquals(BlockRouting.INSTAGRAM_REELS_RULE_ID, decision.ruleId)
    }

    @Test
    fun storyEvidenceWithoutReelsSignalsAllows() {
        val s = snapshot(
            roots = listOf(node(desc = "Your story"))
        )

        val result = detector.detect(s)
        val decision = RuleEvaluator.evaluate(s, config(), instagramReels = result)

        assertEquals(Decision.ALLOW, decision.decision)
    }

    @Test
    fun directMessageEvidenceWithoutReelsSignalsAllows() {
        val s = snapshot(
            roots = listOf(node(text = "Messages"))
        )

        val result = detector.detect(s)
        val decision = RuleEvaluator.evaluate(s, config(), instagramReels = result)

        assertEquals(Decision.ALLOW, decision.decision)
    }

    @Test
    fun truncatedTreeWithViewerOnlyIsAmbiguous() {
        // Required positive signals are incomplete; the missing clips_tab could
        // be in the uninspected part of the tree.
        val s = snapshot(
            roots = listOf(node(id = "com.instagram.android:id/clips_viewer")),
            truncated = true
        )

        val result = detector.detect(s)
        val decision = RuleEvaluator.evaluate(s, config(), instagramReels = result)

        assertEquals(ReelsEvidenceOutcome.AMBIGUOUS, result.outcome)
        assertEquals(Decision.UNKNOWN, decision.decision)
    }

    @Test
    fun emptyInstagramTreeIsUnknownNotBlocked() {
        val s = snapshot()
        val result = detector.detect(s)
        val decision = RuleEvaluator.evaluate(s, config(), instagramReels = result)

        assertEquals(ReelsEvidenceOutcome.AMBIGUOUS, result.outcome)
        assertEquals(Decision.UNKNOWN, decision.decision)
    }

    @Test
    fun nonInstagramSnapshotIsNeverConfirmed() {
        val s = snapshot(
            pkg = youtube,
            roots = listOf(
                node(id = "com.instagram.android:id/clips_tab", selected = true),
                node(id = "com.instagram.android:id/clips_viewer")
            )
        )

        assertEquals(ReelsEvidenceOutcome.NONE, detector.detect(s).outcome)
    }

    @Test
    fun confirmedReelsStillAllowsWhenToggleIsOff() {
        val s = snapshot(
            roots = listOf(
                node(id = "com.instagram.android:id/clips_tab", selected = true),
                node(id = "com.instagram.android:id/clips_viewer")
            )
        )

        val result = detector.detect(s)
        val decision = RuleEvaluator.evaluate(s, config(reels = false), instagramReels = result)

        assertEquals(ReelsEvidenceOutcome.CONFIRMED, result.outcome)
        assertEquals(Decision.ALLOW, decision.decision)
        assertEquals("cfg_off", decision.ruleId)
    }

    @Test
    fun diagnosticsContainOnlySignalsNotUserContent() {
        val s = snapshot(
            roots = listOf(
                node(id = "com.instagram.android:id/clips_tab", selected = true),
                node(id = "com.instagram.android:id/clips_viewer"),
                node(text = "Private caption text")
            )
        )

        val result = detector.detect(s)

        assertTrue(result.signals.contains("clips_tab:selected"))
        assertTrue(result.signals.contains("clips_viewer"))
        assertFalse(result.signals.any { it.contains("Private caption") })
        assertFalse(result.reason.contains("Private caption"))
    }

    @Test
    fun wholeAppInstagramBlockingRemainsSeparate() {
        assertTrue(
            AlwaysOnRestrictions.wholeAppBlocked(
                instagram,
                blockYoutubeApp = false,
                blockInstagramApp = true
            )
        )
        // The YouTube whole-app toggle never applies to Instagram.
        assertFalse(
            AlwaysOnRestrictions.wholeAppBlocked(
                instagram,
                blockYoutubeApp = true,
                blockInstagramApp = false
            )
        )
    }
}
