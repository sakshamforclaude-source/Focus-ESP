package com.example.focusesp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

interface AlarmScheduler {
    fun scheduleExactAt(epochMillis: Long)
    fun scheduleInexactAt(epochMillis: Long)
    fun cancel()
}

/**
 * Schedules the single Hard Focus expiry alarm.
 *
 * The persisted [FocusLock.endEpoch] is always the source of truth: this alarm may
 * only complete a session, never extend it, and a cancelled/absent alarm simply
 * leaves expiry to the other checks (service tick, app resume, session init).
 */
object HardSessionAlarm {

    const val REQUEST_CODE = 0x0FE5

    /** Exact alarms need SCHEDULE_EXACT_ALARM on API 31+, but are always allowed below. */
    fun canUseExact(sdkInt: Int, canScheduleExact: Boolean): Boolean =
        sdkInt < Build.VERSION_CODES.S || canScheduleExact

    fun hasExactAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return runCatching { manager.canScheduleExactAlarms() }.getOrDefault(false)
    }

    fun exactAccessMissing(context: Context): Boolean = !hasExactAccess(context)

    fun schedule(context: Context, endEpoch: Long) {
        scheduleWith(endEpoch, systemScheduler(context), hasExactAccess(context))
    }

    fun scheduleWith(endEpoch: Long, scheduler: AlarmScheduler, exact: Boolean) {
        if (exact) scheduler.scheduleExactAt(endEpoch) else scheduler.scheduleInexactAt(endEpoch)
    }

    fun cancel(context: Context) {
        runCatching { systemScheduler(context).cancel() }
    }

    fun cancelWith(scheduler: AlarmScheduler) {
        scheduler.cancel()
    }

    /** Opens the system "Alarms & reminders" screen for this app, with safe fallbacks. */
    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intents = listOf(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
        )
        for (base in intents) {
            val intent = base.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val started = runCatching { context.startActivity(intent); true }.getOrDefault(false)
            if (started) return
        }
    }

    private fun systemScheduler(context: Context): AlarmScheduler {
        val manager = context.getSystemService(AlarmManager::class.java)
        return object : AlarmScheduler {
            override fun scheduleExactAt(epochMillis: Long) {
                manager?.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, epochMillis, expiryPendingIntent(context)
                )
            }

            override fun scheduleInexactAt(epochMillis: Long) {
                manager?.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, epochMillis, expiryPendingIntent(context)
                )
            }

            override fun cancel() {
                manager?.cancel(expiryPendingIntent(context))
            }
        }
    }

    private fun expiryPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, SessionExpiryReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
