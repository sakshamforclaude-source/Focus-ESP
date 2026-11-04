package com.example.focusesp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Reminder alarm for the restriction unlock appointment.
 *
 * This is a **reminder only**. It never changes the appointment state: the
 * persisted timestamps decide whether the window is open, so a delayed or
 * missing notification can never open, extend or restore a window.
 */
object RestrictionUnlockAlarm {

    const val REQUEST_CODE = 0x0FE7

    fun schedule(context: Context, appointmentEpochMs: Long) {
        scheduleWith(appointmentEpochMs, systemScheduler(context), HardSessionAlarm.hasExactAccess(context))
    }

    fun scheduleWith(appointmentEpochMs: Long, scheduler: AlarmScheduler, exact: Boolean) {
        if (exact) scheduler.scheduleExactAt(appointmentEpochMs) else scheduler.scheduleInexactAt(appointmentEpochMs)
    }

    fun cancel(context: Context) {
        runCatching { systemScheduler(context).cancel() }
    }

    fun cancelWith(scheduler: AlarmScheduler) {
        scheduler.cancel()
    }

    private fun systemScheduler(context: Context): AlarmScheduler {
        val manager = context.getSystemService(AlarmManager::class.java)
        return object : AlarmScheduler {
            override fun scheduleExactAt(epochMillis: Long) {
                manager?.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, epochMillis, reminderPendingIntent(context)
                )
            }

            override fun scheduleInexactAt(epochMillis: Long) {
                manager?.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, epochMillis, reminderPendingIntent(context)
                )
            }

            override fun cancel() {
                manager?.cancel(reminderPendingIntent(context))
            }
        }
    }

    private fun reminderPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, RestrictionUnlockReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
