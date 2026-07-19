package com.otto.launcher.quest.llm

import com.otto.launcher.OttoConfig
import com.otto.launcher.quest.data.QuestEntity
import com.otto.launcher.quest.domain.*
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class ClassifiedQuest(
    val title: String, val kind: QuestKind, val tiedTo: String?, val moneyAmount: String?,
    val deadline: Instant?, val place: QuestPlace, val placeNote: String?,
    val effortMinutes: Int?, val urgent: Boolean,
)
data class QuestPlan(val steps: List<String>, val suggestedApps: List<String>)
data class QuestEndVerdict(val completed: Boolean, val reason: String)

private object GroqQuestClient {
    private val client = OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).build()
    suspend fun json(system: String, user: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            require(OttoConfig.hasGroqKey) { "Missing Groq API key." }
            val body = JSONObject().put("model", "llama-3.1-8b-instant").put("temperature", 0)
                .put("response_format", JSONObject().put("type", "json_object"))
                .put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user))).toString()
            val request = Request.Builder().url("https://api.groq.com/openai/v1/chat/completions")
                .header("Authorization", "Bearer ${OttoConfig.groqApiKey}")
                .post(body.toRequestBody("application/json".toMediaType())).build()
            client.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IOException("Groq error ${response.code}")
                JSONObject(JSONObject(payload).getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content"))
            }
        }
    }
}

object QuestClassifier {
    suspend fun classify(raw: String): Result<ClassifiedQuest> = GroqQuestClient.json(
        "Classify a spoken task. OBLIGATION means another person, money, or a promised deadline depends on it; otherwise WISH. place is PHONE, LAPTOP, or PLACE_BOUND. urgent only for a near deadline or a person blocked/waiting. Return JSON keys title,kind,tiedTo,moneyAmount,deadline (epoch milliseconds or null),place,placeNote,effortMinutes,urgent.", raw
    ).mapCatching { j ->
        ClassifiedQuest(j.getString("title").take(80), QuestKind.valueOf(j.getString("kind")), j.nullable("tiedTo"),
            j.nullable("moneyAmount"), j.optLongOrNull("deadline")?.let(Instant::ofEpochMilli),
            QuestPlace.valueOf(j.getString("place")), j.nullable("placeNote"), j.optIntOrNull("effortMinutes"), j.optBoolean("urgent"))
    }

    fun fallback(raw: String) = ClassifiedQuest(raw.trim().take(80).ifBlank { "Untitled quest" }, QuestKind.WISH,
        null, null, null, QuestPlace.PHONE, null, null, false)
}

object QuestDrafter {
    suspend fun draft(summon: String, quests: List<QuestEntity>, defers: Map<QuestEntity, Int>): Result<QuestDraft> {
        val candidates = JSONArray().apply { quests.forEach { q -> put(JSONObject().put("id", q.id).put("title", q.title)
            .put("kind", q.kind.name).put("place", q.place.name).put("placeNote", q.placeNote)
            .put("effortMinutes", q.effortMinutes).put("deadline", q.deadline?.toEpochMilli()).put("urgent", q.urgent)
            .put("tiedTo", q.tiedTo).put("lastSurfacedAt", q.lastSurfacedAt?.toEpochMilli()).put("deferCount", defers[q] ?: 0)) } }
        val rules = "Parse spoken minutes/place/device, filter impossible context and over-window tasks, rank obligations then deadlines then dodged/old. Return 1-3 real candidate IDs only. Never invent. JSON: {constraints:{minutes,place,device},picks:[{questId,reason}],nearMiss:{questId,estimateMinutes,note}|null}."
        return GroqQuestClient.json(rules, "Summon: $summon\nCandidates: $candidates").mapCatching(::parseDraft)
    }

    fun fallback(quests: List<QuestEntity>): QuestDraft {
        val sorted = quests.sortedWith(compareBy<QuestEntity> { it.kind != QuestKind.OBLIGATION }
            .thenBy { it.deadline ?: Instant.MAX }.thenBy { it.lastSurfacedAt ?: Instant.MIN }).take(3)
        return QuestDraft(QuestConstraints(null, null, null), sorted.map { QuestPick(it.id, if (it.kind == QuestKind.OBLIGATION) "obligation" else "oldest open quest") })
    }

    private fun parseDraft(j: JSONObject): QuestDraft {
        val c = j.optJSONObject("constraints") ?: JSONObject()
        val picks = j.optJSONArray("picks") ?: JSONArray()
        val near = j.optJSONObject("nearMiss")
        return QuestDraft(QuestConstraints(c.optIntOrNull("minutes"), c.nullable("place"), c.nullable("device")),
            (0 until picks.length()).map { picks.getJSONObject(it) }.map { QuestPick(it.getString("questId"), it.optString("reason")) },
            near?.let { QuestNearMiss(it.getString("questId"), it.optIntOrNull("estimateMinutes"), it.optString("note")) })
    }
}

object QuestStepPlanner {
    suspend fun plan(quest: QuestEntity, installedApps: Map<String, String>): Result<QuestPlan> {
        val apps = installedApps.entries.joinToString("\n") { "${it.value} (${it.key})" }
        return GroqQuestClient.json("Create concrete ordered imperative steps. suggestedApps must contain only packages from the supplied installed list. Return {steps:[string],suggestedApps:[package]}.",
            "Quest: ${quest.title}\nOriginal: ${quest.rawText}\nPlace: ${quest.place}\nInstalled apps:\n$apps").mapCatching { j ->
            QuestPlan(j.getJSONArray("steps").strings(), j.getJSONArray("suggestedApps").strings().filter(installedApps::containsKey))
        }
    }
    fun fallback(quest: QuestEntity) = QuestPlan(listOf(quest.rawText), emptyList())
}

object QuestEndJudge {
    suspend fun judge(text: String): Result<QuestEndVerdict> = GroqQuestClient.json(
        "Decide spoken quest outcome. Bare done/finished is completion. Anything explaining a blocker or not-now is deferral. Return {completed:boolean,reason:string}.", text
    ).mapCatching { QuestEndVerdict(it.getBoolean("completed"), it.optString("reason", text)) }
}

private fun JSONObject.nullable(key: String): String? = if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)
private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key) || !has(key)) null else getInt(key)
private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key) || !has(key)) null else getLong(key)
private fun JSONArray.strings() = (0 until length()).map { getString(it) }
