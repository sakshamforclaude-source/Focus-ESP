package com.example.focusesp

import android.content.Context
import android.content.Intent

/**
 * Packages that must never be blocked, so the user can always recover:
 * FocusESP itself, the home/launcher, system UI, settings, dialer/emergency,
 * permission controllers and the package installer.
 */
object SystemProtection {

    private val basePackages = setOf(
        "com.android.systemui",
        "com.android.settings",
        "com.android.phone",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.google.android.phone",
        "com.android.emergency",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.providers.settings",
        "android"
    )

    private val protectedPackages = LinkedHashSet<String>()

    @Synchronized
    fun init(context: Context) {
        protectedPackages.clear()
        protectedPackages.addAll(basePackages)
        protectedPackages.add(context.packageName)
        protectedPackages.addAll(homePackages(context))
    }

    fun isProtected(packageName: String): Boolean =
        synchronized(protectedPackages) { protectedPackages.contains(packageName) }

    fun protectedSnapshot(): Set<String> =
        synchronized(protectedPackages) { protectedPackages.toSet() }

    private fun homePackages(context: Context): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return runCatching {
            context.packageManager
                .queryIntentActivities(intent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        }.getOrDefault(emptySet())
    }
}
