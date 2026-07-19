package com.otto.launcher.nag

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Owns the timing of a nag through WorkManager, which persists jobs across process death and reboots.
 * Three job kinds share one worker: OPEN fires when the prompt is due (daily or on its weekday), opens
 * it, and re-arms the next occurrence; RENAG re-buzzes while it stays unanswered; GIVE_UP closes an
 * unanswered prompt at its deadline so it stops nagging until it next opens. Each is unique per prompt
 * so re-scheduling never stacks duplicates.
 */
object NagScheduler {
    const val KEY_PROMPT_ID = "promptId"
    const val KEY_MODE = "mode"
    const val MODE_OPEN = "open"
    const val MODE_RENAG = "renag"
    const val MODE_GIVE_UP = "give_up"

    /** Schedules the next time the prompt opens (daily, or on its weekday). */
    fun armOpen(context: Context, prompt: NagPrompt) {
        enqueue(
            context, prompt.id, MODE_OPEN, openName(prompt.id),
            durationUntilNext(prompt.dayOfWeek, prompt.hour, prompt.minute),
        )
    }

    fun scheduleRenag(context: Context, prompt: NagPrompt) {
        enqueue(
            context, prompt.id, MODE_RENAG, renagName(prompt.id),
            Duration.ofMinutes(prompt.renagMinutes),
        )
    }

    /** Schedules the deadline after which an unanswered prompt gives up until it next opens. */
    fun scheduleGiveUp(context: Context, prompt: NagPrompt) {
        val giveUp = prompt.giveUp ?: return
        enqueue(
            context, prompt.id, MODE_GIVE_UP, giveUpName(prompt.id),
            durationUntilNext(giveUp.dayOfWeek, giveUp.hour, giveUp.minute),
        )
    }

    fun cancelRenag(context: Context, promptId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(renagName(promptId))
    }

    /** Testing hook: open a prompt shortly instead of waiting for its scheduled time. */
    fun fireSoon(context: Context, prompt: NagPrompt, seconds: Long = 5) {
        enqueue(context, prompt.id, MODE_OPEN, openName(prompt.id), Duration.ofSeconds(seconds))
    }

    private fun enqueue(context: Context, promptId: String, mode: String, uniqueName: String, delay: Duration) {
        val request = OneTimeWorkRequestBuilder<NagWorker>()
            .setInitialDelay(delay)
            .setInputData(
                Data.Builder()
                    .putString(KEY_PROMPT_ID, promptId)
                    .putString(KEY_MODE, mode)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)
    }

    /** Delay until the next [hour]:[minute], either today/tomorrow (daily) or on [dayOfWeek] (weekly). */
    private fun durationUntilNext(dayOfWeek: DayOfWeek?, hour: Int, minute: Int): Duration {
        val now = LocalDateTime.now(ZoneId.systemDefault())
        val time = LocalTime.of(hour, minute)
        var next = if (dayOfWeek == null) {
            now.toLocalDate().atTime(time)
        } else {
            now.toLocalDate().with(TemporalAdjusters.nextOrSame(dayOfWeek)).atTime(time)
        }
        if (!next.isAfter(now)) {
            next = if (dayOfWeek == null) next.plusDays(1) else next.plusWeeks(1)
        }
        return Duration.between(now, next)
    }

    private fun openName(id: String) = "nag-open-$id"

    private fun renagName(id: String) = "nag-renag-$id"

    private fun giveUpName(id: String) = "nag-giveup-$id"
}
