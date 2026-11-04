package com.example.focusesp

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

object InstalledAppsHelper {

    private val launchablePackages = LinkedHashSet<String>()

    fun init(context: Context) {
        loadAndCache(context)
    }

    fun refresh(context: Context) {
        loadAndCache(context)
    }

    /**
     * Queries the launcher-visible apps and updates the in-memory package cache
     * used by whitelist mode. Returns the resolved list (with icons) for the UI.
     */
    fun loadAndCache(context: Context): List<InstalledApp> {
        val apps = queryLaunchable(context)
        synchronized(launchablePackages) {
            launchablePackages.clear()
            launchablePackages.addAll(apps.map { it.packageName })
        }
        return apps
    }

    fun launchableApps(context: Context): List<InstalledApp> = queryLaunchable(context)

    /**
     * True when the package has a launcher activity. Populated by [init]/[refresh].
     * Used to avoid blocking non-launchable/system components in whitelist mode.
     */
    fun isLaunchable(packageName: String): Boolean =
        synchronized(launchablePackages) { launchablePackages.contains(packageName) }

    private fun queryLaunchable(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        val selfPackage = context.packageName
        val byPackage = LinkedHashMap<String, InstalledApp>()
        for (info in resolved) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (pkg == selfPackage) continue
            if (byPackage.containsKey(pkg)) continue
            val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
            val icon = runCatching { info.loadIcon(pm) }.getOrNull()
            byPackage[pkg] = InstalledApp(pkg, label, icon)
        }
        return byPackage.values.sortedBy { it.label.lowercase() }
    }
}
