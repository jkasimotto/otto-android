package com.otto.launcher.quest

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.otto.launcher.quest.data.QuestRepository
import com.otto.launcher.quest.ui.QuestActivity
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object QuestKickoffScheduler {
    private const val OPEN = "quest-kickoff-open"
    private const val RENAG = "quest-kickoff-renag"
    fun arm(context: Context) = enqueue(context, OPEN, delayTo(7, 0), "open")
    fun renag(context: Context) = enqueue(context, RENAG, Duration.ofMinutes(20), "renag")
    fun cancelRenag(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(RENAG)
    private fun enqueue(context: Context, name: String, delay: Duration, mode: String) {
        val request = OneTimeWorkRequestBuilder<QuestKickoffWorker>().setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("mode" to mode)).build()
        WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
    }
    private fun delayTo(hour: Int, minute: Int): Duration {
        val now = LocalDateTime.now(ZoneId.systemDefault()); var next = now.toLocalDate().atTime(LocalTime.of(hour, minute))
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }
}

class QuestKickoffWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val mode = inputData.getString("mode") ?: "open"
        if (mode == "open") QuestKickoffScheduler.arm(applicationContext)
        val now = LocalDateTime.now()
        if (now.toLocalTime() !in LocalTime.of(7, 0)..LocalTime.of(10, 0)) return Result.success()
        val repo = QuestRepository(applicationContext)
        val morning = now.toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (repo.urgentOpenCount() == 0 || repo.engagedSince(morning)) { QuestKickoffScheduler.cancelRenag(applicationContext); return Result.success() }
        QuestKickoffNotifier(applicationContext).post()
        runCatching { applicationContext.startActivity(Intent(applicationContext, QuestActivity::class.java).putExtra(QuestActivity.EXTRA_URGENT, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        QuestKickoffScheduler.renag(applicationContext)
        return Result.success()
    }
}

class QuestKickoffNotifier(context: Context) {
    private val app = context.applicationContext
    fun ensureChannel() { app.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Morning quests", NotificationManager.IMPORTANCE_HIGH)) }
    fun post() {
        ensureChannel()
        val intent = Intent(app, QuestActivity::class.java).putExtra(QuestActivity.EXTRA_URGENT, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pending = PendingIntent.getActivity(app, 9127, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(app, CHANNEL).setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("A quest is waiting").setContentText("Handle one urgent thing this morning.")
            .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pending).setFullScreenIntent(pending, true).setVibrate(longArrayOf(0, 250, 150, 250)).build()
        runCatching { NotificationManagerCompat.from(app).notify(9127, notification) }
    }
    companion object { private const val CHANNEL = "otto_quest_kickoff" }
}
