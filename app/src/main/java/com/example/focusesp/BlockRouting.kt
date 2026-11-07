package com.example.focusesp

/**
 * Pure routing rules for enforcement actions.
 *
 * Confirmed content detections (YouTube Shorts and Instagram Reels) get the
 * special treatment: BACK navigation followed by the full-screen FocusESP
 * overlay. Every other block (whole-app fallback, browser, focus-mode app
 * blocking) keeps the original HOME behaviour.
 */
object BlockRouting {

    const val YOUTUBE_SHORTS_RULE_ID = "yt_reel_progress_bar"
    const val INSTAGRAM_REELS_RULE_ID = "ig_reels_confirmed"

    enum class Navigation { HOME, BACK }

    fun usesBlockingOverlay(ruleId: String?): Boolean =
        ruleId == YOUTUBE_SHORTS_RULE_ID || ruleId == INSTAGRAM_REELS_RULE_ID

    fun navigationFor(ruleId: String?): Navigation =
        if (usesBlockingOverlay(ruleId)) Navigation.BACK else Navigation.HOME
}
