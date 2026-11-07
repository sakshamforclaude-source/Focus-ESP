package com.example.focusesp

/**
 * Pure rule evaluator over a [ScreenSnapshot].
 *
 * Decision procedure (documented, deterministic):
 *  1. Package not governed by content rules            -> ALLOW (informational).
 *  2. App toggle off                                   -> ALLOW, rule "cfg_off".
 *  3. YouTube only: any text matching an allowed
 *     channel (exact, case-insensitive)                -> ALLOW, rule "yt_allowed_channel".
 *  4. YouTube only: the proven `reel_progress_bar` view
 *     ID was found by [YouTubeShortsDetector]          -> BLOCK, rule "yt_reel_progress_bar".
 *  5. Instagram: [InstagramReelsDetector] result      -> BLOCK only when CONFIRMED,
 *     UNKNOWN when AMBIGUOUS, otherwise ALLOW. Legacy broad Instagram
 *     activity/view-ID heuristics are intentionally not used.
 *  6. Browser packages                                -> [BrowserUrlDetector] path.
 *  7. Score matched rules:
 *       allowConfidence = max weight of matched ALLOW rules
 *       blockConfidence = min(1, sum of matched BLOCK weights)
 *  8. STRONG block (any matched BLOCK rule with weight >= 0.85,
 *     i.e. dedicated player activity / specific player label)
 *     regardless of allow matches                      -> BLOCK.
 *     Rationale: a dedicated Shorts player activity or the explicit player
 *     accessibility label is unambiguous evidence of an active short-form
 *     surface; a generic feed/home allow cannot override it.
 *  9. allowConfidence >= 0.60                          -> ALLOW.
 *     (watch/search/home beats weak tab-label evidence; prevents "Shorts"
 *     nav-tab false positives.)
 * 10. blockConfidence >= 0.75 (BLOCK_THRESHOLD)        -> BLOCK.
 * 11. snapshot has no nodes and no window class        -> UNKNOWN (empty).
 * 12. snapshot truncated with some positive evidence   -> UNKNOWN (partial;
 *     do not claim ALLOW or BLOCK on an uninspectable screen).
 * 13. positive but sub-threshold evidence on a
 *     complete tree                                    -> ALLOW,
 *     reason "insufficient evidence".
 * 14. no evidence                                      -> ALLOW.
 *
 * UNKNOWN never triggers a blocking action; it only records diagnostics and
 * can trigger a scheduled recheck.
 */
object RuleEvaluator {

    const val BLOCK_THRESHOLD = 0.75f
    const val STRONG_THRESHOLD = 0.85f
    const val ALLOW_THRESHOLD = 0.60f
    const val MAX_FLATTENED_NODES = 400

    fun evaluate(
        snapshot: ScreenSnapshot,
        config: EvalConfig,
        youtubeShortsDetected: Boolean = false,
        instagramReels: InstagramReelsResult? = null
    ): BlockDecision {
        val app = RuleRepository.appFor(snapshot.packageName)
            ?: return allow("Package not governed by content rules.")

        if (app == "youtube" && !config.blockYoutubeShorts)
            return allow("YouTube Shorts blocking is disabled.", "cfg_off")
        if (app == "instagram" && !config.blockInstagramReels)
            return allow("Instagram Reels blocking is disabled.", "cfg_off")

        if (app == "browser")
            return evaluateBrowser(snapshot, config)

        // Instagram never uses generic rule scoring. Only the conservative
        // detector's positive result may block; ambiguous/truncated evidence is
        // reported as UNKNOWN so it is visible in diagnostics but never acts.
        if (app == "instagram")
            return evaluateInstagram(snapshot, instagramReels)

        if (app == "youtube" && config.allowedChannels.isNotEmpty() &&
            matchesAllowedChannel(snapshot, config.allowedChannels)
        ) {
            return BlockDecision(Decision.ALLOW, 0.95f, "yt_allowed_channel",
                "Foreground content matches an allowed channel.")
        }

        // The Shorts-Blocker detector is authoritative when it fires: it was
        // verified on-device and relies on the live accessibility root rather
        // than the possibly-truncated snapshot.
        if (app == "youtube" && youtubeShortsDetected) {
            return BlockDecision(Decision.BLOCK, 1f, BlockRouting.YOUTUBE_SHORTS_RULE_ID,
                "YouTube Shorts detected (reel_progress_bar).",
                listOf(BlockRouting.YOUTUBE_SHORTS_RULE_ID))
        }

        val nodes = flatten(snapshot)
        var allowConfidence = 0f
        var allowRule: String? = null
        var blockConfidence = 0f
        val blockRules = mutableListOf<String>()
        var hasStrongBlock = false

        for (rule in RuleRepository.rulesFor(app)) {
            if (!matches(rule, snapshot, nodes)) continue
            when (rule.kind) {
                RuleRepository.Kind.ALLOW -> if (rule.weight > allowConfidence) {
                    allowConfidence = rule.weight
                    allowRule = rule.id
                }
                RuleRepository.Kind.BLOCK -> {
                    blockConfidence += rule.weight
                    blockRules.add(rule.id)
                    if (rule.weight >= STRONG_THRESHOLD) hasStrongBlock = true
                }
            }
        }
        blockConfidence = blockConfidence.coerceAtMost(1f)

        val evidence = buildString {
            if (blockRules.isNotEmpty()) append("block=").append(blockRules.joinToString("+"))
            if (allowRule != null) {
                if (isNotEmpty()) append(", ")
                append("allow=").append(allowRule)
            }
        }

        return when {
            hasStrongBlock && blockConfidence >= STRONG_THRESHOLD ->
                BlockDecision(Decision.BLOCK, blockConfidence, blockRules.first(),
                    "Strong short-form evidence ($evidence).", blockRules)

            allowConfidence >= ALLOW_THRESHOLD ->
                BlockDecision(Decision.ALLOW, allowConfidence, allowRule,
                    "Allowed screen context ($evidence).", blockRules + listOfNotNull(allowRule))

            blockConfidence >= BLOCK_THRESHOLD ->
                BlockDecision(Decision.BLOCK, blockConfidence, blockRules.first(),
                    "Short-form feed evidence ($evidence).", blockRules)

            snapshot.nodeCount == 0 && snapshot.windowClassName.isNullOrEmpty() ->
                BlockDecision(Decision.UNKNOWN, 0f, null,
                    "No accessible window content to inspect (empty snapshot).")

            snapshot.truncated && blockConfidence > 0f ->
                BlockDecision(Decision.UNKNOWN, blockConfidence, blockRules.firstOrNull(),
                    "Partial evidence below confidence on a truncated tree ($evidence).", blockRules)

            blockConfidence > 0f ->
                BlockDecision(Decision.ALLOW, blockConfidence, blockRules.firstOrNull(),
                    "Insufficient evidence: $blockConfidence < $BLOCK_THRESHOLD ($evidence).", blockRules)

            else ->
                BlockDecision(Decision.ALLOW, 0f, null, "No short-form feed signals found.")
        }
    }

    private fun evaluateInstagram(
        snapshot: ScreenSnapshot,
        result: InstagramReelsResult?
    ): BlockDecision {
        if (result == null) {
            return if (snapshot.nodeCount == 0 || snapshot.truncated) {
                BlockDecision(Decision.UNKNOWN, 0f, null,
                    "Instagram content could not be fully inspected; no confirmed Reels evidence.")
            } else {
                BlockDecision(Decision.ALLOW, 0f, null,
                    "No confirmed Instagram Reels evidence.")
            }
        }

        val reason = if (result.signals.isEmpty()) {
            result.reason
        } else {
            "${result.reason} Signals: ${result.signals.joinToString()}."
        }

        return when (result.outcome) {
            ReelsEvidenceOutcome.CONFIRMED ->
                BlockDecision(Decision.BLOCK, result.confidence,
                    BlockRouting.INSTAGRAM_REELS_RULE_ID, reason,
                    listOf(BlockRouting.INSTAGRAM_REELS_RULE_ID))

            ReelsEvidenceOutcome.AMBIGUOUS ->
                BlockDecision(Decision.UNKNOWN, result.confidence, null, reason)

            // The live detector only returns NONE after a completed traversal
            // with no Reels evidence, so a separately truncated snapshot must
            // not turn that into UNKNOWN.
            ReelsEvidenceOutcome.NONE ->
                BlockDecision(Decision.ALLOW, result.confidence, null, reason)
        }
    }

    private fun allow(reason: String, ruleId: String? = null) =
        BlockDecision(Decision.ALLOW, 1f, ruleId, reason)

    private fun matchesAllowedChannel(snapshot: ScreenSnapshot, channels: List<String>): Boolean {
        val wanted = channels.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (wanted.isEmpty()) return false
        return flatten(snapshot).any { n ->
            listOf(n.text, n.contentDescription)
                .filterNotNull()
                .any { t -> t.trim().lowercase() in wanted }
        }
    }

    private fun matches(rule: RuleRepository.DetectionRule, snapshot: ScreenSnapshot, nodes: List<DetectedNode>): Boolean {
        val wc = snapshot.windowClassName
        if (rule.windowClassPattern != null && wc != null && rule.windowClassPattern.matches(wc)) return true
        for (n in nodes) {
            val id = n.viewId
            if (id != null && rule.viewIdPatterns.any { it.matches(id) }) return true
            if (rule.textPatterns.isNotEmpty()) {
                val t = n.text
                val d = n.contentDescription
                if ((t != null && rule.textPatterns.any { it.matches(t) }) ||
                    (d != null && rule.textPatterns.any { it.matches(d) })
                ) return true
            }
        }
        return false
    }

    private fun evaluateBrowser(snapshot: ScreenSnapshot, config: EvalConfig): BlockDecision {
        val result = BrowserUrlDetector.evaluate(snapshot, config.browserKeywords)
        return when (result.kind) {
            BrowserUrlDetector.Result.Kind.BLOCK ->
                BlockDecision(Decision.BLOCK, result.confidence, result.ruleId, result.reason)
            BrowserUrlDetector.Result.Kind.ALLOW ->
                BlockDecision(Decision.ALLOW, result.confidence, result.ruleId, result.reason)
            BrowserUrlDetector.Result.Kind.UNKNOWN ->
                BlockDecision(Decision.UNKNOWN, 0f, null, result.reason)
        }
    }

    private fun flatten(snapshot: ScreenSnapshot): List<DetectedNode> {
        val out = ArrayList<DetectedNode>(minOf(snapshot.nodeCount, MAX_FLATTENED_NODES))
        val queue = ArrayDeque(snapshot.roots)
        while (queue.isNotEmpty() && out.size < MAX_FLATTENED_NODES) {
            val n = queue.removeFirst()
            out.add(n)
            n.children.forEach { queue.add(it) }
        }
        return out
    }
}
