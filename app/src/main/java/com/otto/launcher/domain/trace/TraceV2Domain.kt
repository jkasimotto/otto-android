package com.otto.launcher.domain.trace

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The calendar day a sleep session belongs to in the weekly chart: the evening the sleep began.
 * A session that starts after midnight but before midday (e.g. a 02:00 bedtime) is the continuation
 * of the previous evening's night, so it maps to the day before its start date. Without this pivot an
 * after-midnight bedtime lands on the following day's row, which is the bug behind editing a day's
 * sleep to an after-midnight time and watching it jump to the next day.
 */
fun sleepNightDate(startAt: Instant, zoneId: ZoneId): LocalDate {
    val local = startAt.atZone(zoneId)
    return if (local.toLocalTime() < SLEEP_NIGHT_PIVOT) {
        local.toLocalDate().minusDays(1)
    } else {
        local.toLocalDate()
    }
}

/** Bedtimes from midday onward count as that day's night; earlier starts belong to the prior night. */
private val SLEEP_NIGHT_PIVOT: LocalTime = LocalTime.NOON

enum class TraceCaptureType {
    FOOD,
    DRINK,
    WEIGHT,
    SLEEP,
    NOTE,
    TASK
}

enum class InboxKind {
    NOTE,
    TASK
}

enum class InboxState {
    OPEN,
    DONE,
    ARCHIVED
}

/**
 * Lifecycle of a captured voice memo. Memos are recorded as raw audio and held in the queue
 * (QUEUED) until a later "processing" phase transcribes and folds them into a daily log
 * (PROCESSED), or the user drops one (DISCARDED). Capture and storage only set QUEUED today;
 * the other states exist so processing can be added without a schema migration.
 */
enum class MemoState {
    QUEUED,
    PROCESSED,
    DISCARDED
}

data class TodayLedgerState(
    val date: LocalDate,
    val sleepText: String,
    val weightText: String,
    val foodText: String,
    val phoneText: String,
    val activeSleepStartAt: Instant?,
    val unresolvedFoodCount: Int
)

data class WeeklySleepDay(
    val date: LocalDate,
    val sessionId: String?,
    val startAt: Instant?,
    val endAt: Instant?
)

