package com.otto.launcher.quest.domain

enum class QuestKind { OBLIGATION, WISH }
enum class QuestPlace { PHONE, LAPTOP, PLACE_BOUND }
enum class QuestStatus { OPEN, ACTIVE, DONE, DROPPED }
enum class QuestEventType { CAPTURED, SURFACED, PICKED, COMPLETED, DEFERRED }

data class QuestConstraints(val minutes: Int?, val place: String?, val device: String?)
data class QuestPick(val questId: String, val reason: String)
data class QuestNearMiss(val questId: String, val estimateMinutes: Int?, val note: String)
data class QuestDraft(
    val constraints: QuestConstraints,
    val picks: List<QuestPick>,
    val nearMiss: QuestNearMiss? = null,
)
