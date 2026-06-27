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
import com.otto.launcher.domain.time.AppUsageSlice
import com.otto.launcher.domain.time.UsageTimeSnapshot
import com.otto.launcher.domain.usage.DailyPhoneUsage
import com.otto.launcher.domain.usage.TodayUsageSummary
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
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

    fun observeTodayUsageSlices(): Flow<UsageTimeSnapshot> = flow {
        while (true) {
            emit(usageSlices(dayStart(), clock.instant()))
            delay(60_000)
        }
    }

    fun observeUsageSlices(start: java.time.Instant, end: java.time.Instant): Flow<UsageTimeSnapshot> = flow {
        while (true) {
            emit(usageSlices(start, end))
            delay(60_000)
        }
    }

    fun observeCurrentWeekUsageSlices(): Flow<UsageTimeSnapshot> = flow {
        while (true) {
            val start = LocalDate.now(clock)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(zoneId)
                .toInstant()
            emit(usageSlices(start, clock.instant()))
            delay(60_000)
        }
    }

    /**
     * Total foreground minutes for each of the last 7 days (oldest first, ending today). Empty when
     * usage access is off. queryAndAggregateUsageStats only totals a whole range, so each day is
     * queried separately to get a per-day breakdown for the weekly chart.
     */
    fun observeWeeklyDailyUsage(): Flow<List<DailyPhoneUsage>> = flow {
        while (true) {
            emit(weeklyDailyUsage())
            delay(60_000)
        }
    }

    fun weeklyDailyUsage(): List<DailyPhoneUsage> {
        if (!hasUsageAccess()) return emptyList()
        val today = LocalDate.now(clock)
        val now = clock.instant()
        return (0L..6L).map { offset ->
            val date = today.minusDays(6 - offset)
            val start = date.atStartOfDay(zoneId).toInstant()
            val end = if (date == today) now else date.plusDays(1).atStartOfDay(zoneId).toInstant()
            val stats = usageStatsManager.queryAndAggregateUsageStats(start.toEpochMilli(), end.toEpochMilli())
            val totalMs = stats.values.sumOf { it.totalTimeInForeground.coerceAtLeast(0L) }
            DailyPhoneUsage(date = date, totalMinutes = (totalMs / 60_000L).toInt())
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
        val start = dayStart()
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

    fun usageSlices(start: java.time.Instant, end: java.time.Instant): UsageTimeSnapshot {
        if (!hasUsageAccess()) return UsageTimeSnapshot.MissingPermission
        val stats = usageStatsManager.queryAndAggregateUsageStats(start.toEpochMilli(), end.toEpochMilli())
        val slices = stats
            .mapNotNull { (packageName, usageStats) ->
                val minutes = (usageStats.totalTimeInForeground.coerceAtLeast(0L) / 60_000L).toInt()
                if (minutes > 0) AppUsageSlice(packageName, minutes) else null
            }
            .sortedByDescending { it.minutes }
        return UsageTimeSnapshot(hasUsageAccess = true, slices = slices)
    }

    private fun dayStart(): java.time.Instant {
        return LocalDate.now(clock).atStartOfDay(zoneId).toInstant()
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
