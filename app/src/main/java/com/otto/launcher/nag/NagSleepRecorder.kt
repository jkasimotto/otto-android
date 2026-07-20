package com.otto.launcher.nag

import android.content.Context
import com.otto.launcher.core.llm.GroqClient
import com.otto.launcher.trace.data.TraceV2Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Turns a spoken sleep answer into a real sleep session on the home-screen graph. [NagJudge] only
 * decides whether the reply counted; this pulls the actual bedtime and wake time out of it and writes
 * them to the same `sleep_session` store the weekly chart reads (`TraceV2Repository`). The date rules
 * mirror `sleepNightDate`: a bedtime from midday onward belongs to that evening's night, an
 * after-midnight bedtime belongs to the night that just ended, so the bar lands on last night's row.
 */
object NagSleepRecorder {
    /** Extracts the times and records the session. Returns true only if a session was written. */
    suspend fun record(context: Context, transcript: String): Boolean = withContext(Dispatchers.IO) {
        val times = extractTimes(transcript) ?: return@withContext false
        val zone = ZoneId.systemDefault()
        val today = ZonedDateTime.now(zone).toLocalDate()
        val bedDate = if (!times.sleep.isBefore(LocalTime.NOON)) today.minusDays(1) else today
        val bedAt = bedDate.atTime(times.sleep).atZone(zone)
        var wakeAt = today.atTime(times.wake).atZone(zone)
        if (!wakeAt.isAfter(bedAt)) wakeAt = wakeAt.plusDays(1)
        TraceV2Repository(context.applicationContext)
            .recordSleepSession(bedAt.toInstant(), wakeAt.toInstant())
        true
    }

    private data class Times(val sleep: LocalTime, val wake: LocalTime)

    private suspend fun extractTimes(transcript: String): Times? {
        val system = "Extract the bedtime and the wake time from a spoken sleep report. " +
            "Reply ONLY with JSON: {\"sleep\":\"HH:MM\",\"wake\":\"HH:MM\"} using 24-hour local " +
            "clock times. Use null for a field that is not stated."
        val parsed = GroqClient.chatJson(system, transcript).getOrNull() ?: return null
        val sleep = parseTime(parsed.optString("sleep")) ?: return null
        val wake = parseTime(parsed.optString("wake")) ?: return null
        return Times(sleep, wake)
    }

    private fun parseTime(raw: String?): LocalTime? {
        if (raw.isNullOrBlank() || raw.equals("null", ignoreCase = true)) return null
        return runCatching { LocalTime.parse(raw) }
            .recoverCatching { LocalTime.parse(raw, java.time.format.DateTimeFormatter.ofPattern("H:mm")) }
            .getOrNull()
    }

}
