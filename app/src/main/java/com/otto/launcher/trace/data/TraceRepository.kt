package com.otto.launcher.trace.data

import android.content.Context
import android.net.Uri
import com.otto.launcher.trace.domain.DailySummary
import com.otto.launcher.trace.domain.DataCoverage
import com.otto.launcher.trace.domain.MealSlot
import com.otto.launcher.trace.domain.SleepEstimate
import com.otto.launcher.trace.domain.TraceCategory
import com.otto.launcher.trace.domain.TraceConfidence
import com.otto.launcher.trace.domain.TraceDashboardState
import com.otto.launcher.trace.domain.TraceMedia
import com.otto.launcher.trace.domain.TracePromptPolicy
import com.otto.launcher.trace.domain.TraceRules
import com.otto.launcher.trace.domain.TraceSettingsState
import com.otto.launcher.trace.domain.TraceSource
import com.otto.launcher.trace.domain.TraceTimelineItem
import com.otto.launcher.trace.domain.TraceType
import com.otto.launcher.trace.domain.WeeklySummary
import java.io.File
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

class TraceRepository(
    context: Context,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    private val appContext = context.applicationContext
    private val zoneId: ZoneId = ZoneId.systemDefault()
    private val dao = TraceDatabase.get(appContext).traceDao()
    private val preferences = TracePreferences(appContext)
    private val mediaStore = TraceMediaStore(appContext)

    fun createCameraImageFile(): File = mediaStore.createCameraImageFile()

    fun observeDashboard(refreshNonce: Flow<Int>): Flow<TraceDashboardState> {
        val initialNow = clock.instant()
        val initialToday = LocalDate.now(clock)
        val start = initialToday.minusDays(27).atStartOfDay(zoneId).toInstant()
        val end = initialToday.plusDays(1).atStartOfDay(zoneId).toInstant()
        return combine(
            dao.observeEvidenceBetween(start, end),
            dao.observeMediaAssets(),
            refreshNonce
        ) { evidence, mediaAssets, _ ->
            buildDashboard(
                evidence = evidence,
                mediaAssets = mediaAssets.associateBy { it.id },
                now = clock.instant().takeIf { it >= initialNow } ?: initialNow
            )
        }
    }

    suspend fun noteLauncherVisible() = withContext(Dispatchers.IO) {
        preferences.noteLauncherVisible(clock.instant(), zoneId)
    }

    suspend fun recordCameraPhoto(file: File, isDrinkOnly: Boolean) = withContext(Dispatchers.IO) {
        val now = clock.instant()
        val media = mediaStore.createAssetFromCameraFile(file, now)
        insertPhotoTrace(media, isDrinkOnly, TraceSource.CAMERA, now)
    }

    suspend fun importPhoto(uri: Uri, isDrinkOnly: Boolean) = withContext(Dispatchers.IO) {
        val now = clock.instant()
        val media = mediaStore.importImage(uri, now)
        insertPhotoTrace(media, isDrinkOnly, TraceSource.PHOTO_PICKER, now)
    }

    suspend fun recordWeight(kilograms: Double) = withContext(Dispatchers.IO) {
        val now = clock.instant()
        val traceId = UUID.randomUUID().toString()
        dao.insertWeightTrace(
            trace = TraceEntity(
                id = traceId,
                type = TraceType.WEIGHT,
                source = TraceSource.MANUAL,
                confidence = TraceConfidence.EXACT,
                occurredAt = now,
                endedAt = null,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
                notes = null
            ),
            weightMeasurement = WeightMeasurementEntity(
                traceId = traceId,
                kilograms = kilograms,
                sourceLabel = null
            )
        )
    }

    suspend fun recordSleep(
        startAt: Instant,
        endAt: Instant,
        wasAdjustedByUser: Boolean,
        confidence: TraceConfidence = TraceConfidence.USER_CONFIRMED
    ) = withContext(Dispatchers.IO) {
        val normalizedEnd = if (endAt > startAt) endAt else startAt.plus(Duration.ofMinutes(1))
        val now = clock.instant()
        val traceId = UUID.randomUUID().toString()
        dao.insertSleepTrace(
            trace = TraceEntity(
                id = traceId,
                type = TraceType.SLEEP_SESSION,
                source = TraceSource.MANUAL,
                confidence = confidence,
                occurredAt = startAt,
                endedAt = normalizedEnd,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
                notes = null
            ),
            sleepSession = SleepSessionEntity(
                traceId = traceId,
                startAt = startAt,
                endAt = normalizedEnd,
                durationMinutes = Duration.between(startAt, normalizedEnd).toMinutes().toInt().coerceAtLeast(1),
                sourceLabel = if (confidence == TraceConfidence.ESTIMATED) "Launcher estimate" else null,
                wasAdjustedByUser = wasAdjustedByUser
            )
        )
        preferences.clearSleepEstimate()
    }

    suspend fun recordMealAbsence(slot: MealSlot) = withContext(Dispatchers.IO) {
        val now = clock.instant()
        dao.insertTrace(
            TraceEntity(
                id = UUID.randomUUID().toString(),
                type = TraceType.MEAL_ABSENCE,
                source = TraceSource.MANUAL,
                confidence = TraceConfidence.EXACT,
                occurredAt = now,
                endedAt = null,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
                notes = "No ${slot.name.lowercase()}"
            )
        )
        preferences.clearMealWindowIgnore(slot)
    }

    fun ignoreMealPrompt(slot: MealSlot) {
        preferences.ignoredMealWindow(slot, clock.instant())
    }

    fun setCategoryEnabled(category: TraceCategory, enabled: Boolean) {
        preferences.setCategoryEnabled(category, enabled)
    }

    fun clearSleepEstimate() {
        preferences.clearSleepEstimate()
    }

    suspend fun setFoodHidden(traceId: String, hidden: Boolean) = withContext(Dispatchers.IO) {
        val evidence = dao.evidenceById(traceId) ?: return@withContext
        val capture = evidence.foodCapture ?: return@withContext
        dao.updateFoodCapture(capture.copy(hidden = hidden))
    }

    suspend fun setDrinkOnly(traceId: String, drinkOnly: Boolean) = withContext(Dispatchers.IO) {
        val evidence = dao.evidenceById(traceId) ?: return@withContext
        val capture = evidence.foodCapture ?: return@withContext
        val now = clock.instant()
        dao.updateFoodCapture(capture.copy(isDrinkOnly = drinkOnly, mealSlot = if (drinkOnly) MealSlot.DRINK else capture.mealSlot))
        dao.updateTrace(
            evidence.trace.copy(
                type = if (drinkOnly) TraceType.DRINK_PHOTO else TraceType.FOOD_PHOTO,
                updatedAt = now
            )
        )
    }

    suspend fun updateNote(traceId: String, note: String?) = withContext(Dispatchers.IO) {
        val evidence = dao.evidenceById(traceId) ?: return@withContext
        dao.updateTrace(
            evidence.trace.copy(
                notes = note?.trim()?.takeIf { it.isNotBlank() },
                updatedAt = clock.instant()
            )
        )
    }

    private suspend fun insertPhotoTrace(
        media: MediaAssetEntity,
        isDrinkOnly: Boolean,
        source: TraceSource,
        now: Instant
    ) {
        val traceId = UUID.randomUUID().toString()
        val localTime = now.atZone(zoneId).toLocalTime()
        val inferredSlot = if (isDrinkOnly) MealSlot.DRINK else TraceRules.inferMealSlot(localTime)
        dao.insertFoodTrace(
            trace = TraceEntity(
                id = traceId,
                type = if (isDrinkOnly) TraceType.DRINK_PHOTO else TraceType.FOOD_PHOTO,
                source = source,
                confidence = TraceConfidence.EXACT,
                occurredAt = now,
                endedAt = null,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
                notes = null
            ),
            media = media,
            foodCapture = FoodCaptureEntity(
                traceId = traceId,
                mealSlot = null,
                inferredMealSlot = inferredSlot,
                isDrinkOnly = isDrinkOnly,
                mediaId = media.id,
                hidden = false
            )
        )
        if (!isDrinkOnly && inferredSlot != MealSlot.SNACK && inferredSlot != MealSlot.UNKNOWN) {
            preferences.clearMealWindowIgnore(inferredSlot)
        }
    }

    private fun buildDashboard(
        evidence: List<TraceEvidenceEntity>,
        mediaAssets: Map<String, MediaAssetEntity>,
        now: Instant
    ): TraceDashboardState {
        val todayDate = now.atZone(zoneId).toLocalDate()
        val today = buildDailySummary(todayDate, evidence)
        val weekly = buildWeeklySummary(todayDate, evidence)
        val timeline = buildTimeline(todayDate, evidence, mediaAssets)
        val lastWeight = evidence
            .mapNotNull { item -> item.weightMeasurement?.let { item.trace.occurredAt to it.kilograms } }
            .maxByOrNull { it.first }
            ?.second
        val settings = TraceSettingsState(
            foodEnabled = preferences.isCategoryEnabled(TraceCategory.FOOD),
            drinkEnabled = preferences.isCategoryEnabled(TraceCategory.DRINK),
            weightEnabled = preferences.isCategoryEnabled(TraceCategory.WEIGHT),
            sleepEnabled = preferences.isCategoryEnabled(TraceCategory.SLEEP)
        )
        val sleepEstimate = preferences.sleepEstimate()
            ?.takeIf { !hasSleepForDate(todayDate, evidence) }
        return TraceDashboardState(
            today = today,
            weekly = weekly,
            timeline = timeline,
            nextAction = TracePromptPolicy.nextAction(now, zoneId, today, settings, sleepEstimate),
            sleepEstimate = sleepEstimate,
            lastWeightKg = lastWeight,
            settings = settings
        )
    }

    private fun buildDailySummary(date: LocalDate, evidence: List<TraceEvidenceEntity>): DailySummary {
        val dayEvidence = evidence.filter { belongsToDate(it, date) }
        val visiblePhotos = dayEvidence.visiblePhotoEvidence()
        val foodPhotos = visiblePhotos.filterNot { it.foodCapture?.isDrinkOnly == true }
        val drinkPhotos = visiblePhotos.filter { it.foodCapture?.isDrinkOnly == true }
        val foodTimes = visiblePhotos.map { it.trace.occurredAt }.sorted()
        val weights = evidence
            .mapNotNull { item -> item.weightMeasurement?.let { item.trace.occurredAt to it.kilograms } }
            .filter { it.first.atZone(zoneId).toLocalDate() <= date }
        val todayWeight = dayEvidence
            .mapNotNull { item -> item.weightMeasurement?.let { item.trace.occurredAt to it.kilograms } }
            .maxByOrNull { it.first }
            ?.second
        val todaySleep = dayEvidence
            .mapNotNull { it.sleepSession }
            .maxByOrNull { it.endAt }

        return DailySummary(
            date = date,
            foodPhotoCount = foodPhotos.size,
            drinkPhotoCount = drinkPhotos.size,
            firstFoodAt = foodTimes.firstOrNull(),
            lastFoodAt = foodTimes.lastOrNull(),
            eatingWindowMinutes = TraceRules.eatingWindowMinutes(foodTimes.firstOrNull(), foodTimes.lastOrNull()),
            weightKg = todayWeight,
            weightTrendKg = TraceRules.weightTrendDeltaKg(weights.takeLast(14)),
            sleepDurationMinutes = todaySleep?.durationMinutes,
            sleepStartAt = todaySleep?.startAt,
            sleepEndAt = todaySleep?.endAt,
            dataCoverage = coverage(date, evidence, 7)
        )
    }

    private fun buildWeeklySummary(today: LocalDate, evidence: List<TraceEvidenceEntity>): WeeklySummary {
        val start = today.minusDays(6)
        val dates = generateSequence(start) { it.plusDays(1) }.take(7).toList()
        val weekEvidence = evidence.filter { item -> dates.any { belongsToDate(item, it) } }
        val visiblePhotos = weekEvidence.visiblePhotoEvidence()
        val foodCoverage = visiblePhotos.map { it.trace.occurredAt.atZone(zoneId).toLocalDate() }.toSet().size
        val weightCoverage = weekEvidence
            .mapNotNull { item -> item.weightMeasurement?.let { item.trace.occurredAt.atZone(zoneId).toLocalDate() } }
            .toSet()
            .size
        val sleepCoverage = weekEvidence
            .mapNotNull { it.sleepSession?.endAt?.atZone(zoneId)?.toLocalDate() }
            .toSet()
            .size
        val eatingWindows = dates.mapNotNull { date ->
            val foodTimes = evidence
                .filter { belongsToDate(it, date) }
                .visiblePhotoEvidence()
                .map { it.trace.occurredAt }
                .sorted()
            TraceRules.eatingWindowMinutes(foodTimes.firstOrNull(), foodTimes.lastOrNull())
        }
        val sleepDurations = weekEvidence.mapNotNull { it.sleepSession?.durationMinutes }
        val weights = weekEvidence
            .mapNotNull { item -> item.weightMeasurement?.let { item.trace.occurredAt to it.kilograms } }
        val lateFoodNights = visiblePhotos
            .filter { TraceRules.isLateFood(it.trace.occurredAt, zoneId) }
            .map { it.trace.occurredAt.atZone(zoneId).toLocalDate() }
            .toSet()
            .size
        return WeeklySummary(
            startDate = start,
            endDate = today,
            foodPhotoCount = visiblePhotos.count { it.foodCapture?.isDrinkOnly != true },
            foodCoverageDays = foodCoverage,
            weightCoverageDays = weightCoverage,
            sleepCoverageDays = sleepCoverage,
            medianEatingWindowMinutes = TraceRules.median(eatingWindows),
            medianSleepDurationMinutes = TraceRules.median(sleepDurations),
            weightTrendDeltaKg = TraceRules.weightTrendDeltaKg(weights),
            lateFoodNights = lateFoodNights
        )
    }

    private fun buildTimeline(
        date: LocalDate,
        evidence: List<TraceEvidenceEntity>,
        mediaAssets: Map<String, MediaAssetEntity>
    ): List<TraceTimelineItem> {
        return evidence
            .filter { belongsToDate(it, date) }
            .sortedBy { it.trace.occurredAt }
            .map { item ->
                val media = item.foodCapture?.mediaId?.let(mediaAssets::get)
                val title = when {
                    item.foodCapture != null && item.foodCapture.isDrinkOnly -> "Drink photo"
                    item.foodCapture != null -> "Food photo"
                    item.weightMeasurement != null -> "Weight"
                    item.sleepSession != null -> "Sleep"
                    item.trace.type == TraceType.MEAL_ABSENCE -> item.trace.notes.orEmpty().replaceFirstChar { it.uppercase() }
                    else -> "Trace"
                }
                val detail = when {
                    item.weightMeasurement != null -> "${formatOneDecimal(item.weightMeasurement.kilograms)}kg"
                    item.sleepSession != null -> "${formatDuration(item.sleepSession.durationMinutes)}"
                    item.foodCapture?.hidden == true -> "Hidden"
                    else -> null
                }
                TraceTimelineItem(
                    traceId = item.trace.id,
                    type = item.trace.type,
                    occurredAt = item.trace.occurredAt,
                    endedAt = item.trace.endedAt,
                    title = title,
                    detail = detail,
                    notes = item.trace.notes,
                    media = media?.let {
                        TraceMedia(
                            id = it.id,
                            localPath = it.localUri,
                            thumbnailPath = it.thumbnailUri,
                            mimeType = it.mimeType
                        )
                    },
                    isDrinkOnly = item.foodCapture?.isDrinkOnly == true,
                    hidden = item.foodCapture?.hidden == true,
                    weightKg = item.weightMeasurement?.kilograms,
                    sleepDurationMinutes = item.sleepSession?.durationMinutes
                )
            }
    }

    private fun coverage(date: LocalDate, evidence: List<TraceEvidenceEntity>, days: Long): DataCoverage {
        val start = date.minusDays(days - 1)
        val dates = generateSequence(start) { it.plusDays(1) }.take(days.toInt()).toSet()
        val foodDays = evidence.visiblePhotoEvidence()
            .map { it.trace.occurredAt.atZone(zoneId).toLocalDate() }
            .filter { it in dates }
            .toSet()
            .size
        val weightDays = evidence
            .mapNotNull { item -> item.weightMeasurement?.let { item.trace.occurredAt.atZone(zoneId).toLocalDate() } }
            .filter { it in dates }
            .toSet()
            .size
        val sleepDays = evidence
            .mapNotNull { it.sleepSession?.endAt?.atZone(zoneId)?.toLocalDate() }
            .filter { it in dates }
            .toSet()
            .size
        return DataCoverage(foodDays, weightDays, sleepDays, days.toInt())
    }

    private fun hasSleepForDate(date: LocalDate, evidence: List<TraceEvidenceEntity>): Boolean {
        return evidence.any { item ->
            item.sleepSession?.endAt?.atZone(zoneId)?.toLocalDate() == date
        }
    }

    private fun belongsToDate(item: TraceEvidenceEntity, date: LocalDate): Boolean {
        val occurredDate = item.trace.occurredAt.atZone(zoneId).toLocalDate()
        val endedDate = item.trace.endedAt?.atZone(zoneId)?.toLocalDate()
        return occurredDate == date || endedDate == date
    }

    private fun List<TraceEvidenceEntity>.visiblePhotoEvidence(): List<TraceEvidenceEntity> {
        return filter { item ->
            item.foodCapture != null &&
                item.trace.deletedAt == null &&
                !item.foodCapture.hidden
        }
    }

    private fun formatDuration(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return "${hours}h%02d".format(mins)
    }

    private fun formatOneDecimal(value: Double): String = "%.1f".format(value)
}
