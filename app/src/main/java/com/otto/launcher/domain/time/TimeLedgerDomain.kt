package com.otto.launcher.domain.time

import com.otto.launcher.domain.policy.AppTier
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

enum class TimeCategoryKind {
    CORE,
    GROWTH,
    RECOVERY,
    MAINTENANCE,
    LEAK,
    BUFFER
}

enum class BudgetPeriod {
    DAILY,
    WEEKLY
}

enum class TimeBlockSource {
    MANUAL,
    COMMAND,
    APP_USAGE,
    CALENDAR,
    SLEEP_TRACE,
    INFERRED,
    REVIEW_CORRECTED
}

enum class TimeMode {
    OPEN,
    FOCUS_WORK,
    RELATIONSHIP,
    MOVEMENT,
    REST,
    WIND_DOWN,
    SLEEP
}

enum class DayPlanMode {
    NORMAL,
    LOW_SLEEP,
    DISRUPTED,
    MINIMUM_VIABLE
}

data class TimeCategory(
    val id: String,
    val name: String,
    val kind: TimeCategoryKind,
    val sortOrder: Int,
    val isDefault: Boolean
)

data class TimeBudget(
    val id: String,
    val categoryId: String,
    val period: BudgetPeriod,
    val floorMinutes: Int?,
    val targetMinutes: Int?,
    val capMinutes: Int?,
    val activeFrom: LocalDate,
    val activeTo: LocalDate?
)

data class TimeBlock(
    val id: String,
    val categoryId: String,
    val startedAt: Instant,
    val endedAt: Instant?,
    val source: TimeBlockSource,
    val confidence: Float,
    val label: String?,
    val linkedPackageName: String?,
    val linkedCalendarEventId: String?
) {
    fun durationMinutes(now: Instant): Int {
        val end = endedAt ?: now
        return Duration.between(startedAt, end).toMinutes().toInt().coerceAtLeast(0)
    }
}

data class WellbeingPulse(
    val id: String,
    val timeBlockId: String?,
    val date: LocalDate,
    val timeAffluence: Int?,
    val enjoyment: Int?,
    val meaning: Int?,
    val energy: Int?,
    val connection: Int?,
    val flow: Int?,
    val stress: Int?,
    val note: String?
)

data class AppTimeMapping(
    val packageName: String,
    val defaultCategoryId: String,
    val appTier: AppTier,
    val countAsDigitalDrift: Boolean,
    val requiresIntent: Boolean
)

data class AppUsageSlice(
    val packageName: String,
    val minutes: Int
)

data class UsageTimeSnapshot(
    val hasUsageAccess: Boolean,
    val slices: List<AppUsageSlice>
) {
    companion object {
        val MissingPermission = UsageTimeSnapshot(
            hasUsageAccess = false,
            slices = emptyList()
        )
    }
}

data class TimeLedgerTotal(
    val categoryId: String,
    val minutes: Int
)

data class TimeLedgerRow(
    val categoryId: String,
    val label: String,
    val value: String,
    val minutes: Int,
    val budgetMinutes: Int?,
    val budgetKind: BudgetKind?
)

enum class BudgetKind {
    FLOOR,
    TARGET,
    CAP
}

data class ActiveTimeBlock(
    val block: TimeBlock,
    val category: TimeCategory,
    val elapsedMinutes: Int
)

data class TodayTimeLedger(
    val date: LocalDate,
    val hasUsageAccess: Boolean,
    val activeBlock: ActiveTimeBlock?,
    val rows: List<TimeLedgerRow>,
    val totals: List<TimeLedgerTotal>,
    val unknownMinutes: Int,
    val digitalDriftMinutes: Int,
    val digitalDriftCapMinutes: Int?,
    val dayPlanMode: DayPlanMode,
    val timeAffluence: Int?
)

data class WeeklyTimeLedger(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val rows: List<TimeLedgerRow>,
    val experimentSuggestion: String?
)

data class TimeLedgerReviewGap(
    val startAt: Instant,
    val endAt: Instant,
    val minutes: Int
)

data class DailyTimeReview(
    val date: LocalDate,
    val rows: List<TimeLedgerRow>,
    val unknownGaps: List<TimeLedgerReviewGap>,
    val timeAffluence: Int?
)

object TimeCategoryIds {
    const val SLEEP = "sleep"
    const val FOCUSED_WORK = "focused_work"
    const val RELATIONSHIPS = "relationships"
    const val MOVEMENT = "movement"
    const val REST = "rest"
    const val GROWTH = "growth"
    const val LEARNING = "learning"
    const val CHORES = "chores"
    const val ADMIN = "admin"
    const val COMMUTE = "commute"
    const val DIGITAL_DRIFT = "digital_drift"
    const val BUFFER = "buffer"
    const val UTILITY = "utility"
    const val UNKNOWN = "unknown"
}

object DefaultTimeCategories {
    val categories = listOf(
        TimeCategory(TimeCategoryIds.SLEEP, "Sleep", TimeCategoryKind.CORE, 10, true),
        TimeCategory(TimeCategoryIds.FOCUSED_WORK, "Focused work", TimeCategoryKind.CORE, 20, true),
        TimeCategory(TimeCategoryIds.RELATIONSHIPS, "Relationships", TimeCategoryKind.CORE, 30, true),
        TimeCategory(TimeCategoryIds.MOVEMENT, "Movement", TimeCategoryKind.CORE, 40, true),
        TimeCategory(TimeCategoryIds.REST, "Rest", TimeCategoryKind.RECOVERY, 50, true),
        TimeCategory(TimeCategoryIds.GROWTH, "Growth", TimeCategoryKind.GROWTH, 60, true),
        TimeCategory(TimeCategoryIds.LEARNING, "Learning", TimeCategoryKind.GROWTH, 70, true),
        TimeCategory(TimeCategoryIds.CHORES, "Chores", TimeCategoryKind.MAINTENANCE, 80, true),
        TimeCategory(TimeCategoryIds.ADMIN, "Admin", TimeCategoryKind.MAINTENANCE, 90, true),
        TimeCategory(TimeCategoryIds.COMMUTE, "Commute", TimeCategoryKind.MAINTENANCE, 100, true),
        TimeCategory(TimeCategoryIds.DIGITAL_DRIFT, "Digital drift", TimeCategoryKind.LEAK, 110, true),
        TimeCategory(TimeCategoryIds.BUFFER, "Buffer", TimeCategoryKind.BUFFER, 120, true),
        TimeCategory(TimeCategoryIds.UTILITY, "Utility", TimeCategoryKind.MAINTENANCE, 130, true),
        TimeCategory(TimeCategoryIds.UNKNOWN, "Unknown", TimeCategoryKind.BUFFER, 140, true)
    )
}

fun TimeMode.categoryId(): String? {
    return when (this) {
        TimeMode.OPEN -> null
        TimeMode.FOCUS_WORK -> TimeCategoryIds.FOCUSED_WORK
        TimeMode.RELATIONSHIP -> TimeCategoryIds.RELATIONSHIPS
        TimeMode.MOVEMENT -> TimeCategoryIds.MOVEMENT
        TimeMode.REST -> TimeCategoryIds.REST
        TimeMode.WIND_DOWN -> TimeCategoryIds.REST
        TimeMode.SLEEP -> TimeCategoryIds.SLEEP
    }
}

fun TimeCategory.displayLabel(): String {
    return when (id) {
        TimeCategoryIds.RELATIONSHIPS -> "People"
        TimeCategoryIds.MOVEMENT -> "Move"
        TimeCategoryIds.DIGITAL_DRIFT -> "Drift"
        else -> name
    }
}
