package com.otto.launcher.domain.trace

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class SleepNightDateTest {
    private val zoneId = ZoneId.of("UTC")

    @Test
    fun eveningBedtimeBelongsToThatDay() {
        val startAt = Instant.parse("2026-06-20T23:00:00Z")
        assertEquals(LocalDate.of(2026, 6, 20), sleepNightDate(startAt, zoneId))
    }

    @Test
    fun afterMidnightBedtimeBelongsToThePreviousDay() {
        val startAt = Instant.parse("2026-06-21T02:00:00Z")
        assertEquals(LocalDate.of(2026, 6, 20), sleepNightDate(startAt, zoneId))
    }

    @Test
    fun lateMorningStartStillBelongsToThePreviousDay() {
        val startAt = Instant.parse("2026-06-21T11:59:00Z")
        assertEquals(LocalDate.of(2026, 6, 20), sleepNightDate(startAt, zoneId))
    }

    @Test
    fun middayPivotStartsTheNewNight() {
        val startAt = Instant.parse("2026-06-21T12:00:00Z")
        assertEquals(LocalDate.of(2026, 6, 21), sleepNightDate(startAt, zoneId))
    }
}
