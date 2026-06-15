package com.otto.launcher.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.otto.launcher.data.goals.GoalSettingsRepository
import com.otto.launcher.data.mode.ModeRepository
import com.otto.launcher.data.policy.PolicyRepository
import com.otto.launcher.data.usage.UsageStatsRepository
import com.otto.launcher.domain.command.CommandResolver
import com.otto.launcher.domain.command.CommandResult
import com.otto.launcher.domain.mode.OttoMode
import com.otto.launcher.domain.policy.AppDescriptor
import com.otto.launcher.domain.policy.AppPolicy
import com.otto.launcher.domain.policy.AppPolicyEngine
import com.otto.launcher.domain.trace.InboxKind
import com.otto.launcher.domain.trace.InboxState
import com.otto.launcher.domain.trace.TodayLedgerState
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
    val unresolvedFood: List<FoodEntryEntity>,
    val inbox: List<InboxItemEntity>,
    val policies: List<AppPolicy>
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val traceRepository = TraceV2Repository(application)
    private val goalsRepository = GoalSettingsRepository(application)
    private val usageStatsRepository = UsageStatsRepository(application)
    private val modeRepository = ModeRepository(application)
    private val policyRepository = PolicyRepository(application)
    private val seededPackages = MutableStateFlow<Set<String>>(emptySet())

    private val goals = goalsRepository.observeSettings()
    private val usage = usageStatsRepository.observeTodayUsage()
    private val ledger = traceRepository.observeTodayLedger(usage, goals)

    val uiState: StateFlow<HomeUiState> = combine(
        modeRepository.observeBaseMode(),
        ledger,
        traceRepository.observeFoodReviewEntries(),
        traceRepository.observeOpenInbox(),
        policyRepository.observePolicies()
    ) { baseMode, ledgerState, unresolvedFood, inbox, policies ->
        HomeUiState(
            mode = if (ledgerState.activeSleepStartAt != null) OttoMode.SLEEP else baseMode,
            ledger = ledgerState,
            unresolvedFood = unresolvedFood,
            inbox = inbox,
            policies = policies
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
            unresolvedFood = emptyList(),
            inbox = emptyList(),
            policies = emptyList()
        )
    }
}
