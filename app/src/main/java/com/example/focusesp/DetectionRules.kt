package com.example.focusesp

/**
 * Versioned rule definitions for short-form feed detection.
 *
 * Grounding policy:
 * - STRONG signals are window/activity class names and very specific player
 *   accessibility labels ("Reel player"). These alone can justify a block.
 * - WEAK signals are tab labels ("Shorts", "Reels") and opportunistic view-ID
 *   substrings. Tab labels are visible on ordinary screens, so they never
 *   block on their own; they only add confidence on top of contextual
 *   evidence. View-ID patterns are opportunistic heuristics (app layouts
 *   change between versions) and are deliberately weighted below threshold.
 * - Explicit ALLOW rules (watch player, search, home feed, DMs) beat weak and
 *   mid-strength block evidence, but never a STRONG block from a dedicated
 *   player activity.
 */
object RuleRepository {

    const val VERSION = 2

    enum class Kind { BLOCK, ALLOW }

    data class DetectionRule(
        val id: String,
        val app: String,
        val kind: Kind,
        val weight: Float,
        val description: String,
        val windowClassPattern: Regex? = null,
        val viewIdPatterns: List<Regex> = emptyList(),
        val textPatterns: List<Regex> = emptyList()
    )

    fun isValid(rule: DetectionRule): Boolean = try {
        rule.weight in 0f..1f &&
            (rule.windowClassPattern != null || rule.viewIdPatterns.isNotEmpty() || rule.textPatterns.isNotEmpty())
    } catch (e: Exception) {
        false
    }

    private fun rx(pattern: String): Regex = Regex(pattern)

    private val rules: List<DetectionRule> = listOf(
        // ---- YouTube -------------------------------------------------------
        DetectionRule(
            id = "yt_strong_activity",
            app = "youtube",
            kind = Kind.BLOCK,
            weight = 0.9f,
            description = "Dedicated Shorts/Reel player activity in window class",
            windowClassPattern = rx("(?i).*(shorts|reel).*activity.*")
        ),
        DetectionRule(
            id = "yt_strong_reel_player_label",
            app = "youtube",
            kind = Kind.BLOCK,
            weight = 0.9f,
            description = "Shorts player accessibility label present",
            textPatterns = listOf(rx("(?i).*(reel player|shorts player).*"))
        ),
        DetectionRule(
            id = "yt_weak_id_shorts",
            app = "youtube",
            kind = Kind.BLOCK,
            weight = 0.35f,
            description = "Opportunistic Shorts view-ID fragments (unverified across app versions)",
            viewIdPatterns = listOf(rx("(?i).*(shorts_player|shorts_viewer|reel_player|reel_recycler|shorts_fragment).*"))
        ),
        DetectionRule(
            id = "yt_weak_tab_label",
            app = "youtube",
            kind = Kind.BLOCK,
            weight = 0.2f,
            description = "'Shorts' label (also a home-screen tab; supporting evidence only)",
            textPatterns = listOf(rx("(?i)^shorts$"))
        ),
        DetectionRule(
            id = "yt_allow_watch",
            app = "youtube",
            kind = Kind.ALLOW,
            weight = 0.8f,
            description = "Regular watch/player activity",
            windowClassPattern = rx("(?i).*watch.*activity.*")
        ),
        DetectionRule(
            id = "yt_allow_search",
            app = "youtube",
            kind = Kind.ALLOW,
            weight = 0.7f,
            description = "Search screen activity",
            windowClassPattern = rx("(?i).*search.*activity.*")
        ),
        DetectionRule(
            id = "yt_allow_home",
            app = "youtube",
            kind = Kind.ALLOW,
            weight = 0.7f,
            description = "Home/Subscriptions/Library activity",
            windowClassPattern = rx("(?i).*(home|subscription|library).*activity.*")
        ),

        // ---- Instagram -----------------------------------------------------
        DetectionRule(
            id = "ig_strong_activity",
            app = "instagram",
            kind = Kind.BLOCK,
            weight = 0.9f,
            description = "Dedicated Reels viewer activity in window class",
            windowClassPattern = rx("(?i).*(reel|clips).*activity.*")
        ),
        DetectionRule(
            id = "ig_weak_id_reels_viewer",
            app = "instagram",
            kind = Kind.BLOCK,
            weight = 0.35f,
            description = "Opportunistic Reels viewer view-ID fragments (unverified across app versions)",
            viewIdPatterns = listOf(rx("(?i).*(reel_viewer|clips_viewer|reels_fragment).*"))
        ),
        DetectionRule(
            id = "ig_weak_tab_label",
            app = "instagram",
            kind = Kind.BLOCK,
            weight = 0.2f,
            description = "'Reels' label (also a nav tab; supporting evidence only)",
            textPatterns = listOf(rx("(?i)^reels?$"))
        ),
        DetectionRule(
            id = "ig_allow_direct",
            app = "instagram",
            kind = Kind.ALLOW,
            weight = 0.8f,
            description = "Direct messages / inbox activity",
            windowClassPattern = rx("(?i).*(direct|inbox|message).*")
        ),
        DetectionRule(
            id = "ig_allow_main",
            app = "instagram",
            kind = Kind.ALLOW,
            weight = 0.7f,
            description = "Main feed / profile / tabbed activity",
            windowClassPattern = rx("(?i).*(mainactivity|hometab|feed|profile|tabbed).*")
        )
    )

    /** App key for a package name, or null when the package is not governed by content rules. */
    fun appFor(packageName: String): String? = when (packageName) {
        "com.google.android.youtube" -> "youtube"
        "com.instagram.android" -> "instagram"
        else -> if (BrowserUrlDetector.isBrowserPackage(packageName)) "browser" else null
    }

    fun rulesFor(app: String): List<DetectionRule> = rules.filter { it.app == app }

    /** Drops rules with out-of-range weights or no matchable signal. */
    fun sanitize(rulesIn: List<DetectionRule>): List<DetectionRule> = rulesIn.filter { isValid(it) }
}
