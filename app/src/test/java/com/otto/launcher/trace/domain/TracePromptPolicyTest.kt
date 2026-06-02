package com.otto.launcher.trace.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TracePromptPolicyTest {
    private val zoneId = ZoneId.of("Europe/London")
    private val date = LocalDate.of(2026, 6, 2)
    private val enabledSettings = TraceSettingsState(
        foodEnabled = true,
        drinkEnabled = true,
        weightEnabled = true,
        sleepEnabled = true
    )

    @Test
    fun nextActionOnlyPromptsForMissingMorningSleep() {
        data class Case(
            val name: String,
            val time: LocalTime,
            val today: DailySummary,
            val settings: TraceSettingsState,
            val sleepEstimate: SleepEstimate?,
            val expectedKind: NextTraceActionKind
        )

        val cases = listOf(
            Case(
                name = "morning missing sleep with estimate",
                time = LocalTime.of(7, 0),
                today = summary(),
                settings = enabledSettings,
                sleepEstimate = sleepEstimate(),
                expectedKind = NextTraceActionKind.CONFIRM_SLEEP
            ),
            Case(
                name = "morning missing sleep without estimate",
                time = LocalTime.of(7, 0),
                today = summary(),
                settings = enabledSettings,
                sleepEstimate = null,
                expectedKind = NextTraceActionKind.LOG_SLEEP
            ),
            Case(
                name = "morning missing weight does not prompt",
                time = LocalTime.of(7, 0),
                today = summary(sleepDurationMinutes = 450, weightKg = null),
                settings = enabledSettings,
                sleepEstimate = null,
                expectedKind = NextTraceActionKind.OPEN_CAPTURE
            ),
            Case(
                name = "meal window without food still opens capture",
                time = LocalTime.of(12, 15),
                today = summary(),
                settings = enabledSettings,
                sleepEstimate = null,
                expectedKind = NextTraceActionKind.OPEN_CAPTURE
            ),
            Case(
                name = "outside meal windows opens capture",
                time = LocalTime.of(16, 0),
                today = summary(),
                settings = enabledSettings,
                sleepEstimate = null,
                expectedKind = NextTraceActionKind.OPEN_CAPTURE
            ),
            Case(
                name = "existing trace data keeps capture default",
                time = LocalTime.of(18, 30),
                today = summary(
                    foodPhotoCount = 3,
                    drinkPhotoCount = 1,
                    eatingWindowMinutes = 620,
                    weightKg = 81.8,
                    sleepDurationMinutes = 450
                ),
                settings = enabledSettings,
                sleepEstimate = null,
                expectedKind = NextTraceActionKind.OPEN_CAPTURE
            ),
            Case(
                name = "disabled sleep keeps capture default",
                time = LocalTime.of(7, 0),
                today = summary(),
                settings = enabledSettings.copy(sleepEnabled = false),
                sleepEstimate = sleepEstimate(),
                expectedKind = NextTraceActionKind.OPEN_CAPTURE
            )
        )

        cases.forEach { case ->
            val action = TracePromptPolicy.nextAction(
                now = instantAt(case.time),
                zoneId = zoneId,
                today = case.today,
                settings = case.settings,
                sleepEstimate = case.sleepEstimate
            )

            assertEquals(case.name, case.expectedKind, action.kind)
        }
    }

    @Test
    fun sleepEstimateActionIncludesReadableDetail() {
        val action = TracePromptPolicy.nextAction(
            now = instantAt(LocalTime.of(7, 0)),
            zoneId = zoneId,
            today = summary(),
            settings = enabledSettings,
            sleepEstimate = sleepEstimate()
        )

        assertEquals(NextTraceActionKind.CONFIRM_SLEEP, action.kind)
        assertEquals("Sleep estimate", action.title)
        assertEquals("23:10-06:40 · 7h30", action.detail)
        assertEquals("Save", action.primaryLabel)
    }

    private fun summary(
        foodPhotoCount: Int = 0,
        drinkPhotoCount: Int = 0,
        eatingWindowMinutes: Int? = null,
        weightKg: Double? = null,
        sleepDurationMinutes: Int? = null
    ): DailySummary {
        return DailySummary(
            date = date,
            foodPhotoCount = foodPhotoCount,
            drinkPhotoCount = drinkPhotoCount,
            firstFoodAt = null,
            lastFoodAt = null,
            eatingWindowMinutes = eatingWindowMinutes,
            weightKg = weightKg,
            weightTrendKg = null,
            sleepDurationMinutes = sleepDurationMinutes,
            sleepStartAt = null,
            sleepEndAt = null,
            dataCoverage = DataCoverage(0, 0, 0, 7)
        )
    }

    private fun sleepEstimate(): SleepEstimate {
        return SleepEstimate(
            startAt = Instant.parse("2026-06-01T22:10:00Z"),
            endAt = Instant.parse("2026-06-02T05:40:00Z")
        )
    }

    private fun instantAt(time: LocalTime): Instant {
        return LocalDateTime.of(date, time).atZone(zoneId).toInstant()
    }
}
