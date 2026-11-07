package com.example.focusesp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for the YouTube Shorts blocking surface: message persistence/defaults
 * and routing of the verified `reel_progress_bar` decision to the overlay path.
 */
class ShortsBlockingFeatureTest {

    private val youtube = "com.google.android.youtube"
    private val instagram = "com.instagram.android"
    private lateinit var store: FakeKeyValueStore

    @Before
    fun setUp() {
        store = FakeKeyValueStore()
        ContentRestrictionStore.init(store)
    }

    private fun config() = EvalConfig(
        blockYoutubeShorts = true,
        blockInstagramReels = true,
        browserKeywords = emptyList(),
        allowedChannels = emptyList()
    )

    // ---- Message persistence ---------------------------------------------

    @Test
    fun defaultMessageUsedWhenNothingSaved() {
        assertEquals(
            ContentRestrictionStore.DEFAULT_SHORTS_BLOCK_MESSAGE,
            ContentRestrictionStore.effectiveShortsBlockMessage()
        )
    }

    @Test
    fun customMessagePersistsAcrossStoreReinitialization() {
        assertTrue(ContentRestrictionStore.setShortsBlockMessage("Finish the task first."))

        ContentRestrictionStore.init(store)

        assertEquals(
            "Finish the task first.",
            ContentRestrictionStore.effectiveShortsBlockMessage()
        )
    }

    @Test
    fun resetRestoresDefaultAndPersistsIt() {
        ContentRestrictionStore.setShortsBlockMessage("Custom text")
        ContentRestrictionStore.resetShortsBlockMessage()

        assertEquals(
            ContentRestrictionStore.DEFAULT_SHORTS_BLOCK_MESSAGE,
            ContentRestrictionStore.effectiveShortsBlockMessage()
        )

        ContentRestrictionStore.init(store)
        assertEquals(
            ContentRestrictionStore.DEFAULT_SHORTS_BLOCK_MESSAGE,
            ContentRestrictionStore.effectiveShortsBlockMessage()
        )
    }

    @Test
    fun blankMessageFallsBackToDefault() {
        ContentRestrictionStore.setShortsBlockMessage("   \n  ")

        assertEquals(
            ContentRestrictionStore.DEFAULT_SHORTS_BLOCK_MESSAGE,
            ContentRestrictionStore.effectiveShortsBlockMessage()
        )

        ContentRestrictionStore.init(store)
        assertEquals(
            ContentRestrictionStore.DEFAULT_SHORTS_BLOCK_MESSAGE,
            ContentRestrictionStore.effectiveShortsBlockMessage()
        )
    }

    @Test
    fun messageIsCappedAtConfiguredLimit() {
        ContentRestrictionStore.setShortsBlockMessage("a".repeat(500))

        assertEquals(
            ContentRestrictionStore.MAX_SHORTS_BLOCK_MESSAGE_LENGTH,
            ContentRestrictionStore.effectiveShortsBlockMessage().length
        )
    }

    // ---- Routing ----------------------------------------------------------

    @Test
    fun verifiedShortsDetectionRoutesToOverlayAndBackNavigation() {
        val snapshot = ScreenSnapshot(
            packageName = youtube,
            windowClassName = null,
            roots = listOf(DetectedNode(viewId = "com.google.android.youtube:id/reel_progress_bar")),
            nodeCount = 1,
            truncated = false,
            capturedAtMs = 0L
        )

        val decision = RuleEvaluator.evaluate(snapshot, config(), youtubeShortsDetected = true)

        assertEquals(Decision.BLOCK, decision.decision)
        assertEquals(BlockRouting.YOUTUBE_SHORTS_RULE_ID, decision.ruleId)
        assertTrue(BlockRouting.usesBlockingOverlay(decision.ruleId))
        assertEquals(BlockRouting.Navigation.BACK, BlockRouting.navigationFor(decision.ruleId))
    }

    @Test
    fun confirmedInstagramReelsRoutesToOverlayAndBackNavigation() {
        assertTrue(BlockRouting.usesBlockingOverlay(BlockRouting.INSTAGRAM_REELS_RULE_ID))
        assertEquals(
            BlockRouting.Navigation.BACK,
            BlockRouting.navigationFor(BlockRouting.INSTAGRAM_REELS_RULE_ID)
        )
    }

    @Test
    fun otherContentBlocksKeepExistingHomeNavigation() {
        assertFalse(BlockRouting.usesBlockingOverlay("browser_yt_shorts_url"))
        assertEquals(
            BlockRouting.Navigation.HOME,
            BlockRouting.navigationFor("browser_yt_shorts_url")
        )
        assertEquals(BlockRouting.Navigation.HOME, BlockRouting.navigationFor(null))
    }

    @Test
    fun wholeAppFallbackBehaviourIsUnchanged() {
        ContentRestrictionStore.setBlockYoutubeApp(true)

        assertTrue(
            AlwaysOnRestrictions.wholeAppBlocked(
                youtube,
                ContentRestrictionStore.blockYoutubeApp.value,
                ContentRestrictionStore.blockInstagramApp.value
            )
        )
        assertFalse(
            AlwaysOnRestrictions.wholeAppBlocked(
                instagram,
                ContentRestrictionStore.blockYoutubeApp.value,
                ContentRestrictionStore.blockInstagramApp.value
            )
        )
        // Whole-app blocking is not routed through the blocking overlay.
        assertFalse(BlockRouting.usesBlockingOverlay("whole_app_youtube"))
        assertEquals(
            BlockRouting.Navigation.HOME,
            BlockRouting.navigationFor("whole_app_youtube")
        )
    }
}
