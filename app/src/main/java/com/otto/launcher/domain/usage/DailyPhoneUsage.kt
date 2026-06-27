package com.otto.launcher.domain.usage

import java.time.LocalDate

/** Total phone foreground time for a single calendar day, used by the weekly phone-usage chart. */
data class DailyPhoneUsage(
    val date: LocalDate,
    val totalMinutes: Int
)
