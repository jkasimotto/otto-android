package com.otto.launcher

import android.app.Application
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
