package com.otto.launcher.quest.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.otto.launcher.quest.domain.QuestEventType
import com.otto.launcher.quest.domain.QuestStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertQuest(quest: QuestEntity)
    @Update suspend fun updateQuest(quest: QuestEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEvent(event: QuestEventEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSteps(steps: List<QuestStepEntity>)
    @Query("DELETE FROM quest_step WHERE questId = :questId") suspend fun deleteSteps(questId: String)
    @Query("SELECT * FROM quest WHERE id = :id") suspend fun quest(id: String): QuestEntity?
    @Query("SELECT * FROM quest WHERE status = :status ORDER BY createdAt") suspend fun quests(status: QuestStatus): List<QuestEntity>
    @Query("SELECT * FROM quest WHERE status = 'ACTIVE' ORDER BY updatedAt DESC LIMIT 1") suspend fun activeQuest(): QuestEntity?
    @Query("SELECT * FROM quest WHERE status = 'ACTIVE' ORDER BY updatedAt DESC LIMIT 1") fun observeActiveQuest(): Flow<QuestEntity?>
    @Query("SELECT * FROM quest_step WHERE questId = :questId ORDER BY orderIndex") suspend fun steps(questId: String): List<QuestStepEntity>
    @Query("SELECT COUNT(*) FROM quest_event WHERE questId = :questId AND type = :type") suspend fun eventCount(questId: String, type: QuestEventType): Int
    @Query("SELECT * FROM quest_event WHERE questId = :questId ORDER BY createdAt DESC") suspend fun events(questId: String): List<QuestEventEntity>
    @Query("SELECT COUNT(*) FROM quest WHERE status = 'OPEN' AND urgent = 1") suspend fun urgentOpenCount(): Int
    @Query("SELECT COUNT(*) FROM quest_event e INNER JOIN quest q ON q.id = e.questId WHERE q.urgent = 1 AND e.type IN ('PICKED','DEFERRED') AND e.createdAt >= :since") suspend fun morningEngagements(since: Long): Int

    @Transaction
    suspend fun replacePlan(quest: QuestEntity, steps: List<QuestStepEntity>, event: QuestEventEntity) {
        deleteSteps(quest.id)
        updateQuest(quest)
        insertSteps(steps)
        insertEvent(event)
    }
}
