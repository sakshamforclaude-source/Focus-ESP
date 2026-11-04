package com.example.focusesp

import android.content.Context

object AppInitializer {

    @Volatile
    private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        LocalStore.init(app)
        FocusPrefs.init(app)
        FocusLock.init(LocalStore)
        SystemProtection.init(app)
        InstalledAppsHelper.init(app)
        ScheduleStore.init(app)
        GoalStore.init(app)
        EspConfig.init(app)
        ScreenTimeStore.init(app)
        ContentRestrictionCommitment.init(LocalStore)
        ContentRestrictionStore.init(app)
        FocusTimer.init(app)
        Notifications.ensureChannel(app)
        GoalStore.rescheduleAll(app)
        initialized = true
    }
}
