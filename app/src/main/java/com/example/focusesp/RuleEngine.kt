package com.example.focusesp

import java.util.Calendar

object RuleEngine {

    fun isPaused(now: Long = System.currentTimeMillis()): Boolean =
        now < FocusPrefs.pausedUntil.value

    fun manualFocusActive(): Boolean = FocusPrefs.focusActive.value

    fun activeSchedule(now: Long = System.currentTimeMillis()): Schedule? =
        ScheduleStore.schedules.firstOrNull { it.enabled && isActive(it, now) }

    fun blockingActive(now: Long = System.currentTimeMillis()): Boolean {
        if (isPaused(now)) return false
        return FocusPrefs.focusActive.value || activeSchedule(now) != null
    }

    fun shouldBlock(
        packageName: String,
        selfPackage: String,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        if (packageName == selfPackage) return false
        if (isPaused(now)) return false
        if (SystemProtection.isProtected(packageName)) return false

        if (FocusLock.isLocked(now)) {
            // Hard Focus: use only the frozen configuration, never live preferences.
            when (FocusLock.frozenMode.value) {
                FocusMode.BLOCKLIST ->
                    if (FocusLock.frozenPackages.contains(packageName)) return true
                FocusMode.WHITELIST ->
                    if (InstalledAppsHelper.isLaunchable(packageName) &&
                        !FocusLock.frozenPackages.contains(packageName)
                    ) {
                        return true
                    }
            }
        } else if (FocusPrefs.focusActive.value) {
            when (FocusPrefs.mode.value) {
                FocusMode.BLOCKLIST ->
                    if (FocusPrefs.isBlocked(packageName)) return true
                FocusMode.WHITELIST ->
                    if (InstalledAppsHelper.isLaunchable(packageName) &&
                        !FocusPrefs.isBlocked(packageName)
                    ) {
                        return true
                    }
            }
        }

        val schedule = activeSchedule(now)
        return schedule != null && schedule.packages.contains(packageName)
    }

    fun isActive(schedule: Schedule, now: Long): Boolean {
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val nowMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val today = calendar.get(Calendar.DAY_OF_WEEK)
        val yesterday = if (today == Calendar.SUNDAY) Calendar.SATURDAY else today - 1

        return if (schedule.startMinutes <= schedule.endMinutes) {
            today in schedule.days && nowMinutes in schedule.startMinutes until schedule.endMinutes
        } else {
            (today in schedule.days && nowMinutes >= schedule.startMinutes) ||
                (yesterday in schedule.days && nowMinutes < schedule.endMinutes)
        }
    }
}
