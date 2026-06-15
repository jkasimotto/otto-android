package com.otto.launcher.domain.time

import com.otto.launcher.domain.policy.AppTier
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TimeLedgerCalculatorTest {
    private val date = LocalDate.of(2026, 6, 15)
    private val dayStart = Instant.parse("2026-06-15T00:00:00Z")
    private val now = Instant.parse("2026-06-15T10:00:00Z")
    private val categories = DefaultTimeCategories.categories

    @Test
    fun buildsTodayRowsFromBlocksUsageAndBudgets() {
        val ledger = TimeLedgerCalculator.buildToday(
            date = date,
            now = now,
            dayStart = dayStart,
            hasUsageAccess = true,
            categories = categories,
            budgets = listOf(
                dailyBudget(TimeCategoryIds.SLEEP, target = 480),
                dailyBudget(TimeCategoryIds.RELATIONSHIPS, floor = 60),
                dailyBudget(TimeCategoryIds.MOVEMENT, target = 30),
                dailyBudget(TimeCategoryIds.DIGITAL_DRIFT, cap = 30)
            ),
            blocks = listOf(
                block(TimeCategoryIds.MOVEMENT, "2026-06-15T09:00:00Z", "2026-06-15T09:35:00Z"),
                block(TimeCategoryIds.RELATIONSHIPS, "2026-06-15T09:35:00Z", null)
            ),
            usage = listOf(AppUsageSlice("com.reddit.frontpage", 18)),
            mappings = listOf(
                AppTimeMapping(
                    packageName = "com.reddit.frontpage",
                    defaultCategoryId = TimeCategoryIds.DIGITAL_DRIFT,
                    appTier = AppTier.DISTRACTION,
                    countAsDigitalDrift = true,
                    requiresIntent = true
                )
            ),
            sleepMinutes = 462,
            timeAffluence = null
        )

        assertEquals("7h 42m / 8h target", row(ledger, TimeCategoryIds.SLEEP).value)
        assertEquals("25m / 1h floor", row(ledger, TimeCategoryIds.RELATIONSHIPS).value)
        assertEquals("35m / 30m target", row(ledger, TimeCategoryIds.MOVEMENT).value)
        assertEquals("18m / 30m cap", row(ledger, TimeCategoryIds.DIGITAL_DRIFT).value)
        assertEquals(18, ledger.digitalDriftMinutes)
        assertEquals(30, ledger.digitalDriftCapMinutes)
        assertNotNull(ledger.activeBlock)
    }

    @Test
    fun reviewGapsIgnoreShortGaps() {
        val gaps = TimeLedgerCalculator.buildReviewGaps(
            dayStart = dayStart,
            dayEnd = Instant.parse("2026-06-15T12:00:00Z"),
            blocks = listOf(
                block(TimeCategoryIds.SLEEP, "2026-06-15T00:00:00Z", "2026-06-15T08:00:00Z"),
                block(TimeCategoryIds.MOVEMENT, "2026-06-15T08:20:00Z", "2026-06-15T09:00:00Z"),
                block(TimeCategoryIds.FOCUSED_WORK, "2026-06-15T10:00:00Z", "2026-06-15T11:00:00Z")
            )
        )

        assertEquals(2, gaps.size)
        assertEquals(60, gaps.first().minutes)
        assertEquals(60, gaps.last().minutes)
    }

    private fun row(ledger: TodayTimeLedger, categoryId: String): TimeLedgerRow {
        return ledger.rows.first { it.categoryId == categoryId }
    }

    private fun dailyBudget(
        categoryId: String,
        floor: Int? = null,
        target: Int? = null,
        cap: Int? = null
    ): TimeBudget {
        return TimeBudget(
            id = "daily_$categoryId",
            categoryId = categoryId,
            period = BudgetPeriod.DAILY,
            floorMinutes = floor,
            targetMinutes = target,
            capMinutes = cap,
            activeFrom = date,
            activeTo = null
        )
    }

    private fun block(categoryId: String, start: String, end: String?): TimeBlock {
        return TimeBlock(
            id = "$categoryId-$start",
            categoryId = categoryId,
            startedAt = Instant.parse(start),
            endedAt = end?.let(Instant::parse),
            source = TimeBlockSource.COMMAND,
            confidence = 1f,
            label = null,
            linkedPackageName = null,
            linkedCalendarEventId = null
        )
    }
}
