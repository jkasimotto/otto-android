package com.otto.launcher.trace.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.otto.launcher.domain.trace.UseCaseCandidate
import com.otto.launcher.trace.data.TraceRepository
import com.otto.launcher.trace.domain.DailySummary
import com.otto.launcher.trace.domain.DataCoverage
import com.otto.launcher.trace.domain.MealSlot
import com.otto.launcher.trace.domain.NextTraceAction
import com.otto.launcher.trace.domain.NextTraceActionKind
import com.otto.launcher.trace.domain.TraceCategory
import com.otto.launcher.trace.domain.TraceConfidence
import com.otto.launcher.trace.domain.TraceDashboardState
import com.otto.launcher.trace.domain.TraceSettingsState
import com.otto.launcher.trace.domain.WeeklySummary
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TraceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TraceRepository(application)
    private val refreshNonce = MutableStateFlow(0)

    val uiState: StateFlow<TraceDashboardState> = repository.observeDashboard(refreshNonce)
        .catch { emit(emptyState()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyState()
        )

    val queuedMemoCount: StateFlow<Int> = repository.observeQueuedMemoCount()
        .catch { emit(0) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    fun createCameraImageFile(): File = repository.createCameraImageFile()

    fun onLauncherVisible() {
        viewModelScope.launch {
            repository.noteLauncherVisible()
            refresh()
        }
    }

    fun recordCameraPhoto(file: File, isDrinkOnly: Boolean) {
        viewModelScope.launch {
            repository.recordCameraPhoto(file, isDrinkOnly)
            refresh()
        }
    }

    fun importPhoto(uri: Uri, isDrinkOnly: Boolean) {
        viewModelScope.launch {
            repository.importPhoto(uri, isDrinkOnly)
            refresh()
        }
    }

    fun recordVoiceMemo(
        tempFile: File,
        transcriber: (suspend (File) -> Result<String>)? = null,
        onTranscript: (suspend (String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            repository.recordVoiceMemo(tempFile, transcriber, onTranscript)
            refresh()
        }
    }

    /**
     * Drains the offline backlog: transcribes any QUEUED voice memos when a key and connectivity
     * are available. Safe to call on every launcher resume; the repository no-ops when there is
     * nothing to do or a run is already in flight.
     */
    fun processQueuedMemos(
        transcriber: suspend (File) -> Result<String>,
        onTranscript: (suspend (String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val processed = repository.transcribeQueuedMemos(transcriber, onTranscript)
            if (processed > 0) refresh()
        }
    }

    /**
     * Mines transcribed memos for recurring Otto use cases into local storage. Backfills existing
     * history on first call, then only handles new memos. Local-only, no UI; safe on every resume.
     */
    fun extractUseCases(extractor: suspend (String) -> List<UseCaseCandidate>) {
        viewModelScope.launch {
            repository.extractUseCases(extractor)
        }
    }

    fun recordWeight(kilograms: Double) {
        viewModelScope.launch {
            repository.recordWeight(kilograms)
            refresh()
        }
    }

    fun confirmSleepEstimate() {
        val estimate = uiState.value.sleepEstimate ?: return
        viewModelScope.launch {
            repository.recordSleep(
                startAt = estimate.startAt,
                endAt = estimate.endAt,
                wasAdjustedByUser = false,
                confidence = TraceConfidence.USER_CONFIRMED
            )
            refresh()
        }
    }

    fun recordSleep(startAt: Instant, endAt: Instant, wasAdjustedByUser: Boolean) {
        viewModelScope.launch {
            repository.recordSleep(startAt, endAt, wasAdjustedByUser)
            refresh()
        }
    }

    fun updateWeight(traceId: String, kilograms: Double) {
        viewModelScope.launch {
            repository.updateWeight(traceId, kilograms)
            refresh()
        }
    }

    fun updateSleep(traceId: String, startAt: Instant, endAt: Instant, wasAdjustedByUser: Boolean) {
        viewModelScope.launch {
            repository.updateSleep(traceId, startAt, endAt, wasAdjustedByUser)
            refresh()
        }
    }

    fun deleteTrace(traceId: String) {
        viewModelScope.launch {
            repository.deleteTrace(traceId)
            refresh()
        }
    }

    fun ignoreSleepEstimate() {
        repository.clearSleepEstimate()
        refresh()
    }

    fun recordMealAbsence(slot: MealSlot) {
        viewModelScope.launch {
            repository.recordMealAbsence(slot)
            refresh()
        }
    }

    fun ignoreMealPrompt(slot: MealSlot) {
        repository.ignoreMealPrompt(slot)
        refresh()
    }

    fun setFoodHidden(traceId: String, hidden: Boolean) {
        viewModelScope.launch {
            repository.setFoodHidden(traceId, hidden)
            refresh()
        }
    }

    fun setDrinkOnly(traceId: String, drinkOnly: Boolean) {
        viewModelScope.launch {
            repository.setDrinkOnly(traceId, drinkOnly)
            refresh()
        }
    }

    fun updateNote(traceId: String, note: String?) {
        viewModelScope.launch {
            repository.updateNote(traceId, note)
            refresh()
        }
    }

    fun setCategoryEnabled(category: TraceCategory, enabled: Boolean) {
        repository.setCategoryEnabled(category, enabled)
        refresh()
    }

    private fun refresh() {
        refreshNonce.value += 1
    }

    private fun emptyState(): TraceDashboardState {
        val today = LocalDate.now()
        return TraceDashboardState(
            today = DailySummary(
                date = today,
                foodPhotoCount = 0,
                drinkPhotoCount = 0,
                firstFoodAt = null,
                lastFoodAt = null,
                eatingWindowMinutes = null,
                weightKg = null,
                weightTrendKg = null,
                sleepDurationMinutes = null,
                sleepStartAt = null,
                sleepEndAt = null,
                dataCoverage = DataCoverage(0, 0, 0, 7)
            ),
            weekly = WeeklySummary(
                startDate = today.minusDays(6),
                endDate = today,
                foodPhotoCount = 0,
                foodCoverageDays = 0,
                weightCoverageDays = 0,
                sleepCoverageDays = 0,
                medianEatingWindowMinutes = null,
                medianSleepDurationMinutes = null,
                weightTrendDeltaKg = null,
                lateFoodNights = 0
            ),
            timeline = emptyList(),
            nextAction = NextTraceAction(
                kind = NextTraceActionKind.OPEN_CAPTURE,
                title = "Capture",
                detail = null,
                primaryLabel = "Open"
            ),
            sleepEstimate = null,
            lastWeightKg = null,
            settings = TraceSettingsState(
                foodEnabled = true,
                drinkEnabled = true,
                weightEnabled = true,
                sleepEnabled = true
            )
        )
    }
}

fun Instant.withDurationTo(other: Instant): Duration = Duration.between(this, other)
