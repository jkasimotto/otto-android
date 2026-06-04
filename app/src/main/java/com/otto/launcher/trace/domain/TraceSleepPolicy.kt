package com.otto.launcher.trace.domain

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

object TraceSleepPolicy {
    fun normalizedEnd(startAt: Instant, endAt: Instant): Instant {
        return if (endAt > startAt) endAt else startAt.plus(Duration.ofMinutes(1))
    }

    fun endDateBounds(endAt: Instant, zoneId: ZoneId): Pair<Instant, Instant> {
        val date = endAt.atZone(zoneId).toLocalDate()
        return TraceRules.dayBounds(date, zoneId)
    }

    fun durationMinutes(startAt: Instant, endAt: Instant): Int {
        return Duration.between(startAt, endAt).toMinutes().toInt().coerceAtLeast(1)
    }
}
