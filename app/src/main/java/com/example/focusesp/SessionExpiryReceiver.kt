package com.example.focusesp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Alarm-driven Hard Focus expiry backstop.
 *
 * Idempotent: [FocusTimer.checkExpiry] only performs a transition when the persisted
 * deadline has actually passed, so duplicate or stale deliveries are no-ops, and an
 * alarm delivered early can never shorten or extend the session.
 */
class SessionExpiryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        AppInitializer.init(context)
        val transitioned = FocusTimer.checkExpiry(System.currentTimeMillis())
        if (transitioned) {
            FocusState.addEvent("Hard Focus expired (scheduled alarm)")
        }
    }
}
