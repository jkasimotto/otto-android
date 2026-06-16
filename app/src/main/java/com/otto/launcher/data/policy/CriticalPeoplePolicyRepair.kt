package com.otto.launcher.data.policy

import com.otto.launcher.domain.policy.AppPolicyEngine
import com.otto.launcher.domain.policy.AppTier
import com.otto.launcher.trace.data.AppPolicyEntity
import java.time.Instant

internal fun isCriticalPeoplePackage(packageName: String): Boolean {
    return AppPolicyEngine.packageKey(packageName) in CRITICAL_PEOPLE_PACKAGES
}

internal fun AppPolicyEntity.repairCriticalPeoplePolicy(now: Instant): AppPolicyEntity? {
    if (!isCriticalPeoplePackage(packageName)) return null
    if (
        tier == AppTier.PEOPLE &&
        !hiddenByDefault &&
        !suspendedByDefault &&
        !challengeRequired &&
        !reasonRequired &&
        defaultTimeboxMinutes == null &&
        workWindowJson == null
    ) {
        return null
    }
    return copy(
        tier = AppTier.PEOPLE,
        hiddenByDefault = false,
        suspendedByDefault = false,
        challengeRequired = false,
        reasonRequired = false,
        defaultTimeboxMinutes = null,
        dailyLimitMinutes = null,
        workWindowJson = null,
        updatedAt = now
    )
}

private val CRITICAL_PEOPLE_PACKAGES = setOf(
    "com.facebook.orca",
    "com.facebook.mlite"
)
