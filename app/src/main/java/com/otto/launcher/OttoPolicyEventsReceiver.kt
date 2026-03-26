package com.otto.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

class OttoPolicyEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val action = intent.action.orEmpty()
        OttoDiagnostics.info(context.applicationContext, "PolicyReceiver", "Received broadcast: $action")
        val packageStateChanged = action.startsWith("android.intent.action.PACKAGE_")
        val staticPoliciesChanged = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        OttoPolicyController.markPolicyDirty(
            packageStateChanged = packageStateChanged,
            staticPoliciesChanged = staticPoliciesChanged
        )
        CoroutineScope(Dispatchers.IO).launch {
            try {
                OttoPolicyController.applyPolicies(context)
                OttoDiagnostics.info(context.applicationContext, "PolicyReceiver", "Policies applied for broadcast: $action")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
