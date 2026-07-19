package com.otto.launcher.quest.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.otto.launcher.quest.domain.QuestEventType
import com.otto.launcher.quest.domain.QuestKind
import com.otto.launcher.quest.domain.QuestPlace
import com.otto.launcher.quest.domain.QuestStatus
import java.time.Instant

@Entity(tableName = "quest", indices = [Index("status"), Index("createdAt"), Index("deadline")])
data class QuestEntity(
    @PrimaryKey val id: String,
    val title: String,
    val rawText: String,
    val kind: QuestKind,
    val tiedTo: String?,
    val moneyAmount: String?,
    val deadline: Instant?,
    val place: QuestPlace,
    val placeNote: String?,
    val effortMinutes: Int?,
    val urgent: Boolean,
    val status: QuestStatus,
    val suggestedAppsJson: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val lastSurfacedAt: Instant?,
)

@Entity(tableName = "quest_step", indices = [Index("questId")])
data class QuestStepEntity(
    @PrimaryKey val id: String,
    val questId: String,
    val orderIndex: Int,
    val text: String,
    val done: Boolean = false,
)

@Entity(tableName = "quest_event", indices = [Index("questId"), Index("type")])
data class QuestEventEntity(
    @PrimaryKey val id: String,
    val questId: String,
    val type: QuestEventType,
    val reasonText: String?,
    val createdAt: Instant,
)

data class QuestWithDefers(val quest: QuestEntity, val deferCount: Int)
