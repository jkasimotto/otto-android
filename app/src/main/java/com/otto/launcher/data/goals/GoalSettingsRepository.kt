package com.otto.launcher.data.goals

import android.content.Context
import com.otto.launcher.domain.goals.GoalSettings
import com.otto.launcher.trace.data.GoalSettingsEntity
import com.otto.launcher.trace.data.TraceDatabase
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GoalSettingsRepository(
    context: Context,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    private val dao = TraceDatabase.get(context.applicationContext).traceV2Dao()

    fun observeSettings(): Flow<GoalSettings> {
        return dao.observeGoalSettings(GoalSettingsEntity.DEFAULT_ID)
            .map { it?.toDomain() ?: GoalSettings() }
    }

    suspend fun settings(): GoalSettings {
        return dao.goalSettings(GoalSettingsEntity.DEFAULT_ID)?.toDomain() ?: GoalSettings()
    }

    suspend fun save(settings: GoalSettings) {
        dao.upsertGoalSettings(settings.toEntity(clock))
    }
}

private fun GoalSettingsEntity.toDomain(): GoalSettings {
    return GoalSettings(
        sleepTargetMinutes = sleepTargetMinutes,
        sleepTargetStart = sleepTargetStart,
        sleepTargetWake = sleepTargetWake,
        dailyKjTarget = dailyKjTarget,
        weightGoalKg = weightGoalKg,
        dailyScreenLimitMinutes = dailyScreenLimitMinutes,
        windDownMinutesBeforeSleep = windDownMinutesBeforeSleep,
        hardSleepLock = hardSleepLock
    )
}

private fun GoalSettings.toEntity(clock: Clock): GoalSettingsEntity {
    return GoalSettingsEntity(
        sleepTargetMinutes = sleepTargetMinutes,
        sleepTargetStart = sleepTargetStart,
        sleepTargetWake = sleepTargetWake,
        dailyKjTarget = dailyKjTarget,
        weightGoalKg = weightGoalKg,
        dailyScreenLimitMinutes = dailyScreenLimitMinutes,
        windDownMinutesBeforeSleep = windDownMinutesBeforeSleep,
        hardSleepLock = hardSleepLock,
        updatedAt = clock.instant()
    )
}

