package com.otto.launcher.domain.goals

import java.time.LocalTime

data class GoalSettings(
    val sleepTargetMinutes: Int = 8 * 60,
    val sleepTargetStart: LocalTime? = null,
    val sleepTargetWake: LocalTime? = null,
    val dailyKjTarget: Int? = null,
    val weightGoalKg: Double? = null,
    val dailyScreenLimitMinutes: Int? = null,
    val windDownMinutesBeforeSleep: Int = 60,
    val hardSleepLock: Boolean = false
)

