package com.example.focusesp

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.util.Calendar

data class AppUsage(
    val packageName: String,
    val label: String,
    val timeInForegroundMillis: Long
)

/** Total foreground time for a single calendar day. */
data class DailyUsage(
    val dayStartMillis: Long,
    val totalMillis: Long
)

object UsageStatsHelper {

    private const val DAY_MS = 24L * 60L * 60L * 1000L

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun todayUsage(context: Context): List<AppUsage> {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
        val pm = context.packageManager
        val now = System.currentTimeMillis()
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now)
            ?: return emptyList()

        return stats
            .asSequence()
            .filter { it.totalTimeInForeground > 0 }
            .groupBy { it.packageName }
            .map { (pkg, entries) ->
                val total = entries.sumOf { it.totalTimeInForeground }
                AppUsage(pkg, labelFor(pm, pkg), total)
            }
            .sortedByDescending { it.timeInForegroundMillis }
            .toList()
    }

    /**
     * Foreground totals for the last seven calendar days, oldest first. Each day
     * is queried as its own daily bucket so the chart reflects real usage only.
     */
    fun lastSevenDays(context: Context): List<DailyUsage> {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStart = cal.timeInMillis
        val result = ArrayList<DailyUsage>(7)
        for (offset in 6 downTo 0) {
            val start = todayStart - offset * DAY_MS
            val end = start + DAY_MS
            val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            val total = stats?.asSequence()
                ?.filter { it.totalTimeInForeground > 0 }
                ?.filter { it.lastTimeStamp in start..end }
                ?.sumOf { it.totalTimeInForeground }
                ?: 0L
            result.add(DailyUsage(start, total))
        }
        return result
    }

    private fun labelFor(pm: android.content.pm.PackageManager, pkg: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
        pkg
    }

    fun formatDuration(millis: Long): String {
        val totalMinutes = millis / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }
}
