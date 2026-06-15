package com.otto.launcher.domain.policy

import java.time.DayOfWeek
import java.time.LocalTime

data class TimeWindow(
    val days: Set<DayOfWeek>,
    val start: LocalTime,
    val endExclusive: LocalTime
) {
    fun contains(day: DayOfWeek, time: LocalTime): Boolean {
        if (day !in days) return false
        return if (start <= endExclusive) {
            time >= start && time < endExclusive
        } else {
            time >= start || time < endExclusive
        }
    }

    fun label(): String {
        val dayLabel = if (days == WEEKDAYS) "weekdays" else "configured days"
        return "$dayLabel ${formatTime(start)}-${formatTime(endExclusive)}"
    }

    companion object {
        val WEEKDAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY
        )

        fun weekdays(start: LocalTime, endExclusive: LocalTime): TimeWindow {
            return TimeWindow(WEEKDAYS, start, endExclusive)
        }

        private fun formatTime(time: LocalTime): String {
            return "%02d:%02d".format(time.hour, time.minute)
        }
    }
}

data class AppPolicy(
    val packageName: String,
    val tier: AppTier,
    val hiddenByDefault: Boolean,
    val suspendedByDefault: Boolean,
    val allowedWindows: List<TimeWindow>,
    val challengeRequired: Boolean,
    val reasonRequired: Boolean,
    val defaultTimeboxMinutes: Int?,
    val dailyLimitMinutes: Int?
)

