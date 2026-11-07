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
 * Detects the YouTube Shorts player using the proven `reel_progress_bar` signal.
 *
 * The detector performs a breadth-first traversal of the accessibility node
 * tree (bounded to [MAX_NODES]) and looks for a view ID containing
 * [REEL_PROGRESS_BAR_ID], case-insensitively. That progress bar is unique to
 * the Shorts viewing surface and is absent from ordinary YouTube videos.
 *
 * This mirrors the detection technique that was verified on-device with
 * Shorts-Blocker. It deliberately does not add visibility or activity-name
 * heuristics, which would change the known-working behaviour.
 */
class YouTubeShortsDetector {

    fun isShortFormContent(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        var nodeCount = 0
        while (queue.isNotEmpty() && nodeCount < MAX_NODES) {
            val node = queue.removeFirst()
            nodeCount++

            if (matchesViewId(node.viewIdResourceName)) return true

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return false
    }

    companion object {
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        const val REEL_PROGRESS_BAR_ID = "reel_progress_bar"

        /** Maximum number of nodes inspected, matching the tested detector. */
        const val MAX_NODES = 120

        fun matchesViewId(viewId: String?): Boolean {
            val id = viewId?.lowercase() ?: return false
            return REEL_PROGRESS_BAR_ID in id
        }
    }
}
