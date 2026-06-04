package com.otto.launcher.trace.domain

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TraceSleepPolicyTest {
    private val zoneId = ZoneId.of("Europe/London")

    @Test
    fun normalizedEndKeepsValidEndAndRepairsInvalidEnd() {
        val start = Instant.parse("2026-06-03T22:30:00Z")
        val validEnd = Instant.parse("2026-06-04T06:45:00Z")

        assertEquals(validEnd, TraceSleepPolicy.normalizedEnd(start, validEnd))
        assertEquals(start.plusSeconds(60), TraceSleepPolicy.normalizedEnd(start, start))
        assertEquals(start.plusSeconds(60), TraceSleepPolicy.normalizedEnd(start, start.minusSeconds(3600)))
    }

    @Test
    fun endDateBoundsUseTheLocalSleepEndDate() {
        val endAt = Instant.parse("2026-06-04T05:45:00Z")
        val (start, end) = TraceSleepPolicy.endDateBounds(endAt, zoneId)

        assertEquals(Instant.parse("2026-06-03T23:00:00Z"), start)
        assertEquals(Instant.parse("2026-06-04T23:00:00Z"), end)
    }

    @Test
    fun durationMinutesIsAtLeastOne() {
        val start = Instant.parse("2026-06-04T06:00:00Z")

        assertEquals(450, TraceSleepPolicy.durationMinutes(start, start.plusSeconds(450 * 60)))
        assertEquals(1, TraceSleepPolicy.durationMinutes(start, start))
    }
}
