package com.otto.launcher.domain.usage

data class TodayUsageSummary(
    val hasUsageAccess: Boolean,
    val totalMinutes: Int,
    val distractingMinutes: Int,
    val workOutsideWindowMinutes: Int
) {
    companion object {
        val MissingPermission = TodayUsageSummary(
            hasUsageAccess = false,
            totalMinutes = 0,
            distractingMinutes = 0,
            workOutsideWindowMinutes = 0
        )
    }
}

