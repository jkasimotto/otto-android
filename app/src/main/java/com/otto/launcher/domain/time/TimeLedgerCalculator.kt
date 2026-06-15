package com.otto.launcher.domain.time

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

object TimeLedgerCalculator {
    fun buildToday(
        date: LocalDate,
        now: Instant,
        dayStart: Instant,
        hasUsageAccess: Boolean,
        categories: List<TimeCategory>,
        budgets: List<TimeBudget>,
        blocks: List<TimeBlock>,
        usage: List<AppUsageSlice>,
        mappings: List<AppTimeMapping>,
        sleepMinutes: Int,
        timeAffluence: Int?
    ): TodayTimeLedger {
        val totals = aggregateTotals(
            now = now,
            categories = categories,
            blocks = blocks,
            usage = usage,
            mappings = mappings,
            extraTotals = if (sleepMinutes > 0) mapOf(TimeCategoryIds.SLEEP to sleepMinutes) else emptyMap()
        )
        val rows = homeRows(categories, budgets.filter { it.period == BudgetPeriod.DAILY }, totals)
        val active = blocks
            .filter { it.endedAt == null }
            .maxByOrNull { it.startedAt }
            ?.let { block ->
                categories.firstOrNull { it.id == block.categoryId }?.let { category ->
                    ActiveTimeBlock(block, category, block.durationMinutes(now))
                }
            }
        val elapsedToday = Duration.between(dayStart, now).toMinutes().toInt().coerceIn(0, 24 * 60)
        val knownMinutes = totals.sumOf { it.minutes }.coerceAtLeast(0)
        val drift = totals.firstOrNull { it.categoryId == TimeCategoryIds.DIGITAL_DRIFT }?.minutes ?: 0
        val driftBudget = budgets
            .filter { it.period == BudgetPeriod.DAILY && it.categoryId == TimeCategoryIds.DIGITAL_DRIFT }
            .mapNotNull { it.capMinutes }
            .firstOrNull()
        return TodayTimeLedger(
            date = date,
            hasUsageAccess = hasUsageAccess,
            activeBlock = active,
            rows = rows,
            totals = totals,
            unknownMinutes = (elapsedToday - knownMinutes).coerceAtLeast(0),
            digitalDriftMinutes = drift,
            digitalDriftCapMinutes = driftBudget,
            dayPlanMode = if (sleepMinutes in 1 until LOW_SLEEP_THRESHOLD_MINUTES) {
                DayPlanMode.LOW_SLEEP
            } else {
                DayPlanMode.NORMAL
            },
            timeAffluence = timeAffluence
        )
    }

    fun buildWeek(
        startDate: LocalDate,
        endDate: LocalDate,
        now: Instant,
        categories: List<TimeCategory>,
        budgets: List<TimeBudget>,
        blocks: List<TimeBlock>,
        usage: List<AppUsageSlice>,
        mappings: List<AppTimeMapping>,
        sleepMinutes: Int
    ): WeeklyTimeLedger {
        val weeklyBudgets = budgets.filter { it.period == BudgetPeriod.WEEKLY }
        val totals = aggregateTotals(
            now = now,
            categories = categories,
            blocks = blocks,
            usage = usage,
            mappings = mappings,
            extraTotals = if (sleepMinutes > 0) mapOf(TimeCategoryIds.SLEEP to sleepMinutes) else emptyMap()
        )
        val rows = categories
            .filter { it.id in WEEKLY_ROW_CATEGORY_IDS }
            .map { category ->
                rowFor(
                    category = category,
                    minutes = totals.firstOrNull { it.categoryId == category.id }?.minutes ?: 0,
                    budget = budgetFor(category.id, weeklyBudgets)
                )
            }
        val drift = totals.firstOrNull { it.categoryId == TimeCategoryIds.DIGITAL_DRIFT }?.minutes ?: 0
        val movement = totals.firstOrNull { it.categoryId == TimeCategoryIds.MOVEMENT }?.minutes ?: 0
        val suggestion = if (drift >= 30 && movement < (budgetFor(TimeCategoryIds.MOVEMENT, weeklyBudgets)?.targetMinutes ?: Int.MAX_VALUE)) {
            "Replace 30m of digital drift with movement on Tue/Thu."
        } else {
            null
        }
        return WeeklyTimeLedger(
            startDate = startDate,
            endDate = endDate,
            rows = rows,
            experimentSuggestion = suggestion
        )
    }

    fun buildReviewGaps(
        dayStart: Instant,
        dayEnd: Instant,
        blocks: List<TimeBlock>,
        minimumGapMinutes: Int = 30
    ): List<TimeLedgerReviewGap> {
        val occupied = blocks
            .mapNotNull { block ->
                val end = block.endedAt ?: dayEnd
                val start = maxOf(block.startedAt, dayStart)
                val clippedEnd = minOf(end, dayEnd)
                if (clippedEnd.isAfter(start)) start to clippedEnd else null
            }
            .sortedBy { it.first }
        val gaps = mutableListOf<TimeLedgerReviewGap>()
        var cursor = dayStart
        occupied.forEach { (start, end) ->
            if (start.isAfter(cursor)) {
                addGap(gaps, cursor, start, minimumGapMinutes)
            }
            if (end.isAfter(cursor)) cursor = end
        }
        if (dayEnd.isAfter(cursor)) addGap(gaps, cursor, dayEnd, minimumGapMinutes)
        return gaps
    }

    private fun aggregateTotals(
        now: Instant,
        categories: List<TimeCategory>,
        blocks: List<TimeBlock>,
        usage: List<AppUsageSlice>,
        mappings: List<AppTimeMapping>,
        extraTotals: Map<String, Int>
    ): List<TimeLedgerTotal> {
        val validIds = categories.map { it.id }.toSet()
        val totals = mutableMapOf<String, Int>()
        extraTotals.forEach { (categoryId, minutes) ->
            if (categoryId in validIds && minutes > 0) totals[categoryId] = totals.getOrDefault(categoryId, 0) + minutes
        }
        blocks.forEach { block ->
            val minutes = block.durationMinutes(now)
            if (block.categoryId in validIds && minutes > 0) {
                totals[block.categoryId] = totals.getOrDefault(block.categoryId, 0) + minutes
            }
        }
        val mappingByPackage = mappings.associateBy { it.packageName.lowercase() }
        usage.forEach { slice ->
            val categoryId = mappingByPackage[slice.packageName.lowercase()]?.defaultCategoryId ?: TimeCategoryIds.UNKNOWN
            if (categoryId in validIds && slice.minutes > 0) {
                totals[categoryId] = totals.getOrDefault(categoryId, 0) + slice.minutes
            }
        }
        return totals.map { (categoryId, minutes) -> TimeLedgerTotal(categoryId, minutes) }
    }

    private fun homeRows(
        categories: List<TimeCategory>,
        dailyBudgets: List<TimeBudget>,
        totals: List<TimeLedgerTotal>
    ): List<TimeLedgerRow> {
        return HOME_ROW_CATEGORY_IDS.mapNotNull { categoryId ->
            val category = categories.firstOrNull { it.id == categoryId } ?: return@mapNotNull null
            rowFor(
                category = category,
                minutes = totals.firstOrNull { it.categoryId == categoryId }?.minutes ?: 0,
                budget = budgetFor(categoryId, dailyBudgets)
            )
        }
    }

    private fun rowFor(
        category: TimeCategory,
        minutes: Int,
        budget: TimeBudget?
    ): TimeLedgerRow {
        val (budgetMinutes, budgetKind) = when {
            budget?.capMinutes != null -> budget.capMinutes to BudgetKind.CAP
            budget?.floorMinutes != null -> budget.floorMinutes to BudgetKind.FLOOR
            budget?.targetMinutes != null -> budget.targetMinutes to BudgetKind.TARGET
            else -> null to null
        }
        val value = when {
            budgetMinutes != null && budgetKind == BudgetKind.CAP -> "${formatDuration(minutes)} / ${formatDuration(budgetMinutes)} cap"
            budgetMinutes != null && budgetKind == BudgetKind.FLOOR -> "${formatDuration(minutes)} / ${formatDuration(budgetMinutes)} floor"
            budgetMinutes != null && budgetKind == BudgetKind.TARGET -> "${formatDuration(minutes)} / ${formatDuration(budgetMinutes)} target"
            minutes <= 0 -> "not recorded"
            else -> formatDuration(minutes)
        }
        return TimeLedgerRow(
            categoryId = category.id,
            label = category.displayLabel(),
            value = value,
            minutes = minutes,
            budgetMinutes = budgetMinutes,
            budgetKind = budgetKind
        )
    }

    private fun budgetFor(categoryId: String, budgets: List<TimeBudget>): TimeBudget? {
        return budgets.firstOrNull { it.categoryId == categoryId }
    }

    fun formatDuration(minutes: Int): String {
        val safe = minutes.coerceAtLeast(0)
        if (safe < 60) return "${safe}m"
        val hours = safe / 60
        val remaining = safe % 60
        return if (remaining == 0) "${hours}h" else "${hours}h ${remaining}m"
    }

    private fun addGap(
        gaps: MutableList<TimeLedgerReviewGap>,
        start: Instant,
        end: Instant,
        minimumGapMinutes: Int
    ) {
        val minutes = Duration.between(start, end).toMinutes().toInt().coerceAtLeast(0)
        if (minutes >= minimumGapMinutes) {
            gaps += TimeLedgerReviewGap(start, end, minutes)
        }
    }

    private val HOME_ROW_CATEGORY_IDS = listOf(
        TimeCategoryIds.SLEEP,
        TimeCategoryIds.RELATIONSHIPS,
        TimeCategoryIds.MOVEMENT,
        TimeCategoryIds.DIGITAL_DRIFT
    )

    private val WEEKLY_ROW_CATEGORY_IDS = listOf(
        TimeCategoryIds.SLEEP,
        TimeCategoryIds.RELATIONSHIPS,
        TimeCategoryIds.MOVEMENT,
        TimeCategoryIds.FOCUSED_WORK,
        TimeCategoryIds.ADMIN,
        TimeCategoryIds.CHORES,
        TimeCategoryIds.COMMUTE,
        TimeCategoryIds.DIGITAL_DRIFT,
        TimeCategoryIds.BUFFER
    )

    private const val LOW_SLEEP_THRESHOLD_MINUTES = 6 * 60
}
