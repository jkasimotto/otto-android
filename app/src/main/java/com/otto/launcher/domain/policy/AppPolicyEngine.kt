package com.otto.launcher.domain.policy

import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

data class AppDescriptor(
    val label: String,
    val packageName: String,
    val activityName: String
)

class AppPolicyEngine(
    private val policies: List<AppPolicy> = emptyList(),
    private val clock: Clock = Clock.systemDefaultZone()
) {
    private val policyByPackage = policies.associateBy { packageKey(it.packageName) }

    fun policyFor(app: AppDescriptor): AppPolicy {
        return policyByPackage[packageKey(app.packageName)] ?: DefaultAppPolicyCatalog.policyFor(app)
    }

    fun gateFor(app: AppDescriptor): AppGate {
        val policy = policyFor(app)
        return gateFor(policy)
    }

    fun gateFor(policy: AppPolicy): AppGate {
        return when (policy.tier) {
            AppTier.BLOCKED -> AppGate.Blocked
            AppTier.ADMIN -> AppGate.AdminHidden
            AppTier.DISTRACTION -> AppGate.Distraction(
                challengeRequired = policy.challengeRequired,
                reasonRequired = policy.reasonRequired,
                defaultTimeboxMinutes = policy.defaultTimeboxMinutes ?: 10
            )
            AppTier.WORK -> {
                if (policy.allowedWindows.isEmpty() || isInsideAnyWindow(policy.allowedWindows)) {
                    AppGate.Allowed
                } else {
                    AppGate.WorkWindowClosed(policy.allowedWindows.first().label())
                }
            }
            AppTier.CORE,
            AppTier.PEOPLE,
            AppTier.UTILITY -> AppGate.Allowed
        }
    }

    fun shouldShowForQuery(app: AppDescriptor, query: String): Boolean {
        val normalized = query.trim().lowercase(Locale.US)
        if (normalized.length < 3) return false
        val policy = policyFor(app)
        if (policy.tier != AppTier.DISTRACTION && policy.tier != AppTier.BLOCKED) return true
        return isStrongMatch(app, normalized)
    }

    fun isStrongMatch(app: AppDescriptor, normalizedQuery: String): Boolean {
        val label = app.label.lowercase(Locale.US)
        val pkg = app.packageName.lowercase(Locale.US)
        return label == normalizedQuery ||
            pkg == normalizedQuery ||
            (normalizedQuery.length >= 4 && label.startsWith(normalizedQuery)) ||
            (normalizedQuery.length >= 8 && pkg.contains(normalizedQuery))
    }

    private fun isInsideAnyWindow(windows: List<TimeWindow>): Boolean {
        val now = LocalDateTime.now(clock)
        return windows.any { it.contains(now.dayOfWeek, now.toLocalTime()) }
    }

    companion object {
        fun packageKey(packageName: String): String = packageName.lowercase(Locale.US)
    }
}

object DefaultAppPolicyCatalog {
    private val workWindow = TimeWindow.weekdays(LocalTime.of(9, 0), LocalTime.of(17, 0))

    private val distractionPackageHints = listOf(
        "reddit",
        "tiktok",
        "musically",
        "instagram",
        "youtube",
        "facebook",
        "twitter",
        "x.android",
        "snapchat",
        "netflix",
        "twitch"
    )

    private val workPackageHints = listOf(
        "slack",
        "teams",
        "linear",
        "github",
        "gmail",
        "outlook",
        "email"
    )

    private val corePackageHints = listOf(
        "dialer",
        "phone",
        "contacts",
        "camera",
        "clock",
        "maps",
        "authenticator",
        "permissioncontroller",
        "packageinstaller"
    )

    private val adminPackageHints = listOf(
        "settings"
    )

    fun policyFor(app: AppDescriptor): AppPolicy {
        val haystack = "${app.label} ${app.packageName}".lowercase(Locale.US)
        val tier = when {
            adminPackageHints.any(haystack::contains) -> AppTier.ADMIN
            corePackageHints.any(haystack::contains) -> AppTier.CORE
            workPackageHints.any(haystack::contains) -> AppTier.WORK
            distractionPackageHints.any(haystack::contains) -> AppTier.DISTRACTION
            else -> AppTier.UTILITY
        }
        return AppPolicy(
            packageName = app.packageName,
            tier = tier,
            hiddenByDefault = tier == AppTier.DISTRACTION || tier == AppTier.BLOCKED || tier == AppTier.ADMIN,
            suspendedByDefault = tier == AppTier.DISTRACTION || tier == AppTier.BLOCKED,
            allowedWindows = if (tier == AppTier.WORK) listOf(workWindow) else emptyList(),
            challengeRequired = tier == AppTier.DISTRACTION,
            reasonRequired = tier == AppTier.DISTRACTION,
            defaultTimeboxMinutes = if (tier == AppTier.DISTRACTION) 10 else null,
            dailyLimitMinutes = null
        )
    }
}

