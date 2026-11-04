package com.example.focusesp

/**
 * Pure browser URL classifier.
 *
 * URL extraction (heuristic, documented):
 * - A node is treated as an address-bar candidate when its view id contains
 *   "omnibox", "url", "location" or "search_box", or when its text looks like
 *   a URL (host-like token followed by a known host).
 * - Only well-known short-form paths are blocked, so a search result page
 *   mentioning "shorts" in its text does not match (URL path is what counts).
 *
 * Outcomes:
 * - youtube.com/shorts/... and instagram.com/reel(s)/...  -> BLOCK
 * - youtube.com/watch, instagram.com/direct/..., other clean URLs -> ALLOW
 * - configured keyword found in the URL -> BLOCK (rule keyword:...)
 * - no URL candidate present in the snapshot -> UNKNOWN
 */
object BrowserUrlDetector {

    private val BROWSER_PACKAGES = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "org.mozilla.firefox",
        "com.brave.browser",
        "com.microsoft.emmx",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.sec.android.app.sbrowser",
        "com.vivaldi.browser",
        "com.duckduckgo.mobile.android",
        "com.android.browser",
        "com.google.android.apps.chrome"
    )

    fun isBrowserPackage(pkg: String): Boolean = pkg in BROWSER_PACKAGES

    data class Result(
        val kind: Kind,
        val ruleId: String?,
        val confidence: Float,
        val reason: String,
        val url: String? = null
    ) {
        enum class Kind { BLOCK, ALLOW, UNKNOWN }
    }

    fun evaluate(snapshot: ScreenSnapshot, keywords: List<String>): Result {
        val url = findUrl(snapshot)
            ?: return Result(Result.Kind.UNKNOWN, null, 0f,
                "No address-bar URL exposed in the current view.")

        val host = hostOf(url)
        val path = pathOf(url)

        when {
            host.endsWith("youtube.com") && path.startsWith("/shorts") ->
                return Result(Result.Kind.BLOCK, "browser_yt_shorts_url", 0.95f,
                    "YouTube Shorts URL detected.", url)

            (host.endsWith("instagram.com") || host.endsWith("instagr.am")) &&
                path.startsWith("/reel") ->
                return Result(Result.Kind.BLOCK, "browser_ig_reels_url", 0.95f,
                    "Instagram Reels URL detected.", url)

            host.endsWith("youtube.com") && path.startsWith("/watch") ->
                return Result(Result.Kind.ALLOW, "browser_yt_watch_url", 0.9f,
                    "Regular YouTube watch URL.", url)

            host.endsWith("instagram.com") && path.startsWith("/direct") ->
                return Result(Result.Kind.ALLOW, "browser_ig_direct_url", 0.9f,
                    "Instagram direct messages URL.", url)
        }

        val keyword = keywords.firstOrNull { it.isNotBlank() && url.contains(it.trim().lowercase()) }
        if (keyword != null) {
            return Result(Result.Kind.BLOCK, "keyword:$keyword", 0.85f,
                "Blocked keyword present in URL: $keyword", url)
        }

        return Result(Result.Kind.ALLOW, "browser_other_url", 0.5f,
            "URL is not a short-form feed.", url)
    }

    /** Normalizes raw text to "host/path" (lowercase, scheme and "www." stripped). */
    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var s = raw.trim().lowercase()
        s = s.substringBefore(' ') // drop trailing title text after a space
        s = s.removePrefix("https://").removePrefix("http://")
        s = s.removePrefix("www.")
        if (!looksLikeUrl(s)) return null
        return s
    }

    private fun looksLikeUrl(s: String): Boolean {
        val host = hostOf(s)
        if (host.isEmpty() || !host.contains('.') || host.contains(' ') || host.length > 253) return false
        val tld = host.substringAfterLast('.')
        return tld.length in 2..24 && tld.all { it.isLetter() }
    }

    private fun hostOf(u: String): String = u.substringBefore('/')

    private fun pathOf(u: String): String {
        val rest = u.substringAfter('/', "")
        return if (rest.isEmpty()) "" else "/$rest"
    }

    private fun findUrl(snapshot: ScreenSnapshot): String? {
        val queue = ArrayDeque(snapshot.roots)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val idHit = n.viewId?.lowercase()?.let {
                it.contains("omnibox") || it.contains("url") ||
                    it.contains("location") || it.contains("search_box")
            } == true

            if (idHit) {
                normalize(n.text)?.let { return it }
                normalize(n.contentDescription)?.let { return it }
            }
            n.children.forEach { queue.add(it) }
        }
        return null
    }
}
