package com.otto.launcher.domain.command

import com.otto.launcher.domain.mode.OttoMode
import com.otto.launcher.domain.time.TimeCategoryIds
import com.otto.launcher.domain.time.TimeMode
import java.util.Locale

class CommandParser {
    fun parse(input: String): OttoCommand? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        val normalized = trimmed.lowercase(Locale.US)

        parseFood(trimmed, normalized)?.let { return it }
        parseWeight(normalized)?.let { return it }
        parseTimeBlockStart(normalized)?.let { return it }
        parseTimeDuration(normalized)?.let { return it }
        parseTextCapture(trimmed, normalized)?.let { return it }

        return when (normalized) {
            "drink" -> OttoCommand.CaptureDrink
            "sleep" -> OttoCommand.StartSleep
            "wake" -> OttoCommand.EndSleep
            "today" -> OttoCommand.OpenToday
            "review" -> OttoCommand.OpenReview
            "week" -> OttoCommand.OpenWeek
            "budget" -> OttoCommand.OpenTimeBudget
            "time" -> OttoCommand.OpenTimeReview
            "done" -> OttoCommand.FinishTimeBlock
            "move", "walk", "gym", "run", "stretch" -> OttoCommand.StartTimeBlock(TimeMode.MOVEMENT)
            "social", "people" -> OttoCommand.StartTimeBlock(TimeMode.RELATIONSHIP)
            "rest" -> OttoCommand.StartTimeBlock(TimeMode.REST)
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

    private fun parseTimeBlockStart(normalized: String): OttoCommand? {
        val value = normalized.removePrefix("start ").takeIf { it != normalized } ?: return null
        return when (value) {
            "focus", "work", "focused work" -> OttoCommand.StartTimeBlock(TimeMode.FOCUS_WORK)
            "social", "people", "relationship", "relationships" -> OttoCommand.StartTimeBlock(TimeMode.RELATIONSHIP)
            "movement", "move", "walk", "gym", "run", "stretch" -> OttoCommand.StartTimeBlock(TimeMode.MOVEMENT)
            "rest", "recover", "recovery" -> OttoCommand.StartTimeBlock(TimeMode.REST)
            "admin" -> OttoCommand.StartTimeCategoryBlock(TimeCategoryIds.ADMIN, "admin")
            "commute" -> OttoCommand.StartTimeCategoryBlock(TimeCategoryIds.COMMUTE, "commute")
            else -> null
        }
    }

    private fun parseTimeDuration(normalized: String): OttoCommand? {
        val match = TIME_DURATION.matchEntire(normalized) ?: return null
        val label = match.groupValues[1]
        val minutes = match.groupValues[2].toIntOrNull()?.takeIf { it in 1..(24 * 60) } ?: return null
        val categoryId = when (label) {
            "walk", "gym", "run", "stretch", "move", "movement" -> TimeCategoryIds.MOVEMENT
            "social", "people", "dinner", "relationship", "relationships" -> TimeCategoryIds.RELATIONSHIPS
            "admin" -> TimeCategoryIds.ADMIN
            "commute" -> TimeCategoryIds.COMMUTE
            "rest" -> TimeCategoryIds.REST
            "drift" -> TimeCategoryIds.DIGITAL_DRIFT
            "focus", "work" -> TimeCategoryIds.FOCUSED_WORK
            "learn", "learning" -> TimeCategoryIds.LEARNING
            else -> return null
        }
        return OttoCommand.SaveTimeBlockDuration(categoryId, minutes, label)
    }

    companion object {
        private val FOOD_WITH_ENERGY = Regex("""(?i)^food\s+(\d{1,6})$""")
        private val WEIGHT = Regex("""^(?:w|weight)\s+(\d{2,3}(?:[\.,]\d{1,2})?)$""")
        private val TIME_DURATION = Regex("""^([a-z ]+)\s+(\d{1,4})$""")
    }
}
