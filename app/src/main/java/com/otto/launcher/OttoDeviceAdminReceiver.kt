package com.otto.launcher

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class OttoDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        OttoPolicyController.markPolicyDirty(staticPoliciesChanged = true)
        OttoPolicyController.applyPolicies(context, force = true)
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        OttoPolicyController.markPolicyDirty(staticPoliciesChanged = true)
        OttoPolicyController.applyPolicies(context, force = true)
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        OttoPolicyController.markPolicyDirty()
        OttoPolicyController.applyPolicies(context)
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        OttoPolicyController.markPolicyDirty()
        OttoPolicyController.applyPolicies(context)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Disabling device admin removes Otto's app blocking policy."
    }
}
