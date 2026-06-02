package com.otto.launcher.trace.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

object TracePromptPolicy {
    private val sleepPromptWindow = LocalTime.of(5, 0)..LocalTime.of(11, 30)

    fun nextAction(
        now: Instant,
        zoneId: ZoneId,
        today: DailySummary,
        settings: TraceSettingsState,
        sleepEstimate: SleepEstimate?
    ): NextTraceAction {
        val localTime = now.atZone(zoneId).toLocalTime()
        if (settings.sleepEnabled && today.sleepDurationMinutes == null && localTime in sleepPromptWindow) {
            return if (sleepEstimate != null) {
                NextTraceAction(
                    kind = NextTraceActionKind.CONFIRM_SLEEP,
                    title = "Sleep estimate",
                    detail = "${formatClock(sleepEstimate.startAt, zoneId)}-" +
                        "${formatClock(sleepEstimate.endAt, zoneId)} · ${formatDuration(sleepEstimate.durationMinutes)}",
                    primaryLabel = "Save"
                )
            } else {
                NextTraceAction(
                    kind = NextTraceActionKind.LOG_SLEEP,
                    title = "Sleep?",
                    detail = null,
                    primaryLabel = "Add"
                )
            }
        }

        return NextTraceAction(
            kind = NextTraceActionKind.OPEN_CAPTURE,
            title = "Capture",
            detail = null,
            primaryLabel = "Open"
        )
    }

    private fun formatClock(instant: Instant, zoneId: ZoneId): String {
        val value = instant.atZone(zoneId).toLocalTime()
        return "%02d:%02d".format(value.hour, value.minute)
    }

    private fun formatDuration(minutes: Int): String {
        val safeMinutes = minutes.coerceAtLeast(0)
        return "${safeMinutes / 60}h%02d".format(safeMinutes % 60)
    }
}
