package com.otto.launcher.data.policy

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.otto.launcher.domain.policy.AppDescriptor
import com.otto.launcher.domain.policy.AppPolicy
import com.otto.launcher.domain.policy.AppPolicyEngine
import com.otto.launcher.domain.policy.AppTier
import com.otto.launcher.domain.policy.DefaultAppPolicyCatalog
import com.otto.launcher.domain.policy.TimeWindow
import com.otto.launcher.trace.data.AppPolicyEntity
import com.otto.launcher.trace.data.AppSessionEntity
import com.otto.launcher.trace.data.TraceDatabase
import com.otto.launcher.worker.PolicyRestoreWorker
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PolicyRepository(
    context: Context,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    private val appContext = context.applicationContext
    private val dao = TraceDatabase.get(appContext).traceV2Dao()

    fun observePolicies(): Flow<List<AppPolicy>> {
        return dao.observeAppPolicies().map { policies -> policies.map { it.toDomain() } }
    }

    suspend fun policies(): List<AppPolicy> {
        return dao.appPolicies().map { it.toDomain() }
    }

    suspend fun seedDefaults(apps: List<AppDescriptor>) {
        val existingPolicies = dao.appPolicies()
        val existing = existingPolicies.map { AppPolicyEngine.packageKey(it.packageName) }.toSet()
        val now = clock.instant()
        val defaults = apps
            .filter { AppPolicyEngine.packageKey(it.packageName) !in existing }
            .map { DefaultAppPolicyCatalog.policyFor(it).toEntity(now) }
        val repaired = existingPolicies.mapNotNull { it.repairCriticalPeoplePolicy(now) }
        val changes = defaults + repaired
        if (changes.isNotEmpty()) {
            dao.upsertAppPolicies(changes)
        }
    }

    suspend fun recordDistractionSession(
        packageName: String,
        tier: AppTier,
        reason: String,
        timeboxMinutes: Int
    ) {
        val now = clock.instant()
        dao.insertAppSession(
            AppSessionEntity(
                id = UUID.randomUUID().toString(),
                packageName = packageName,
                tier = tier,
                startedAt = now,
                endedAt = null,
                launchReason = reason,
                gateType = "DISTRACTION",
                timeboxMinutes = timeboxMinutes
            )
        )
        val work = OneTimeWorkRequestBuilder<PolicyRestoreWorker>()
            .setInitialDelay(timeboxMinutes.toLong(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            "policy_restore_$packageName",
            ExistingWorkPolicy.REPLACE,
            work
        )
    }

    suspend fun endOpenSession(packageName: String) {
        dao.endOpenSessions(packageName, clock.instant())
    }
}

private fun AppPolicyEntity.toDomain(): AppPolicy {
    return AppPolicy(
        packageName = packageName,
        tier = tier,
        hiddenByDefault = hiddenByDefault,
        suspendedByDefault = suspendedByDefault,
        allowedWindows = parseWindows(workWindowJson),
        challengeRequired = challengeRequired,
        reasonRequired = reasonRequired,
        defaultTimeboxMinutes = defaultTimeboxMinutes,
        dailyLimitMinutes = dailyLimitMinutes
    )
}

private fun AppPolicy.toEntity(now: java.time.Instant): AppPolicyEntity {
    return AppPolicyEntity(
        packageName = packageName,
        tier = tier,
        hiddenByDefault = hiddenByDefault,
        suspendedByDefault = suspendedByDefault,
        challengeRequired = challengeRequired,
        reasonRequired = reasonRequired,
        defaultTimeboxMinutes = defaultTimeboxMinutes,
        dailyLimitMinutes = dailyLimitMinutes,
        workWindowJson = allowedWindows.firstOrNull()?.let { "${it.start}-${it.endExclusive}" },
        updatedAt = now
    )
}

private fun parseWindows(value: String?): List<TimeWindow> {
    if (value.isNullOrBlank()) return emptyList()
    val parts = value.split("-")
    if (parts.size != 2) return emptyList()
    val start = runCatching { LocalTime.parse(parts[0]) }.getOrNull() ?: return emptyList()
    val end = runCatching { LocalTime.parse(parts[1]) }.getOrNull() ?: return emptyList()
    return listOf(
        TimeWindow(
            days = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
            ),
            start = start,
            endExclusive = end
        )
    )
}
