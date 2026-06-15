package com.otto.launcher.data.health

import android.content.Context

class HealthConnectRepository(private val context: Context) {
    fun isAvailable(): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(HEALTH_CONNECT_PACKAGE, 0)
            true
        }.getOrDefault(false)
    }

    suspend fun syncReviewedRecords(): Result<Unit> {
        return Result.success(Unit)
    }

    companion object {
        private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
    }
}

