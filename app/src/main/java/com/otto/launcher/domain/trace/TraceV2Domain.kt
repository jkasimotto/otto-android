package com.otto.launcher.domain.trace

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

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

