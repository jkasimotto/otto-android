package com.otto.launcher.nag

import java.time.DayOfWeek

/**
 * A question Otto is allowed to nag you about until you actually answer it. [judgeHint] is the rule an
 * LLM uses to decide whether a spoken reply genuinely answered, so a vague reply is treated as
 * UNANSWERED and the nag keeps firing.
 *
 * Scheduling: [dayOfWeek] null means the prompt opens every day at [hour]:[minute]; a weekday means it
 * opens weekly on that day. [giveUp], when set, is the deadline after which an unanswered prompt stops
 * nagging until it next opens (so the weekend meal plan does not keep alarming into Monday).
 */
data class NagPrompt(
    val id: String,
    val question: String,
    val judgeHint: String,
    /** Weekday the prompt opens, or null to open every day. */
    val dayOfWeek: DayOfWeek?,
    /** Local hour the prompt opens. */
    val hour: Int,
    /** Local minute the prompt opens. */
    val minute: Int,
    /** How long to wait before buzzing again while the prompt is still unanswered. */
    val renagMinutes: Long,
    /** Deadline after which an unanswered prompt gives up until it next opens, or null to nag forever. */
    val giveUp: GiveUp? = null,
    /** When true, an accepted answer is parsed into a sleep session for the home-screen graph. */
    val recordsSleepSession: Boolean = false,
) {
    /** A weekday-and-time deadline for a prompt to stop nagging if still unanswered. */
    data class GiveUp(val dayOfWeek: DayOfWeek, val hour: Int, val minute: Int)

    companion object {
        val SLEEP_WAKE = NagPrompt(
            id = "sleep_wake",
            question = "What time did you fall asleep, and what time did you wake up?",
            judgeHint = "A valid answer states BOTH a sleep time and a wake time. Clock times " +
                "(\"11:30\", \"7am\") or clear relative times (\"midnight\", \"half six\") both count. " +
                "If either the sleep time or the wake time is missing or vague, it is NOT answered.",
            dayOfWeek = null,
            hour = 9,
            minute = 0,
            renagMinutes = 20,
            recordsSleepSession = true,
        )

        val MEAL_PLAN = NagPrompt(
            id = "meal_plan",
            question = "Name at least one meal you'll actually cook this week.",
            judgeHint = "A valid answer names at least one specific meal or dish the speaker commits " +
                "to cooking/making themselves in the coming week (e.g. \"spaghetti bolognese\", " +
                "\"I'll make a stir fry Tuesday\"). Vague intentions like \"eat healthy\" or \"cook " +
                "more\" do NOT count; it must be a concrete, named meal they will make.",
            dayOfWeek = DayOfWeek.SATURDAY,
            hour = 9,
            minute = 0,
            renagMinutes = 150,
            giveUp = GiveUp(DayOfWeek.SUNDAY, 21, 0),
        )

        val ALL = listOf(SLEEP_WAKE, MEAL_PLAN)

        fun byId(id: String): NagPrompt? = ALL.firstOrNull { it.id == id }
    }
}
