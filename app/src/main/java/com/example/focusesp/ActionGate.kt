package com.example.focusesp

/**
 * Pure state machine that decides whether a blocking action may be performed.
 *
 * Guarantees:
 * - Repeated identical events for the same foreground visit and screen do not
 *   produce duplicate actions within [cooldownMs].
 * - Rapid navigation across packages cannot leave a stale pending action:
 *   [onForeground] resets all per-package bookkeeping as soon as the
 *   foreground package changes.
 * - At most [maxActionsPerVisit] actions are issued per (package, screen)
 *   visit; a changed screen (new hash) is eligible again immediately.
 *
 * The caller is responsible for invoking [onForeground] on every
 * window-state change before calling [tryAct].
 */
class ActionGate(
    private val cooldownMs: Long = 2500L,
    private val maxActionsPerVisit: Int = 2,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    private data class Acted(val ruleId: String, val screenHash: Int, val atMs: Long, val attempts: Int)

    private var foregroundPkg: String? = null
    private val actedByPkg = HashMap<String, Acted>()

    /** Call on every foreground/window-state change. */
    fun onForeground(pkg: String?) {
        if (pkg != foregroundPkg) {
            foregroundPkg = pkg
            actedByPkg.clear()
        }
    }

    /** 0 = suppressed (duplicate/cooldown/exhausted), otherwise the 1-based attempt number. */
    @Synchronized
    fun tryAct(pkg: String, ruleId: String, screenHash: Int): Int {
        if (foregroundPkg != null && foregroundPkg != pkg) return 0
        val t = now()
        val prev = actedByPkg[pkg]
        return when {
            prev == null -> {
                actedByPkg[pkg] = Acted(ruleId, screenHash, t, 1)
                1
            }
            prev.ruleId != ruleId || prev.screenHash != screenHash -> {
                actedByPkg[pkg] = Acted(ruleId, screenHash, t, 1)
                1
            }
            t - prev.atMs < cooldownMs -> 0
            prev.attempts >= maxActionsPerVisit -> 0
            else -> {
                val attempt = prev.attempts + 1
                actedByPkg[pkg] = prev.copy(atMs = t, attempts = attempt)
                attempt
            }
        }
    }

    fun reset() {
        foregroundPkg = null
        actedByPkg.clear()
    }
}
