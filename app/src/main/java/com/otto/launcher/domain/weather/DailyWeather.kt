package com.otto.launcher.domain.weather

import java.time.LocalDate

/** One day of forecast: the date and a compact symbol derived from the WMO weather code. */
data class DailyWeather(
    val date: LocalDate,
    val symbol: String
)

/**
 * Maps an Open-Meteo WMO weather code to a single-glyph symbol for the home chart.
 * See https://open-meteo.com/en/docs for the code table.
 */
fun weatherSymbolForCode(code: Int): String = when (code) {
    0 -> "☀"
    1, 2 -> "⛅"
    3 -> "☁"
    45, 48 -> "🌫"
    51, 53, 55, 56, 57 -> "🌦"
    61, 63, 65, 66, 67, 80, 81, 82 -> "🌧"
    71, 73, 75, 77, 85, 86 -> "🌨"
    95, 96, 99 -> "⛈"
    else -> "·"
}
