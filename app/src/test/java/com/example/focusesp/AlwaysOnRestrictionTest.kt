package com.example.focusesp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Behavioural tests for the always-on restriction system and its six-hour
 * commitment. These cover the acceptance criteria (restrictions independent of
 * Focus Mode, one central unlock rule, persistence across restarts, honest
 * service reporting).
 *
 * All clocks are injected through [FakeTimeSource]; no test waits six hours.
 */
class AlwaysOnRestrictionTest {

    private val selfPackage = "com.example.focusesp"
    private val youtube = "com.google.android.youtube"
    private val instagram = "com.instagram.android"
    private val sixHours = RestrictionCommitment.UNLOCK_DELAY_MS
    private val oneMinute = RestrictionCommitment.UNLOCK_WINDOW_MS

    private lateinit var store: FakeKeyValueStore
    private lateinit var clock: FakeTimeSource

    @Before
    fun setUp() {
        store = FakeKeyValueStore()
        clock = FakeTimeSource()
        FocusPrefs.init(store)
        FocusLock.init(store, now = 0L)
        FocusTimer.init(store, clock)
        ContentRestrictionCommitment.init(store, clock)
        ContentRestrictionStore.init(store)
    }

    private fun youtubeShortsSnapshot() = ScreenSnapshot(
        packageName = youtube,
        windowClassName = "com.google.android.youtube/.shorts.ReelPlayerActivity",
        roots = listOf(DetectedNode(viewId = "com.google.android.youtube:id/reel_player")),
        nodeCount = 1,
        truncated = false,
        capturedAtMs = 0L
    )

    private fun instagramReelsSnapshot() = ScreenSnapshot(
        packageName = instagram,
        windowClassName = "com.instagram.android.activity.ReelViewerActivity",
        roots = listOf(
            DetectedNode(
                viewId = "com.instagram.android:id/clips_tab",
                isSelected = true
            ),
            DetectedNode(viewId = "com.instagram.android:id/clips_viewer")
        ),
        nodeCount = 2,
        truncated = false,
        capturedAtMs = 0L
    )

    private fun confirmNow() {
        clock.advance(sixHours + 10_000L)
        assertTrue(ContentRestrictionCommitment.confirm())
    }

    // 1. Shorts restriction remains active when Focus Mode is off.
    @Test
    fun shortsRestrictionActiveWhenFocusModeOff() {
        FocusPrefs.setFocusActive(false)
        assertTrue(ContentRestrictionStore.setBlockYoutubeShorts(true))

        val decision = RuleEvaluator.evaluate(youtubeShortsSnapshot(), ContentRestrictions.currentConfig())

        assertEquals(Decision.BLOCK, decision.decision)
        assertFalse(FocusPrefs.focusActive.value)
    }

    // 2. Reels restriction remains active when Focus Mode is off.
    @Test
    fun reelsRestrictionActiveWhenFocusModeOff() {
        FocusPrefs.setFocusActive(false)
        assertTrue(ContentRestrictionStore.setBlockInstagramReels(true))

        val snapshot = instagramReelsSnapshot()
        val decision = RuleEvaluator.evaluate(
            snapshot,
            ContentRestrictions.currentConfig(),
            instagramReels = InstagramReelsDetector().detect(snapshot)
        )

        assertEquals(Decision.BLOCK, decision.decision)
        assertFalse(FocusPrefs.focusActive.value)
    }

    // 3. Whole-app fallback remains active when Focus Mode is off.
    @Test
    fun wholeAppFallbackActiveWhenFocusModeOff() {
        FocusPrefs.setFocusActive(false)
        assertTrue(ContentRestrictionStore.setBlockYoutubeApp(true))

        assertTrue(
            AlwaysOnRestrictions.wholeAppBlocked(
                youtube,
                ContentRestrictionStore.blockYoutubeApp.value,
                ContentRestrictionStore.blockInstagramApp.value
            )
        )
        // The Instagram fallback is independent and still off.
        assertFalse(
            AlwaysOnRestrictions.wholeAppBlocked(
                instagram,
                ContentRestrictionStore.blockYoutubeApp.value,
                ContentRestrictionStore.blockInstagramApp.value
            )
        )
        assertFalse(FocusPrefs.focusActive.value)
    }

    // 4. Stopping or completing a focus session does not alter any restriction.
    @Test
    fun focusSessionDoesNotAlterRestrictions() {
        ContentRestrictionStore.setBlockYoutubeShorts(true)
        ContentRestrictionStore.setBlockInstagramReels(true)
        ContentRestrictionStore.setBlockYoutubeApp(true)

        FocusTimer.selectMode(FocusTimer.Mode.COUNTDOWN)
        FocusTimer.setCountdownMinutes(1)
        assertTrue(FocusTimer.start(clock.wallClockMs()))
        assertTrue(FocusPrefs.focusActive.value)

        FocusTimer.reset()

        assertFalse(FocusPrefs.focusActive.value)
        assertTrue(ContentRestrictionStore.blockYoutubeShorts.value)
        assertTrue(ContentRestrictionStore.blockInstagramReels.value)
        assertTrue(ContentRestrictionStore.blockYoutubeApp.value)
    }

    // 5. Requesting an appointment does not disable restrictions.
    @Test
    fun requestDoesNotDisableRestrictions() {
        ContentRestrictionStore.setBlockYoutubeShorts(true)
        ContentRestrictionStore.setBlockInstagramReels(true)

        assertTrue(ContentRestrictionCommitment.request())

        assertTrue(ContentRestrictionStore.blockYoutubeShorts.value)
        assertTrue(ContentRestrictionStore.blockInstagramReels.value)
        assertEquals(RestrictionCommitment.State.WAITING, ContentRestrictionCommitment.state())
    }

    // 6. Unlock attempts before the appointment are rejected.
    @Test
    fun unlockBeforeAppointmentRejected() {
        ContentRestrictionStore.setBlockYoutubeShorts(true)
        assertTrue(ContentRestrictionCommitment.request())

        clock.advance(3 * 60 * 60 * 1000L)

        assertFalse(ContentRestrictionCommitment.canConfirm())
        assertFalse(ContentRestrictionCommitment.confirm())
        assertFalse(ContentRestrictionCommitment.canWeaken())
        assertFalse(ContentRestrictionStore.setBlockYoutubeShorts(false))
        assertTrue(ContentRestrictionStore.blockYoutubeShorts.value)
    }

    // 7. Explicit confirmation during the valid 60-second window succeeds.
    @Test
    fun confirmationDuringWindowSucceeds() {
        ContentRestrictionStore.setBlockYoutubeShorts(true)
        assertTrue(ContentRestrictionCommitment.request())

        clock.advance(sixHours + 30_000L)
        assertEquals(RestrictionCommitment.State.UNLOCK_WINDOW, ContentRestrictionCommitment.state())
        assertTrue(ContentRestrictionCommitment.canConfirm())
        assertTrue(ContentRestrictionCommitment.confirm())
        assertEquals(RestrictionCommitment.State.CONFIRMED, ContentRestrictionCommitment.state())
        assertTrue(ContentRestrictionCommitment.canWeaken())

        // Confirmation makes the controls editable; it does not disable anything.
        assertTrue(ContentRestrictionStore.blockYoutubeShorts.value)
        assertTrue(ContentRestrictionStore.setBlockYoutubeShorts(false))
        assertFalse(ContentRestrictionStore.blockYoutubeShorts.value)
    }

    // 8. Confirmation exactly at or after the defined expiry boundary is rejected.
    @Test
    fun confirmationAtOrAfterExpiryBoundaryRejected() {
        ContentRestrictionStore.setBlockYoutubeShorts(true)
        assertTrue(ContentRestrictionCommitment.request())

        clock.advance(sixHours + oneMinute)
        assertEquals(RestrictionCommitment.State.MISSED, ContentRestrictionCommitment.state())
        assertFalse(ContentRestrictionCommitment.confirm())
        assertFalse(ContentRestrictionCommitment.canWeaken())
        assertFalse(ContentRestrictionStore.setBlockYoutubeShorts(false))
        assertTrue(ContentRestrictionStore.blockYoutubeShorts.value)
    }

    // 9. A missed appointment requires a new six-hour waiting period.
    @Test
    fun missedAppointmentRequiresNewSixHourWait() {
        ContentRestrictionStore.setBlockYoutubeShorts(true)
        assertTrue(ContentRestrictionCommitment.request())

        clock.advance(sixHours + oneMinute + 1_000L)
        assertEquals(RestrictionCommitment.State.MISSED, ContentRestrictionCommitment.state())

        // A new appointment can be filed and is a full six hours from now.
        assertTrue(ContentRestrictionCommitment.request())
        assertEquals(RestrictionCommitment.State.WAITING, ContentRestrictionCommitment.state())
        assertEquals(clock.wallClockMs() + sixHours, ContentRestrictionCommitment.appointmentEpochMs())

        // No weakening before the new appointment.
        assertFalse(ContentRestrictionStore.setBlockYoutubeShorts(false))
    }

    // 10. Confirmation cannot extend the original unlock window.
    @Test
    fun confirmationCannotExtendOriginalWindow() {
        ContentRestrictionStore.setBlockYoutubeShorts(true)
        assertTrue(ContentRestrictionCommitment.request())

        clock.advance(sixHours + 40_000L)
        assertTrue(ContentRestrictionCommitment.confirm())
        assertEquals(20_000L, ContentRestrictionCommitment.windowRemainingMs())

        // Twenty seconds later the window closes even though it was confirmed.
        clock.advance(20_000L)
        assertEquals(RestrictionCommitment.State.LOCKED, ContentRestrictionCommitment.state())
        assertFalse(ContentRestrictionCommitment.canWeaken())
        assertFalse(ContentRestrictionStore.setBlockYoutubeShorts(false))
        assertTrue(ContentRestrictionStore.blockYoutubeShorts.value)
    }

    // 11. Delayed notifications cannot extend the unlock window.
    @Test
    fun delayedNotificationCannotExtendWindow() {
        assertTrue(ContentRestrictionCommitment.request())

        // An alarm was scheduled for the appointment time, but is never delivered
        // on time and arrives late. Scheduling itself changes nothing.
        val scheduler = FakeAlarmScheduler()
        RestrictionUnlockAlarm.scheduleWith(ContentRestrictionCommitment.appointmentEpochMs(), scheduler, exact = false)
        assertEquals(listOf(ContentRestrictionCommitment.appointmentEpochMs()), scheduler.inexactAt)

        // Time moves past the whole window with no confirmation.
        clock.advance(sixHours + oneMinute + 30_000L)
        ContentRestrictionCommitment.refresh()

        assertEquals(RestrictionCommitment.State.MISSED, ContentRestrictionCommitment.state())
        assertFalse(ContentRestrictionCommitment.canConfirm())
        assertFalse(ContentRestrictionCommitment.canWeaken())
    }

    // 12. Restarting the app or restoring persisted state cannot bypass the appointment.
    @Test
    fun restartCannotBypassAppointment() {
        ContentRestrictionStore.setBlockYoutubeShorts(true)
        assertTrue(ContentRestrictionCommitment.request())
        val persisted = ContentRestrictionCommitment.appointment.value

        // Simulate process death and a fresh read of persistent storage.
        ContentRestrictionCommitment.init(store, clock)
        assertEquals(persisted, ContentRestrictionCommitment.appointment.value)
        assertFalse(ContentRestrictionCommitment.canWeaken())
        assertFalse(ContentRestrictionStore.setBlockYoutubeShorts(false))

        // Even after several hours and another restore the wait still applies.
        clock.advance(3 * 60 * 60 * 1000L)
        ContentRestrictionCommitment.init(store, clock)
        assertEquals(RestrictionCommitment.State.WAITING, ContentRestrictionCommitment.state())
        assertFalse(ContentRestrictionStore.setBlockYoutubeShorts(false))
    }

    // 13. All controls that weaken restrictions obey the same central validation rule.
    @Test
    fun allWeakeningControlsShareTheCentralRule() {
        // Enable everything while no appointment exists (enabling is always allowed).
        assertTrue(ContentRestrictionStore.setBlockYoutubeShorts(true))
        assertTrue(ContentRestrictionStore.setBlockInstagramReels(true))
        assertTrue(ContentRestrictionStore.setBlockYoutubeApp(true))
        assertTrue(ContentRestrictionStore.setBlockInstagramApp(true))
        assertTrue(ContentRestrictionStore.addBrowserKeyword("games"))

        // Locked: every weakening action is refused.
        assertFalse(ContentRestrictionStore.setBlockYoutubeShorts(false))
        assertFalse(ContentRestrictionStore.setBlockInstagramReels(false))
        assertFalse(ContentRestrictionStore.setBlockYoutubeApp(false))
        assertFalse(ContentRestrictionStore.setBlockInstagramApp(false))
        assertFalse(ContentRestrictionStore.removeBrowserKeyword("games"))
        assertFalse(ContentRestrictionStore.addAllowedChannel("MrBeast"))

        // Confirmed window: the same actions are allowed.
        assertTrue(ContentRestrictionCommitment.request())
        confirmNow()
        assertTrue(ContentRestrictionCommitment.canWeaken())
        assertTrue(ContentRestrictionStore.setBlockYoutubeShorts(false))
        assertTrue(ContentRestrictionStore.setBlockInstagramReels(false))
        assertTrue(ContentRestrictionStore.setBlockYoutubeApp(false))
        assertTrue(ContentRestrictionStore.setBlockInstagramApp(false))
        assertTrue(ContentRestrictionStore.removeBrowserKeyword("games"))
        assertTrue(ContentRestrictionStore.addAllowedChannel("MrBeast"))
    }

    // 14. Enabling an additional restriction remains possible while other settings are locked.
    @Test
    fun enablingAdditionalRestrictionAllowedWhileLocked() {
        ContentRestrictionStore.setBlockYoutubeShorts(true)
        assertTrue(ContentRestrictionCommitment.request())
        clock.advance(60 * 60 * 1000L)

        assertTrue(ContentRestrictionStore.setBlockInstagramReels(true))
        assertTrue(ContentRestrictionStore.setBlockYoutubeApp(true))
        // Weakening is still refused.
        assertFalse(ContentRestrictionStore.setBlockYoutubeShorts(false))
        assertFalse(ContentRestrictionStore.setBlockYoutubeApp(false))
    }

    // 15. A disabled accessibility service is reported honestly.
    @Test
    fun disabledServiceReportedHonestly() {
        val offEnabled = AlwaysOnRestrictions.enforcementSummary(serviceEnabled = false, anyRestrictionEnabled = true)
        assertTrue(offEnabled.contains("NOT enforced"))

        val offNone = AlwaysOnRestrictions.enforcementSummary(serviceEnabled = false, anyRestrictionEnabled = false)
        assertFalse(offNone.contains("are enforced"))

        val on = AlwaysOnRestrictions.enforcementSummary(serviceEnabled = true, anyRestrictionEnabled = true)
        assertTrue(on.contains("are enforced"))
    }

    // 16. Focus Timer operations never modify the commitment state.
    @Test
    fun focusTimerNeverModifiesCommitment() {
        ContentRestrictionStore.setBlockYoutubeShorts(true)
        assertTrue(ContentRestrictionCommitment.request())
        val persisted = ContentRestrictionCommitment.appointment.value

        FocusTimer.selectMode(FocusTimer.Mode.COUNTDOWN)
        FocusTimer.setCountdownMinutes(1)
        FocusTimer.start(clock.wallClockMs())
        FocusTimer.pause()
        FocusTimer.reset()

        assertEquals(persisted, ContentRestrictionCommitment.appointment.value)
        assertEquals(RestrictionCommitment.State.WAITING, ContentRestrictionCommitment.state())
        // The restriction is unchanged and still locked.
        assertTrue(ContentRestrictionStore.blockYoutubeShorts.value)
        assertFalse(ContentRestrictionStore.setBlockYoutubeShorts(false))
    }

    // Guard: the whole-app fallback is never silently enabled by content rules.
    @Test
    fun wholeAppFallbackIsNeverImplicitlyEnabled() {
        ContentRestrictionStore.setBlockYoutubeShorts(true)
        ContentRestrictionStore.setBlockInstagramReels(true)
        // Content-level detection never blocks the whole app by itself.
        assertFalse(
            AlwaysOnRestrictions.wholeAppBlocked(
                youtube,
                ContentRestrictionStore.blockYoutubeApp.value,
                ContentRestrictionStore.blockInstagramApp.value
            )
        )
    }

    // Legacy umbrella flag migrates to both per-app fallbacks.
    @Test
    fun legacyWholeAppFlagMigratesToPerAppSelections() {
        val legacyStore = FakeKeyValueStore()
        legacyStore.putBoolean("restrict_whole_app_fallback", true)
        // Fresh store object is required so init reads the legacy value.
        ContentRestrictionStore.init(legacyStore)
        assertTrue(ContentRestrictionStore.blockYoutubeApp.value)
        assertTrue(ContentRestrictionStore.blockInstagramApp.value)
        // Restore the shared store for any later use.
        ContentRestrictionStore.init(store)
    }
}
