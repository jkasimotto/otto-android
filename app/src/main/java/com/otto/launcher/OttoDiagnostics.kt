package com.otto.launcher

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object OttoDiagnostics {
    private const val LOG_TAG = "Otto"
    private const val LOG_DIRECTORY = "diagnostics"
    private const val LOG_FILE_NAME = "otto.log"
    private const val MAX_LOG_FILE_BYTES = 256 * 1024
    private const val DEFAULT_READ_MAX_CHARS = 12_000

    private val fileLock = Any()
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        .withZone(ZoneId.systemDefault())

    fun info(context: Context?, category: String, message: String) {
        log(context, Log.INFO, category, message)
    }

    fun warn(context: Context?, category: String, message: String) {
        log(context, Log.WARN, category, message)
    }

    fun error(
        context: Context?,
        category: String,
        message: String,
        throwable: Throwable? = null
    ) {
        log(context, Log.ERROR, category, message, throwable)
    }

    fun readRecent(context: Context, maxChars: Int = DEFAULT_READ_MAX_CHARS): String {
        val file = logFile(context.applicationContext)
        if (!file.exists()) return ""

        return synchronized(fileLock) {
            runCatching { file.readText() }
                .getOrDefault("")
                .takeLast(maxChars)
        }
    }

    fun clear(context: Context) {
        synchronized(fileLock) {
            runCatching { logFile(context.applicationContext).writeText("") }
        }
        info(context.applicationContext, "Diagnostics", "Local diagnostics log cleared.")
    }

    fun filePath(context: Context): String {
        return logFile(context.applicationContext).absolutePath
    }

    private fun log(
        context: Context?,
        priority: Int,
        category: String,
        message: String,
        throwable: Throwable? = null
    ) {
        val renderedMessage = "[$category] $message"
        when (priority) {
            Log.ERROR -> Log.e(LOG_TAG, renderedMessage, throwable)
            Log.WARN -> Log.w(LOG_TAG, renderedMessage, throwable)
            else -> Log.i(LOG_TAG, renderedMessage, throwable)
        }

        val appContext = context?.applicationContext ?: return
        val timestamp = timestampFormatter.format(Instant.now())
        val line = buildString {
            append(timestamp)
            append(" ")
            append(priorityLabel(priority))
            append("/")
            append(category)
            append(": ")
            append(message)
            throwable?.let {
                append("\n")
                append(renderThrowable(it))
            }
        }
        appendToFile(appContext, line)
    }

    private fun appendToFile(context: Context, line: String) {
        synchronized(fileLock) {
            val file = logFile(context)
            runCatching {
                file.parentFile?.mkdirs()
                file.appendText(line + "\n")
                trimIfNeeded(file)
            }
        }
    }

    private fun trimIfNeeded(file: File) {
        if (!file.exists() || file.length() <= MAX_LOG_FILE_BYTES) return

        val trimmed = runCatching { file.readText().takeLast(MAX_LOG_FILE_BYTES / 2) }
            .getOrNull()
            ?: return
        runCatching { file.writeText(trimmed) }
    }

    private fun logFile(context: Context): File {
        return File(File(context.filesDir, LOG_DIRECTORY), LOG_FILE_NAME)
    }

    private fun priorityLabel(priority: Int): String {
        return when (priority) {
            Log.ERROR -> "E"
            Log.WARN -> "W"
            else -> "I"
        }
    }

    private fun renderThrowable(throwable: Throwable): String {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        return writer.toString().trimEnd()
    }
}
