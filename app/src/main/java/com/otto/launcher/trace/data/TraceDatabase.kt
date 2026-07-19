package com.otto.launcher.trace.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.otto.launcher.domain.policy.AppTier
import com.otto.launcher.domain.time.BudgetPeriod
import com.otto.launcher.domain.time.TimeBlockSource
import com.otto.launcher.domain.time.TimeCategoryKind
import com.otto.launcher.domain.trace.InboxKind
import com.otto.launcher.domain.trace.InboxState
import com.otto.launcher.domain.trace.MemoState
import com.otto.launcher.quest.data.QuestDao
import com.otto.launcher.quest.data.QuestEntity
import com.otto.launcher.quest.data.QuestEventEntity
import com.otto.launcher.quest.data.QuestStepEntity
import com.otto.launcher.quest.domain.QuestEventType
import com.otto.launcher.quest.domain.QuestKind
import com.otto.launcher.quest.domain.QuestPlace
import com.otto.launcher.quest.domain.QuestStatus
import com.otto.launcher.trace.domain.MealSlot
import com.otto.launcher.trace.domain.TraceConfidence
import com.otto.launcher.trace.domain.TraceSource
import com.otto.launcher.trace.domain.TraceType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Database(
    entities = [
        TraceEntity::class,
        MediaAssetEntity::class,
        FoodCaptureEntity::class,
        WeightMeasurementEntity::class,
        SleepSessionEntity::class,
        AppPolicyEntity::class,
        AppSessionEntity::class,
        GoalSettingsEntity::class,
        FoodEntryEntity::class,
        DrinkEntryEntity::class,
        WeightEntryEntity::class,
        V2SleepSessionEntity::class,
        InboxItemEntity::class,
        HealthConnectMappingEntity::class,
        DailyLocalSummaryEntity::class,
        TimeCategoryEntity::class,
        TimeBudgetEntity::class,
        TimeBlockEntity::class,
        WellbeingPulseEntity::class,
        AppTimeMappingEntity::class,
        VoiceMemoEntity::class,
        UseCaseObservationEntity::class,
        QuestEntity::class,
        QuestStepEntity::class,
        QuestEventEntity::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(TraceConverters::class)
abstract class TraceDatabase : RoomDatabase() {
    abstract fun traceDao(): TraceDao
    abstract fun traceV2Dao(): TraceV2Dao
    abstract fun questDao(): QuestDao

    companion object {
        @Volatile
        private var instance: TraceDatabase? = null

        fun get(context: Context): TraceDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TraceDatabase::class.java,
                    "trace.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .addMigrations(MIGRATION_2_3)
                    .addMigrations(MIGRATION_3_4)
                    .addMigrations(MIGRATION_4_5)
                    .addMigrations(MIGRATION_5_6)
                    .build()
                    .also { instance = it }
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_policy` (
                        `packageName` TEXT NOT NULL,
                        `tier` TEXT NOT NULL,
                        `hiddenByDefault` INTEGER NOT NULL,
                        `suspendedByDefault` INTEGER NOT NULL,
                        `challengeRequired` INTEGER NOT NULL,
                        `reasonRequired` INTEGER NOT NULL,
                        `defaultTimeboxMinutes` INTEGER,
                        `dailyLimitMinutes` INTEGER,
                        `workWindowJson` TEXT,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`packageName`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_session` (
                        `id` TEXT NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `tier` TEXT NOT NULL,
                        `startedAt` INTEGER NOT NULL,
                        `endedAt` INTEGER,
                        `launchReason` TEXT,
                        `gateType` TEXT,
                        `timeboxMinutes` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_app_session_packageName` ON `app_session` (`packageName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_app_session_startedAt` ON `app_session` (`startedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_app_session_endedAt` ON `app_session` (`endedAt`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `goal_settings` (
                        `id` TEXT NOT NULL,
                        `sleepTargetMinutes` INTEGER NOT NULL,
                        `sleepTargetStart` TEXT,
                        `sleepTargetWake` TEXT,
                        `dailyKjTarget` INTEGER,
                        `weightGoalKg` REAL,
                        `dailyScreenLimitMinutes` INTEGER,
                        `windDownMinutesBeforeSleep` INTEGER NOT NULL,
                        `hardSleepLock` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `food_entry` (
                        `id` TEXT NOT NULL,
                        `capturedAt` INTEGER NOT NULL,
                        `photoUri` TEXT,
                        `thumbnailUri` TEXT,
                        `energyKj` INTEGER,
                        `mealType` TEXT,
                        `reviewedAt` INTEGER,
                        `source` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_entry_capturedAt` ON `food_entry` (`capturedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_entry_reviewedAt` ON `food_entry` (`reviewedAt`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drink_entry` (
                        `id` TEXT NOT NULL,
                        `capturedAt` INTEGER NOT NULL,
                        `photoUri` TEXT,
                        `thumbnailUri` TEXT,
                        `amountMl` INTEGER,
                        `source` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drink_entry_capturedAt` ON `drink_entry` (`capturedAt`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `weight_entry` (
                        `id` TEXT NOT NULL,
                        `measuredAt` INTEGER NOT NULL,
                        `kg` REAL NOT NULL,
                        `source` TEXT NOT NULL,
                        `healthConnectId` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_weight_entry_measuredAt` ON `weight_entry` (`measuredAt`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sleep_session` (
                        `id` TEXT NOT NULL,
                        `startAt` INTEGER NOT NULL,
                        `endAt` INTEGER,
                        `source` TEXT NOT NULL,
                        `targetStartLocalTime` TEXT,
                        `targetWakeLocalTime` TEXT,
                        `healthConnectId` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleep_session_startAt` ON `sleep_session` (`startAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleep_session_endAt` ON `sleep_session` (`endAt`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `inbox_item` (
                        `id` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `audioUri` TEXT,
                        `transcriptConfidence` REAL,
                        `state` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `reviewedAt` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inbox_item_kind` ON `inbox_item` (`kind`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inbox_item_state` ON `inbox_item` (`state`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inbox_item_createdAt` ON `inbox_item` (`createdAt`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `health_connect_mapping` (
                        `localId` TEXT NOT NULL,
                        `localType` TEXT NOT NULL,
                        `healthConnectRecordId` TEXT,
                        `clientRecordVersion` INTEGER NOT NULL,
                        `lastSyncedAt` INTEGER,
                        PRIMARY KEY(`localId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_health_connect_mapping_localType` ON `health_connect_mapping` (`localType`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_local_summary` (
                        `date` TEXT NOT NULL,
                        `sleepMinutes` INTEGER,
                        `latestWeightKg` REAL,
                        `foodPhotoCount` INTEGER NOT NULL,
                        `foodEnergyKj` INTEGER,
                        `unresolvedFoodCount` INTEGER NOT NULL,
                        `totalPhoneMinutes` INTEGER,
                        `distractingPhoneMinutes` INTEGER,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`date`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `time_category` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `isDefault` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `time_budget` (
                        `id` TEXT NOT NULL,
                        `categoryId` TEXT NOT NULL,
                        `period` TEXT NOT NULL,
                        `floorMinutes` INTEGER,
                        `targetMinutes` INTEGER,
                        `capMinutes` INTEGER,
                        `activeFrom` TEXT NOT NULL,
                        `activeTo` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_time_budget_categoryId` ON `time_budget` (`categoryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_time_budget_period` ON `time_budget` (`period`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_time_budget_activeFrom` ON `time_budget` (`activeFrom`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_time_budget_activeTo` ON `time_budget` (`activeTo`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `time_block` (
                        `id` TEXT NOT NULL,
                        `categoryId` TEXT NOT NULL,
                        `startedAt` INTEGER NOT NULL,
                        `endedAt` INTEGER,
                        `source` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `label` TEXT,
                        `linkedPackageName` TEXT,
                        `linkedCalendarEventId` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_time_block_categoryId` ON `time_block` (`categoryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_time_block_startedAt` ON `time_block` (`startedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_time_block_endedAt` ON `time_block` (`endedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_time_block_source` ON `time_block` (`source`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_time_block_linkedPackageName` ON `time_block` (`linkedPackageName`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `wellbeing_pulse` (
                        `id` TEXT NOT NULL,
                        `timeBlockId` TEXT,
                        `date` TEXT NOT NULL,
                        `timeAffluence` INTEGER,
                        `enjoyment` INTEGER,
                        `meaning` INTEGER,
                        `energy` INTEGER,
                        `connection` INTEGER,
                        `flow` INTEGER,
                        `stress` INTEGER,
                        `note` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wellbeing_pulse_timeBlockId` ON `wellbeing_pulse` (`timeBlockId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wellbeing_pulse_date` ON `wellbeing_pulse` (`date`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_time_mapping` (
                        `packageName` TEXT NOT NULL,
                        `defaultCategoryId` TEXT NOT NULL,
                        `appTier` TEXT NOT NULL,
                        `countAsDigitalDrift` INTEGER NOT NULL,
                        `requiresIntent` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`packageName`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `voice_memo` (
                        `id` TEXT NOT NULL,
                        `audioUri` TEXT NOT NULL,
                        `durationMs` INTEGER,
                        `sizeBytes` INTEGER,
                        `capturedAt` INTEGER NOT NULL,
                        `state` TEXT NOT NULL,
                        `transcript` TEXT,
                        `processedAt` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_voice_memo_state` ON `voice_memo` (`state`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_voice_memo_capturedAt` ON `voice_memo` (`capturedAt`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `voice_memo` ADD COLUMN `useCasesProcessedAt` INTEGER")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `use_case_observation` (
                        `id` TEXT NOT NULL,
                        `theme` TEXT NOT NULL,
                        `useCase` TEXT NOT NULL,
                        `sourceMemoId` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_use_case_observation_theme` ON `use_case_observation` (`theme`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_use_case_observation_createdAt` ON `use_case_observation` (`createdAt`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `quest` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `rawText` TEXT NOT NULL, `kind` TEXT NOT NULL, `tiedTo` TEXT, `moneyAmount` TEXT, `deadline` INTEGER, `place` TEXT NOT NULL, `placeNote` TEXT, `effortMinutes` INTEGER, `urgent` INTEGER NOT NULL, `status` TEXT NOT NULL, `suggestedAppsJson` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `lastSurfacedAt` INTEGER, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quest_status` ON `quest` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quest_createdAt` ON `quest` (`createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quest_deadline` ON `quest` (`deadline`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `quest_step` (`id` TEXT NOT NULL, `questId` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `text` TEXT NOT NULL, `done` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quest_step_questId` ON `quest_step` (`questId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `quest_event` (`id` TEXT NOT NULL, `questId` TEXT NOT NULL, `type` TEXT NOT NULL, `reasonText` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quest_event_questId` ON `quest_event` (`questId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quest_event_type` ON `quest_event` (`type`)")
            }
        }
    }
}

class TraceConverters {
    @TypeConverter
    fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun localTimeToString(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalTime(value: String?): LocalTime? = value?.let(LocalTime::parse)

    @TypeConverter
    fun localDateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun traceTypeToString(value: TraceType?): String? = value?.name

    @TypeConverter
    fun stringToTraceType(value: String?): TraceType? = value?.let(TraceType::valueOf)

    @TypeConverter
    fun traceSourceToString(value: TraceSource?): String? = value?.name

    @TypeConverter
    fun stringToTraceSource(value: String?): TraceSource? = value?.let(TraceSource::valueOf)

    @TypeConverter
    fun traceConfidenceToString(value: TraceConfidence?): String? = value?.name

    @TypeConverter
    fun stringToTraceConfidence(value: String?): TraceConfidence? = value?.let(TraceConfidence::valueOf)

    @TypeConverter
    fun mealSlotToString(value: MealSlot?): String? = value?.name

    @TypeConverter
    fun stringToMealSlot(value: String?): MealSlot? = value?.let(MealSlot::valueOf)

    @TypeConverter
    fun appTierToString(value: AppTier?): String? = value?.name

    @TypeConverter
    fun stringToAppTier(value: String?): AppTier? = value?.let(AppTier::valueOf)

    @TypeConverter
    fun inboxKindToString(value: InboxKind?): String? = value?.name

    @TypeConverter
    fun stringToInboxKind(value: String?): InboxKind? = value?.let(InboxKind::valueOf)

    @TypeConverter
    fun inboxStateToString(value: InboxState?): String? = value?.name

    @TypeConverter
    fun stringToInboxState(value: String?): InboxState? = value?.let(InboxState::valueOf)

    @TypeConverter
    fun memoStateToString(value: MemoState?): String? = value?.name

    @TypeConverter
    fun stringToMemoState(value: String?): MemoState? = value?.let(MemoState::valueOf)

    @TypeConverter
    fun timeCategoryKindToString(value: TimeCategoryKind?): String? = value?.name

    @TypeConverter
    fun stringToTimeCategoryKind(value: String?): TimeCategoryKind? = value?.let(TimeCategoryKind::valueOf)

    @TypeConverter
    fun budgetPeriodToString(value: BudgetPeriod?): String? = value?.name

    @TypeConverter
    fun stringToBudgetPeriod(value: String?): BudgetPeriod? = value?.let(BudgetPeriod::valueOf)

    @TypeConverter
    fun timeBlockSourceToString(value: TimeBlockSource?): String? = value?.name

    @TypeConverter
    fun stringToTimeBlockSource(value: String?): TimeBlockSource? = value?.let(TimeBlockSource::valueOf)

    @TypeConverter fun questKindToString(value: QuestKind?): String? = value?.name
    @TypeConverter fun stringToQuestKind(value: String?): QuestKind? = value?.let(QuestKind::valueOf)
    @TypeConverter fun questPlaceToString(value: QuestPlace?): String? = value?.name
    @TypeConverter fun stringToQuestPlace(value: String?): QuestPlace? = value?.let(QuestPlace::valueOf)
    @TypeConverter fun questStatusToString(value: QuestStatus?): String? = value?.name
    @TypeConverter fun stringToQuestStatus(value: String?): QuestStatus? = value?.let(QuestStatus::valueOf)
    @TypeConverter fun questEventTypeToString(value: QuestEventType?): String? = value?.name
    @TypeConverter fun stringToQuestEventType(value: String?): QuestEventType? = value?.let(QuestEventType::valueOf)
}
