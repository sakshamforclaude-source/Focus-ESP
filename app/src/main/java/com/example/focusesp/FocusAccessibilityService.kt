package com.example.focusesp

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

class FocusAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private var lastBlockedPackage: String? = null
    private var lastBlockTime = 0L

    private var lastContentCheck = 0L
    private var lastSnapshotHash = 0
    private var lastHashPackage: String? = null

    private var foregroundPackage: String? = null
    private var recheckToken = 0
    private var pendingRecheck: Runnable? = null
    private var verifyHash: Int? = null
    private var contentRecheckAttempts = 0

    private val gate = ActionGate()
    private val youTubeShortsDetector = YouTubeShortsDetector()
    private val instagramReelsDetector = InstagramReelsDetector()
    private var shortsOverlay: ShortsBlockingOverlay? = null

    private val tickRunnable = object : Runnable {
        override fun run() {
            FocusTimer.checkExpiry()
            FocusTimer.onTick()
            handler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        AppInitializer.init(this)
        InstalledAppsHelper.refresh(this)
        FocusState.onServiceConnected()
        DetectionPipeline.onServiceConnected()
        handler.removeCallbacks(tickRunnable)
        handler.postDelayed(tickRunnable, TICK_INTERVAL_MS)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        try {
            handleEvent(event)
        } catch (e: Exception) {
            // A failure here must never silently disable the whole pipeline.
            DetectionPipeline.onFailure(
                "event handling ${e.javaClass.simpleName}: ${e.message ?: "unknown"}"
            )
        }
    }

    private fun handleEvent(event: AccessibilityEvent) {
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        val pkg = event.packageName?.toString() ?: return
        DetectionPipeline.onEvent(eventTypeName(type), pkg)

        if (pkg == packageName) {
            lastBlockedPackage = null
            foregroundPackage = null
            gate.onForeground(null)
            cancelPendingRecheck()
            return
        }

        val className = event.className?.toString()
        val stateChanged = type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        if (stateChanged) {
            foregroundPackage = pkg
            gate.onForeground(pkg)
            cancelPendingRecheck()
            contentRecheckAttempts = 0
            FocusState.onForegroundAppChanged(pkg, className ?: "")
        }

        // Always-on, opt-in fallback: block the whole native app when enabled,
        // independently of Focus Mode. Detection-only Shorts/Reels blocking can
        // be unreliable on some app versions, so this is offered explicitly.
        val wholeAppFallback = AlwaysOnRestrictions.wholeAppBlocked(
            pkg,
            ContentRestrictionStore.blockYoutubeApp.value,
            ContentRestrictionStore.blockInstagramApp.value
        )

        if (wholeAppFallback || RuleEngine.shouldBlock(pkg, packageName)) {
            blockPackage(pkg)
            return
        }
        lastBlockedPackage = null

        if (stateChanged || type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            checkContentRestrictions(pkg, className, force = stateChanged)
        }
    }

    private fun blockPackage(pkg: String) {
        val now = SystemClock.elapsedRealtime()
        if (pkg == lastBlockedPackage && now - lastBlockTime <= BLOCK_COOLDOWN_MS) {
            DetectionPipeline.onEnforcement(EnforcementOutcome.SUPPRESSED_BY_GATE, "app block cooldown")
            return
        }
        lastBlockedPackage = pkg
        lastBlockTime = now
        val ok = performGlobalAction(GLOBAL_ACTION_HOME)
        if (!ok) {
            DetectionPipeline.onFailure("GLOBAL_ACTION_HOME failed for $pkg")
            DetectionPipeline.onEnforcement(EnforcementOutcome.BLOCK_ACTION_FAILED, "app block $pkg")
            return
        }
        DetectionPipeline.onEnforcement(EnforcementOutcome.BLOCK_REQUESTED, "app block $pkg")
        FocusState.onBlocked(pkg)
        ViolationCenter.report("Blocked app: $pkg")
    }

    private fun checkContentRestrictions(pkg: String, windowClassName: String?, force: Boolean) {
        // Only governed packages (YouTube, Instagram, known browsers) are ever
        // inspected. Filtering first also keeps the pipeline diagnostics focused
        // on apps the restriction feature actually applies to.
        if (!ContentRestrictions.handlesPackage(pkg)) return

        val enabled = AlwaysOnRestrictions.contentRestrictionsEnabled(
            ContentRestrictionStore.blockYoutubeShorts.value,
            ContentRestrictionStore.blockInstagramReels.value,
            ContentRestrictionStore.browserKeywords.toList()
        )

        // Content restrictions are always-on and independent of Focus Mode; the
        // only gate is whether the user enabled them. Report diagnostics as such.
        DetectionPipeline.onGating(foregroundPackage, enabled, enabled)

        if (!enabled) {
            ContentRestrictionStore.setDiagnostic("Shorts/Reels restrictions are disabled.")
            DetectionPipeline.onEnforcement(EnforcementOutcome.RESTRICTION_DISABLED)
            return
        }

        val now = SystemClock.elapsedRealtime()
        val intervalElapsed = force || now - lastContentCheck >= CONTENT_CHECK_INTERVAL_MS
        if (intervalElapsed) lastContentCheck = now

        // The proven Shorts/Reels detectors are cheap (bounded BFS, no snapshot
        // build) and must see the restricted surface as soon as it appears. Run
        // them even between full-pipeline ticks so the content-check throttle
        // cannot hide a newly opened Short or confirmed Reel. The full
        // snapshot/rules pipeline stays throttled.
        val app = RuleRepository.appFor(pkg)
        val shortsCandidate = app == "youtube" &&
            ContentRestrictionStore.blockYoutubeShorts.value
        val reelsCandidate = app == "instagram" &&
            ContentRestrictionStore.blockInstagramReels.value
        if (!intervalElapsed && !shortsCandidate && !reelsCandidate) return

        val root = rootInActiveWindow
        if (root == null) {
            if (!intervalElapsed) return
            ContentRestrictionStore.setDiagnostic(
                "No active window content. Enable 'Retrieve window content' for FocusESP or try again after the screen loads."
            )
            DetectionPipeline.onEnforcement(EnforcementOutcome.DETECTION_UNKNOWN, "null root")
            scheduleRecheck(pkg, windowClassName)
            return
        }

        // Guard against inspecting a window that is no longer foreground.
        val rootPkg = root.packageName?.toString()
        if (rootPkg != null && rootPkg != pkg) {
            ContentRestrictionStore.setDiagnostic("Window changed before inspection ($rootPkg).")
            recycleQuietly(root)
            return
        }

        // Run the proven Shorts-Blocker detector against the live accessibility
        // root before converting it to the (bounded) snapshot model. This uses
        // the `reel_progress_bar` view ID and is authoritative when it fires.
        val youtubeShortsDetected =
            shortsCandidate && youTubeShortsDetector.isShortFormContent(root)

        // Instagram is inspected on the live AccessibilityNodeInfo root, before
        // the bounded ScreenSnapshot is built. The live result is authoritative
        // for positive Reels signals, so a separately truncated snapshot can no
        // longer erase evidence the live traversal already found. Ambiguous
        // results are passed through for diagnostics but never act.
        val instagramReels = if (reelsCandidate) {
            instagramReelsDetector.detect(root, windowClassName)
        } else {
            null
        }
        val instagramReelsConfirmed =
            instagramReels?.outcome == ReelsEvidenceOutcome.CONFIRMED

        if (!intervalElapsed && !youtubeShortsDetected && !instagramReelsConfirmed) {
            recycleQuietly(root)
            return
        }

        val snapshot = ContentRestrictions.capture(root, pkg, windowClassName)
        recycleQuietly(root)
        DetectionPipeline.onCapture(snapshot)

        val hash = NodeAdapter.structuralHash(snapshot)
        val unchanged = hash == lastSnapshotHash && pkg == lastHashPackage
        lastSnapshotHash = hash
        lastHashPackage = pkg

        if (unchanged && !force && !youtubeShortsDetected && !instagramReelsConfirmed) {
            // Content-changed burst with identical structure: nothing new to do.
            return
        }

        val decision = ContentRestrictions.evaluate(
            snapshot, youtubeShortsDetected, instagramReels
        )
        DetectionPipeline.onDecision(decision)
        ContentRestrictionStore.setDiagnostic(ContentRestrictions.diagnose(snapshot, decision))
        reportDiagnostic(pkg, decision)

        when (decision.decision) {
            Decision.BLOCK -> handleBlock(pkg, decision, hash)
            Decision.UNKNOWN -> {
                DetectionPipeline.onEnforcement(EnforcementOutcome.DETECTION_UNKNOWN)
                if (force) scheduleRecheck(pkg, windowClassName)
            }
            Decision.ALLOW -> {
                DetectionPipeline.onEnforcement(EnforcementOutcome.NOT_APPLICABLE)
                // The feed may still be rendering on the first event. Schedule a
                // bounded re-scan while the governed app stays foreground, so a
                // late-appearing player is not missed. This is deliberately
                // capped and only runs during an active session.
                if (force && contentRecheckAttempts < MAX_CONTENT_RECHECKS) {
                    contentRecheckAttempts++
                    scheduleRecheck(pkg, windowClassName)
                }
            }
        }
    }

    private fun handleBlock(pkg: String, decision: BlockDecision, hash: Int) {
        val ruleId = decision.ruleId ?: "unknown"
        val blockingRoute = BlockRouting.usesBlockingOverlay(ruleId)

        // The overlay stays until the user dismisses it. While it is visible do
        // not issue more BACK presses or re-show it for continuing events.
        if (blockingRoute && shortsOverlay?.isShowing == true) {
            DetectionPipeline.onEnforcement(
                EnforcementOutcome.SUPPRESSED_BY_GATE,
                "blocking overlay already visible"
            )
            return
        }

        val attempt = gate.tryAct(pkg, ruleId, hash)
        if (attempt == 0) {
            DetectionPipeline.onEnforcement(EnforcementOutcome.SUPPRESSED_BY_GATE, "gate suppressed $ruleId")
            return
        }

        val navigation = BlockRouting.navigationFor(ruleId)
        val globalAction = when (navigation) {
            BlockRouting.Navigation.HOME -> GLOBAL_ACTION_HOME
            BlockRouting.Navigation.BACK -> GLOBAL_ACTION_BACK
        }
        val navigationOk = performGlobalAction(globalAction)

        if (!navigationOk) {
            DetectionPipeline.onFailure("${navigation.name} failed for $pkg ($ruleId)")
        }

        if (blockingRoute) {
            val overlayShown = showBlockingOverlay(overlayEyebrow(ruleId))

            if (overlayShown) {
                DetectionPipeline.onEnforcement(
                    EnforcementOutcome.BLOCK_REQUESTED,
                    "$pkg $ruleId back=$navigationOk overlay=true attempt=$attempt"
                )
            } else {
                // Safe fallback: never use HOME for short-form surfaces (it can
                // trigger picture-in-picture). If BACK already worked we are on
                // a normal screen; if it failed, retry BACK once. Either way,
                // keep the failure logged.
                val retryOk = if (!navigationOk) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                } else {
                    false
                }
                DetectionPipeline.onFailure(
                    "Blocking overlay failed for $pkg; back=$navigationOk retry=$retryOk"
                )
                DetectionPipeline.onEnforcement(
                    EnforcementOutcome.BLOCK_ACTION_FAILED,
                    "$pkg $ruleId back=$navigationOk overlay=false backRetry=$retryOk"
                )
                return
            }
        } else {
            if (!navigationOk) {
                DetectionPipeline.onEnforcement(
                    EnforcementOutcome.BLOCK_ACTION_FAILED,
                    "$pkg $ruleId"
                )
                return
            }
            DetectionPipeline.onEnforcement(
                EnforcementOutcome.BLOCK_REQUESTED,
                "$pkg $ruleId attempt=$attempt"
            )
        }

        val label = "$pkg ($ruleId)"
        FocusState.onBlocked(label)
        ViolationCenter.report(decision.reason)

        // Verify once for the existing HOME-based blocks. Short-form blocks are
        // handled by the overlay and must not be verified against a snapshot.
        if (!blockingRoute) {
            scheduleVerify(pkg, hash)
        }
    }

    private fun overlayEyebrow(ruleId: String): String =
        if (ruleId == BlockRouting.INSTAGRAM_REELS_RULE_ID) {
            "INSTAGRAM REELS BLOCKED"
        } else {
            "YOUTUBE SHORTS BLOCKED"
        }

    private fun showBlockingOverlay(eyebrow: String): Boolean {
        val overlay = shortsOverlay ?: ShortsBlockingOverlay(
            context = this,
            messageProvider = { ContentRestrictionStore.effectiveShortsBlockMessage() },
            onDismiss = { onBlockingOverlayDismissed() }
        ).also { shortsOverlay = it }
        return overlay.show(eyebrow)
    }

    private fun onBlockingOverlayDismissed() {
        // The user deliberately closed the blocking screen: start a fresh
        // enforcement visit so newly opened content can be blocked again.
        gate.reset()
        cancelPendingRecheck()
        lastSnapshotHash = 0
        lastHashPackage = null
        contentRecheckAttempts = 0
        DetectionPipeline.onEnforcement(
            EnforcementOutcome.NOT_APPLICABLE,
            "blocking overlay dismissed by user"
        )
        FocusState.addEvent("Blocking overlay dismissed")
    }

    private fun scheduleVerify(pkg: String, blockedHash: Int) {
        cancelPendingRecheck()
        val token = ++recheckToken
        verifyHash = blockedHash
        val runnable = Runnable {
            if (token != recheckToken) return@Runnable
            verifyBlock(pkg)
        }
        pendingRecheck = runnable
        handler.postDelayed(runnable, VERIFY_DELAY_MS)
    }

    private fun verifyBlock(pkg: String) {
        // If foreground moved away, the Home action worked.
        if (foregroundPackage != pkg) {
            DetectionPipeline.onEnforcement(EnforcementOutcome.BLOCK_VERIFIED, "$pkg left after block")
            return
        }
        val root = rootInActiveWindow
        if (root == null) {
            DetectionPipeline.onEnforcement(EnforcementOutcome.BLOCK_VERIFIED, "no window for $pkg")
            return
        }
        val snapshot = ContentRestrictions.capture(root, pkg, null)
        recycleQuietly(root)
        val hash = NodeAdapter.structuralHash(snapshot)
        val blockedHash = verifyHash
        if (blockedHash != null && hash != blockedHash) {
            DetectionPipeline.onEnforcement(EnforcementOutcome.BLOCK_VERIFIED, "screen changed after block")
        } else {
            DetectionPipeline.onFailure("block not verified: $pkg still on the same screen")
            DetectionPipeline.onEnforcement(EnforcementOutcome.BLOCK_ACTION_FAILED, "$pkg remained open")
        }
    }

    private fun reportDiagnostic(pkg: String, decision: BlockDecision) {
        if (!ContentRestrictionStore.diagnosticsEnabled.value) return
        DetectionDiagnostics.record(
            packageName = pkg,
            decision = decision.decision,
            ruleId = decision.ruleId,
            confidence = decision.confidence,
            note = decision.reason.take(80)
        )
    }

    private fun scheduleRecheck(pkg: String, snapshotClassName: String?, delayMs: Long = RECHECK_DELAY_MS) {
        cancelPendingRecheck()
        val token = ++recheckToken
        verifyHash = null
        val runnable = Runnable {
            if (token != recheckToken) return@Runnable
            if (foregroundPackage != pkg) return@Runnable
            checkContentRestrictions(pkg, snapshotClassName, force = true)
        }
        pendingRecheck = runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun cancelPendingRecheck() {
        pendingRecheck?.let { handler.removeCallbacks(it) }
        pendingRecheck = null
        verifyHash = null
        recheckToken++
    }

    private fun recycleQuietly(node: android.view.accessibility.AccessibilityNodeInfo) {
        // Framework-managed child nodes must not be recycled; the root obtained
        // from getRootInActiveWindow is safe to release and failing to do so can
        // leak nodes and eventually stall event delivery on some devices.
        @Suppress("DEPRECATION")
        try {
            node.recycle()
        } catch (e: Exception) {
            DetectionPipeline.onFailure("node recycle: ${e.javaClass.simpleName}")
        }
    }

    private fun eventTypeName(type: Int): String = when (type) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_STATE_CHANGED"
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "WINDOW_CONTENT_CHANGED"
        AccessibilityEvent.TYPE_WINDOWS_CHANGED -> "WINDOWS_CHANGED"
        else -> "TYPE_$type"
    }

    override fun onInterrupt() {
        shortsOverlay?.hide()
        FocusState.onServiceInterrupt()
        DetectionPipeline.onFailure("service interrupted")
    }

    override fun onDestroy() {
        shortsOverlay?.hide()
        shortsOverlay = null
        handler.removeCallbacks(tickRunnable)
        cancelPendingRecheck()
        gate.reset()
        DetectionPipeline.onServiceDisconnected()
        super.onDestroy()
    }

    private companion object {
        const val BLOCK_COOLDOWN_MS = 800L
        const val CONTENT_CHECK_INTERVAL_MS = 1200L
        const val RECHECK_DELAY_MS = 500L
        const val VERIFY_DELAY_MS = 1500L
        const val TICK_INTERVAL_MS = 1000L
        const val MAX_CONTENT_RECHECKS = 3
    }
}
