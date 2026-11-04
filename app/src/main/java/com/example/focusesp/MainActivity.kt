package com.example.focusesp

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.example.focusesp.ui.theme.FocusESPTheme

class MainActivity : ComponentActivity() {

    private val serviceEnabled = mutableStateOf(false)
    private val usageAccess = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppInitializer.init(this)
        refreshPermissionState()
        setContent {
            FocusESPTheme {
                FocusEspApp(
                    serviceEnabled = serviceEnabled.value,
                    usageAccess = usageAccess.value,
                    onOpenAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onOpenUsageAccess = {
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppInitializer.init(this)
        FocusTimer.checkExpiry()
        FocusTimer.syncExpiryAlarm()
        ContentRestrictionCommitment.refresh(this)
        refreshPermissionState()
    }

    private fun refreshPermissionState() {
        serviceEnabled.value = isServiceEnabled()
        usageAccess.value = UsageStatsHelper.hasUsageAccess(this)
    }

    private fun isServiceEnabled(): Boolean {
        val manager = getSystemService(AccessibilityManager::class.java) ?: return false
        val expected = ComponentName(this, FocusAccessibilityService::class.java)
        return manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val serviceInfo = info.resolveInfo.serviceInfo
                ComponentName(serviceInfo.packageName, serviceInfo.name) == expected
            }
    }
}
