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
import java.time.DayOfWeek
import java.time.LocalDateTime

object OttoPolicyController {
    private const val PREFS_NAME = "otto_policy_controller"
    private const val KEY_LAST_HARD_BLOCKED_APPS = "last_hard_blocked_apps"
    private const val KEY_SLACK_UNLOCKED_UNTIL = "slack_unlocked_until"
    private const val LOCK_TASK_FEATURE_QUICK_SETTINGS = 1 shl 7

    private data class TimeGateRule(
        val packageName: String,
        val label: String,
        val startHourInclusive: Int,
        val endHourExclusive: Int,
        val passphrase: String,
        val temporaryUnlockDurationMs: Long
    )

    val blockedLauncherPackagePrefixes = setOf(
        "com.android.server",
        "com.sec.android.app.desktoplauncher",
        "com.sec.android.app.dexonpc",
        "com.samsung.desktopsystemui"
    )

    private val hardBlockedAppPackages = setOf(
        "com.reddit.frontpage",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill"
    )

    private val slackGateRule = TimeGateRule(
        packageName = "com.Slack",
        label = "Slack",
        startHourInclusive = 9,
        endHourExclusive = 17,
        passphrase = "jack-and-the-bean-stalk",
        temporaryUnlockDurationMs = 15 * 60 * 1000L
    )

    fun isBlockedApp(packageName: String): Boolean {
        return packageName.lowercase() in hardBlockedAppPackages
    }

    fun shouldHideFromLauncher(packageName: String): Boolean {
        val normalizedPackage = packageName.lowercase()
        return blockedLauncherPackagePrefixes.any { normalizedPackage.startsWith(it) } ||
            normalizedPackage in hardBlockedAppPackages
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
        val previousBlockedApps = prefs.getStringSet(KEY_LAST_HARD_BLOCKED_APPS, emptySet()).orEmpty()
        val currentBlockedApps = hardBlockedAppPackages
        val installedBlockedApps = currentBlockedApps.filter { isPackageInstalled(packageManager, it) }
        val packagesToUnblock = previousBlockedApps - currentBlockedApps
        val isSlackInstalled = isPackageInstalled(packageManager, slackGateRule.packageName)
        val shouldSuspendSlack = isSlackInstalled && shouldSuspendTimeGatedPackage(appContext, slackGateRule)

        packagesToUnblock.forEach { packageName ->
            clearPackageState(dpm, admin, packageManager, packageName, clearHidden = true)
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
        if (isSlackInstalled) {
            clearPackageState(
                dpm = dpm,
                admin = admin,
                packageManager = packageManager,
                packageName = slackGateRule.packageName,
                clearHidden = false
            )
            if (shouldSuspendSlack) {
                runCatching {
                    dpm.setPackagesSuspended(admin, arrayOf(slackGateRule.packageName), true)
                }
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
        applyLockTaskFeatures(dpm, admin)

        prefs.edit()
            .putStringSet(KEY_LAST_HARD_BLOCKED_APPS, currentBlockedApps)
            .apply()
    }

    fun requiresLaunchPassphrase(context: Context, packageName: String): Boolean {
        val rule = timeGateRuleFor(packageName) ?: return false
        if (!isPackageInstalled(context.packageManager, rule.packageName)) return false
        return shouldSuspendTimeGatedPackage(context.applicationContext, rule)
    }

    fun verifyLaunchPassphrase(
        context: Context,
        packageName: String,
        attemptedPassphrase: String
    ): Boolean {
        val rule = timeGateRuleFor(packageName) ?: return false
        if (attemptedPassphrase != rule.passphrase) return false

        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_SLACK_UNLOCKED_UNTIL, System.currentTimeMillis() + rule.temporaryUnlockDurationMs)
            .apply()

        applyPolicies(context.applicationContext)
        return true
    }

    fun launchGatePrompt(packageName: String): String? {
        return timeGateRuleFor(packageName)?.let {
            "Enter passphrase to open ${it.label} outside 9am to 5pm."
        }
    }

    fun launchGateFailureMessage(packageName: String): String? {
        return timeGateRuleFor(packageName)?.let {
            "Incorrect passphrase for ${it.label}."
        }
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

    private fun clearPackageState(
        dpm: DevicePolicyManager,
        admin: ComponentName,
        packageManager: PackageManager,
        packageName: String,
        clearHidden: Boolean
    ) {
        if (!isPackageInstalled(packageManager, packageName)) return

        runCatching {
            dpm.setPackagesSuspended(admin, arrayOf(packageName), false)
        }
        if (clearHidden) {
            runCatching {
                dpm.setApplicationHidden(admin, packageName, false)
            }
        }
    }

    private fun applyUserRestrictions(dpm: DevicePolicyManager, admin: ComponentName) {
        runCatching { dpm.setAutoTimeEnabled(admin, true) }
        runCatching { dpm.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES) }
        runCatching { dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_DATE_TIME) }
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

    private fun applyLockTaskFeatures(
        dpm: DevicePolicyManager,
        admin: ComponentName
    ) {
        val baseFlags = DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO or
            DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS or
            DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
            DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS or
            DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD

        runCatching {
            dpm.setLockTaskFeatures(admin, baseFlags or LOCK_TASK_FEATURE_QUICK_SETTINGS)
        }.getOrElse {
            dpm.setLockTaskFeatures(admin, baseFlags)
        }
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

    private fun timeGateRuleFor(packageName: String): TimeGateRule? {
        return if (packageName.equals(slackGateRule.packageName, ignoreCase = true)) {
            slackGateRule
        } else {
            null
        }
    }

    private fun shouldSuspendTimeGatedPackage(context: Context, rule: TimeGateRule): Boolean {
        if (isWithinAllowedHours(rule)) return false

        val unlockedUntil = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_SLACK_UNLOCKED_UNTIL, 0L)
        return System.currentTimeMillis() >= unlockedUntil
    }

    private fun isWithinAllowedHours(rule: TimeGateRule): Boolean {
        val now = LocalDateTime.now()
        val isWeekday = now.dayOfWeek in DayOfWeek.MONDAY..DayOfWeek.FRIDAY
        if (!isWeekday) return false
        return now.hour in rule.startHourInclusive until rule.endHourExclusive
    }
}
