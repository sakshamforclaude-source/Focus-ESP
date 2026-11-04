package com.example.focusesp

/**
 * Pure, Compose-free rules that decide whether the always-on restrictions apply.
 *
 * Kept separate from the accessibility service so the behaviour can be unit
 * tested on the JVM. Critically, none of these functions consult Focus Mode,
 * the focus timer or any schedule: the always-on restrictions depend only on
 * the user's restriction configuration.
 */
object AlwaysOnRestrictions {

    /** True when the whole native app should be sent Home by the fallback. */
    fun wholeAppBlocked(
        packageName: String,
        blockYoutubeApp: Boolean,
        blockInstagramApp: Boolean
    ): Boolean = when (RuleRepository.appFor(packageName)) {
        "youtube" -> blockYoutubeApp
        "instagram" -> blockInstagramApp
        else -> false
    }

    /** True when the content-inspection pipeline has anything worth inspecting. */
    fun contentRestrictionsEnabled(
        blockYoutubeShorts: Boolean,
        blockInstagramReels: Boolean,
        browserKeywords: List<String>
    ): Boolean = blockYoutubeShorts || blockInstagramReels || browserKeywords.isNotEmpty()

    /** True when at least one of the three primary restrictions is enabled. */
    fun anyRestrictionEnabled(
        blockYoutubeShorts: Boolean,
        blockInstagramReels: Boolean,
        blockYoutubeApp: Boolean,
        blockInstagramApp: Boolean
    ): Boolean = blockYoutubeShorts || blockInstagramReels || blockYoutubeApp || blockInstagramApp

    /**
     * Honest, human-readable enforcement status. Never claims blocking is
     * operational while the Accessibility service is disabled.
     */
    fun enforcementSummary(serviceEnabled: Boolean, anyRestrictionEnabled: Boolean): String = when {
        !serviceEnabled && anyRestrictionEnabled ->
            "Accessibility service is OFF: your restrictions are NOT enforced right now."
        !serviceEnabled ->
            "Accessibility service is OFF. Restrictions cannot be enforced until it is enabled."
        !anyRestrictionEnabled ->
            "Accessibility service is on, but no content restriction is enabled yet."
        else ->
            "Accessibility service is on and your content restrictions are enforced."
    }
}
