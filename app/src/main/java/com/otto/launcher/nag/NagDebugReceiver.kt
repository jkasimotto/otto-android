package com.otto.launcher.nag

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Testing / control hook. Fires a prompt on demand, or silences every open nag:
 *
 *   adb shell am broadcast -n com.otto.launcher/.nag.NagDebugReceiver
 *   adb shell am broadcast -n com.otto.launcher/.nag.NagDebugReceiver --es promptId meal_plan
 *   adb shell am broadcast -n com.otto.launcher/.nag.NagDebugReceiver --es action silence
 *
 * Silence closes each open prompt, cancels its pending re-nag, and clears its notification. It does
 * not touch the future schedule, so prompts still open again at their next scheduled time.
 */
class NagDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        if (intent.getStringExtra("action") == "silence") {
            val store = NagStore(app)
            val notifier = NagNotifier(app)
            NagPrompt.ALL.forEach { prompt ->
                store.close(prompt.id)
                NagScheduler.cancelRenag(app, prompt.id)
                notifier.cancel(prompt.id)
            }
            return
        }
        val prompt = intent.getStringExtra("promptId")?.let { NagPrompt.byId(it) }
            ?: NagPrompt.SLEEP_WAKE
        NagScheduler.fireSoon(app, prompt, seconds = 3)
    }
}
