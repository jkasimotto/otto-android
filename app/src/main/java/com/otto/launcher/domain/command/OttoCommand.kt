package com.otto.launcher.domain.command

import com.otto.launcher.domain.mode.OttoMode

sealed interface OttoCommand {
    data object CaptureFood : OttoCommand
    data class CaptureFoodWithEnergy(val kJ: Int) : OttoCommand
    data object CaptureDrink : OttoCommand
    data class SaveWeight(val kg: Double) : OttoCommand
    data object StartSleep : OttoCommand
    data object EndSleep : OttoCommand
    data class SaveNote(val text: String) : OttoCommand
    data class SaveTask(val text: String) : OttoCommand
    data object OpenToday : OttoCommand
    data object OpenReview : OttoCommand
    data object OpenWeek : OttoCommand
    data class SetMode(val mode: OttoMode) : OttoCommand
    data class LaunchApp(val packageName: String) : OttoCommand
    data class ExplainBlockedApp(val packageName: String) : OttoCommand
    data class OpenMaintenance(val section: MaintenanceSection) : OttoCommand
}

enum class MaintenanceSection {
    SETTINGS,
    LOGS,
    UPDATE
}

