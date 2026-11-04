package com.example.focusesp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionEngineTest {

    private val youtube = "com.google.android.youtube"
    private val instagram = "com.instagram.android"
    private val chrome = "com.android.chrome"

    private fun node(
        id: String? = null,
        text: String? = null,
        desc: String? = null,
        cls: String? = null,
        vararg children: DetectedNode
    ) = DetectedNode(id, text, desc, cls, children.toList())

    private fun snapshot(
        pkg: String,
        windowClass: String? = null,
        roots: List<DetectedNode> = emptyList(),
        truncated: Boolean = false
    ): ScreenSnapshot {
        val count = countNodes(roots)
        return ScreenSnapshot(pkg, windowClass, roots, count, truncated, 0L)
    }

    private fun countNodes(roots: List<DetectedNode>): Int {
        var n = 0
        val stack = ArrayDeque(roots)
        while (stack.isNotEmpty()) {
            val cur = stack.removeFirst()
            n++
            cur.children.forEach { stack.add(it) }
        }
        return n
    }

    private fun config(
        shorts: Boolean = true,
        reels: Boolean = true,
        keywords: List<String> = emptyList(),
        channels: List<String> = emptyList()
    ) = EvalConfig(shorts, reels, keywords, channels)

    // ---- YouTube ----------------------------------------------------------

    @Test
    fun youtubeShortsPlayerActivityBlocks() {
        val s = snapshot(
            youtube,
            windowClass = "com.google.android.youtube/.shorts.ReelPlayerActivity",
            roots = listOf(node(id = "com.google.android.youtube:id/reel_player"))
        )
        val d = RuleEvaluator.evaluate(s, config())
        assertEquals(Decision.BLOCK, d.decision)
        assertEquals("yt_strong_activity", d.ruleId)
        assertTrue(d.confidence >= RuleEvaluator.STRONG_THRESHOLD)
    }

    @Test
    fun youtubeShortsLabelWithoutViewIdsBlocks() {
        // Missing view ids, only the documented player label is available.
        val s = snapshot(
            youtube,
            windowClass = "com.google.android.youtube/.MainActivity",
            roots = listOf(node(desc = "Reel player"))
        )
        val d = RuleEvaluator.evaluate(s, config())
        assertEquals(Decision.BLOCK, d.decision)
        assertEquals("yt_strong_reel_player_label", d.ruleId)
    }

    @Test
    fun youtubeNormalWatchAllowsEvenWithShortsTextVisible() {
        val s = snapshot(
            youtube,
            windowClass = "com.google.android.youtube/.WatchActivity",
            roots = listOf(node(text = "Video title"), node(text = "Shorts"))
        )
        val d = RuleEvaluator.evaluate(s, config())
        assertEquals(Decision.ALLOW, d.decision)
    }

    @Test
    fun youtubeSearchResultsMentioningShortsAllows() {
        // No activity hint; only a weak "Shorts" label on a complete tree.
        val s = snapshot(
            youtube,
            windowClass = null,
            roots = listOf(
                node(id = "com.google.android.youtube:id/results", text = "Shorts compilation"),
                node(text = "Shorts")
            )
        )
        val d = RuleEvaluator.evaluate(s, config())
        assertEquals(Decision.ALLOW, d.decision)
        assertTrue(d.reason.contains("Insufficient evidence"))
    }

    @Test
    fun strongPlayerLabelBeatsGenericWatchAllowRule() {
        // Contradictory evidence: watch activity but an explicit player label.
        val s = snapshot(
            youtube,
            windowClass = "com.google.android.youtube/.WatchActivity",
            roots = listOf(node(desc = "Reel player"))
        )
        val d = RuleEvaluator.evaluate(s, config())
        assertEquals(Decision.BLOCK, d.decision)
    }

    @Test
    fun youtubeToggleOffAllows() {
        val s = snapshot(
            youtube,
            windowClass = "com.google.android.youtube/.shorts.ReelPlayerActivity"
        )
        val d = RuleEvaluator.evaluate(s, config(shorts = false))
        assertEquals(Decision.ALLOW, d.decision)
        assertEquals("cfg_off", d.ruleId)
    }

    @Test
    fun youtubeAllowedChannelAllows() {
        val s = snapshot(
            youtube,
            windowClass = "com.google.android.youtube/.shorts.ReelPlayerActivity",
            roots = listOf(node(text = "MrBeast"))
        )
        val d = RuleEvaluator.evaluate(s, config(channels = listOf("MrBeast")))
        assertEquals(Decision.ALLOW, d.decision)
        assertEquals("yt_allowed_channel", d.ruleId)
    }

    // ---- Instagram --------------------------------------------------------

    @Test
    fun instagramReelsViewerActivityBlocks() {
        val s = snapshot(
            instagram,
            windowClass = "com.instagram.android.activity.ReelViewerActivity",
            roots = listOf(node(id = "com.instagram.android:id/clips_viewer"))
        )
        val d = RuleEvaluator.evaluate(s, config())
        assertEquals(Decision.BLOCK, d.decision)
        assertEquals("ig_strong_activity", d.ruleId)
    }

    @Test
    fun instagramDirectMessagesAllows() {
        val s = snapshot(
            instagram,
            windowClass = "com.instagram.android.direct.DirectInboxActivity",
            roots = listOf(node(text = "Messages"))
        )
        val d = RuleEvaluator.evaluate(s, config())
        assertEquals(Decision.ALLOW, d.decision)
    }

    @Test
    fun instagramHomeTabShowingReelsTabLabelAllows() {
        // "Reels" is a nav tab here, not an active feed.
        val s = snapshot(
            instagram,
            windowClass = "com.instagram.android.activity.MainTabActivity",
            roots = listOf(
                node(id = "com.instagram.android:id/reels_tab", desc = "Reels"),
                node(id = "com.instagram.android:id/feed_tab", desc = "Home")
            )
        )
        val d = RuleEvaluator.evaluate(s, config())
        assertEquals(Decision.ALLOW, d.decision)
    }

    @Test
    fun instagramReelsIdAndLabelBelowThresholdAllows() {
        // Weak id fragment + nav label on a complete tree: not enough to block.
        val s = snapshot(
            instagram,
            windowClass = null,
            roots = listOf(
                node(id = "com.instagram.android:id/clips_viewer"),
                node(desc = "Reels")
            )
        )
        val d = RuleEvaluator.evaluate(s, config())
        assertEquals(Decision.ALLOW, d.decision)
        assertTrue(d.confidence in 0.5f..0.6f)
    }

    @Test
    fun instagramToggleOffAllows() {
        val s = snapshot(
            instagram,
            windowClass = "com.instagram.android.activity.ReelViewerActivity"
        )
        val d = RuleEvaluator.evaluate(s, config(reels = false))
        assertEquals(Decision.ALLOW, d.decision)
        assertEquals("cfg_off", d.ruleId)
    }

    // ---- Uncertain screens ------------------------------------------------

    @Test
    fun emptySnapshotIsUnknown() {
        val d = RuleEvaluator.evaluate(snapshot(youtube), config())
        assertEquals(Decision.UNKNOWN, d.decision)
    }

    @Test
    fun truncatedTreeWithPartialEvidenceIsUnknown() {
        val s = snapshot(
            youtube,
            windowClass = null,
            roots = listOf(node(text = "Shorts")),
            truncated = true
        )
        val d = RuleEvaluator.evaluate(s, config())
        assertEquals(Decision.UNKNOWN, d.decision)
    }

    @Test
    fun unknownViewIdsDoNotMatchAnyRule() {
        val s = snapshot(
            instagram,
            windowClass = "com.instagram.android.activity.NewsfeedActivity",
            roots = listOf(
                node(id = "com.instagram.android:id/unknown_root"),
                node(id = "com.instagram.android:id/unknown_child", text = "Feed")
            )
        )
        val d = RuleEvaluator.evaluate(s, config())
        assertEquals(Decision.ALLOW, d.decision)
    }

    @Test
    fun unmonitoredPackageAllows() {
        val d = RuleEvaluator.evaluate(snapshot("com.example.notes"), config())
        assertEquals(Decision.ALLOW, d.decision)
    }

    // ---- Browser ----------------------------------------------------------

    private fun omnibox(url: String) =
        node(id = "com.android.chrome:id/url_bar", text = url)

    @Test
    fun browserYoutubeShortsUrlBlocks() {
        val s = snapshot(chrome, roots = listOf(omnibox("m.youtube.com/shorts/abc123")))
        val d = RuleEvaluator.evaluate(s, config())
        assertEquals(Decision.BLOCK, d.decision)
        assertEquals("browser_yt_shorts_url", d.ruleId)
    }

    @Test
    fun browserInstagramReelsUrlBlocks() {
        val s = snapshot(chrome, roots = listOf(omnibox("www.instagram.com/reels/Cxyz123/")))
        val d = RuleEvaluator.evaluate(s, config())
        assertEquals(Decision.BLOCK, d.decision)
        assertEquals("browser_ig_reels_url", d.ruleId)
    }

    @Test
    fun browserYoutubeWatchUrlAllows() {
        val s = snapshot(chrome, roots = listOf(omnibox("https://www.youtube.com/watch?v=dQw4w9WgXcQ")))
        val d = RuleEvaluator.evaluate(s, config())
        assertEquals(Decision.ALLOW, d.decision)
        assertEquals("browser_yt_watch_url", d.ruleId)
    }

    @Test
    fun browserKeywordBlocks() {
        val s = snapshot(chrome, roots = listOf(omnibox("example.com/games/play")))
        val d = RuleEvaluator.evaluate(s, config(keywords = listOf("games")))
        assertEquals(Decision.BLOCK, d.decision)
        assertEquals("keyword:games", d.ruleId)
    }

    @Test
    fun browserSearchPageMentioningShortsDoesNotBlock() {
        // The word "shorts" is a query, not a Shorts feed path.
        val s = snapshot(chrome, roots = listOf(omnibox("www.google.com/search?q=shorts")))
        val d = RuleEvaluator.evaluate(s, config())
        assertEquals(Decision.ALLOW, d.decision)
    }

    @Test
    fun browserWithoutAddressBarIsUnknown() {
        val s = snapshot(chrome, roots = listOf(node(id = "com.android.chrome:id/content", text = "Page body")))
        val d = RuleEvaluator.evaluate(s, config())
        assertEquals(Decision.UNKNOWN, d.decision)
    }

    // ---- Action gate ------------------------------------------------------

    @Test
    fun actionGateSuppressesRepeatedIdenticalEvents() {
        var now = 1_000L
        val gate = ActionGate(cooldownMs = 2_500L, now = { now })
        gate.onForeground(youtube)
        assertEquals(1, gate.tryAct(youtube, "yt_strong_activity", 42))
        // Same screen again within the cooldown window.
        now += 300L
        assertEquals(0, gate.tryAct(youtube, "yt_strong_activity", 42))
        // Cooldown elapsed: one bounded retry is allowed.
        now += 3_000L
        assertEquals(2, gate.tryAct(youtube, "yt_strong_activity", 42))
        // Attempts exhausted.
        now += 3_000L
        assertEquals(0, gate.tryAct(youtube, "yt_strong_activity", 42))
    }

    @Test
    fun actionGateAllowsNewScreenImmediately() {
        var now = 1_000L
        val gate = ActionGate(cooldownMs = 2_500L, now = { now })
        gate.onForeground(youtube)
        assertEquals(1, gate.tryAct(youtube, "yt_strong_activity", 42))
        // Navigating (new structural hash) is a new screen: act again.
        assertEquals(1, gate.tryAct(youtube, "yt_strong_activity", 43))
    }

    @Test
    fun actionGateDropsStaleActionAfterForegroundChange() {
        var now = 1_000L
        val gate = ActionGate(cooldownMs = 2_500L, now = { now })
        gate.onForeground(youtube)
        assertEquals(1, gate.tryAct(youtube, "yt_strong_activity", 42))

        // Foreground moved to Instagram: the YouTube visit is over.
        gate.onForeground(instagram)
        assertEquals(0, gate.tryAct(youtube, "yt_strong_activity", 42))

        // Coming back to the same screen starts a fresh visit.
        gate.onForeground(youtube)
        assertEquals(1, gate.tryAct(youtube, "yt_strong_activity", 42))
    }

    @Test
    fun actionGateWithoutForegroundInfoStillActs() {
        val gate = ActionGate(now = { 5_000L })
        assertEquals(1, gate.tryAct(youtube, "yt_strong_activity", 7))
    }

    // ---- Structural hash --------------------------------------------------

    @Test
    fun structuralHashChangesWhenContentChanges() {
        val a = snapshot(youtube, roots = listOf(node(text = "one")))
        val b = snapshot(youtube, roots = listOf(node(text = "two")))
        assertNotEquals(NodeAdapter.structuralHash(a), NodeAdapter.structuralHash(b))
    }

    @Test
    fun structuralHashStableForIdenticalSnapshots() {
        val a = snapshot(youtube, roots = listOf(node(id = "x", text = "same")))
        val b = snapshot(youtube, roots = listOf(node(id = "x", text = "same")))
        assertEquals(NodeAdapter.structuralHash(a), NodeAdapter.structuralHash(b))
    }

    // ---- Rule hygiene -----------------------------------------------------

    @Test
    fun sanitizeDropsRulesWithoutSignals() {
        val invalid = RuleRepository.DetectionRule(
            id = "broken",
            app = "youtube",
            kind = RuleRepository.Kind.BLOCK,
            weight = 0.5f,
            description = "no signal"
        )
        val all = RuleRepository.rulesFor("youtube") + invalid
        val cleaned = RuleRepository.sanitize(all)
        assertTrue(cleaned.none { it.id == "broken" })
        assertEquals(RuleRepository.rulesFor("youtube").size, cleaned.size)
    }

    @Test
    fun allBundledRulesAreValid() {
        val rules = RuleRepository.rulesFor("youtube") + RuleRepository.rulesFor("instagram")
        assertEquals(rules.size, RuleRepository.sanitize(rules).size)
    }

    // ---- Additional required coverage -------------------------------------

    @Test
    fun youtubeShortsTabOnlyWithoutPlayerAllows() {
        // Bottom-nav "Shorts" tab label but the watch/home activity is showing.
        val s = snapshot(
            youtube,
            windowClass = "com.google.android.youtube/.MainActivity",
            roots = listOf(
                node(id = "com.google.android.youtube:id/tab_shorts", desc = "Shorts"),
                node(text = "Home")
            )
        )
        val d = RuleEvaluator.evaluate(s, config())
        assertEquals(Decision.ALLOW, d.decision)
    }

    @Test
    fun instagramFeedWithReelsTabLabelAllows() {
        // Feed activity + a Reels tab label: allow beats the weak tab signal.
        val s = snapshot(
            instagram,
            windowClass = "com.instagram.android.activity.FeedActivity",
            roots = listOf(
                node(id = "com.instagram.android:id/feed_tab", desc = "Home"),
                node(id = "com.instagram.android:id/reels_tab", desc = "Reels")
            )
        )
        val d = RuleEvaluator.evaluate(s, config())
        assertEquals(Decision.ALLOW, d.decision)
        assertTrue(d.confidence >= RuleEvaluator.ALLOW_THRESHOLD)
    }

    @Test
    fun instagramProfileActivityAllows() {
        val s = snapshot(
            instagram,
            windowClass = "com.instagram.android.activity.ProfileActivity",
            roots = listOf(node(text = "Profile"))
        )
        val d = RuleEvaluator.evaluate(s, config())
        assertEquals(Decision.ALLOW, d.decision)
    }

    @Test
    fun strongBlockBeatsGenericAllowForInstagram() {
        // A dedicated Reels viewer activity with an id fragment: block regardless.
        val s = snapshot(
            instagram,
            windowClass = "com.instagram.android.clips.ClipsViewerActivity",
            roots = listOf(node(id = "com.instagram.android:id/clips_viewer"))
        )
        val d = RuleEvaluator.evaluate(s, config())
        assertEquals(Decision.BLOCK, d.decision)
        assertEquals("ig_strong_activity", d.ruleId)
    }

    @Test
    fun truncatedTreeWithOnlyWeakEvidenceIsUnknownNotBlock() {
        val s = snapshot(
            instagram,
            windowClass = null,
            roots = listOf(node(desc = "Reels")),
            truncated = true
        )
        val d = RuleEvaluator.evaluate(s, config())
        assertEquals(Decision.UNKNOWN, d.decision)
    }

    @Test
    fun rapidNavigationBetweenScreensIsDeterministic() {
        val allowed = snapshot(
            youtube,
            windowClass = "com.google.android.youtube/.WatchActivity",
            roots = listOf(node(text = "Video"))
        )
        val blocked = snapshot(
            youtube,
            windowClass = "com.google.android.youtube/.shorts.ReelPlayerActivity"
        )
        repeat(3) {
            assertEquals(Decision.ALLOW, RuleEvaluator.evaluate(allowed, config()).decision)
            assertEquals(Decision.BLOCK, RuleEvaluator.evaluate(blocked, config()).decision)
        }
    }

    @Test
    fun actionGatePreventsEnforcementLoopAcrossRapidNavigation() {
        var now = 10_000L
        val gate = ActionGate(cooldownMs = 2_500L, now = { now })

        // Enter YouTube on a blocked screen: one action.
        gate.onForeground(youtube)
        assertEquals(1, gate.tryAct(youtube, "yt_strong_activity", 100))

        // The same blocked screen keeps emitting events: suppressed, no loop.
        now += 200L
        assertEquals(0, gate.tryAct(youtube, "yt_strong_activity", 100))
        now += 200L
        assertEquals(0, gate.tryAct(youtube, "yt_strong_activity", 100))

        // A genuinely different blocked screen is a new surface: act again.
        assertEquals(1, gate.tryAct(youtube, "yt_strong_activity", 200))

        // Navigating back to the first blocked screen is a fresh visit, but
        // repeated events for it are still suppressed.
        assertEquals(1, gate.tryAct(youtube, "yt_strong_activity", 100))
        now += 100L
        assertEquals(0, gate.tryAct(youtube, "yt_strong_activity", 100))
    }
}
