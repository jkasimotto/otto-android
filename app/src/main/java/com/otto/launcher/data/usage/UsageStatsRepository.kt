package com.otto.launcher.data.usage

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.otto.launcher.domain.policy.AppDescriptor
import com.otto.launcher.domain.policy.AppPolicyEngine
import com.otto.launcher.domain.policy.AppTier
import com.otto.launcher.domain.usage.TodayUsageSummary
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class UsageStatsRepository(
    private val context: Context,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val policyEngine: AppPolicyEngine = AppPolicyEngine()
) {
    private val appContext = context.applicationContext
    private val usageStatsManager = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val packageManager = appContext.packageManager
    private val zoneId: ZoneId = ZoneId.systemDefault()

    fun observeTodayUsage(): Flow<TodayUsageSummary> = flow {
        while (true) {
            emit(todayUsage())
            delay(60_000)
        }
    }

    fun hasUsageAccess(): Boolean {
        val appOps = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { appContext.startActivity(intent) }
    }

    fun todayUsage(): TodayUsageSummary {
        if (!hasUsageAccess()) return TodayUsageSummary.MissingPermission
        val now = clock.instant()
        val start = LocalDate.now(clock).atStartOfDay(zoneId).toInstant()
        val stats = usageStatsManager.queryAndAggregateUsageStats(start.toEpochMilli(), now.toEpochMilli())
        var totalMs = 0L
        var distractMs = 0L
        var workMs = 0L

        stats.forEach { (packageName, usageStats) ->
            val foregroundMs = usageStats.totalTimeInForeground.coerceAtLeast(0L)
            if (foregroundMs <= 0L) return@forEach
            totalMs += foregroundMs
            val app = AppDescriptor(
                label = appLabel(packageName),
                packageName = packageName,
                activityName = ""
            )
            when (policyEngine.policyFor(app).tier) {
                AppTier.DISTRACTION -> distractMs += foregroundMs
                AppTier.WORK -> workMs += foregroundMs
                else -> Unit
            }
        }

        return TodayUsageSummary(
            hasUsageAccess = true,
            totalMinutes = (totalMs / 60_000L).toInt(),
            distractingMinutes = (distractMs / 60_000L).toInt(),
            workOutsideWindowMinutes = (workMs / 60_000L).toInt()
        )
    }

    private fun appLabel(packageName: String): String {
        return runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
    }
}

