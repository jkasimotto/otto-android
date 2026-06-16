package com.otto.launcher.data.time

import android.content.Context
import com.otto.launcher.data.policy.isCriticalPeoplePackage
import com.otto.launcher.domain.policy.AppDescriptor
import com.otto.launcher.domain.policy.AppPolicyEngine
import com.otto.launcher.domain.policy.AppTier
import com.otto.launcher.domain.time.AppTimeMapping
import com.otto.launcher.domain.time.BudgetPeriod
import com.otto.launcher.domain.time.DailyTimeReview
import com.otto.launcher.domain.time.DefaultTimeCategories
import com.otto.launcher.domain.time.TimeBlock
import com.otto.launcher.domain.time.TimeBlockSource
import com.otto.launcher.domain.time.TimeBudget
import com.otto.launcher.domain.time.TimeCategory
import com.otto.launcher.domain.time.TimeCategoryIds
import com.otto.launcher.domain.time.TimeLedgerCalculator
import com.otto.launcher.domain.time.TimeMode
import com.otto.launcher.domain.time.TodayTimeLedger
import com.otto.launcher.domain.time.UsageTimeSnapshot
import com.otto.launcher.domain.time.WeeklyTimeLedger
import com.otto.launcher.domain.time.categoryId
import com.otto.launcher.trace.data.AppTimeMappingEntity
import com.otto.launcher.trace.data.TimeBlockEntity
import com.otto.launcher.trace.data.TimeBudgetEntity
import com.otto.launcher.trace.data.TimeCategoryEntity
import com.otto.launcher.trace.data.TraceDatabase
import com.otto.launcher.trace.data.WellbeingPulseEntity
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

class TimeLedgerRepository(
    context: Context,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    private val dao = TraceDatabase.get(context.applicationContext).traceV2Dao()
    private val zoneId: ZoneId = ZoneId.systemDefault()

    fun observeTodayLedger(usage: Flow<UsageTimeSnapshot>): Flow<TodayTimeLedger> {
        val date = LocalDate.now(clock)
        val (start, end) = dayBounds(date)
        val base = combine(
            dao.observeTimeCategories(),
            dao.observeActiveTimeBudgets(date),
            dao.observeTimeBlocksOverlapping(start, end),
            dao.observeSleepSessionsOverlapping(start.minus(Duration.ofHours(12)), end),
            dao.observeAppTimeMappings()
        ) { categories, budgets, blocks, sleep, mappings ->
            TodayLedgerBase(categories, budgets, blocks, sleep, mappings)
        }
        return combine(
            base,
            dao.observeWellbeingPulses(date),
            usage
        ) { baseState, pulses, usageSnapshot ->
            val domainCategories = baseState.categories.map { it.toDomain() }
            TimeLedgerCalculator.buildToday(
                date = date,
                now = clock.instant(),
                dayStart = start,
                hasUsageAccess = usageSnapshot.hasUsageAccess,
                categories = domainCategories,
                budgets = baseState.budgets.map { it.toDomain() },
                blocks = baseState.blocks.map { it.toDomain() },
                usage = usageSnapshot.slices,
                mappings = baseState.mappings.map { it.toDomain() },
                sleepMinutes = baseState.sleep.sumOf { session ->
                    val endAt = session.endAt ?: clock.instant()
                    Duration.between(maxOf(session.startAt, start), minOf(endAt, end)).toMinutes().toInt().coerceAtLeast(0)
                },
                timeAffluence = pulses.firstOrNull { it.timeAffluence != null }?.timeAffluence
            )
        }
    }

    fun observeWeeklyLedger(usage: Flow<UsageTimeSnapshot>): Flow<WeeklyTimeLedger> {
        val startDate = currentWeekStart()
        val endDate = startDate.plusDays(7)
        val start = startDate.atStartOfDay(zoneId).toInstant()
        val end = endDate.atStartOfDay(zoneId).toInstant()
        val today = LocalDate.now(clock)
        val base = combine(
            dao.observeTimeCategories(),
            dao.observeActiveTimeBudgets(today),
            dao.observeTimeBlocksOverlapping(start, end),
            dao.observeSleepSessionsOverlapping(start.minus(Duration.ofHours(12)), end),
            dao.observeAppTimeMappings()
        ) { categories, budgets, blocks, sleep, mappings ->
            TodayLedgerBase(categories, budgets, blocks, sleep, mappings)
        }
        return combine(
            base,
            usage
        ) { baseState, usageSnapshot ->
            TimeLedgerCalculator.buildWeek(
                startDate = startDate,
                endDate = endDate.minusDays(1),
                now = clock.instant(),
                categories = baseState.categories.map { it.toDomain() },
                budgets = baseState.budgets.map { it.toDomain() },
                blocks = baseState.blocks.map { it.toDomain() },
                usage = usageSnapshot.slices,
                mappings = baseState.mappings.map { it.toDomain() },
                sleepMinutes = baseState.sleep.sumOf { session ->
                    val endAt = session.endAt ?: clock.instant()
                    Duration.between(maxOf(session.startAt, start), minOf(endAt, end)).toMinutes().toInt().coerceAtLeast(0)
                }
            )
        }
    }

    fun observeDailyReview(usage: Flow<UsageTimeSnapshot>): Flow<DailyTimeReview> {
        val date = LocalDate.now(clock)
        val (start, end) = dayBounds(date)
        return combine(
            observeTodayLedger(usage),
            dao.observeTimeBlocksOverlapping(start, end)
        ) { ledger, blocks ->
            DailyTimeReview(
                date = date,
                rows = ledger.rows,
                unknownGaps = TimeLedgerCalculator.buildReviewGaps(
                    dayStart = start,
                    dayEnd = minOf(clock.instant(), end),
                    blocks = blocks.map { it.toDomain() }
                ),
                timeAffluence = ledger.timeAffluence
            )
        }
    }

    suspend fun seedDefaults(apps: List<AppDescriptor>) = withContext(Dispatchers.IO) {
        val now = clock.instant()
        if (dao.timeCategories().isEmpty()) {
            dao.upsertTimeCategories(DefaultTimeCategories.categories.map { it.toEntity(now) })
        }
        if (dao.activeTimeBudgets(LocalDate.now(clock)).isEmpty()) {
            dao.upsertTimeBudgets(defaultBudgets(LocalDate.now(clock)))
        }
        val existingMappings = dao.appTimeMappings()
        val existingMappingKeys = existingMappings.map { AppPolicyEngine.packageKey(it.packageName) }.toSet()
        val newMappings = apps
            .filter { AppPolicyEngine.packageKey(it.packageName) !in existingMappingKeys }
            .map { app ->
                val policy = AppPolicyEngine().policyFor(app)
                val categoryId = defaultCategoryId(policy.tier, app)
                AppTimeMappingEntity(
                    packageName = app.packageName,
                    defaultCategoryId = categoryId,
                    appTier = policy.tier,
                    countAsDigitalDrift = categoryId == TimeCategoryIds.DIGITAL_DRIFT,
                    requiresIntent = policy.tier in setOf(AppTier.DISTRACTION, AppTier.BLOCKED),
                    updatedAt = now
                )
            }
        val repairedMappings = existingMappings.mapNotNull { it.repairCriticalPeopleMapping(now) }
        val mappingsToUpsert = newMappings + repairedMappings
        if (mappingsToUpsert.isNotEmpty()) {
            dao.upsertAppTimeMappings(mappingsToUpsert)
        }
    }

    suspend fun startBlock(mode: TimeMode, label: String? = null) = withContext(Dispatchers.IO) {
        val categoryId = mode.categoryId() ?: return@withContext
        startCategoryBlock(categoryId, label ?: labelForCategory(categoryId), TimeBlockSource.COMMAND)
    }

    suspend fun startCategoryBlock(
        categoryId: String,
        label: String? = null,
        source: TimeBlockSource = TimeBlockSource.COMMAND
    ) = withContext(Dispatchers.IO) {
        val now = clock.instant()
        dao.openTimeBlock()?.let { dao.closeTimeBlock(it.id, now, now) }
        dao.upsertTimeBlock(
            TimeBlockEntity(
                id = UUID.randomUUID().toString(),
                categoryId = categoryId,
                startedAt = now,
                endedAt = null,
                source = source,
                confidence = 1f,
                label = label,
                linkedPackageName = null,
                linkedCalendarEventId = null,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun finishActiveBlock(): Int? = withContext(Dispatchers.IO) {
        val open = dao.openTimeBlock() ?: return@withContext null
        val now = clock.instant()
        dao.closeTimeBlock(open.id, now, now)
        Duration.between(open.startedAt, now).toMinutes().toInt().coerceAtLeast(0)
    }

    suspend fun recordDuration(
        categoryId: String,
        minutes: Int,
        label: String? = null
    ) = withContext(Dispatchers.IO) {
        if (minutes <= 0) return@withContext
        val now = clock.instant()
        val startedAt = now.minus(Duration.ofMinutes(minutes.toLong()))
        dao.upsertTimeBlock(
            TimeBlockEntity(
                id = UUID.randomUUID().toString(),
                categoryId = categoryId,
                startedAt = startedAt,
                endedAt = now,
                source = TimeBlockSource.COMMAND,
                confidence = 1f,
                label = label ?: labelForCategory(categoryId),
                linkedPackageName = null,
                linkedCalendarEventId = null,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun recordTimeAffluence(score: Int) = withContext(Dispatchers.IO) {
        val now = clock.instant()
        dao.upsertWellbeingPulse(
            WellbeingPulseEntity(
                id = UUID.randomUUID().toString(),
                timeBlockId = null,
                date = LocalDate.now(clock),
                timeAffluence = score.coerceIn(0, 10),
                enjoyment = null,
                meaning = null,
                energy = null,
                connection = null,
                flow = null,
                stress = null,
                note = null,
                createdAt = now
            )
        )
    }

    fun currentWeekStart(): LocalDate {
        return LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    private fun dayBounds(date: LocalDate): Pair<Instant, Instant> {
        val start = date.atStartOfDay(zoneId).toInstant()
        return start to date.plusDays(1).atStartOfDay(zoneId).toInstant()
    }

    private fun defaultBudgets(activeFrom: LocalDate): List<TimeBudgetEntity> {
        return listOf(
            budget("daily_sleep", TimeCategoryIds.SLEEP, BudgetPeriod.DAILY, target = 8 * 60, activeFrom = activeFrom),
            budget("daily_people", TimeCategoryIds.RELATIONSHIPS, BudgetPeriod.DAILY, floor = 60, activeFrom = activeFrom),
            budget("daily_move", TimeCategoryIds.MOVEMENT, BudgetPeriod.DAILY, target = 30, activeFrom = activeFrom),
            budget("daily_drift", TimeCategoryIds.DIGITAL_DRIFT, BudgetPeriod.DAILY, cap = 30, activeFrom = activeFrom),
            budget("weekly_sleep", TimeCategoryIds.SLEEP, BudgetPeriod.WEEKLY, floor = 56 * 60, activeFrom = activeFrom),
            budget("weekly_people", TimeCategoryIds.RELATIONSHIPS, BudgetPeriod.WEEKLY, floor = 5 * 60, activeFrom = activeFrom),
            budget("weekly_move", TimeCategoryIds.MOVEMENT, BudgetPeriod.WEEKLY, target = 3 * 60, activeFrom = activeFrom),
            budget("weekly_drift", TimeCategoryIds.DIGITAL_DRIFT, BudgetPeriod.WEEKLY, cap = 2 * 60, activeFrom = activeFrom),
            budget("weekly_buffer", TimeCategoryIds.BUFFER, BudgetPeriod.WEEKLY, target = 8 * 60, activeFrom = activeFrom)
        )
    }

    private fun budget(
        id: String,
        categoryId: String,
        period: BudgetPeriod,
        floor: Int? = null,
        target: Int? = null,
        cap: Int? = null,
        activeFrom: LocalDate
    ): TimeBudgetEntity {
        return TimeBudgetEntity(
            id = id,
            categoryId = categoryId,
            period = period,
            floorMinutes = floor,
            targetMinutes = target,
            capMinutes = cap,
            activeFrom = activeFrom,
            activeTo = null
        )
    }

    private fun defaultCategoryId(tier: AppTier, app: AppDescriptor): String {
        val haystack = "${app.label} ${app.packageName}".lowercase()
        return when {
            tier == AppTier.DISTRACTION || tier == AppTier.BLOCKED -> TimeCategoryIds.DIGITAL_DRIFT
            tier == AppTier.WORK -> TimeCategoryIds.FOCUSED_WORK
            tier == AppTier.PEOPLE -> TimeCategoryIds.RELATIONSHIPS
            tier == AppTier.ADMIN -> TimeCategoryIds.ADMIN
            "phone" in haystack || "contacts" in haystack || "messages" in haystack || "dialer" in haystack -> TimeCategoryIds.RELATIONSHIPS
            "kindle" in haystack || "reader" in haystack -> TimeCategoryIds.LEARNING
            "spotify" in haystack || "music" in haystack -> TimeCategoryIds.REST
            else -> TimeCategoryIds.UTILITY
        }
    }

    private fun labelForCategory(categoryId: String): String {
        return DefaultTimeCategories.categories.firstOrNull { it.id == categoryId }?.name ?: categoryId
    }
}

private fun AppTimeMappingEntity.repairCriticalPeopleMapping(now: Instant): AppTimeMappingEntity? {
    if (!isCriticalPeoplePackage(packageName)) return null
    if (
        defaultCategoryId == TimeCategoryIds.RELATIONSHIPS &&
        appTier == AppTier.PEOPLE &&
        !countAsDigitalDrift &&
        !requiresIntent
    ) {
        return null
    }
    return copy(
        defaultCategoryId = TimeCategoryIds.RELATIONSHIPS,
        appTier = AppTier.PEOPLE,
        countAsDigitalDrift = false,
        requiresIntent = false,
        updatedAt = now
    )
}

private data class TodayLedgerBase(
    val categories: List<TimeCategoryEntity>,
    val budgets: List<TimeBudgetEntity>,
    val blocks: List<TimeBlockEntity>,
    val sleep: List<com.otto.launcher.trace.data.V2SleepSessionEntity>,
    val mappings: List<AppTimeMappingEntity>
)

private fun TimeCategory.toEntity(now: Instant): TimeCategoryEntity {
    return TimeCategoryEntity(
        id = id,
        name = name,
        kind = kind,
        sortOrder = sortOrder,
        isDefault = isDefault,
        createdAt = now,
        updatedAt = now
    )
}

private fun TimeCategoryEntity.toDomain(): TimeCategory {
    return TimeCategory(
        id = id,
        name = name,
        kind = kind,
        sortOrder = sortOrder,
        isDefault = isDefault
    )
}

private fun TimeBudgetEntity.toDomain(): TimeBudget {
    return TimeBudget(
        id = id,
        categoryId = categoryId,
        period = period,
        floorMinutes = floorMinutes,
        targetMinutes = targetMinutes,
        capMinutes = capMinutes,
        activeFrom = activeFrom,
        activeTo = activeTo
    )
}

private fun TimeBlockEntity.toDomain(): TimeBlock {
    return TimeBlock(
        id = id,
        categoryId = categoryId,
        startedAt = startedAt,
        endedAt = endedAt,
        source = source,
        confidence = confidence,
        label = label,
        linkedPackageName = linkedPackageName,
        linkedCalendarEventId = linkedCalendarEventId
    )
}

private fun AppTimeMappingEntity.toDomain(): AppTimeMapping {
    return AppTimeMapping(
        packageName = packageName,
        defaultCategoryId = defaultCategoryId,
        appTier = appTier,
        countAsDigitalDrift = countAsDigitalDrift,
        requiresIntent = requiresIntent
    )
}
