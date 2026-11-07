package com.example.focusesp

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

/**
 * Persistent configuration for the always-on content restrictions.
 *
 * The three primary features are:
 *   1. Block YouTube Shorts    ([blockYoutubeShorts])
 *   2. Block Instagram Reels   ([blockInstagramReels])
 *   3. Whole-app fallback      ([blockYoutubeApp] + [blockInstagramApp])
 *
 * These restrictions are independent of Focus Mode: the accessibility service
 * enforces them whenever they are enabled, regardless of any focus session.
 *
 * Every setter that would *weaken* a restriction (turn it off, or make it less
 * strict) is validated against the single central rule
 * [ContentRestrictionCommitment.canWeaken]. Enabling a restriction is always
 * allowed because it never reduces protection. This makes the guard effective
 * even if a UI control is reached by an unexpected path.
 */
object ContentRestrictionStore {

    const val DEFAULT_SHORTS_BLOCK_MESSAGE =
        "That Short can wait. Get back to what you planned to do."
    const val MAX_SHORTS_BLOCK_MESSAGE_LENGTH = 200

    private const val KEY_SHORTS = "restrict_shorts"
    private const val KEY_REELS = "restrict_reels"
    private const val KEY_SHORTS_BLOCK_MESSAGE = "restrict_shorts_block_message"
    private const val KEY_BROWSER_KEYWORDS = "restrict_browser_keywords"
    private const val KEY_ALLOWED_CHANNELS = "restrict_allowed_channels"
    private const val KEY_DIAGNOSTICS = "restrict_diagnostics"

    /** Legacy umbrella flag from before per-app fallback controls existed. */
    private const val KEY_WHOLE_APP_FALLBACK = "restrict_whole_app_fallback"
    private const val KEY_BLOCK_YOUTUBE_APP = "restrict_block_youtube_app"
    private const val KEY_BLOCK_INSTAGRAM_APP = "restrict_block_instagram_app"

    private var store: KeyValueStore? = null

    val blockYoutubeShorts = mutableStateOf(false)
    val blockInstagramReels = mutableStateOf(false)

    /** Customizable message shown on the full-screen short-form blocking overlay. */
    val shortsBlockMessage = mutableStateOf(DEFAULT_SHORTS_BLOCK_MESSAGE)

    /**
     * Whole-app fallback: when enabled for an app, that entire native app
     * (YouTube or Instagram) is sent Home instead of relying on per-screen
     * Shorts/Reels detection. Explicit and opt-in; never silently enabled.
     */
    val blockYoutubeApp = mutableStateOf(false)
    val blockInstagramApp = mutableStateOf(false)

    val browserKeywords = mutableStateListOf<String>()
    val allowedChannels = mutableStateListOf<String>()

    /** Developer option: record privacy-safe detection metadata in [DetectionDiagnostics]. */
    val diagnosticsEnabled = mutableStateOf(false)

    /** Human-readable result of the most recent content inspection attempt. */
    val lastDiagnostic = mutableStateOf("Not evaluated yet.")

    /** Set when a control change is refused by the unlock commitment. */
    val lastControlNotice = mutableStateOf<String?>(null)

    fun setDiagnostic(message: String) {
        lastDiagnostic.value = message
    }

    fun init(context: Context) {
        LocalStore.init(context)
        init(LocalStore)
    }

    fun init(store: KeyValueStore) {
        this.store = store
        blockYoutubeShorts.value = store.getBoolean(KEY_SHORTS, false)
        blockInstagramReels.value = store.getBoolean(KEY_REELS, false)
        shortsBlockMessage.value = normalizeShortsBlockMessage(
            store.getString(KEY_SHORTS_BLOCK_MESSAGE, DEFAULT_SHORTS_BLOCK_MESSAGE)
        )
        // Migrate the legacy umbrella flag into both per-app selections when the
        // user has not yet set them explicitly.
        val legacy = store.getBoolean(KEY_WHOLE_APP_FALLBACK, false)
        blockYoutubeApp.value = store.getBoolean(KEY_BLOCK_YOUTUBE_APP, legacy)
        blockInstagramApp.value = store.getBoolean(KEY_BLOCK_INSTAGRAM_APP, legacy)
        browserKeywords.clear()
        browserKeywords.addAll(readList(store, KEY_BROWSER_KEYWORDS))
        allowedChannels.clear()
        allowedChannels.addAll(readList(store, KEY_ALLOWED_CHANNELS))
        diagnosticsEnabled.value = store.getBoolean(KEY_DIAGNOSTICS, false)
        lastControlNotice.value = null
    }

    // ---- Guarded restriction setters -------------------------------------

    fun setBlockYoutubeShorts(value: Boolean): Boolean {
        if (blockYoutubeShorts.value == value) return true
        if (!value && !guardWeaken("YouTube Shorts blocking")) return false
        blockYoutubeShorts.value = value
        store?.putBoolean(KEY_SHORTS, value)
        return true
    }

    fun setBlockInstagramReels(value: Boolean): Boolean {
        if (blockInstagramReels.value == value) return true
        if (!value && !guardWeaken("Instagram Reels blocking")) return false
        blockInstagramReels.value = value
        store?.putBoolean(KEY_REELS, value)
        return true
    }

    fun setBlockYoutubeApp(value: Boolean): Boolean {
        if (blockYoutubeApp.value == value) return true
        if (!value && !guardWeaken("whole-app YouTube blocking")) return false
        blockYoutubeApp.value = value
        store?.putBoolean(KEY_BLOCK_YOUTUBE_APP, value)
        return true
    }

    fun setBlockInstagramApp(value: Boolean): Boolean {
        if (blockInstagramApp.value == value) return true
        if (!value && !guardWeaken("whole-app Instagram blocking")) return false
        blockInstagramApp.value = value
        store?.putBoolean(KEY_BLOCK_INSTAGRAM_APP, value)
        return true
    }

    // ---- YouTube Shorts blocking message ----------------------------------

    /**
     * Persists the Shorts overlay message. A blank value falls back to
     * [DEFAULT_SHORTS_BLOCK_MESSAGE] and values are capped at
     * [MAX_SHORTS_BLOCK_MESSAGE_LENGTH] characters. Editing the message does not
     * weaken any protection, so it is not gated by the unlock commitment.
     */
    fun setShortsBlockMessage(value: String): Boolean {
        val normalized = normalizeShortsBlockMessage(value)
        shortsBlockMessage.value = normalized
        store?.putString(KEY_SHORTS_BLOCK_MESSAGE, normalized)
        return true
    }

    fun resetShortsBlockMessage(): Boolean =
        setShortsBlockMessage(DEFAULT_SHORTS_BLOCK_MESSAGE)

    fun effectiveShortsBlockMessage(): String =
        normalizeShortsBlockMessage(shortsBlockMessage.value)

    // ---- Browser keywords / allowed channels ------------------------------
    // Adding a browser keyword or removing an allowed channel strengthens the
    // restrictions (always allowed). Removing a keyword or adding an allowed
    // channel weakens them, so it obeys the commitment too.

    fun addBrowserKeyword(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || browserKeywords.contains(trimmed)) return true
        browserKeywords.add(trimmed)
        store?.putString(KEY_BROWSER_KEYWORDS, encodeList(browserKeywords))
        return true
    }

    fun removeBrowserKeyword(value: String): Boolean {
        if (!browserKeywords.contains(value)) return true
        if (!guardWeaken("a browser keyword")) return false
        browserKeywords.remove(value)
        store?.putString(KEY_BROWSER_KEYWORDS, encodeList(browserKeywords))
        return true
    }

    fun addAllowedChannel(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || allowedChannels.contains(trimmed)) return true
        if (!guardWeaken("an allowed channel")) return false
        allowedChannels.add(trimmed)
        store?.putString(KEY_ALLOWED_CHANNELS, encodeList(allowedChannels))
        return true
    }

    fun removeAllowedChannel(value: String): Boolean {
        if (!allowedChannels.contains(value)) return true
        allowedChannels.remove(value)
        store?.putString(KEY_ALLOWED_CHANNELS, encodeList(allowedChannels))
        return true
    }

    /** Diagnostics toggle does not affect protection, so it is never locked. */
    fun setDiagnosticsEnabled(value: Boolean) {
        diagnosticsEnabled.value = value
        store?.putBoolean(KEY_DIAGNOSTICS, value)
        if (!value) DetectionDiagnostics.clear()
    }

    fun clearControlNotice() {
        lastControlNotice.value = null
    }

    // ---- Internals --------------------------------------------------------

    private fun guardWeaken(subject: String): Boolean {
        if (ContentRestrictionCommitment.canWeaken()) {
            lastControlNotice.value = null
            return true
        }
        lastControlNotice.value =
            "Locked: $subject can only be changed during a confirmed unlock window. " +
                "Request an unlock appointment and confirm it within the 1-minute window."
        return false
    }

    private fun normalizeShortsBlockMessage(value: String): String =
        value.trim()
            .take(MAX_SHORTS_BLOCK_MESSAGE_LENGTH)
            .ifBlank { DEFAULT_SHORTS_BLOCK_MESSAGE }

    private fun encodeList(values: List<String>): String =
        runCatching { org.json.JSONArray(values).toString() }.getOrDefault("[]")

    private fun readList(source: KeyValueStore, key: String): List<String> =
        runCatching {
            val raw = source.getString(key, "[]")
            val array = org.json.JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        }.getOrDefault(emptyList())
}
