package com.otto.launcher.trace.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

enum class TraceType {
    FOOD_PHOTO,
    DRINK_PHOTO,
    WEIGHT,
    SLEEP_SESSION,
    MEAL_ABSENCE,
    MANUAL_TIME_BLOCK,
    PHONE_USAGE_WINDOW,
    ACTIVITY_SESSION,
    WATER_COUNT,
    SOCIAL_CONTACT
}

enum class TraceSource {
    CAMERA,
    PHOTO_PICKER,
    MANUAL,
    HEALTH_CONNECT,
    USAGE_STATS,
    INFERRED
}

enum class TraceConfidence {
    EXACT,
    USER_CONFIRMED,
    IMPORTED,
    ESTIMATED,
    UNKNOWN
}

enum class MealSlot {
    BREAKFAST,
    LUNCH,
    DINNER,
    SNACK,
    DRINK,
    UNKNOWN
}

enum class TraceCategory {
    FOOD,
    DRINK,
    WEIGHT,
    SLEEP
}

enum class NextTraceActionKind {
    CONFIRM_SLEEP,
    LOG_SLEEP,
    LOG_WEIGHT,
    FOOD_PHOTO,
    DRINK_PHOTO,
    VIEW_TODAY,
    OPEN_CAPTURE
}

data class DataCoverage(
    val foodDays: Int,
    val weightDays: Int,
    val sleepDays: Int,
    val totalDays: Int
)

data class DailySummary(
    val date: LocalDate,
    val foodPhotoCount: Int,
    val drinkPhotoCount: Int,
    val firstFoodAt: Instant?,
    val lastFoodAt: Instant?,
    val eatingWindowMinutes: Int?,
    val weightKg: Double?,
    val weightTrendKg: Double?,
    val sleepDurationMinutes: Int?,
    val sleepStartAt: Instant?,
    val sleepEndAt: Instant?,
    val dataCoverage: DataCoverage
)

data class WeeklySummary(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val foodPhotoCount: Int,
    val foodCoverageDays: Int,
    val weightCoverageDays: Int,
    val sleepCoverageDays: Int,
    val medianEatingWindowMinutes: Int?,
    val medianSleepDurationMinutes: Int?,
    val weightTrendDeltaKg: Double?,
    val lateFoodNights: Int
)

data class NextTraceAction(
    val kind: NextTraceActionKind,
    val title: String,
    val detail: String?,
    val primaryLabel: String,
    val mealSlot: MealSlot? = null
)

data class TraceMedia(
    val id: String,
    val localPath: String,
    val thumbnailPath: String?,
    val mimeType: String
)

data class TraceTimelineItem(
    val traceId: String,
    val type: TraceType,
    val occurredAt: Instant,
    val endedAt: Instant?,
    val title: String,
    val detail: String?,
    val notes: String?,
    val media: TraceMedia?,
    val isDrinkOnly: Boolean,
    val hidden: Boolean,
    val weightKg: Double?,
    val sleepDurationMinutes: Int?
)

data class TraceSettingsState(
    val foodEnabled: Boolean,
    val drinkEnabled: Boolean,
    val weightEnabled: Boolean,
    val sleepEnabled: Boolean
)

data class TraceDashboardState(
    val today: DailySummary,
    val weekly: WeeklySummary,
    val timeline: List<TraceTimelineItem>,
    val nextAction: NextTraceAction,
    val sleepEstimate: SleepEstimate?,
    val lastWeightKg: Double?,
    val settings: TraceSettingsState
)

data class SleepEstimate(
    val startAt: Instant,
    val endAt: Instant
) {
    val durationMinutes: Int
        get() = Duration.between(startAt, endAt).toMinutes().toInt().coerceAtLeast(0)
}

object TraceRules {
    private val breakfast = LocalTime.of(7, 0)..LocalTime.of(10, 30)
    private val lunch = LocalTime.of(11, 30)..LocalTime.of(14, 30)
    private val dinner = LocalTime.of(18, 0)..LocalTime.of(21, 30)

    fun inferMealSlot(time: LocalTime): MealSlot {
        return when {
            time in breakfast -> MealSlot.BREAKFAST
            time in lunch -> MealSlot.LUNCH
            time in dinner -> MealSlot.DINNER
            else -> MealSlot.SNACK
        }
    }

    fun currentMealWindow(time: LocalTime): MealSlot? {
        return when {
            time in breakfast -> MealSlot.BREAKFAST
            time in lunch -> MealSlot.LUNCH
            time in dinner -> MealSlot.DINNER
            else -> null
        }
    }

    fun dayBounds(date: LocalDate, zoneId: ZoneId): Pair<Instant, Instant> {
        val start = date.atStartOfDay(zoneId).toInstant()
        return start to date.plusDays(1).atStartOfDay(zoneId).toInstant()
    }

    fun eatingWindowMinutes(first: Instant?, last: Instant?): Int? {
        if (first == null || last == null || first == last) return null
        return Duration.between(first, last).toMinutes().toInt().coerceAtLeast(0)
    }

    fun median(values: List<Int>): Int? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val midpoint = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            ((sorted[midpoint - 1] + sorted[midpoint]) / 2.0).roundToInt()
        } else {
            sorted[midpoint]
        }
    }

    fun weightTrendDeltaKg(weights: List<Pair<Instant, Double>>): Double? {
        if (weights.size < 2) return null
        val sorted = weights.sortedBy { it.first }
        val splitIndex = (sorted.size / 2).coerceAtLeast(1)
        val earlier = sorted.take(splitIndex).map { it.second }
        val later = sorted.drop(splitIndex).map { it.second }
        if (earlier.isEmpty() || later.isEmpty()) return null
        return later.average() - earlier.average()
    }

    fun isLateFood(instant: Instant, zoneId: ZoneId): Boolean {
        val localTime = instant.atZone(zoneId).toLocalTime()
        return localTime >= LocalTime.of(21, 30)
    }

    fun candidateSleepEstimate(previousLauncherSeenAt: Instant?, now: Instant, zoneId: ZoneId): SleepEstimate? {
        val previous = previousLauncherSeenAt ?: return null
        val gap = Duration.between(previous, now)
        if (gap < Duration.ofHours(4) || gap > Duration.ofHours(14)) return null

        val previousLocal = previous.atZone(zoneId).toLocalTime()
        val nowLocal = now.atZone(zoneId).toLocalTime()
        val plausibleStart = previousLocal >= LocalTime.of(20, 0) || previousLocal <= LocalTime.of(3, 0)
        val plausibleWake = nowLocal in LocalTime.of(5, 0)..LocalTime.of(11, 30)
        return if (plausibleStart && plausibleWake) SleepEstimate(previous, now) else null
    }
}
