package com.otto.launcher.data.mode

import android.content.Context
import com.otto.launcher.domain.mode.OttoMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ModeRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val modeState = MutableStateFlow(readMode())

    fun observeBaseMode(): StateFlow<OttoMode> = modeState.asStateFlow()

    fun setMode(mode: OttoMode) {
        if (mode == OttoMode.SLEEP || mode == OttoMode.WIND_DOWN) return
        prefs.edit().putString(KEY_MODE, mode.name).apply()
        modeState.value = mode
    }

    private fun readMode(): OttoMode {
        return prefs.getString(KEY_MODE, null)
            ?.let { runCatching { OttoMode.valueOf(it) }.getOrNull() }
            ?.takeIf { it == OttoMode.OPEN || it == OttoMode.FOCUS }
            ?: OttoMode.OPEN
    }

    companion object {
        private const val PREFS_NAME = "otto_mode"
        private const val KEY_MODE = "base_mode"
    }
}

