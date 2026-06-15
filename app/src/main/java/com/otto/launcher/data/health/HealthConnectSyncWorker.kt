package com.otto.launcher.data.health

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class HealthConnectSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return HealthConnectRepository(applicationContext)
            .syncReviewedRecords()
            .fold(
                onSuccess = { Result.success() },
                onFailure = { Result.retry() }
            )
    }
}

