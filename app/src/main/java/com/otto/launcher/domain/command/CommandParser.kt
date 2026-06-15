package com.otto.launcher.domain.command

import com.otto.launcher.domain.mode.OttoMode
import java.util.Locale

class CommandParser {
    fun parse(input: String): OttoCommand? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        val normalized = trimmed.lowercase(Locale.US)

        parseFood(trimmed, normalized)?.let { return it }
        parseWeight(normalized)?.let { return it }
        parseTextCapture(trimmed, normalized)?.let { return it }

        return when (normalized) {
            "drink" -> OttoCommand.CaptureDrink
            "sleep" -> OttoCommand.StartSleep
            "wake" -> OttoCommand.EndSleep
            "today" -> OttoCommand.OpenToday
            "review" -> OttoCommand.OpenReview
            "week" -> OttoCommand.OpenWeek
            "focus" -> OttoCommand.SetMode(OttoMode.FOCUS)
            "open" -> OttoCommand.SetMode(OttoMode.OPEN)
            "settings" -> OttoCommand.OpenMaintenance(MaintenanceSection.SETTINGS)
            "logs" -> OttoCommand.OpenMaintenance(MaintenanceSection.LOGS)
            "update" -> OttoCommand.OpenMaintenance(MaintenanceSection.UPDATE)
            else -> null
        }
    }

    private fun parseFood(original: String, normalized: String): OttoCommand? {
        if (normalized == "food") return OttoCommand.CaptureFood
        val match = FOOD_WITH_ENERGY.matchEntire(original.trim()) ?: return null
        val energy = match.groupValues[1].toIntOrNull() ?: return null
        return OttoCommand.CaptureFoodWithEnergy(energy)
    }

    private fun parseWeight(normalized: String): OttoCommand? {
        val match = WEIGHT.matchEntire(normalized) ?: return null
        val kg = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        return OttoCommand.SaveWeight(kg)
    }

    private fun parseTextCapture(original: String, normalized: String): OttoCommand? {
        if (!normalized.startsWith("note ") && !normalized.startsWith("task ")) return null
        val kind = normalized.substringBefore(' ')
        val text = original.substringAfter(' ').trim()
        if (text.isBlank()) return null
        return if (kind == "note") {
            OttoCommand.SaveNote(text)
        } else {
            OttoCommand.SaveTask(text)
        }
    }

    companion object {
        private val FOOD_WITH_ENERGY = Regex("""(?i)^food\s+(\d{1,6})$""")
        private val WEIGHT = Regex("""^(?:w|weight)\s+(\d{2,3}(?:[\.,]\d{1,2})?)$""")
    }
}

