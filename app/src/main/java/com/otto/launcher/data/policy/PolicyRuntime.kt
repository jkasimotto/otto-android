package com.otto.launcher.data.policy

import android.content.Context
import com.otto.launcher.device.DeviceOwnerController
import com.otto.launcher.domain.policy.AppTier
import com.otto.launcher.domain.time.TimeCategoryIds
import com.otto.launcher.trace.data.AppSessionEntity
import com.otto.launcher.trace.data.TraceDatabase
import java.time.Clock

object PolicyRuntime {
    suspend fun applyCurrentPolicy(
        context: Context,
        clock: Clock = Clock.systemDefaultZone()
    ) {
        val appContext = context.applicationContext
        val controller = DeviceOwnerController(appContext)
        if (!controller.isDeviceOwner()) return

        val dao = TraceDatabase.get(appContext).traceV2Dao()
        val policies = dao.appPolicies()
        if (policies.isEmpty()) return

        val openSessions = dao.openAppSessions()
            .filter { it.isStillOpen(clock) }
            .map { it.packageName }
            .toSet()
        val sleepActive = dao.openSleepSession() != null
        val activeTimeCategory = dao.openTimeBlock()?.categoryId

        val packageNames = policies.map { it.packageName }.distinct()
        controller.suspendPackages(packageNames, suspended = false)
        packageNames.forEach { controller.hidePackage(it, hidden = false) }

        val packagesToHide = policies
            .filter { policy ->
                policy.packageName !in openSessions &&
                    policy.tier != AppTier.ADMIN &&
                    (policy.tier == AppTier.BLOCKED ||
                        policy.hiddenByDefault ||
                        (sleepActive && policy.tier in setOf(AppTier.WORK, AppTier.DISTRACTION)) ||
                        shouldHideForActiveTime(policy.tier, activeTimeCategory))
            }
            .map { it.packageName }

        val packagesToSuspend = policies
            .filter { policy ->
                policy.packageName !in openSessions &&
                    (policy.tier == AppTier.BLOCKED ||
                        policy.suspendedByDefault ||
                        (sleepActive && policy.tier in setOf(AppTier.WORK, AppTier.DISTRACTION)) ||
                        shouldSuspendForActiveTime(policy.tier, activeTimeCategory))
            }
            .map { it.packageName }

        packagesToHide.forEach { controller.hidePackage(it, hidden = true) }
        controller.suspendPackages(packagesToSuspend, suspended = true)
        controller.applyGreyscale(sleepActive || activeTimeCategory == TimeCategoryIds.MOVEMENT)
    }

    private fun AppSessionEntity.isStillOpen(clock: Clock): Boolean {
        val minutes = timeboxMinutes ?: return false
        if (endedAt != null) return false
        return clock.instant().isBefore(startedAt.plusSeconds(minutes * 60L))
    }

    private fun shouldHideForActiveTime(tier: AppTier, activeTimeCategory: String?): Boolean {
        return when (activeTimeCategory) {
            TimeCategoryIds.RELATIONSHIPS,
            TimeCategoryIds.MOVEMENT,
            TimeCategoryIds.REST,
            TimeCategoryIds.COMMUTE -> tier in setOf(AppTier.WORK, AppTier.DISTRACTION, AppTier.ADMIN)
            TimeCategoryIds.FOCUSED_WORK -> tier == AppTier.DISTRACTION
            else -> false
        }
    }

    private fun shouldSuspendForActiveTime(tier: AppTier, activeTimeCategory: String?): Boolean {
        return when (activeTimeCategory) {
            TimeCategoryIds.RELATIONSHIPS,
            TimeCategoryIds.MOVEMENT,
            TimeCategoryIds.REST,
            TimeCategoryIds.COMMUTE -> tier in setOf(AppTier.WORK, AppTier.DISTRACTION)
            TimeCategoryIds.FOCUSED_WORK -> tier == AppTier.DISTRACTION
            else -> false
        }
    }
}
