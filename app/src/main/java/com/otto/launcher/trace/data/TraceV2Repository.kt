package com.otto.launcher.trace.data

import android.content.Context
import com.otto.launcher.domain.goals.GoalSettings
import com.otto.launcher.domain.trace.InboxKind
import com.otto.launcher.domain.trace.InboxState
import com.otto.launcher.domain.trace.MemoState
import com.otto.launcher.domain.trace.TodayLedgerState
import com.otto.launcher.domain.trace.WeeklySleepDay
import com.otto.launcher.domain.trace.sleepNightDate
import com.otto.launcher.domain.usage.TodayUsageSummary
import com.otto.launcher.trace.domain.TraceType
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TraceV2Repository(
    context: Context,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    private val appContext = context.applicationContext
    private val database = TraceDatabase.get(appContext)
    private val legacyDao = database.traceDao()
    private val dao = database.traceV2Dao()
    private val zoneId: ZoneId = ZoneId.systemDefault()

    fun observeTodayLedger(
        usage: Flow<TodayUsageSummary>,
        goals: Flow<GoalSettings>
    ): Flow<TodayLedgerState> {
        val (start, end) = todayBounds()
        return combine(
            dao.observeFoodEntriesBetween(start, end),
            dao.observeWeightEntriesBetween(start, end),
            dao.observeSleepSessionsOverlapping(start.minusSeconds(24 * 60 * 60), end),
            usage,
            goals
        ) { food, weights, sleep, usageSummary, goalSettings ->
            buildLedger(food, weights, sleep, usageSummary, goalSettings)
        }
    }

    fun observeFoodReviewEntries(): Flow<List<FoodEntryEntity>> {
        return dao.observeUnresolvedFoodEntries()
    }

    fun observeOpenInbox(): Flow<List<InboxItemEntity>> {
        return dao.observeInboxItems(InboxState.OPEN)
    }

    fun observeRecentTranscripts(limit: Int = 20): Flow<List<VoiceMemoEntity>> {
        return dao.observeRecentTranscribedMemos(MemoState.PROCESSED, limit)
    }

    fun observeWeeklySleep(): Flow<List<WeeklySleepDay>> {
        val today = LocalDate.now(clock)
        val start = today.minusDays(6).atStartOfDay(zoneId).toInstant()
        val end = today.plusDays(1).atStartOfDay(zoneId).toInstant()
        return dao.observeSleepSessionsOverlapping(start, end).map { sessions ->
            (0L..6L).map { daysAgo ->
                val date = today.minusDays(6 - daysAgo)
                val session = sessions
                    .filter { it.endAt != null }
                    .firstOrNull { sleepNightDate(it.startAt, zoneId) == date }
                WeeklySleepDay(
                    date = date,
                    sessionId = session?.id,
                    startAt = session?.startAt,
                    endAt = session?.endAt
                )
            }
        }
    }

    suspend fun recordSleepSession(startAt: Instant, endAt: Instant) = withContext(Dispatchers.IO) {
        val now = clock.instant()
        dao.upsertSleepSession(
            V2SleepSessionEntity(
                id = UUID.randomUUID().toString(),
                startAt = startAt,
                endAt = endAt,
                source = "MANUAL",
                targetStartLocalTime = null,
                targetWakeLocalTime = null,
                healthConnectId = null,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun updateSleepSession(id: String, startAt: Instant, endAt: Instant) = withContext(Dispatchers.IO) {
        val existing = dao.sleepSession(id) ?: return@withContext
        dao.upsertSleepSession(existing.copy(startAt = startAt, endAt = endAt, updatedAt = clock.instant()))
    }

    suspend fun backfillLegacyIfNeeded() = withContext(Dispatchers.IO) {
        if (dao.foodEntryCount() + dao.weightEntryCount() + dao.sleepSessionCount() > 0) return@withContext

        legacyDao.evidenceAll().forEach { evidence ->
            val trace = evidence.trace
            val createdAt = trace.createdAt
            val updatedAt = trace.updatedAt
            when {
                evidence.foodCapture != null && evidence.foodCapture.isDrinkOnly -> {
                    val media = legacyDao.mediaAsset(evidence.foodCapture.mediaId)
                    dao.upsertDrinkEntry(
                        DrinkEntryEntity(
                            id = trace.id,
                            capturedAt = trace.occurredAt,
                            photoUri = media?.localUri,
                            thumbnailUri = media?.thumbnailUri,
                            amountMl = null,
                            source = trace.source.name,
                            createdAt = createdAt,
                            updatedAt = updatedAt
                        )
                    )
                }
                evidence.foodCapture != null -> {
                    val media = legacyDao.mediaAsset(evidence.foodCapture.mediaId)
                    dao.upsertFoodEntry(
                        FoodEntryEntity(
                            id = trace.id,
                            capturedAt = trace.occurredAt,
                            photoUri = media?.localUri,
                            thumbnailUri = media?.thumbnailUri,
                            energyKj = null,
                            mealType = evidence.foodCapture.inferredMealSlot?.name,
                            reviewedAt = null,
                            source = trace.source.name,
                            createdAt = createdAt,
                            updatedAt = updatedAt
                        )
                    )
                }
                evidence.weightMeasurement != null -> {
                    dao.upsertWeightEntry(
                        WeightEntryEntity(
                            id = trace.id,
                            measuredAt = trace.occurredAt,
                            kg = evidence.weightMeasurement.kilograms,
                            source = trace.source.name,
                            healthConnectId = null,
                            createdAt = createdAt,
                            updatedAt = updatedAt
                        )
                    )
                }
                evidence.sleepSession != null -> {
                    dao.upsertSleepSession(
                        V2SleepSessionEntity(
                            id = trace.id,
                            startAt = evidence.sleepSession.startAt,
                            endAt = evidence.sleepSession.endAt,
                            source = trace.source.name,
                            targetStartLocalTime = null,
                            targetWakeLocalTime = null,
                            healthConnectId = null,
                            createdAt = createdAt,
                            updatedAt = updatedAt
                        )
                    )
                }
            }
        }
    }

    suspend fun recordManualFoodEnergy(energyKj: Int) = withContext(Dispatchers.IO) {
        val now = clock.instant()
        dao.upsertFoodEntry(
            FoodEntryEntity(
                id = UUID.randomUUID().toString(),
                capturedAt = now,
                photoUri = null,
                thumbnailUri = null,
                energyKj = energyKj,
                mealType = null,
                reviewedAt = now,
                source = "MANUAL",
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun updateFoodEnergy(id: String, energyKj: Int?) = withContext(Dispatchers.IO) {
        val entry = dao.foodEntry(id) ?: return@withContext
        val now = clock.instant()
        dao.updateFoodEntry(
            entry.copy(
                energyKj = energyKj,
                reviewedAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun startSleep() = withContext(Dispatchers.IO) {
        if (dao.openSleepSession() != null) return@withContext
        val now = clock.instant()
        dao.upsertSleepSession(
            V2SleepSessionEntity(
                id = UUID.randomUUID().toString(),
                startAt = now,
                endAt = null,
                source = "MANUAL",
                targetStartLocalTime = null,
                targetWakeLocalTime = null,
                healthConnectId = null,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun endSleep(): Int? = withContext(Dispatchers.IO) {
        val open = dao.openSleepSession() ?: return@withContext null
        val now = clock.instant()
        dao.closeSleepSession(open.id, now, now)
        Duration.between(open.startAt, now).toMinutes().toInt().coerceAtLeast(0)
    }

    suspend fun recordInboxItem(kind: InboxKind, text: String, audioUri: String? = null, confidence: Float? = null) =
        withContext(Dispatchers.IO) {
            val now = clock.instant()
            dao.upsertInboxItem(
                InboxItemEntity(
                    id = UUID.randomUUID().toString(),
                    kind = kind,
                    text = text.trim(),
                    audioUri = audioUri,
                    transcriptConfidence = confidence,
                    state = InboxState.OPEN,
                    createdAt = now,
                    reviewedAt = null
                )
            )
        }

    suspend fun updateInboxState(id: String, state: InboxState) = withContext(Dispatchers.IO) {
        val reviewedAt = if (state == InboxState.OPEN) null else clock.instant()
        dao.updateInboxState(id, state, reviewedAt)
    }

    private fun buildLedger(
        food: List<FoodEntryEntity>,
        weights: List<WeightEntryEntity>,
        sleep: List<V2SleepSessionEntity>,
        usage: TodayUsageSummary,
        goals: GoalSettings
    ): TodayLedgerState {
        val latestWeight = weights.maxByOrNull { it.measuredAt }
        val activeSleep = sleep.filter { it.endAt == null }.maxByOrNull { it.startAt }
        val latestClosedSleep = sleep
            .filter { it.endAt != null }
            .maxByOrNull { it.endAt ?: Instant.EPOCH }
        val foodPhotoCount = food.count { it.photoUri != null }
        val energy = food.mapNotNull { it.energyKj }.sum().takeIf { it > 0 }
        val unresolved = food.count { it.energyKj == null && it.reviewedAt == null }

        return TodayLedgerState(
            date = LocalDate.now(clock),
            sleepText = when {
                activeSleep != null -> "active since ${formatClock(activeSleep.startAt)}"
                latestClosedSleep?.endAt != null -> "${formatDuration(Duration.between(latestClosedSleep.startAt, latestClosedSleep.endAt).toMinutes().toInt())} / ${formatDuration(goals.sleepTargetMinutes)}"
                else -> "not recorded today"
            },
            weightText = latestWeight?.let { "${"%.1f".format(it.kg)} kg · today" } ?: "not recorded today",
            foodText = when {
                food.isEmpty() -> "not recorded today"
                energy != null -> "$foodPhotoCount photos · $energy kJ"
                else -> "$foodPhotoCount photos · 0 kJ set"
            },
            phoneText = if (usage.hasUsageAccess) {
                "${formatDuration(usage.totalMinutes)} · ${usage.distractingMinutes}m distract"
            } else {
                "usage access off"
            },
            activeSleepStartAt = activeSleep?.startAt,
            unresolvedFoodCount = unresolved
        )
    }

    private fun todayBounds(): Pair<Instant, Instant> {
        val start = LocalDate.now(clock).atStartOfDay(zoneId).toInstant()
        return start to start.plusSeconds(24 * 60 * 60)
    }

    private fun formatClock(instant: Instant): String {
        val local = instant.atZone(zoneId).toLocalTime()
        return "%02d:%02d".format(local.hour, local.minute)
    }

    private fun formatDuration(minutes: Int): String {
        val safe = minutes.coerceAtLeast(0)
        return "${safe / 60}h %02dm".format(safe % 60)
    }
}

