package com.otto.launcher.quest.llm

import com.otto.launcher.quest.data.QuestEntity
import com.otto.launcher.quest.domain.QuestKind
import com.otto.launcher.quest.domain.QuestPlace
import com.otto.launcher.quest.domain.QuestStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant

class QuestFallbackTest {
    @Test fun `classifier fallback keeps spoken text`() {
        val fallback = QuestClassifier.fallback("  Pay invoice  ")
        assertEquals("Pay invoice", fallback.title)
        assertEquals(QuestKind.WISH, fallback.kind)
        assertFalse(fallback.urgent)
    }

    @Test fun `drafter fallback prioritises obligations`() {
        val wish = quest("wish", QuestKind.WISH)
        val obligation = quest("obligation", QuestKind.OBLIGATION)
        assertEquals("obligation", QuestDrafter.fallback(listOf(wish, obligation)).picks.first().questId)
    }

    @Test fun `step planner fallback uses raw text`() {
        assertEquals(listOf("Do the thing"), QuestStepPlanner.fallback(quest("q", raw = "Do the thing")).steps)
    }

    private fun quest(id: String, kind: QuestKind = QuestKind.WISH, raw: String = id) = QuestEntity(
        id, id, raw, kind, null, null, null, QuestPlace.PHONE, null, null, false,
        QuestStatus.OPEN, null, Instant.EPOCH, Instant.EPOCH, null
    )
}
