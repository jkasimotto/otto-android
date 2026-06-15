package com.otto.launcher.data.policy

import android.content.Context
import com.otto.launcher.device.DeviceOwnerController
import com.otto.launcher.domain.policy.AppTier
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

        val packageNames = policies.map { it.packageName }.distinct()
        controller.suspendPackages(packageNames, suspended = false)
        packageNames.forEach { controller.hidePackage(it, hidden = false) }

        val packagesToHide = policies
            .filter { policy ->
                policy.packageName !in openSessions &&
                    policy.tier != AppTier.ADMIN &&
                    (policy.tier == AppTier.BLOCKED ||
                        policy.hiddenByDefault ||
                        (sleepActive && policy.tier in setOf(AppTier.WORK, AppTier.DISTRACTION)))
            }
            .map { it.packageName }

        val packagesToSuspend = policies
            .filter { policy ->
                policy.packageName !in openSessions &&
                    (policy.tier == AppTier.BLOCKED ||
                        policy.suspendedByDefault ||
                        (sleepActive && policy.tier in setOf(AppTier.WORK, AppTier.DISTRACTION)))
            }
            .map { it.packageName }

        packagesToHide.forEach { controller.hidePackage(it, hidden = true) }
        controller.suspendPackages(packagesToSuspend, suspended = true)
        controller.applyGreyscale(sleepActive)
    }

    private fun AppSessionEntity.isStillOpen(clock: Clock): Boolean {
        val minutes = timeboxMinutes ?: return false
        if (endedAt != null) return false
        return clock.instant().isBefore(startedAt.plusSeconds(minutes * 60L))
    }
}

