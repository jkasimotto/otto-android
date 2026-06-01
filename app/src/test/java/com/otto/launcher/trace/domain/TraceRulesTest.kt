package com.otto.launcher.trace.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TraceRulesTest {
    @Test
    fun inferMealSlotUsesStableMealWindows() {
        val cases = listOf(
            LocalTime.of(8, 15) to MealSlot.BREAKFAST,
            LocalTime.of(12, 45) to MealSlot.LUNCH,
            LocalTime.of(19, 30) to MealSlot.DINNER,
            LocalTime.of(16, 0) to MealSlot.SNACK
        )

        cases.forEach { (time, expected) ->
            assertEquals(expected, TraceRules.inferMealSlot(time))
        }
    }

    @Test
    fun currentMealWindowOnlyReturnsPromptableWindows() {
        assertEquals(MealSlot.BREAKFAST, TraceRules.currentMealWindow(LocalTime.of(7, 0)))
        assertEquals(MealSlot.LUNCH, TraceRules.currentMealWindow(LocalTime.of(14, 30)))
        assertEquals(MealSlot.DINNER, TraceRules.currentMealWindow(LocalTime.of(21, 30)))
        assertNull(TraceRules.currentMealWindow(LocalTime.of(22, 0)))
    }

    @Test
    fun eatingWindowRequiresTwoDifferentTimes() {
        val first = Instant.parse("2026-06-01T09:12:00Z")
        val last = Instant.parse("2026-06-01T20:08:00Z")

        assertEquals(656, TraceRules.eatingWindowMinutes(first, last))
        assertNull(TraceRules.eatingWindowMinutes(first, first))
        assertNull(TraceRules.eatingWindowMinutes(null, last))
    }

    @Test
    fun sleepEstimateRequiresPlausibleOvernightGap() {
        val zoneId = ZoneId.of("Europe/London")
        val previous = Instant.parse("2026-06-01T22:45:00Z")
        val now = Instant.parse("2026-06-02T06:30:00Z")

        val estimate = TraceRules.candidateSleepEstimate(previous, now, zoneId)

        assertEquals(previous, estimate?.startAt)
        assertEquals(now, estimate?.endAt)
        assertNull(
            TraceRules.candidateSleepEstimate(
                Instant.parse("2026-06-02T12:00:00Z"),
                Instant.parse("2026-06-02T16:00:00Z"),
                zoneId
            )
        )
    }

    @Test
    fun mediansAndWeightTrendAreDeterministic() {
        assertEquals(20, TraceRules.median(listOf(10, 30)))
        assertEquals(30, TraceRules.median(listOf(10, 30, 50)))

        val weights = listOf(
            Instant.parse("2026-06-01T07:00:00Z") to 82.0,
            Instant.parse("2026-06-02T07:00:00Z") to 81.8,
            Instant.parse("2026-06-03T07:00:00Z") to 81.4,
            Instant.parse("2026-06-04T07:00:00Z") to 81.2
        )

        assertEquals(-0.6, TraceRules.weightTrendDeltaKg(weights)!!, 0.0001)
        assertNull(TraceRules.weightTrendDeltaKg(weights.take(1)))
    }
}
