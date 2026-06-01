package com.otto.launcher.trace.data

import android.content.Context
import com.otto.launcher.trace.domain.MealSlot
import com.otto.launcher.trace.domain.SleepEstimate
import com.otto.launcher.trace.domain.TraceCategory
import com.otto.launcher.trace.domain.TraceRules
import java.time.Instant
import java.time.ZoneId

class TracePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isCategoryEnabled(category: TraceCategory): Boolean {
        return prefs.getBoolean(enabledKey(category), true)
    }

    fun setCategoryEnabled(category: TraceCategory, enabled: Boolean) {
        prefs.edit().putBoolean(enabledKey(category), enabled).apply()
    }

    fun ignoredMealWindow(slot: MealSlot, now: Instant) {
        val countKey = ignoredCountKey(slot)
        val newCount = prefs.getInt(countKey, 0) + 1
        val suppressUntil = if (newCount >= 2) {
            now.plusSeconds(3 * 24 * 60 * 60)
        } else {
            now.plusSeconds(4 * 60 * 60)
        }
        prefs.edit()
            .putInt(countKey, newCount)
            .putLong(suppressKey(slot), suppressUntil.toEpochMilli())
            .apply()
    }

    fun clearMealWindowIgnore(slot: MealSlot) {
        prefs.edit()
            .remove(ignoredCountKey(slot))
            .remove(suppressKey(slot))
            .apply()
    }

    fun isMealWindowSuppressed(slot: MealSlot, now: Instant): Boolean {
        val suppressUntil = prefs.getLong(suppressKey(slot), 0L)
        return suppressUntil > now.toEpochMilli()
    }

    fun noteLauncherVisible(now: Instant, zoneId: ZoneId) {
        val previous = lastLauncherSeenAt()
        val estimate = TraceRules.candidateSleepEstimate(previous, now, zoneId)
        val edits = prefs.edit()
        if (estimate != null) {
            edits
                .putLong(KEY_SLEEP_ESTIMATE_START, estimate.startAt.toEpochMilli())
                .putLong(KEY_SLEEP_ESTIMATE_END, estimate.endAt.toEpochMilli())
        }
        edits.putLong(KEY_LAST_LAUNCHER_SEEN, now.toEpochMilli()).apply()
    }

    fun sleepEstimate(): SleepEstimate? {
        val start = prefs.getLong(KEY_SLEEP_ESTIMATE_START, 0L)
        val end = prefs.getLong(KEY_SLEEP_ESTIMATE_END, 0L)
        if (start <= 0L || end <= start) return null
        return SleepEstimate(Instant.ofEpochMilli(start), Instant.ofEpochMilli(end))
    }

    fun clearSleepEstimate() {
        prefs.edit()
            .remove(KEY_SLEEP_ESTIMATE_START)
            .remove(KEY_SLEEP_ESTIMATE_END)
            .apply()
    }

    private fun lastLauncherSeenAt(): Instant? {
        val value = prefs.getLong(KEY_LAST_LAUNCHER_SEEN, 0L)
        return if (value > 0L) Instant.ofEpochMilli(value) else null
    }

    private fun enabledKey(category: TraceCategory): String = "category_enabled_${category.name}"

    private fun suppressKey(slot: MealSlot): String = "meal_suppress_until_${slot.name}"

    private fun ignoredCountKey(slot: MealSlot): String = "meal_ignore_count_${slot.name}"

    companion object {
        private const val PREFS_NAME = "trace_preferences"
        private const val KEY_LAST_LAUNCHER_SEEN = "last_launcher_seen"
        private const val KEY_SLEEP_ESTIMATE_START = "sleep_estimate_start"
        private const val KEY_SLEEP_ESTIMATE_END = "sleep_estimate_end"
    }
}
