/*
 * Copyright 2025 Atick Faisal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Adapted from Shorts-Blocker by Atick Faisal:
 * https://github.com/atick-faisal/Shorts-Blocker
 */

package com.example.focusesp

import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

/**
 * Conservative Instagram Reels detector.
 *
 * ## What the available evidence establishes
 * - The external Shorts-Blocker detector treats a view ID containing
 *   `clips_tab` with `isSelected == true` as an explicit Reels-tab indicator.
 * - `clips_viewer` is documented by that source as shared by all Instagram
 *   video content. It is never blocking evidence on its own.
 * - `feed_tab` is available for diagnostics, but its absence is unsafe and is
 *   never used as evidence.
 *
 * ## What remains unverified
 * - The external source does not use `clips_tab` + `clips_viewer` as a pair and
 *   no on-device verification exists for that combination.
 * - `clips_viewer` being present while the Reels tab is selected is therefore a
 *   conservative inference, not proof that active Reels playback is on screen.
 * - No verified resource ID or activity class distinguishes Stories, Direct
 *   Messages, or ordinary fullscreen video from Reels in this repository.
 *
 * The detector therefore blocks only on the inferred combination
 * `clips_tab selected AND clips_viewer present`, never on a single signal, and
 * reports AMBIGUOUS when that pair is incomplete on a truncated tree or when a
 * recognised Story/DM conflict is present. Once both positive signals have been
 * observed, truncation of the remaining traversal does not erase them.
 * Conflict hints are exact visible Story/DM labels plus the repository's former
 * `direct|inbox|message` window hint. They are deliberately used only to avoid
 * blocking and are not claimed to be verified Instagram identifiers.
 *
 * Known limitations:
 * - Reels opened where the Reels tab is not selected are not confirmed.
 * - A Story/DM screen without a recognised conflict hint may still be
 *   misclassified as the blocking combination.
 */
class InstagramReelsDetector {

    private data class Evidence(
        var clipsTabPresent: Boolean = false,
        var clipsTabSelected: Boolean = false,
        var clipsViewerPresent: Boolean = false,
        var feedTabPresent: Boolean = false,
        var reelsLabelPresent: Boolean = false,
        var storyLabelPresent: Boolean = false,
        var directMessageLabelPresent: Boolean = false,
        var windowClassName: String? = null,
        var nodeCount: Int = 0,
        var truncated: Boolean = false
    )

    /** Inspects a live accessibility tree. */
    fun detect(root: AccessibilityNodeInfo?, windowClassName: String? = null): InstagramReelsResult {
        if (root == null) {
            return InstagramReelsResult(
                ReelsEvidenceOutcome.AMBIGUOUS, 0f,
                "No active window content to inspect."
            )
        }
        val pkg = root.packageName?.toString()
        if (pkg != null && pkg != INSTAGRAM_PACKAGE) {
            return InstagramReelsResult(
                ReelsEvidenceOutcome.NONE, 0f,
                "Window does not belong to Instagram."
            )
        }

        val evidence = Evidence(
            windowClassName = windowClassName?.takeIf { it.isNotBlank() }
                ?: root.className?.toString()
        )
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && evidence.nodeCount < MAX_NODES) {
            val node = queue.removeFirst()
            evidence.nodeCount++
            inspect(
                evidence = evidence,
                viewId = node.viewIdResourceName,
                isSelected = node.isSelected,
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString()
            )
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        if (queue.isNotEmpty()) evidence.truncated = true
        return classify(evidence)
    }

    /** Inspects a captured snapshot. Used by pure unit tests. */
    fun detect(snapshot: ScreenSnapshot): InstagramReelsResult {
        if (snapshot.packageName != INSTAGRAM_PACKAGE) {
            return InstagramReelsResult(
                ReelsEvidenceOutcome.NONE, 0f,
                "Snapshot does not belong to Instagram."
            )
        }

        val evidence = Evidence(windowClassName = snapshot.windowClassName)
        val queue = ArrayDeque<DetectedNode>()
        snapshot.roots.forEach { queue.add(it) }
        while (queue.isNotEmpty() && evidence.nodeCount < MAX_NODES) {
            val node = queue.removeFirst()
            evidence.nodeCount++
            inspect(
                evidence = evidence,
                viewId = node.viewId,
                isSelected = node.isSelected,
                text = node.text,
                contentDescription = node.contentDescription
            )
            node.children.forEach { queue.add(it) }
        }
        if (queue.isNotEmpty()) evidence.truncated = true
        if (snapshot.truncated) evidence.truncated = true
        return classify(evidence)
    }

    private fun inspect(
        evidence: Evidence,
        viewId: String?,
        isSelected: Boolean,
        text: String?,
        contentDescription: String?
    ) {
        val id = viewId?.lowercase()
        if (id != null) {
            if (CLIPS_TAB in id) {
                evidence.clipsTabPresent = true
                if (isSelected) evidence.clipsTabSelected = true
            }
            if (CLIPS_VIEWER in id) evidence.clipsViewerPresent = true
            if (FEED_TAB in id) evidence.feedTabPresent = true
        }
        if (isReelsLabel(text) || isReelsLabel(contentDescription)) {
            evidence.reelsLabelPresent = true
        }
        if (isStoryLabel(text) || isStoryLabel(contentDescription)) {
            evidence.storyLabelPresent = true
        }
        // Text only: bottom-navigation icons often expose DM words through
        // contentDescription, which would make every Reels screen ambiguous.
        if (isDirectMessageLabel(text)) {
            evidence.directMessageLabelPresent = true
        }
    }

    private fun isReelsLabel(value: String?): Boolean =
        value?.trim()?.lowercase()?.let { it == "reel" || it == "reels" } == true

    /**
     * Exact visible Story labels only. These are not verified resource IDs.
     * They are used solely to avoid blocking, so a false match can only cause a
     * false negative.
     */
    private fun isStoryLabel(value: String?): Boolean =
        value?.trim()?.lowercase()?.let {
            it == "story" || it == "stories" || it == "your story"
        } == true

    /**
     * Exact visible DM text only. These are not verified resource IDs and are
     * used solely as non-blocking conflict hints.
     */
    private fun isDirectMessageLabel(value: String?): Boolean =
        value?.trim()?.lowercase()?.let {
            it == "direct" || it == "inbox" || it == "messages" ||
                it == "direct message" || it == "direct messages"
        } == true

    private fun classify(evidence: Evidence): InstagramReelsResult {
        val storyContext = evidence.storyLabelPresent
        val directMessageContext = evidence.directMessageLabelPresent ||
            matchesDirectMessageWindow(evidence.windowClassName)
        val conflict = storyContext || directMessageContext

        val signals = buildList {
            if (evidence.clipsTabSelected) {
                add("clips_tab:selected")
            } else if (evidence.clipsTabPresent) {
                add("clips_tab:not_selected")
            }
            if (evidence.clipsViewerPresent) add("clips_viewer")
            if (evidence.feedTabPresent) add("feed_tab:present")
            if (evidence.reelsLabelPresent) add("reels_label")
            if (storyContext) add("conflict:story")
            if (directMessageContext) add("conflict:direct_message")
            if (evidence.truncated) add("tree:truncated")
        }

        if (evidence.nodeCount == 0) {
            return InstagramReelsResult(
                ReelsEvidenceOutcome.AMBIGUOUS, 0f,
                "No accessibility content returned for Instagram.",
                signals
            )
        }

        val reelsSignalsPresent =
            evidence.clipsTabSelected && evidence.clipsViewerPresent

        // Story/DM evidence must always win over the inferred Reels combination.
        if (reelsSignalsPresent && conflict) {
            return InstagramReelsResult(
                ReelsEvidenceOutcome.AMBIGUOUS, 0.4f,
                "Reels tab and clips_viewer are present, but Story/Direct evidence " +
                    "conflicts with Reels playback; not blocking.",
                signals
            )
        }

        // The required positive signals are complete. A truncated tree can hide
        // more nodes, but it cannot invalidate evidence that was already found
        // and is corroborated by two independent signals.
        if (reelsSignalsPresent) {
            // This is a best-effort inference, not an on-device verified
            // playback signal: clips_viewer is shared by all Instagram video.
            return InstagramReelsResult(
                ReelsEvidenceOutcome.CONFIRMED, 0.6f,
                "Best-effort Reels match: clips_tab selected with clips_viewer present. " +
                    "The combination is inferred, not verified on-device.",
                signals
            )
        }

        // From here on the required pair is incomplete. Truncation now matters
        // because the missing signal could be in the uninspected portion.
        if (evidence.truncated) {
            return InstagramReelsResult(
                ReelsEvidenceOutcome.AMBIGUOUS, 0.2f,
                "Incomplete accessibility tree and incomplete Reels signals; not blocking.",
                signals
            )
        }

        if (conflict) {
            return InstagramReelsResult(
                ReelsEvidenceOutcome.NONE, 0f,
                "Story/Direct evidence without Reels signals; not blocking.",
                signals
            )
        }

        if (evidence.clipsTabSelected) {
            return InstagramReelsResult(
                ReelsEvidenceOutcome.AMBIGUOUS, 0.5f,
                "Reels tab is selected but clips_viewer corroboration is missing; not blocking.",
                signals
            )
        }

        return InstagramReelsResult(
            ReelsEvidenceOutcome.NONE, 0f,
            "No confirmed Instagram Reels evidence.",
            signals
        )
    }

    /**
     * The `direct|inbox|message` window hint is derived from this repository's
     * former `ig_allow_direct` allow rule. It is useful only as a non-blocking
     * conflict hint, not as a verified DM identifier.
     */
    private fun matchesDirectMessageWindow(windowClassName: String?): Boolean =
        windowClassName?.let { DIRECT_MESSAGE_WINDOW.containsMatchIn(it) } == true

    companion object {
        const val INSTAGRAM_PACKAGE = "com.instagram.android"
        const val CLIPS_TAB = "clips_tab"
        const val CLIPS_VIEWER = "clips_viewer"
        const val FEED_TAB = "feed_tab"

        /** Bounded BFS, matching the documented external limitation of 10 nodes. */
        const val MAX_NODES = 120

        private val DIRECT_MESSAGE_WINDOW = Regex("(?i).*(direct|inbox|message).*")
    }
}
