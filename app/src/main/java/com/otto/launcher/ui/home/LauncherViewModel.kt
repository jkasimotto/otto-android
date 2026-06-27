package com.otto.launcher.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.otto.launcher.data.goals.GoalSettingsRepository
import com.otto.launcher.data.mode.ModeRepository
import com.otto.launcher.data.policy.PolicyRepository
import com.otto.launcher.data.time.TimeLedgerRepository
import com.otto.launcher.data.usage.UsageStatsRepository
import com.otto.launcher.domain.command.CommandResolver
import com.otto.launcher.domain.command.CommandResult
import java.time.Instant
import com.otto.launcher.domain.mode.OttoMode
import com.otto.launcher.domain.policy.AppDescriptor
import com.otto.launcher.domain.policy.AppPolicy
import com.otto.launcher.domain.policy.AppPolicyEngine
import com.otto.launcher.domain.time.DailyTimeReview
import com.otto.launcher.domain.time.DayPlanMode
import com.otto.launcher.domain.time.TimeCategoryIds
import com.otto.launcher.domain.time.TimeLedgerCalculator
import com.otto.launcher.domain.time.TimeMode
import com.otto.launcher.domain.time.TodayTimeLedger
import com.otto.launcher.domain.time.WeeklyTimeLedger
import com.otto.launcher.domain.trace.InboxKind
import com.otto.launcher.domain.trace.InboxState
import com.otto.launcher.domain.trace.TodayLedgerState
import com.otto.launcher.domain.trace.WeeklySleepDay
import com.otto.launcher.domain.usage.DailyPhoneUsage
import com.otto.launcher.trace.data.FoodEntryEntity
import com.otto.launcher.trace.data.InboxItemEntity
import com.otto.launcher.trace.data.TraceV2Repository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val mode: OttoMode,
    val ledger: TodayLedgerState,
    val timeLedger: TodayTimeLedger,
    val weeklyTimeLedger: WeeklyTimeLedger,
    val dailyTimeReview: DailyTimeReview,
    val unresolvedFood: List<FoodEntryEntity>,
    val inbox: List<InboxItemEntity>,
    val policies: List<AppPolicy>,
    val weeklySleep: List<WeeklySleepDay>,
    val weeklyPhoneUsage: List<DailyPhoneUsage>,
    val weeklyNightUnlocks: Map<LocalDate, List<Instant>> = emptyMap()
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val traceRepository = TraceV2Repository(application)
    private val goalsRepository = GoalSettingsRepository(application)
    private val usageStatsRepository = UsageStatsRepository(application)
    private val timeLedgerRepository = TimeLedgerRepository(application)
    private val modeRepository = ModeRepository(application)
    private val policyRepository = PolicyRepository(application)
    private val seededPackages = MutableStateFlow<Set<String>>(emptySet())

    private val goals = goalsRepository.observeSettings()
    private val usage = usageStatsRepository.observeTodayUsage()
    private val todayUsageSlices = usageStatsRepository.observeTodayUsageSlices()
    private val weeklyUsageSlices = usageStatsRepository.observeCurrentWeekUsageSlices()
    private val ledger = traceRepository.observeTodayLedger(usage, goals)
    private val timeLedger = timeLedgerRepository.observeTodayLedger(todayUsageSlices)
    private val weeklyTimeLedger = timeLedgerRepository.observeWeeklyLedger(weeklyUsageSlices)
    private val dailyTimeReview = timeLedgerRepository.observeDailyReview(todayUsageSlices)
    private val weeklySleep = traceRepository.observeWeeklySleep()
    private val weeklyPhoneUsage = usageStatsRepository.observeWeeklyDailyUsage()
    private val weeklyNightUnlocks = usageStatsRepository.observeWeeklyNightUnlocks()
    private val weeklyData = combine(weeklySleep, weeklyPhoneUsage, weeklyNightUnlocks) { sleep, usage, unlocks ->
        Triple(sleep, usage, unlocks)
    }

    private val traceAndTime = combine(
        ledger,
        timeLedger,
        weeklyTimeLedger,
        dailyTimeReview,
        traceRepository.observeFoodReviewEntries()
    ) { ledgerState, timeLedgerState, weeklyTimeLedgerState, dailyTimeReviewState, unresolvedFood ->
        TraceAndTimeState(
            ledger = ledgerState,
            timeLedger = timeLedgerState,
            weeklyTimeLedger = weeklyTimeLedgerState,
            dailyTimeReview = dailyTimeReviewState,
            unresolvedFood = unresolvedFood
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        modeRepository.observeBaseMode(),
        traceAndTime,
        traceRepository.observeOpenInbox(),
        policyRepository.observePolicies(),
        weeklyData
    ) { baseMode, traceAndTimeState, inbox, policies, weekly ->
        val (weeklySleepDays, weeklyPhoneUsageDays, nightUnlocks) = weekly
        HomeUiState(
            mode = if (traceAndTimeState.ledger.activeSleepStartAt != null) OttoMode.SLEEP else baseMode,
            ledger = traceAndTimeState.ledger,
            timeLedger = traceAndTimeState.timeLedger,
            weeklyTimeLedger = traceAndTimeState.weeklyTimeLedger,
            dailyTimeReview = traceAndTimeState.dailyTimeReview,
            unresolvedFood = traceAndTimeState.unresolvedFood,
            inbox = inbox,
            policies = policies,
            weeklySleep = weeklySleepDays,
            weeklyPhoneUsage = weeklyPhoneUsageDays,
            weeklyNightUnlocks = nightUnlocks
        )
    }
        .catch { emit(emptyState()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyState()
        )

    fun seedApps(apps: List<AppDescriptor>) {
        val packageKeys = apps.map { AppPolicyEngine.packageKey(it.packageName) }.toSet()
        if (seededPackages.value == packageKeys) return
        seededPackages.value = packageKeys
        viewModelScope.launch {
            policyRepository.seedDefaults(apps)
            timeLedgerRepository.seedDefaults(apps)
            traceRepository.backfillLegacyIfNeeded()
        }
    }

    fun resolveCommand(query: String, apps: List<AppDescriptor>): CommandResult {
        val resolver = CommandResolver(policyEngine = AppPolicyEngine(uiState.value.policies))
        return resolver.resolve(query, apps)
    }

    fun setMode(mode: OttoMode) {
        modeRepository.setMode(mode)
    }

    fun startTimeBlock(mode: TimeMode, onStarted: () -> Unit = {}) {
        viewModelScope.launch {
            timeLedgerRepository.startBlock(mode)
            onStarted()
        }
    }

    fun startCategoryBlock(categoryId: String, label: String? = null, onStarted: () -> Unit = {}) {
        viewModelScope.launch {
            timeLedgerRepository.startCategoryBlock(categoryId, label)
            onStarted()
        }
    }

    fun finishActiveTimeBlock(onFinished: (Int?) -> Unit = {}) {
        viewModelScope.launch {
            onFinished(timeLedgerRepository.finishActiveBlock())
        }
    }

    fun recordTimeDuration(categoryId: String, minutes: Int, label: String? = null) {
        viewModelScope.launch {
            timeLedgerRepository.recordDuration(categoryId, minutes, label)
        }
    }

    fun recordTimeAffluence(score: Int) {
        viewModelScope.launch { timeLedgerRepository.recordTimeAffluence(score) }
    }

    fun recordManualFoodEnergy(kJ: Int) {
        viewModelScope.launch { traceRepository.recordManualFoodEnergy(kJ) }
    }

    fun updateFoodEnergy(id: String, kJ: Int?) {
        viewModelScope.launch { traceRepository.updateFoodEnergy(id, kJ) }
    }

    fun startSleep(onStarted: () -> Unit = {}) {
        viewModelScope.launch {
            traceRepository.startSleep()
            onStarted()
        }
    }

    fun recordSleepSession(startAt: Instant, endAt: Instant, onDone: () -> Unit = {}) {
        viewModelScope.launch { traceRepository.recordSleepSession(startAt, endAt); onDone() }
    }

    fun updateSleepSession(id: String, startAt: Instant, endAt: Instant, onDone: () -> Unit = {}) {
        viewModelScope.launch { traceRepository.updateSleepSession(id, startAt, endAt); onDone() }
    }

    fun endSleep(onSaved: (Int?) -> Unit) {
        viewModelScope.launch {
            onSaved(traceRepository.endSleep())
        }
    }

    fun saveNote(text: String) {
        viewModelScope.launch { traceRepository.recordInboxItem(InboxKind.NOTE, text) }
    }

    fun saveTask(text: String) {
        viewModelScope.launch { traceRepository.recordInboxItem(InboxKind.TASK, text) }
    }

    fun updateInboxState(id: String, state: InboxState) {
        viewModelScope.launch { traceRepository.updateInboxState(id, state) }
    }

    fun recordDistractionSession(packageName: String, reason: String, timeboxMinutes: Int) {
        viewModelScope.launch {
            policyRepository.recordDistractionSession(
                packageName = packageName,
                tier = com.otto.launcher.domain.policy.AppTier.DISTRACTION,
                reason = reason,
                timeboxMinutes = timeboxMinutes
            )
        }
    }

    fun openUsageAccessSettings() {
        usageStatsRepository.openUsageAccessSettings()
    }

    private fun emptyState(): HomeUiState {
        return HomeUiState(
            mode = OttoMode.OPEN,
            ledger = TodayLedgerState(
                date = LocalDate.now(),
                sleepText = "not recorded today",
                weightText = "not recorded today",
                foodText = "not recorded today",
                phoneText = "usage access off",
                activeSleepStartAt = null,
                unresolvedFoodCount = 0
            ),
            timeLedger = emptyTodayTimeLedger(),
            weeklyTimeLedger = emptyWeeklyTimeLedger(),
            dailyTimeReview = DailyTimeReview(
                date = LocalDate.now(),
                rows = emptyList(),
                unknownGaps = emptyList(),
                timeAffluence = null
            ),
            unresolvedFood = emptyList(),
            inbox = emptyList(),
            policies = emptyList(),
            weeklySleep = emptyList(),
            weeklyPhoneUsage = emptyList()
        )
    }

    private fun emptyTodayTimeLedger(): TodayTimeLedger {
        return TodayTimeLedger(
            date = LocalDate.now(),
            hasUsageAccess = false,
            activeBlock = null,
            rows = emptyList(),
            totals = emptyList(),
            unknownMinutes = 0,
            digitalDriftMinutes = 0,
            digitalDriftCapMinutes = null,
            dayPlanMode = DayPlanMode.NORMAL,
            timeAffluence = null
        )
    }

    private fun emptyWeeklyTimeLedger(): WeeklyTimeLedger {
        val today = LocalDate.now()
        return WeeklyTimeLedger(
            startDate = today,
            endDate = today,
            rows = emptyList(),
            experimentSuggestion = null
        )
    }
}

private data class TraceAndTimeState(
    val ledger: TodayLedgerState,
    val timeLedger: TodayTimeLedger,
    val weeklyTimeLedger: WeeklyTimeLedger,
    val dailyTimeReview: DailyTimeReview,
    val unresolvedFood: List<FoodEntryEntity>
)
