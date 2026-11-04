package com.example.focusesp

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Alarm-driven reminder for an open restriction unlock window.
 *
 * Idempotent and non-authoritative: it only reads the persisted appointment to
 * decide what (if anything) to show. It never opens, confirms, extends or
 * restores a window, so a delayed or repeated delivery changes nothing.
 */
class RestrictionUnlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        AppInitializer.init(context)
        when (ContentRestrictionCommitment.state()) {
            RestrictionCommitment.State.WAITING -> {
                // Delivered early (inexact alarm jitter): reschedule, do not notify.
                ContentRestrictionCommitment.syncReminder(context)
            }
            RestrictionCommitment.State.UNLOCK_WINDOW -> {
                val formatter = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                val closeAt = formatter.format(java.util.Date(ContentRestrictionCommitment.windowEndEpochMs()))
                Notifications.show(
                    context,
                    NOTIFICATION_ID,
                    "Restriction unlock window is open",
                    "Open FocusESP and confirm before $closeAt to change restrictions.",
                    openAppIntent(context)
                )
                FocusState.addEvent("Unlock window reminder shown")
            }
            else -> Unit
        }
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private companion object {
        const val NOTIFICATION_ID = 0x0FE8
    }
}
