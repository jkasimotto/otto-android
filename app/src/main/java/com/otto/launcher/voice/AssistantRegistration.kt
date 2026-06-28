package com.otto.launcher.voice

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat

// Secure-settings keys the assist gesture reads to find the assistant component.
private const val SETTING_ASSISTANT = "assistant"
private const val SETTING_VOICE_INTERACTION = "voice_interaction_service"

/** Flattened component of the quick-capture activity, the value the assistant setting must hold. */
fun Context.ottoAssistantComponent(): String =
    ComponentName(packageName, QuickVoiceCaptureActivity::class.java.name).flattenToString()

fun Context.isOttoAssistant(): Boolean =
    Settings.Secure.getString(contentResolver, SETTING_ASSISTANT) == ottoAssistantComponent()

/**
 * Points the device's assistant slot at Otto's quick-capture activity, so the assist gesture
 * launches a voice note from anywhere, including the lock screen. Writes the secure settings
 * directly (Otto already holds WRITE_SECURE_SETTINGS for greyscale), so no settings UI is needed.
 * Clears voice_interaction_service so the system routes the gesture via ACTION_ASSIST to our
 * activity instead of trying to bind a voice-interaction service we do not provide. Returns true
 * once the assistant setting points at Otto.
 */
fun Context.registerOttoAsAssistant(): Boolean {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_SECURE_SETTINGS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }
    return runCatching {
        Settings.Secure.putString(contentResolver, SETTING_VOICE_INTERACTION, "")
        Settings.Secure.putString(contentResolver, SETTING_ASSISTANT, ottoAssistantComponent())
        isOttoAssistant()
    }.getOrDefault(false)
}
