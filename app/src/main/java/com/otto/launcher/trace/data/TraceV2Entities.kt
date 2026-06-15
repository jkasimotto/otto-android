package com.otto.launcher.trace.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.otto.launcher.domain.policy.AppTier
import com.otto.launcher.domain.trace.InboxKind
import com.otto.launcher.domain.trace.InboxState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "app_policy")
data class AppPolicyEntity(
    @PrimaryKey val packageName: String,
    val tier: AppTier,
    val hiddenByDefault: Boolean,
    val suspendedByDefault: Boolean,
    val challengeRequired: Boolean,
    val reasonRequired: Boolean,
    val defaultTimeboxMinutes: Int?,
    val dailyLimitMinutes: Int?,
    val workWindowJson: String?,
    val updatedAt: Instant
)

@Entity(
    tableName = "app_session",
    indices = [
        Index("packageName"),
        Index("startedAt"),
        Index("endedAt")
    ]
)
data class AppSessionEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val tier: AppTier,
    val startedAt: Instant,
    val endedAt: Instant?,
    val launchReason: String?,
    val gateType: String?,
    val timeboxMinutes: Int?
)

@Entity(
    tableName = "goal_settings"
)
data class GoalSettingsEntity(
    @PrimaryKey val id: String = DEFAULT_ID,
    val sleepTargetMinutes: Int,
    val sleepTargetStart: LocalTime?,
    val sleepTargetWake: LocalTime?,
    val dailyKjTarget: Int?,
    val weightGoalKg: Double?,
    val dailyScreenLimitMinutes: Int?,
    val windDownMinutesBeforeSleep: Int,
    val hardSleepLock: Boolean,
    val updatedAt: Instant
) {
    companion object {
        const val DEFAULT_ID = "default"
    }
}

@Entity(
    tableName = "food_entry",
    indices = [
        Index("capturedAt"),
        Index("reviewedAt")
    ]
)
data class FoodEntryEntity(
    @PrimaryKey val id: String,
    val capturedAt: Instant,
    val photoUri: String?,
    val thumbnailUri: String?,
    val energyKj: Int?,
    val mealType: String?,
    val reviewedAt: Instant?,
    val source: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

@Entity(
    tableName = "drink_entry",
    indices = [
        Index("capturedAt")
    ]
)
data class DrinkEntryEntity(
    @PrimaryKey val id: String,
    val capturedAt: Instant,
    val photoUri: String?,
    val thumbnailUri: String?,
    val amountMl: Int?,
    val source: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

@Entity(
    tableName = "weight_entry",
    indices = [
        Index("measuredAt")
    ]
)
data class WeightEntryEntity(
    @PrimaryKey val id: String,
    val measuredAt: Instant,
    val kg: Double,
    val source: String,
    val healthConnectId: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)

@Entity(
    tableName = "sleep_session",
    indices = [
        Index("startAt"),
        Index("endAt")
    ]
)
data class V2SleepSessionEntity(
    @PrimaryKey val id: String,
    val startAt: Instant,
    val endAt: Instant?,
    val source: String,
    val targetStartLocalTime: LocalTime?,
    val targetWakeLocalTime: LocalTime?,
    val healthConnectId: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)

@Entity(
    tableName = "inbox_item",
    indices = [
        Index("kind"),
        Index("state"),
        Index("createdAt")
    ]
)
data class InboxItemEntity(
    @PrimaryKey val id: String,
    val kind: InboxKind,
    val text: String,
    val audioUri: String?,
    val transcriptConfidence: Float?,
    val state: InboxState,
    val createdAt: Instant,
    val reviewedAt: Instant?
)

@Entity(
    tableName = "health_connect_mapping",
    indices = [
        Index("localType")
    ]
)
data class HealthConnectMappingEntity(
    @PrimaryKey val localId: String,
    val localType: String,
    val healthConnectRecordId: String?,
    val clientRecordVersion: Long,
    val lastSyncedAt: Instant?
)

@Entity(tableName = "daily_local_summary")
data class DailyLocalSummaryEntity(
    @PrimaryKey val date: LocalDate,
    val sleepMinutes: Int?,
    val latestWeightKg: Double?,
    val foodPhotoCount: Int,
    val foodEnergyKj: Int?,
    val unresolvedFoodCount: Int,
    val totalPhoneMinutes: Int?,
    val distractingPhoneMinutes: Int?,
    val updatedAt: Instant
)

