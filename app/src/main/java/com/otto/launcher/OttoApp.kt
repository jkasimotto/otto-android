package com.otto.launcher

import android.app.Application
import com.otto.launcher.nag.NagNotifier
import com.otto.launcher.nag.NagPrompt
import com.otto.launcher.nag.NagScheduler
import kotlin.system.exitProcess

class OttoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        installCrashLogging()
        OttoDiagnostics.info(
            this,
            "Process",
            "Application created; ${OttoDiagnostics.processMarker(this)}"
        )
        armNags()
    }

    /** Creates the nag notification channel and schedules the daily asks (idempotent per launch). */
    private fun armNags() {
        NagNotifier(this).ensureChannel()
        NagPrompt.ALL.forEach { NagScheduler.armOpen(this, it) }
    }

    private fun installCrashLogging() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            OttoDiagnostics.error(
                this,
                "Crash",
                "Unhandled exception on thread=${thread.name}; ${OttoDiagnostics.processMarker(this)}",
                throwable
            )
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                exitProcess(2)
            }
        }
    }
}
