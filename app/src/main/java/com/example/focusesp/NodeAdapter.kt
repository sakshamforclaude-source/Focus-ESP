package com.example.focusesp

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Converts an Android accessibility tree into the pure [ScreenSnapshot] model.
 *
 * Traversal is bounded by [MAX_NODES] and [MAX_DEPTH]; when either limit is
 * hit the snapshot is marked `truncated`, which makes the evaluator prefer
 * UNKNOWN over a confident decision. Nodes are not recycled: on modern Android
 * framework nodes obtained from [AccessibilityNodeInfo.getChild] are managed by
 * the framework and recycled nodes are only valid until the next event. The
 * previous implementation did not recycle either, so behavior is unchanged.
 */
object NodeAdapter {

    const val MAX_NODES = 250
    const val MAX_DEPTH = 12

    fun capture(
        root: AccessibilityNodeInfo?,
        packageName: String,
        windowClassName: String?,
        nowMs: Long = System.currentTimeMillis()
    ): ScreenSnapshot {
        if (root == null) {
            return ScreenSnapshot(packageName, windowClassName, emptyList(), 0, false, nowMs)
        }
        var count = 0
        var truncated = false

        fun build(node: AccessibilityNodeInfo, depth: Int): DetectedNode? {
            if (count >= MAX_NODES) {
                truncated = true
                return null
            }
            count++
            val children = ArrayList<DetectedNode>()
            if (depth < MAX_DEPTH) {
                val childCount = node.childCount
                for (i in 0 until childCount) {
                    if (count >= MAX_NODES) {
                        truncated = true
                        break
                    }
                    val child = try {
                        node.getChild(i)
                    } catch (e: Exception) {
                        null
                    } ?: continue
                    build(child, depth + 1)?.let { children.add(it) }
                }
            } else if (node.childCount > 0) {
                truncated = true
            }
            return DetectedNode(
                viewId = node.viewIdResourceName,
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
                className = node.className?.toString(),
                children = children
            )
        }

        val rootNode = build(root, 0)
        val roots = if (rootNode != null) listOf(rootNode) else emptyList()
        // The window/activity class is normally supplied by the caller from the
        // accessibility event. Some events (notably content-changed) carry a
        // View class or no class at all; when that happens the root node's own
        // class name is the most reliable window-level hint available, and the
        // strong activity rules depend on it.
        val effectiveWindowClass = windowClassName?.takeIf { it.isNotBlank() } ?: rootNode?.className
        return ScreenSnapshot(packageName, effectiveWindowClass, roots, count, truncated, nowMs)
    }

    /**
     * Cheap structural fingerprint. Changes to ids/text/description/children
     * produce a different hash so unchanged content-changed events can be
     * skipped. Deliberately collision-tolerant: a collision only costs one
     * skipped evaluation, which never blocks content.
     */
    fun structuralHash(snapshot: ScreenSnapshot): Int {
        var h = 17
        fun mix(value: Any?) {
            h = 31 * h + (value?.hashCode() ?: 0)
        }
        mix(snapshot.packageName)
        mix(snapshot.windowClassName)
        val stack = ArrayDeque<DetectedNode>()
        snapshot.roots.forEach { stack.add(it) }
        while (stack.isNotEmpty()) {
            val n = stack.removeFirst()
            mix(n.viewId)
            mix(n.text)
            mix(n.contentDescription)
            mix(n.className)
            n.children.forEach { stack.add(it) }
        }
        return h
    }
}
