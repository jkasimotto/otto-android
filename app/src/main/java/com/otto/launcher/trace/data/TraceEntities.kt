package com.otto.launcher.trace.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.otto.launcher.trace.domain.MealSlot
import com.otto.launcher.trace.domain.TraceConfidence
import com.otto.launcher.trace.domain.TraceSource
import com.otto.launcher.trace.domain.TraceType
import java.time.Instant

@Entity(
    tableName = "traces",
    indices = [
        Index("type"),
        Index("occurredAt"),
        Index("deletedAt")
    ]
)
data class TraceEntity(
    @PrimaryKey val id: String,
    val type: TraceType,
    val source: TraceSource,
    val confidence: TraceConfidence,
    val occurredAt: Instant,
    val endedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    val notes: String?
)

@Entity(tableName = "media_assets")
data class MediaAssetEntity(
    @PrimaryKey val id: String,
    val localUri: String,
    val thumbnailUri: String?,
    val mimeType: String,
    val capturedAt: Instant,
    val width: Int?,
    val height: Int?,
    val sizeBytes: Long?,
    val sha256: String?
)

@Entity(
    tableName = "food_captures",
    indices = [
        Index("mediaId"),
        Index("mealSlot"),
        Index("inferredMealSlot")
    ]
)
data class FoodCaptureEntity(
    @PrimaryKey val traceId: String,
    val mealSlot: MealSlot?,
    val inferredMealSlot: MealSlot?,
    val isDrinkOnly: Boolean,
    val mediaId: String,
    val hidden: Boolean
)

@Entity(tableName = "weight_measurements")
data class WeightMeasurementEntity(
    @PrimaryKey val traceId: String,
    val kilograms: Double,
    val sourceLabel: String?
)

@Entity(tableName = "sleep_sessions")
data class SleepSessionEntity(
    @PrimaryKey val traceId: String,
    val startAt: Instant,
    val endAt: Instant,
    val durationMinutes: Int,
    val sourceLabel: String?,
    val wasAdjustedByUser: Boolean
)
