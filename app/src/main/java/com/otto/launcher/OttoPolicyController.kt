package com.otto.launcher

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager

object OttoPolicyController {
    private const val PREFS_NAME = "otto_policy_controller"
    private const val KEY_LAST_BLOCKED_APPS = "last_blocked_apps"

    val blockedLauncherPackagePrefixes = setOf(
        "com.android.server",
        "com.sec.android.app.desktoplauncher",
        "com.sec.android.app.dexonpc",
        "com.samsung.desktopsystemui"
    )

    val blockedAppPackages = setOf(
        "com.reddit.frontpage",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill"
    )

    fun isBlockedApp(packageName: String): Boolean {
        return packageName.lowercase() in blockedAppPackages
    }

    fun shouldHideFromLauncher(packageName: String): Boolean {
        val normalizedPackage = packageName.lowercase()
        return blockedLauncherPackagePrefixes.any { normalizedPackage.startsWith(it) } ||
            normalizedPackage in blockedAppPackages
    }

    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    fun applyPolicies(context: Context) {
        val appContext = context.applicationContext
        val dpm = appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(appContext, OttoDeviceAdminReceiver::class.java)
        if (!dpm.isDeviceOwnerApp(appContext.packageName)) return

        val packageManager = appContext.packageManager
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previousBlockedApps = prefs.getStringSet(KEY_LAST_BLOCKED_APPS, emptySet()).orEmpty()
        val currentBlockedApps = blockedAppPackages
        val installedBlockedApps = currentBlockedApps.filter { isPackageInstalled(packageManager, it) }
        val packagesToUnblock = previousBlockedApps - currentBlockedApps

        packagesToUnblock.forEach { packageName ->
            clearBlockedState(dpm, admin, packageManager, packageName)
        }

        installedBlockedApps.forEach { packageName ->
            runCatching {
                dpm.setApplicationHidden(admin, packageName, true)
            }
        }
        if (installedBlockedApps.isNotEmpty()) {
            runCatching {
                dpm.setPackagesSuspended(admin, installedBlockedApps.toTypedArray(), true)
            }
        }

        runCatching {
            dpm.setUserControlDisabledPackages(admin, (installedBlockedApps + appContext.packageName).distinct())
        }
        runCatching {
            dpm.setUninstallBlocked(admin, appContext.packageName, true)
        }

        applyUserRestrictions(dpm, admin)
        applyPersistentHome(dpm, admin, appContext)
        applyLockTaskPackages(dpm, admin, appContext)

        prefs.edit()
            .putStringSet(KEY_LAST_BLOCKED_APPS, currentBlockedApps)
            .apply()
    }

    fun maybeEnterLockTask(activity: Activity) {
        val appContext = activity.applicationContext
        val dpm = appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val activityManager = activity.getSystemService(ActivityManager::class.java)
        if (!dpm.isDeviceOwnerApp(appContext.packageName)) return
        if (!dpm.isLockTaskPermitted(appContext.packageName)) return
        if (activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) return

        runCatching { activity.startLockTask() }
    }

    private fun clearBlockedState(
        dpm: DevicePolicyManager,
        admin: ComponentName,
        packageManager: PackageManager,
        packageName: String
    ) {
        if (!isPackageInstalled(packageManager, packageName)) return

        runCatching {
            dpm.setPackagesSuspended(admin, arrayOf(packageName), false)
        }
        runCatching {
            dpm.setApplicationHidden(admin, packageName, false)
        }
    }

    private fun applyUserRestrictions(dpm: DevicePolicyManager, admin: ComponentName) {
        runCatching { dpm.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                dpm.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY)
            }
        }
    }

    private fun applyPersistentHome(
        dpm: DevicePolicyManager,
        admin: ComponentName,
        context: Context
    ) {
        val homeComponent = ComponentName(context, MainActivity::class.java)
        val homeFilter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }

        runCatching { dpm.clearPackagePersistentPreferredActivities(admin, context.packageName) }
        runCatching { dpm.addPersistentPreferredActivity(admin, homeFilter, homeComponent) }
    }

    private fun applyLockTaskPackages(
        dpm: DevicePolicyManager,
        admin: ComponentName,
        context: Context
    ) {
        val allowedPackages = (loadLauncherApps(context.packageManager).map { it.packageName } +
            context.packageName +
            ALLOWED_SYSTEM_PACKAGES)
            .distinct()
            .sorted()

        runCatching { dpm.setLockTaskPackages(admin, allowedPackages.toTypedArray()) }
    }

    private fun isPackageInstalled(packageManager: PackageManager, packageName: String): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
            }
            true
        }.getOrDefault(false)
    }
}
