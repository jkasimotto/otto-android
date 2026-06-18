package com.otto.launcher.trace.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.otto.launcher.domain.policy.AppTier
import com.otto.launcher.domain.time.BudgetPeriod
import com.otto.launcher.domain.trace.InboxKind
import com.otto.launcher.domain.trace.InboxState
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface TraceV2Dao {
    @Query("SELECT * FROM app_policy ORDER BY packageName")
    fun observeAppPolicies(): Flow<List<AppPolicyEntity>>

    @Query("SELECT * FROM app_policy")
    suspend fun appPolicies(): List<AppPolicyEntity>

    @Query("SELECT * FROM app_policy WHERE packageName = :packageName LIMIT 1")
    suspend fun appPolicy(packageName: String): AppPolicyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppPolicy(policy: AppPolicyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppPolicies(policies: List<AppPolicyEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppSession(session: AppSessionEntity)

    @Query(
        """
        UPDATE app_session
        SET endedAt = :endedAt
        WHERE packageName = :packageName
          AND endedAt IS NULL
        """
    )
    suspend fun endOpenSessions(packageName: String, endedAt: Instant)

    @Query(
        """
        SELECT * FROM app_session
        WHERE endedAt IS NULL
        ORDER BY startedAt DESC
        """
    )
    suspend fun openAppSessions(): List<AppSessionEntity>

    @Query("SELECT * FROM goal_settings WHERE id = :id LIMIT 1")
    fun observeGoalSettings(id: String): Flow<GoalSettingsEntity?>

    @Query("SELECT * FROM goal_settings WHERE id = :id LIMIT 1")
    suspend fun goalSettings(id: String): GoalSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoalSettings(settings: GoalSettingsEntity)

    @Query(
        """
        SELECT * FROM food_entry
        WHERE capturedAt >= :start
          AND capturedAt < :end
        ORDER BY capturedAt ASC
        """
    )
    fun observeFoodEntriesBetween(start: Instant, end: Instant): Flow<List<FoodEntryEntity>>

    @Query(
        """
        SELECT * FROM food_entry
        WHERE capturedAt >= :start
          AND capturedAt < :end
        ORDER BY capturedAt ASC
        """
    )
    suspend fun foodEntriesBetween(start: Instant, end: Instant): List<FoodEntryEntity>

    @Query(
        """
        SELECT * FROM food_entry
        WHERE energyKj IS NULL
        ORDER BY capturedAt ASC
        """
    )
    fun observeUnresolvedFoodEntries(): Flow<List<FoodEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFoodEntry(entry: FoodEntryEntity)

    @Update
    suspend fun updateFoodEntry(entry: FoodEntryEntity)

    @Query("SELECT * FROM food_entry WHERE id = :id LIMIT 1")
    suspend fun foodEntry(id: String): FoodEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDrinkEntry(entry: DrinkEntryEntity)

    @Query(
        """
        SELECT * FROM weight_entry
        WHERE measuredAt >= :start
          AND measuredAt < :end
        ORDER BY measuredAt ASC
        """
    )
    fun observeWeightEntriesBetween(start: Instant, end: Instant): Flow<List<WeightEntryEntity>>

    @Query(
        """
        SELECT * FROM weight_entry
        WHERE measuredAt >= :start
          AND measuredAt < :end
        ORDER BY measuredAt ASC
        """
    )
    suspend fun weightEntriesBetween(start: Instant, end: Instant): List<WeightEntryEntity>

    @Query("SELECT * FROM weight_entry ORDER BY measuredAt DESC LIMIT 1")
    suspend fun latestWeightEntry(): WeightEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeightEntry(entry: WeightEntryEntity)

    @Query(
        """
        SELECT * FROM sleep_session
        WHERE startAt < :end
          AND (endAt IS NULL OR endAt >= :start)
        ORDER BY startAt ASC
        """
    )
    fun observeSleepSessionsOverlapping(start: Instant, end: Instant): Flow<List<V2SleepSessionEntity>>

    @Query(
        """
        SELECT * FROM sleep_session
        WHERE startAt < :end
          AND (endAt IS NULL OR endAt >= :start)
        ORDER BY startAt ASC
        """
    )
    suspend fun sleepSessionsOverlapping(start: Instant, end: Instant): List<V2SleepSessionEntity>

    @Query("SELECT * FROM sleep_session WHERE endAt IS NULL ORDER BY startAt DESC LIMIT 1")
    fun observeOpenSleepSession(): Flow<V2SleepSessionEntity?>

    @Query("SELECT * FROM sleep_session WHERE endAt IS NULL ORDER BY startAt DESC LIMIT 1")
    suspend fun openSleepSession(): V2SleepSessionEntity?

    @Query("SELECT * FROM sleep_session WHERE id = :id")
    suspend fun sleepSession(id: String): V2SleepSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSleepSession(session: V2SleepSessionEntity)

    @Query(
        """
        UPDATE sleep_session
        SET endAt = :endAt,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun closeSleepSession(id: String, endAt: Instant, updatedAt: Instant)

    @Query(
        """
        SELECT * FROM inbox_item
        WHERE state = :state
        ORDER BY createdAt ASC
        """
    )
    fun observeInboxItems(state: InboxState): Flow<List<InboxItemEntity>>

    @Query(
        """
        SELECT * FROM inbox_item
        WHERE createdAt >= :start
          AND createdAt < :end
        ORDER BY createdAt ASC
        """
    )
    fun observeInboxItemsBetween(start: Instant, end: Instant): Flow<List<InboxItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInboxItem(item: InboxItemEntity)

    @Query(
        """
        UPDATE inbox_item
        SET state = :state,
            reviewedAt = :reviewedAt
        WHERE id = :id
        """
    )
    suspend fun updateInboxState(id: String, state: InboxState, reviewedAt: Instant?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHealthConnectMapping(mapping: HealthConnectMappingEntity)

    @Query("SELECT * FROM health_connect_mapping WHERE localType = :localType")
    suspend fun healthConnectMappings(localType: String): List<HealthConnectMappingEntity>

    @Query("SELECT COUNT(*) FROM food_entry")
    suspend fun foodEntryCount(): Int

    @Query("SELECT COUNT(*) FROM weight_entry")
    suspend fun weightEntryCount(): Int

    @Query("SELECT COUNT(*) FROM sleep_session")
    suspend fun sleepSessionCount(): Int

    @Query("SELECT * FROM time_category ORDER BY sortOrder ASC")
    fun observeTimeCategories(): Flow<List<TimeCategoryEntity>>

    @Query("SELECT * FROM time_category ORDER BY sortOrder ASC")
    suspend fun timeCategories(): List<TimeCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTimeCategories(categories: List<TimeCategoryEntity>)

    @Query(
        """
        SELECT * FROM time_budget
        WHERE activeFrom <= :date
          AND (activeTo IS NULL OR activeTo >= :date)
        ORDER BY period, categoryId
        """
    )
    fun observeActiveTimeBudgets(date: LocalDate): Flow<List<TimeBudgetEntity>>

    @Query(
        """
        SELECT * FROM time_budget
        WHERE activeFrom <= :date
          AND (activeTo IS NULL OR activeTo >= :date)
        ORDER BY period, categoryId
        """
    )
    suspend fun activeTimeBudgets(date: LocalDate): List<TimeBudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTimeBudgets(budgets: List<TimeBudgetEntity>)

    @Query("SELECT * FROM time_budget WHERE period = :period ORDER BY categoryId")
    fun observeTimeBudgets(period: BudgetPeriod): Flow<List<TimeBudgetEntity>>

    @Query("SELECT * FROM time_block WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeOpenTimeBlock(): Flow<TimeBlockEntity?>

    @Query("SELECT * FROM time_block WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun openTimeBlock(): TimeBlockEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTimeBlock(block: TimeBlockEntity)

    @Query(
        """
        UPDATE time_block
        SET endedAt = :endedAt,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun closeTimeBlock(id: String, endedAt: Instant, updatedAt: Instant)

    @Query(
        """
        SELECT * FROM time_block
        WHERE startedAt < :end
          AND (endedAt IS NULL OR endedAt > :start)
        ORDER BY startedAt ASC
        """
    )
    fun observeTimeBlocksOverlapping(start: Instant, end: Instant): Flow<List<TimeBlockEntity>>

    @Query(
        """
        SELECT * FROM time_block
        WHERE startedAt < :end
          AND (endedAt IS NULL OR endedAt > :start)
        ORDER BY startedAt ASC
        """
    )
    suspend fun timeBlocksOverlapping(start: Instant, end: Instant): List<TimeBlockEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWellbeingPulse(pulse: WellbeingPulseEntity)

    @Query("SELECT * FROM wellbeing_pulse WHERE date = :date ORDER BY createdAt DESC")
    fun observeWellbeingPulses(date: LocalDate): Flow<List<WellbeingPulseEntity>>

    @Query("SELECT * FROM wellbeing_pulse WHERE date = :date ORDER BY createdAt DESC")
    suspend fun wellbeingPulses(date: LocalDate): List<WellbeingPulseEntity>

    @Query("SELECT * FROM app_time_mapping ORDER BY packageName")
    fun observeAppTimeMappings(): Flow<List<AppTimeMappingEntity>>

    @Query("SELECT * FROM app_time_mapping ORDER BY packageName")
    suspend fun appTimeMappings(): List<AppTimeMappingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppTimeMappings(mappings: List<AppTimeMappingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppTimeMapping(mapping: AppTimeMappingEntity)
}
