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
        if (!force && now - lastContentCheck < CONTENT_CHECK_INTERVAL_MS) return
        lastContentCheck = now

        val root = rootInActiveWindow
        if (root == null) {
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

        val snapshot = ContentRestrictions.capture(root, pkg, windowClassName)
        recycleQuietly(root)
        DetectionPipeline.onCapture(snapshot)

        val hash = NodeAdapter.structuralHash(snapshot)
        val unchanged = hash == lastSnapshotHash && pkg == lastHashPackage
        lastSnapshotHash = hash
        lastHashPackage = pkg

        if (unchanged && !force) {
            // Content-changed burst with identical structure: nothing new to do.
            return
        }

        val decision = ContentRestrictions.evaluate(snapshot)
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
        val attempt = gate.tryAct(pkg, ruleId, hash)
        if (attempt == 0) {
            DetectionPipeline.onEnforcement(EnforcementOutcome.SUPPRESSED_BY_GATE, "gate suppressed $ruleId")
            return
        }

        val ok = performGlobalAction(GLOBAL_ACTION_HOME)
        if (!ok) {
            DetectionPipeline.onFailure("GLOBAL_ACTION_HOME failed for $pkg ($ruleId)")
            DetectionPipeline.onEnforcement(EnforcementOutcome.BLOCK_ACTION_FAILED, "$pkg $ruleId")
            return
        }

        DetectionPipeline.onEnforcement(
            EnforcementOutcome.BLOCK_REQUESTED,
            "$pkg $ruleId attempt=$attempt"
        )
        val label = "$pkg ($ruleId)"
        FocusState.onBlocked(label)
        ViolationCenter.report(decision.reason)

        // Verify once: if the same blocked screen is still visible shortly
        // after the first action, the gate permits a single bounded retry.
        scheduleVerify(pkg, hash)
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
        FocusState.onServiceInterrupt()
        DetectionPipeline.onFailure("service interrupted")
    }

    override fun onDestroy() {
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
