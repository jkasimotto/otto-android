package com.otto.launcher.domain.command

import com.otto.launcher.domain.policy.AppGate
import com.otto.launcher.domain.policy.AppTier

sealed interface CommandResult {
    data object Empty : CommandResult
    data class BuiltIn(val command: OttoCommand) : CommandResult
    data class AppResults(val results: List<AppCommandResult>) : CommandResult
    data object NoResult : CommandResult
}

data class AppCommandResult(
    val label: String,
    val packageName: String,
    val activityName: String,
    val tier: AppTier,
    val gate: AppGate,
    val score: Int
)

