package com.otto.launcher.quest.data

import android.content.Context
import com.otto.launcher.quest.domain.*
import com.otto.launcher.trace.data.TraceDatabase
import java.time.Instant
import java.util.UUID
import org.json.JSONArray

class QuestRepository(context: Context) {
    private val dao = TraceDatabase.get(context.applicationContext).questDao()

    suspend fun capture(raw: String, classified: com.otto.launcher.quest.llm.ClassifiedQuest): QuestEntity {
        val now = Instant.now()
        val quest = QuestEntity(UUID.randomUUID().toString(), classified.title, raw, classified.kind,
            classified.tiedTo, classified.moneyAmount, classified.deadline, classified.place,
            classified.placeNote, classified.effortMinutes, classified.urgent, QuestStatus.OPEN,
            null, now, now, null)
        dao.insertQuest(quest)
        event(quest.id, QuestEventType.CAPTURED)
        return quest
    }

    suspend fun open(urgentOnly: Boolean = false): List<QuestEntity> =
        dao.quests(QuestStatus.OPEN).filter { !urgentOnly || it.urgent }

    suspend fun withDodgeSummaries(quests: List<QuestEntity>) = quests.associateWith {
        dao.eventCount(it.id, QuestEventType.DEFERRED)
    }

    suspend fun markSurfaced(ids: List<String>) {
        val now = Instant.now()
        ids.forEach { id -> dao.quest(id)?.let { dao.updateQuest(it.copy(lastSurfacedAt = now, updatedAt = now)); event(id, QuestEventType.SURFACED) } }
    }

    suspend fun activate(quest: QuestEntity, plan: com.otto.launcher.quest.llm.QuestPlan): QuestEntity {
        val now = Instant.now()
        val active = quest.copy(status = QuestStatus.ACTIVE, suggestedAppsJson = JSONArray(plan.suggestedApps).toString(), updatedAt = now)
        val steps = plan.steps.mapIndexed { index, text -> QuestStepEntity(UUID.randomUUID().toString(), quest.id, index, text) }
        dao.replacePlan(active, steps, QuestEventEntity(UUID.randomUUID().toString(), quest.id, QuestEventType.PICKED, null, now))
        return active
    }

    suspend fun finish(quest: QuestEntity, completed: Boolean, utterance: String) {
        val now = Instant.now()
        dao.updateQuest(quest.copy(status = if (completed) QuestStatus.DONE else QuestStatus.OPEN, updatedAt = now))
        event(quest.id, if (completed) QuestEventType.COMPLETED else QuestEventType.DEFERRED, utterance)
        if (!completed) learnFromDeferral(quest.id, utterance)
    }

    suspend fun steps(id: String) = dao.steps(id)
    suspend fun quest(id: String) = dao.quest(id)
    suspend fun active() = dao.activeQuest()
    suspend fun urgentOpenCount() = dao.urgentOpenCount()
    suspend fun engagedSince(epochMillis: Long) = dao.morningEngagements(epochMillis) > 0

    private suspend fun event(id: String, type: QuestEventType, reason: String? = null) =
        dao.insertEvent(QuestEventEntity(UUID.randomUUID().toString(), id, type, reason, Instant.now()))

    private suspend fun learnFromDeferral(id: String, reason: String) {
        val q = dao.quest(id) ?: return
        val lower = reason.lowercase()
        val laptop = listOf("laptop", "computer").any(lower::contains)
        val moreTime = listOf("more time", "not enough time", "too long").any(lower::contains)
        dao.updateQuest(q.copy(
            place = if (laptop) QuestPlace.LAPTOP else q.place,
            effortMinutes = if (moreTime) ((q.effortMinutes ?: 15) * 2).coerceAtMost(480) else q.effortMinutes,
            updatedAt = Instant.now(),
        ))
    }
}
