package com.otto.launcher.quest

import android.content.Context
import com.otto.launcher.guard.OttoPolicyController
import com.otto.launcher.data.policy.PolicyRepository
import com.otto.launcher.guard.DeviceOwnerController
import com.otto.launcher.domain.policy.AppTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A temporary policy layer. Emergency tiers and the quest's real app packages remain available. */
object QuestFocus {
    private const val PREFS = "quest_focus"
    private const val LOCKED = "locked_packages"

    suspend fun enter(context: Context, suggestedApps: Collection<String>) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val controller = DeviceOwnerController(app)
        if (!controller.isDeviceOwner() || OttoPolicyController.isLockdownActive(app)) return@withContext
        val policies = PolicyRepository(app).policies()
        val emergency = policies.filter { it.tier in setOf(AppTier.CORE, AppTier.PEOPLE, AppTier.ADMIN) }.map { it.packageName }
        val allowed = (emergency + suggestedApps + app.packageName).toSet()
        @Suppress("DEPRECATION")
        val launchable = app.packageManager.getInstalledApplications(0).mapNotNull { info ->
            info.packageName.takeIf { app.packageManager.getLaunchIntentForPackage(it) != null }
        }
        val locked = launchable.filterNot(allowed::contains)
        controller.suspendPackages(locked, true)
        locked.forEach { controller.hidePackage(it, true) }
        controller.setLockTaskAllowlist(allowed.toList())
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(LOCKED, locked.toSet()).apply()
    }

    suspend fun exit(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val controller = DeviceOwnerController(app)
        val locked = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(LOCKED, emptySet()).orEmpty()
        if (controller.isDeviceOwner()) {
            controller.suspendPackages(locked.toList(), false)
            locked.forEach { controller.hidePackage(it, false) }
        }
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(LOCKED).apply()
        OttoPolicyController.markPolicyDirty(packageStateChanged = true)
        OttoPolicyController.applyPolicies(app, force = true)
    }
}
