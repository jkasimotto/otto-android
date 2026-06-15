package com.otto.launcher.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.otto.launcher.OttoPolicyController
import com.otto.launcher.data.policy.PolicyRuntime

class PolicyRestoreWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        OttoPolicyController.markPolicyDirty(packageStateChanged = true)
        OttoPolicyController.applyPolicies(applicationContext, force = true)
        PolicyRuntime.applyCurrentPolicy(applicationContext)
        return Result.success()
    }
}
