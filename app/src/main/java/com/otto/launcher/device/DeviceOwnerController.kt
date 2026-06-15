package com.otto.launcher.device

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.otto.launcher.MainActivity
import com.otto.launcher.OttoDeviceAdminReceiver
import com.otto.launcher.OttoDnsVpnService

class DeviceOwnerController(private val context: Context) {
    private val appContext = context.applicationContext
    private val dpm = appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = ComponentName(appContext, OttoDeviceAdminReceiver::class.java)

    fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(appContext.packageName)

    fun applyPersistentHome() {
        if (!isDeviceOwner()) return
        val homeComponent = ComponentName(appContext, MainActivity::class.java)
        val homeFilter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        runCatching { dpm.clearPackagePersistentPreferredActivities(admin, appContext.packageName) }
        runCatching { dpm.addPersistentPreferredActivity(admin, homeFilter, homeComponent) }
    }

    fun hidePackage(packageName: String, hidden: Boolean) {
        if (!isDeviceOwner()) return
        runCatching { dpm.setApplicationHidden(admin, packageName, hidden) }
    }

    fun suspendPackages(packageNames: List<String>, suspended: Boolean) {
        if (!isDeviceOwner() || packageNames.isEmpty()) return
        runCatching { dpm.setPackagesSuspended(admin, packageNames.toTypedArray(), suspended) }
    }

    fun setLockTaskAllowlist(packageNames: List<String>) {
        if (!isDeviceOwner()) return
        runCatching { dpm.setLockTaskPackages(admin, packageNames.distinct().toTypedArray()) }
    }

    fun startHardSleepLockIfEnabled(activity: Activity, enabled: Boolean) {
        if (!enabled || !isDeviceOwner()) return
        if (!dpm.isLockTaskPermitted(appContext.packageName)) return
        val activityManager = activity.getSystemService(ActivityManager::class.java)
        if (activityManager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
            runCatching { activity.startLockTask() }
        }
    }

    fun stopHardSleepLock(activity: Activity) {
        val activityManager = activity.getSystemService(ActivityManager::class.java)
        if (activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) {
            runCatching { activity.stopLockTask() }
        }
    }

    fun applyGreyscale(enabled: Boolean): Boolean {
        return applyGreyscaleViaDevicePolicy(enabled) || applyGreyscaleViaSecureSettings(enabled)
    }

    fun applyVpnDnsPolicy() {
        if (VpnService.prepare(appContext) != null || OttoDnsVpnService.isActive()) return
        runCatching {
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, OttoDnsVpnService::class.java)
            )
        }
    }

    private fun applyGreyscaleViaDevicePolicy(enabled: Boolean): Boolean {
        if (!isDeviceOwner()) return false
        val daltonizer = if (enabled) DALTONIZER_SIMULATE_MONOCHROMACY else DALTONIZER_DISABLED
        val enabledValue = if (enabled) 1 else 0
        return runCatching {
            dpm.setSecureSetting(admin, SETTING_DISPLAY_DALTONIZER, daltonizer.toString())
            dpm.setSecureSetting(admin, SETTING_DISPLAY_DALTONIZER_ENABLED, enabledValue.toString())
            true
        }.getOrDefault(false)
    }

    private fun applyGreyscaleViaSecureSettings(enabled: Boolean): Boolean {
        val daltonizer = if (enabled) DALTONIZER_SIMULATE_MONOCHROMACY else DALTONIZER_DISABLED
        val enabledValue = if (enabled) 1 else 0
        return runCatching {
            Settings.Secure.putInt(appContext.contentResolver, SETTING_DISPLAY_DALTONIZER, daltonizer) &&
                Settings.Secure.putInt(appContext.contentResolver, SETTING_DISPLAY_DALTONIZER_ENABLED, enabledValue)
        }.getOrDefault(false)
    }

    companion object {
        private const val SETTING_DISPLAY_DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
        private const val SETTING_DISPLAY_DALTONIZER = "accessibility_display_daltonizer"
        private const val DALTONIZER_DISABLED = -1
        private const val DALTONIZER_SIMULATE_MONOCHROMACY = 0
    }
}

