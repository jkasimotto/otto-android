package com.otto.launcher.nag

import android.content.Context

/**
 * Remembers which prompts are currently open (asked but not yet genuinely answered) and how many
 * times we have buzzed about each. Backed by SharedPreferences so the re-nag loop survives process
 * death and reboots; [NagWorker] reads it to decide whether to keep bugging, [NagAnswerActivity]
 * clears it once the judge accepts an answer.
 */
class NagStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("otto_nag", Context.MODE_PRIVATE)

    fun isOpen(promptId: String): Boolean = prefs.getBoolean(openKey(promptId), false)

    fun attempts(promptId: String): Int = prefs.getInt(attemptsKey(promptId), 0)

    /** Opens the prompt for a fresh day and resets the buzz count. */
    fun open(promptId: String) {
        prefs.edit().putBoolean(openKey(promptId), true).putInt(attemptsKey(promptId), 0).apply()
    }

    fun recordAttempt(promptId: String) {
        prefs.edit().putInt(attemptsKey(promptId), attempts(promptId) + 1).apply()
    }

    fun close(promptId: String) {
        prefs.edit().putBoolean(openKey(promptId), false).apply()
    }

    private fun openKey(id: String) = "open_$id"

    private fun attemptsKey(id: String) = "attempts_$id"
}
