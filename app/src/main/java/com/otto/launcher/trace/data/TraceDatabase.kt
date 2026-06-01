package com.otto.launcher.trace.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.otto.launcher.trace.domain.MealSlot
import com.otto.launcher.trace.domain.TraceConfidence
import com.otto.launcher.trace.domain.TraceSource
import com.otto.launcher.trace.domain.TraceType
import java.time.Instant

@Database(
    entities = [
        TraceEntity::class,
        MediaAssetEntity::class,
        FoodCaptureEntity::class,
        WeightMeasurementEntity::class,
        SleepSessionEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(TraceConverters::class)
abstract class TraceDatabase : RoomDatabase() {
    abstract fun traceDao(): TraceDao

    companion object {
        @Volatile
        private var instance: TraceDatabase? = null

        fun get(context: Context): TraceDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TraceDatabase::class.java,
                    "trace.db"
                ).build().also { instance = it }
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
}
