package com.otto.launcher.trace.data

import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.otto.launcher.trace.domain.TraceType
import java.time.Instant
import kotlinx.coroutines.flow.Flow

data class TraceEvidenceEntity(
    @Embedded val trace: TraceEntity,
    @Relation(parentColumn = "id", entityColumn = "traceId")
    val foodCapture: FoodCaptureEntity?,
    @Relation(parentColumn = "id", entityColumn = "traceId")
    val weightMeasurement: WeightMeasurementEntity?,
    @Relation(parentColumn = "id", entityColumn = "traceId")
    val sleepSession: SleepSessionEntity?
)

@androidx.room.Dao
abstract class TraceDao {
    @Transaction
    @Query(
        """
        SELECT * FROM traces
        WHERE deletedAt IS NULL
        ORDER BY occurredAt ASC
        """
    )
    abstract fun observeEvidence(): Flow<List<TraceEvidenceEntity>>

    @Transaction
    @Query(
        """
        SELECT * FROM traces
        WHERE occurredAt >= :start
          AND occurredAt < :end
          AND deletedAt IS NULL
        ORDER BY occurredAt ASC
        """
    )
    abstract fun observeEvidenceBetween(start: Instant, end: Instant): Flow<List<TraceEvidenceEntity>>

    @Transaction
    @Query(
        """
        SELECT * FROM traces
        WHERE occurredAt >= :start
          AND occurredAt < :end
          AND deletedAt IS NULL
        ORDER BY occurredAt ASC
        """
    )
    abstract suspend fun evidenceBetween(start: Instant, end: Instant): List<TraceEvidenceEntity>

    @Transaction
    @Query(
        """
        SELECT * FROM traces
        WHERE type = :type
          AND deletedAt IS NULL
        ORDER BY occurredAt DESC
        LIMIT 1
        """
    )
    abstract suspend fun latestEvidenceOfType(type: TraceType): TraceEvidenceEntity?

    @Transaction
    @Query("SELECT * FROM traces WHERE id = :traceId LIMIT 1")
    abstract suspend fun evidenceById(traceId: String): TraceEvidenceEntity?

    @Transaction
    @Query(
        """
        SELECT * FROM traces
        WHERE type = :type
          AND endedAt >= :start
          AND endedAt < :end
          AND deletedAt IS NULL
        ORDER BY endedAt DESC
        LIMIT 1
        """
    )
    abstract suspend fun latestEvidenceEndingBetween(
        type: TraceType,
        start: Instant,
        end: Instant
    ): TraceEvidenceEntity?

    @Query("SELECT * FROM media_assets")
    abstract fun observeMediaAssets(): Flow<List<MediaAssetEntity>>

    @Query("SELECT * FROM media_assets WHERE id = :id LIMIT 1")
    abstract suspend fun mediaAsset(id: String): MediaAssetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTrace(trace: TraceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertMedia(media: MediaAssetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertFoodCapture(foodCapture: FoodCaptureEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertWeightMeasurement(weightMeasurement: WeightMeasurementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSleepSession(sleepSession: SleepSessionEntity)

    @Update
    abstract suspend fun updateTrace(trace: TraceEntity)

    @Update
    abstract suspend fun updateFoodCapture(foodCapture: FoodCaptureEntity)

    @Update
    abstract suspend fun updateWeightMeasurement(weightMeasurement: WeightMeasurementEntity)

    @Update
    abstract suspend fun updateSleepSession(sleepSession: SleepSessionEntity)

    @Transaction
    open suspend fun insertFoodTrace(
        trace: TraceEntity,
        media: MediaAssetEntity,
        foodCapture: FoodCaptureEntity
    ) {
        insertMedia(media)
        insertTrace(trace)
        insertFoodCapture(foodCapture)
    }

    @Transaction
    open suspend fun insertWeightTrace(
        trace: TraceEntity,
        weightMeasurement: WeightMeasurementEntity
    ) {
        insertTrace(trace)
        insertWeightMeasurement(weightMeasurement)
    }

    @Transaction
    open suspend fun insertSleepTrace(
        trace: TraceEntity,
        sleepSession: SleepSessionEntity
    ) {
        insertTrace(trace)
        insertSleepSession(sleepSession)
    }
}
