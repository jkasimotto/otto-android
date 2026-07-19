package com.otto.launcher.nag

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Posts the nag as a high-importance, vibrating notification. The high importance is deliberate: it
 * is what makes Wear OS bridge the notification to a paired watch and buzz the wrist, so this single
 * call is the entire watch alert surface for v0 (no watch app needed). Tapping it opens
 * [NagAnswerActivity] to record a spoken answer.
 */
class NagNotifier(context: Context) {
    private val appContext = context.applicationContext

    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Otto nags",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Timed questions Otto keeps asking until you answer"
            enableVibration(true)
            vibrationPattern = VIBRATION
        }
        appContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun post(prompt: NagPrompt, attempt: Int) {
        val tapIntent = Intent(appContext, NagAnswerActivity::class.java).apply {
            putExtra(NagAnswerActivity.EXTRA_PROMPT_ID, prompt.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pending = PendingIntent.getActivity(
            appContext,
            prompt.id.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = if (attempt <= 1) prompt.question else prompt.question + "\n\n(still waiting)"
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Otto needs an answer")
            .setContentText(prompt.question)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pending)
            // Launches the alarm screen over the lockscreen and whatever is on screen, so the nag
            // goes off on its own rather than waiting for a tap.
            .setFullScreenIntent(pending, true)
            .setVibrate(VIBRATION)
            .build()
        // notify() throws SecurityException if POST_NOTIFICATIONS was never granted; swallow it so a
        // missing permission just means no buzz rather than a crashed worker.
        runCatching {
            NotificationManagerCompat.from(appContext).notify(prompt.id.hashCode(), notification)
        }
    }

    fun cancel(promptId: String) {
        NotificationManagerCompat.from(appContext).cancel(promptId.hashCode())
    }

    companion object {
        const val CHANNEL_ID = "otto_nag"
        private val VIBRATION = longArrayOf(0, 250, 150, 250)
    }
}
