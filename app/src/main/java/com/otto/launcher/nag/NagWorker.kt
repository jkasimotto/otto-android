package com.otto.launcher.nag

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Fires a nag. OPEN opens the prompt for its cycle, re-arms the next occurrence, and sets the give-up
 * deadline; RENAG re-buzzes only while the prompt stays unanswered; GIVE_UP closes an unanswered
 * prompt at its deadline so it stops nagging until it next opens. OPEN and RENAG both post the
 * notification and launch the alarm; that is the loop that keeps bugging you until [NagAnswerActivity]
 * closes the prompt.
 */
class NagWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val promptId = inputData.getString(NagScheduler.KEY_PROMPT_ID) ?: return Result.success()
        val mode = inputData.getString(NagScheduler.KEY_MODE) ?: NagScheduler.MODE_RENAG
        val prompt = NagPrompt.byId(promptId) ?: return Result.success()
        val store = NagStore(applicationContext)

        when (mode) {
            NagScheduler.MODE_OPEN -> {
                store.open(promptId)
                NagScheduler.armOpen(applicationContext, prompt) // queue the next occurrence
                NagScheduler.scheduleGiveUp(applicationContext, prompt) // no-op if the prompt never gives up
            }
            NagScheduler.MODE_GIVE_UP -> {
                if (store.isOpen(promptId)) {
                    store.close(promptId)
                    NagScheduler.cancelRenag(applicationContext, promptId)
                    NagNotifier(applicationContext).cancel(promptId)
                }
                return Result.success()
            }
            // MODE_RENAG: nothing to set up, just fall through to the fire-if-open logic below.
        }

        if (!store.isOpen(promptId)) {
            return Result.success() // already answered; let the loop die
        }

        store.recordAttempt(promptId)
        NagNotifier(applicationContext).apply { ensureChannel() }
            .post(prompt, store.attempts(promptId))
        // The notification's full-screen intent only auto-launches when the screen is locked/off; when
        // the phone is awake and in use, launch the alarm directly (permitted as the foreground home /
        // Device Owner app) so the nag always goes off instead of sitting as a heads-up.
        launchAlarm(promptId)
        NagScheduler.scheduleRenag(applicationContext, prompt)
        return Result.success()
    }

    private fun launchAlarm(promptId: String) {
        val intent = Intent(applicationContext, NagAnswerActivity::class.java).apply {
            putExtra(NagAnswerActivity.EXTRA_PROMPT_ID, promptId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { applicationContext.startActivity(intent) }
    }
}
