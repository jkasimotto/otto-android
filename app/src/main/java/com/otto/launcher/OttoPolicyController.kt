package com.otto.launcher

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.SystemClock
import android.os.UserManager
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import java.security.SecureRandom
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.util.Locale

object OttoPolicyController {
    private const val PREFS_NAME = "otto_policy_controller"
    private const val KEY_LAST_HARD_BLOCKED_APPS = "last_hard_blocked_apps"
    private const val KEY_LAST_APPLIED_INSTALLED_BLOCKED_APPS = "last_applied_installed_blocked_apps"
    private const val KEY_LEGACY_SLACK_UNLOCKED_UNTIL = "slack_unlocked_until"
    private const val KEY_LAST_TIME_GATED_SUSPENDED_PACKAGES = "last_time_gated_suspended_packages"
    private const val KEY_TIME_GATE_UNLOCKED_UNTIL_PREFIX = "time_gate_unlocked_until_"
    private const val KEY_LOCKDOWN_UNTIL = "lockdown_until_millis"
    private const val KEY_LAST_LOCKDOWN_LOCKED_PACKAGES = "last_lockdown_locked_packages"
    private const val LOCK_TASK_FEATURE_QUICK_SETTINGS = 1 shl 7
    private const val AUTO_ENTER_LOCK_TASK = false
    private const val POLICY_REAPPLY_MIN_INTERVAL_MS = 5_000L
    private const val LAUNCH_GATE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val LAUNCH_GATE_CODE_LENGTH = 30
    private const val TIME_GATE_START_HOUR_INCLUSIVE = 9
    private const val TIME_GATE_END_HOUR_EXCLUSIVE = 17
    private const val TIME_GATE_UNLOCK_DURATION_MS = 15 * 60 * 1000L

    private val policyApplyLock = Any()
    private val launchGateRandom = SecureRandom()

    @Volatile
    private var policyStateDirty = true

    @Volatile
    private var staticPoliciesDirty = true

    @Volatile
    private var lastPolicyApplyElapsedRealtime = 0L

    @Volatile
    private var lastAppliedLockTaskPackages: List<String>? = null

    @Volatile
    private var lastUserControlDisabledPackages: List<String>? = null

    private data class TimeGateRule(
        val packageName: String,
        val label: String,
        val startHourInclusive: Int,
        val endHourExclusive: Int,
        val challengeCodeLength: Int,
        val temporaryUnlockDurationMs: Long,
        val hideWhenBlocked: Boolean
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
        startHourInclusive = TIME_GATE_START_HOUR_INCLUSIVE,
        endHourExclusive = TIME_GATE_END_HOUR_EXCLUSIVE,
        challengeCodeLength = LAUNCH_GATE_CODE_LENGTH,
        temporaryUnlockDurationMs = TIME_GATE_UNLOCK_DURATION_MS,
        hideWhenBlocked = true
    )

    // Packages that stay usable during a timed lockdown: phone, messages, messenger, maps.
    // Matched generously against label+package so the device's stock dialer/SMS app is never
    // accidentally locked away (calls and texts must keep working).
    private val lockdownAllowedHints = listOf(
        "dialer",
        "com.android.phone",
        "incallui",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "phone",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "messaging",
        "messages",
        ".mms",
        ".sms",
        "com.facebook.orca",
        "com.facebook.mlite",
        "messenger",
        "com.google.android.apps.maps",
        "maps",
        "com.google.android.inputmethod.latin",
        "inputmethod",
        "keyboard",
        "gboard"
    )

    fun isBlockedApp(packageName: String): Boolean {
        return packageName.lowercase() in hardBlockedAppPackages
    }

    fun isLockdownActive(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return System.currentTimeMillis() < prefs.getLong(KEY_LOCKDOWN_UNTIL, 0L)
    }

    fun lockdownRemainingMillis(context: Context): Long {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return (prefs.getLong(KEY_LOCKDOWN_UNTIL, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    /**
     * Arms a lockdown for [durationMinutes]: every launchable app except phone/messages/messenger/
     * maps (and Otto itself) is suspended and hidden until the timer expires. There is no early exit
     * by design. The caller must trigger a policy apply (e.g. applyPolicies on a background thread)
     * for it to take effect.
     */
    fun startLockdown(context: Context, durationMinutes: Int) {
        if (durationMinutes <= 0) return
        val until = System.currentTimeMillis() + durationMinutes * 60_000L
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LOCKDOWN_UNTIL, until)
            .apply()
        markPolicyDirty(packageStateChanged = true)
    }

    private fun isLockdownAllowed(packageManager: PackageManager, packageName: String): Boolean {
        val haystack = "${appLabel(packageManager, packageName).orEmpty()} $packageName".lowercase(Locale.US)
        return lockdownAllowedHints.any(haystack::contains)
    }

    /** Lowercased package names of every installed input method, so keyboards survive lockdown. */
    private fun inputMethodPackageKeys(context: Context): Set<String> {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return emptySet()
        return runCatching {
            imm.inputMethodList.mapNotNull { it.packageName?.lowercase(Locale.US) }.toSet()
        }.getOrDefault(emptySet())
    }

    /**
     * When a lockdown timer is running, suspend and hide every launchable app outside the allowlist.
     * Returns true if a lockdown is active (callers should skip the normal blocked/time-gate logic).
     * When inactive, restores any packages a prior lockdown locked and returns false.
     */
    private fun applyLockdownIfActive(
        dpm: DevicePolicyManager,
        admin: ComponentName,
        context: Context,
        packageManager: PackageManager
    ): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previouslyLocked = prefs.getStringSet(KEY_LAST_LOCKDOWN_LOCKED_PACKAGES, emptySet()).orEmpty()

        if (!isLockdownActive(context)) {
            previouslyLocked.forEach { packageName ->
                clearPackageState(dpm, admin, packageManager, packageName, clearHidden = true)
            }
            if (previouslyLocked.isNotEmpty()) {
                prefs.edit().remove(KEY_LAST_LOCKDOWN_LOCKED_PACKAGES).apply()
            }
            return false
        }

        val ownPackage = context.packageName
        // Keyboards (and any installed IME) must keep working during lockdown, otherwise the allowed
        // apps like messages are unusable: suspending an app also disables its IME service.
        val imePackages = inputMethodPackageKeys(context)
        val toLock = loadLauncherApps(context)
            .map { it.packageName }
            .filter { pkg ->
                !pkg.equals(ownPackage, ignoreCase = true) &&
                    pkg.lowercase(Locale.US) !in imePackages &&
                    !isLockdownAllowed(packageManager, pkg)
            }
            .distinct()

        toLock.forEach { packageName ->
            runCatching {
                dpm.setPackagesSuspended(admin, arrayOf(packageName), true)
                dpm.setApplicationHidden(admin, packageName, true)
            }
        }
        previouslyLocked
            .filter { it !in toLock }
            .forEach { packageName ->
                clearPackageState(dpm, admin, packageManager, packageName, clearHidden = true)
            }

        prefs.edit()
            .putStringSet(KEY_LAST_LOCKDOWN_LOCKED_PACKAGES, (previouslyLocked + toLock).toSet())
            .apply()
        return true
    }

    fun shouldHideFromLauncher(packageName: String): Boolean {
        val normalizedPackage = packageName.lowercase()
        return blockedLauncherPackagePrefixes.any { normalizedPackage.startsWith(it) }
    }

    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    fun markPolicyDirty(
        packageStateChanged: Boolean = false,
        staticPoliciesChanged: Boolean = false
    ) {
        if (packageStateChanged) {
            invalidateLauncherAppsCache()
            lastAppliedLockTaskPackages = null
            lastUserControlDisabledPackages = null
        }
        if (staticPoliciesChanged) {
            staticPoliciesDirty = true
        }
        policyStateDirty = true
    }

    fun applyPolicies(context: Context, force: Boolean = false) {
        val appContext = context.applicationContext
        val now = SystemClock.elapsedRealtime()
        if (!force && !policyStateDirty && now - lastPolicyApplyElapsedRealtime < POLICY_REAPPLY_MIN_INTERVAL_MS) {
            return
        }

        synchronized(policyApplyLock) {
            val synchronizedNow = SystemClock.elapsedRealtime()
            if (!force && !policyStateDirty &&
                synchronizedNow - lastPolicyApplyElapsedRealtime < POLICY_REAPPLY_MIN_INTERVAL_MS
            ) {
                return
            }

            val dpm = appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(appContext, OttoDeviceAdminReceiver::class.java)
            if (!dpm.isDeviceOwnerApp(appContext.packageName)) return

            val packageManager = appContext.packageManager

            // A running lockdown supersedes the normal blocked/time-gate policy: lock everything
            // except the allowlist and skip the rest.
            if (applyLockdownIfActive(dpm, admin, appContext, packageManager)) {
                applyStaticPoliciesIfNeeded(dpm, admin, appContext)
                applyLockTaskPackagesIfChanged(dpm, admin, appContext)
                policyStateDirty = false
                lastPolicyApplyElapsedRealtime = SystemClock.elapsedRealtime()
                return
            }

            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val previousBlockedApps = prefs.getStringSet(KEY_LAST_HARD_BLOCKED_APPS, emptySet()).orEmpty()
            val previousAppliedInstalledBlockedApps = prefs
                .getStringSet(KEY_LAST_APPLIED_INSTALLED_BLOCKED_APPS, emptySet())
                .orEmpty()
            val currentBlockedApps = hardBlockedAppPackages
            val installedBlockedApps = currentBlockedApps
                .filter { isPackageInstalled(packageManager, it) }
                .toSet()
            val packagesToBlock = installedBlockedApps - previousAppliedInstalledBlockedApps
            val packagesToUnblock = (
                (previousBlockedApps - currentBlockedApps) +
                    (previousAppliedInstalledBlockedApps - installedBlockedApps)
                ).distinct()
            val timeGateRules = installedTimeGateRules(appContext)
                .filterNot { isBlockedApp(it.packageName) }
            val currentTimeGatedPackages = timeGateRules
                .map { packageKey(it.packageName) }
                .toSet()
            val previousTimeGatedSuspendedPackages = prefs
                .getStringSet(KEY_LAST_TIME_GATED_SUSPENDED_PACKAGES, emptySet())
                .orEmpty()
            val currentTimeGatedSuspendedPackages = mutableSetOf<String>()

            packagesToUnblock.forEach { packageName ->
                clearPackageState(dpm, admin, packageManager, packageName, clearHidden = true)
            }

            packagesToBlock.forEach { packageName ->
                runCatching {
                    dpm.setApplicationHidden(admin, packageName, true)
                }
            }
            if (packagesToBlock.isNotEmpty()) {
                runCatching {
                    dpm.setPackagesSuspended(admin, packagesToBlock.toTypedArray(), true)
                }
            }
            timeGateRules.forEach { rule ->
                if (shouldSuspendTimeGatedPackage(appContext, rule)) {
                    // Hide as well as suspend so the app leaves the system recents/overview list
                    // and cannot be resumed there outside its allowed hours. The 30-letter unlock
                    // code unhides it again for a short window.
                    runCatching {
                        dpm.setPackagesSuspended(admin, arrayOf(rule.packageName), true)
                        if (rule.hideWhenBlocked) {
                            dpm.setApplicationHidden(admin, rule.packageName, true)
                        }
                    }.onSuccess {
                        currentTimeGatedSuspendedPackages += rule.packageName
                    }
                } else {
                    clearPackageState(
                        dpm = dpm,
                        admin = admin,
                        packageManager = packageManager,
                        packageName = rule.packageName,
                        clearHidden = true
                    )
                }
            }
            previousTimeGatedSuspendedPackages
                .filter { packageKey(it) !in currentTimeGatedPackages }
                .forEach { packageName ->
                    clearPackageState(
                        dpm = dpm,
                        admin = admin,
                        packageManager = packageManager,
                        packageName = packageName,
                        clearHidden = true
                    )
                }

            val userControlDisabledPackages = (installedBlockedApps + appContext.packageName)
                .distinct()
                .sorted()
            if (lastUserControlDisabledPackages != userControlDisabledPackages) {
                runCatching {
                    dpm.setUserControlDisabledPackages(admin, userControlDisabledPackages)
                }.onSuccess {
                    lastUserControlDisabledPackages = userControlDisabledPackages
                }
            }
            runCatching {
                dpm.setUninstallBlocked(admin, appContext.packageName, true)
            }

            applyStaticPoliciesIfNeeded(dpm, admin, appContext)
            applyLockTaskPackagesIfChanged(dpm, admin, appContext)

            prefs.edit()
                .putStringSet(KEY_LAST_HARD_BLOCKED_APPS, currentBlockedApps)
                .putStringSet(KEY_LAST_APPLIED_INSTALLED_BLOCKED_APPS, installedBlockedApps)
                .putStringSet(KEY_LAST_TIME_GATED_SUSPENDED_PACKAGES, currentTimeGatedSuspendedPackages)
                .apply()

            policyStateDirty = false
            lastPolicyApplyElapsedRealtime = SystemClock.elapsedRealtime()
        }
    }

    /**
     * Package names of installed time-gated apps that get fully hidden during their blocked window
     * (currently just Slack). The launcher uses these to keep a cached tile reachable in search
     * while the app is hidden, so the unlock-code dialog stays accessible.
     */
    fun hideableTimeGatedPackages(context: Context): List<String> {
        return installedTimeGateRules(context.applicationContext)
            .filter { it.hideWhenBlocked }
            .map { it.packageName }
    }

    fun requiresLaunchGateCode(context: Context, packageName: String): Boolean {
        val rule = timeGateRuleFor(context.applicationContext, packageName) ?: return false
        if (!isPackageInstalled(context.packageManager, rule.packageName)) return false
        return shouldSuspendTimeGatedPackage(context.applicationContext, rule)
    }

    fun newLaunchGateCode(context: Context, packageName: String): String? {
        val rule = timeGateRuleFor(context.applicationContext, packageName) ?: return null
        return buildString(rule.challengeCodeLength) {
            repeat(rule.challengeCodeLength) {
                append(LAUNCH_GATE_ALPHABET[launchGateRandom.nextInt(LAUNCH_GATE_ALPHABET.length)])
            }
        }
    }

    fun normalizeLaunchGateCode(value: String): String {
        return value
            .filter { it.isLetter() }
            .uppercase(Locale.US)
    }

    fun formatLaunchGateCode(value: String): String {
        return value.chunked(5).joinToString(" ")
    }

    fun verifyLaunchGateCode(
        context: Context,
        packageName: String,
        attemptedCode: String,
        expectedCode: String
    ): Boolean {
        val appContext = context.applicationContext
        val rule = timeGateRuleFor(appContext, packageName) ?: return false
        val normalizedExpectedCode = normalizeLaunchGateCode(expectedCode)
        if (normalizedExpectedCode.length != rule.challengeCodeLength) return false
        if (normalizeLaunchGateCode(attemptedCode) != normalizedExpectedCode) return false

        appContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(unlockKeyFor(rule.packageName), System.currentTimeMillis() + rule.temporaryUnlockDurationMs)
            .apply()

        markPolicyDirty()
        applyPolicies(appContext)
        return true
    }

    fun launchGatePrompt(context: Context, packageName: String): String? {
        return timeGateRuleFor(context.applicationContext, packageName)?.let {
            "Type the ${it.challengeCodeLength}-letter code to open ${it.label} outside weekday 9am to 5pm."
        }
    }

    fun launchGateFailureMessage(context: Context, packageName: String): String? {
        return timeGateRuleFor(context.applicationContext, packageName)?.let {
            "Incorrect code for ${it.label}."
        }
    }

    fun syncLockTaskMode(activity: Activity) {
        val appContext = activity.applicationContext
        val dpm = appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val activityManager = activity.getSystemService(ActivityManager::class.java)
        if (!dpm.isDeviceOwnerApp(appContext.packageName)) return
        if (!AUTO_ENTER_LOCK_TASK) {
            if (activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) {
                runCatching { activity.stopLockTask() }
            }
            return
        }
        if (!dpm.isLockTaskPermitted(appContext.packageName)) return
        if (activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) return

        runCatching { activity.startLockTask() }
    }

    fun startWebsiteVpnIfNeeded(context: Context) {
        val appContext = context.applicationContext
        if (VpnService.prepare(appContext) != null) {
            OttoDiagnostics.warn(
                appContext,
                "Policy",
                "Website shield not started because VPN consent is required or another VPN is active."
            )
            return
        }
        if (OttoDnsVpnService.isActive()) {
            OttoDiagnostics.info(appContext, "Policy", "Website shield already active.")
            return
        }
        runCatching {
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, OttoDnsVpnService::class.java)
            )
        }.onSuccess {
            OttoDiagnostics.info(appContext, "Policy", "Requested website shield start.")
        }.onFailure { error ->
            OttoDiagnostics.error(appContext, "Policy", "Failed to start website shield.", error)
        }
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

    private fun applyStaticPoliciesIfNeeded(
        dpm: DevicePolicyManager,
        admin: ComponentName,
        context: Context
    ) {
        if (!staticPoliciesDirty) return
        applyUserRestrictions(dpm, admin)
        applyPersistentHome(dpm, admin, context)
        applyLockTaskFeatures(dpm, admin)
        applyAlwaysOnVpnPolicy(dpm, admin, context)
        staticPoliciesDirty = false
    }

    private fun applyLockTaskPackagesIfChanged(
        dpm: DevicePolicyManager,
        admin: ComponentName,
        context: Context
    ) {
        val allowedPackages = (loadLauncherApps(context).map { it.packageName } +
            context.packageName +
            ALLOWED_SYSTEM_PACKAGES)
            .distinct()
            .sorted()

        if (allowedPackages == lastAppliedLockTaskPackages) return

        runCatching {
            dpm.setLockTaskPackages(admin, allowedPackages.toTypedArray())
        }.onSuccess {
            lastAppliedLockTaskPackages = allowedPackages
        }
    }

    private fun applyLockTaskFeatures(
        dpm: DevicePolicyManager,
        admin: ComponentName
    ) {
        val baseFlags = DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO or
            DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS or
            DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
            DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW or
            DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS or
            DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD

        runCatching {
            dpm.setLockTaskFeatures(admin, baseFlags or LOCK_TASK_FEATURE_QUICK_SETTINGS)
        }.getOrElse {
            dpm.setLockTaskFeatures(admin, baseFlags)
        }
    }

    private fun applyAlwaysOnVpnPolicy(
        dpm: DevicePolicyManager,
        admin: ComponentName,
        context: Context
    ) {
        try {
            dpm.setAlwaysOnVpnPackage(admin, context.packageName, false)
        } catch (_: NameNotFoundException) {
        } catch (_: UnsupportedOperationException) {
        }
    }

    private fun isPackageInstalled(packageManager: PackageManager, packageName: String): Boolean {
        // MATCH_UNINSTALLED_PACKAGES so apps this device owner has hidden (setApplicationHidden)
        // still register as installed, otherwise a hidden gated app looks uninstalled and its
        // policy stops being managed.
        val matchFlags = PackageManager.MATCH_DISABLED_COMPONENTS or
            PackageManager.MATCH_UNINSTALLED_PACKAGES
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(matchFlags.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, matchFlags)
            }
            true
        }.getOrDefault(false)
    }

    private fun timeGateRuleFor(context: Context, packageName: String): TimeGateRule? {
        val packageManager = context.packageManager
        return if (packageName.equals(slackGateRule.packageName, ignoreCase = true)) {
            slackGateRule
        } else if (isBrowserPackage(packageManager, packageName)) {
            browserGateRule(packageManager, packageName)
        } else {
            null
        }
    }

    private fun shouldSuspendTimeGatedPackage(context: Context, rule: TimeGateRule): Boolean {
        if (isWithinAllowedHours(rule)) return false

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val unlockedUntil = maxOf(
            prefs.getLong(unlockKeyFor(rule.packageName), 0L),
            legacyUnlockedUntil(rule, prefs)
        )
        return System.currentTimeMillis() >= unlockedUntil
    }

    private fun installedTimeGateRules(context: Context): List<TimeGateRule> {
        val packageManager = context.packageManager
        return buildList {
            if (isPackageInstalled(packageManager, slackGateRule.packageName)) {
                add(slackGateRule)
            }
            browserPackageNames(packageManager)
                .filterNot { it.equals(slackGateRule.packageName, ignoreCase = true) }
                .mapTo(this) { browserGateRule(packageManager, it) }
        }.distinctBy { packageKey(it.packageName) }
    }

    private fun browserGateRule(packageManager: PackageManager, packageName: String): TimeGateRule {
        return TimeGateRule(
            packageName = packageName,
            label = appLabel(packageManager, packageName) ?: "Browser",
            startHourInclusive = TIME_GATE_START_HOUR_INCLUSIVE,
            endHourExclusive = TIME_GATE_END_HOUR_EXCLUSIVE,
            challengeCodeLength = LAUNCH_GATE_CODE_LENGTH,
            temporaryUnlockDurationMs = TIME_GATE_UNLOCK_DURATION_MS,
            // Browsers are discovered dynamically by intent and would be unrecoverable once hidden,
            // so they stay suspended-only; only Slack is fully hidden during its blocked window.
            hideWhenBlocked = false
        )
    }

    private fun isBrowserPackage(packageManager: PackageManager, packageName: String): Boolean {
        val targetPackageKey = packageKey(packageName)
        return browserPackageNames(packageManager).any { packageKey(it) == targetPackageKey }
    }

    private fun browserPackageNames(packageManager: PackageManager): List<String> {
        val packagesByKey = linkedMapOf<String, String>()
        for (intent in browserQueryIntents()) {
            for (resolveInfo in queryPolicyActivities(packageManager, intent)) {
                val browserPackage = resolveInfo.activityInfo?.packageName ?: continue
                if (browserPackage.isBlank()) continue
                if (!isPackageInstalled(packageManager, browserPackage)) continue

                packagesByKey.putIfAbsent(packageKey(browserPackage), browserPackage)
            }
        }
        return packagesByKey.values.toList()
    }

    private fun queryPolicyActivities(
        packageManager: PackageManager,
        intent: Intent
    ) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
    }

    private fun browserQueryIntents(): List<Intent> {
        return listOf("https://www.example.com", "http://www.example.com").map { url ->
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
        }
    }

    private fun appLabel(packageManager: PackageManager, packageName: String): String? {
        return runCatching {
            val app = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
            }
            packageManager.getApplicationLabel(app).toString()
        }.getOrNull()
    }

    private fun legacyUnlockedUntil(
        rule: TimeGateRule,
        prefs: SharedPreferences
    ): Long {
        return if (rule.packageName.equals(slackGateRule.packageName, ignoreCase = true)) {
            prefs.getLong(KEY_LEGACY_SLACK_UNLOCKED_UNTIL, 0L)
        } else {
            0L
        }
    }

    private fun unlockKeyFor(packageName: String): String {
        return "$KEY_TIME_GATE_UNLOCKED_UNTIL_PREFIX${packageKey(packageName)}"
    }

    private fun packageKey(packageName: String): String {
        return packageName.lowercase(Locale.US)
    }

    private fun isWithinAllowedHours(rule: TimeGateRule): Boolean {
        val now = LocalDateTime.now()
        val isWeekday = now.dayOfWeek in DayOfWeek.MONDAY..DayOfWeek.FRIDAY
        if (!isWeekday) return false
        return now.hour in rule.startHourInclusive until rule.endHourExclusive
    }
}
