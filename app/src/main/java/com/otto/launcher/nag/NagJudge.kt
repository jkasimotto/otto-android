package com.otto.launcher.nag

import com.otto.launcher.OttoConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** The judge's ruling on a spoken reply. [summary] holds the extracted answer, or what was missing. */
data class NagVerdict(val answered: Boolean, val summary: String)

/**
 * Asks Groq whether a transcribed reply genuinely answered a prompt. This is the difference between a
 * plain reminder and Otto's nag: an evasive or partial reply comes back [NagVerdict.answered] = false
 * so [NagAnswerActivity] leaves the prompt open and the buzzing continues. Reuses the same Groq key
 * and chat endpoint the launcher already uses for its voice agent.
 */
object NagJudge {
    private val client = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun judge(prompt: NagPrompt, transcript: String): Result<NagVerdict> =
        withContext(Dispatchers.IO) {
            runCatching {
                val apiKey = OttoConfig.groqApiKey
                require(apiKey.isNotBlank()) { "Missing Groq API key." }

                val system = "You decide whether a spoken reply answered a specific question. " +
                    "Rule for a valid answer: ${prompt.judgeHint} " +
                    "Reply ONLY with compact JSON: " +
                    "{\"answered\": true|false, \"summary\": \"<extracted answer, or what is missing>\"}."
                val user = "Question: ${prompt.question}\nSpoken reply: \"$transcript\""

                val payload = JSONObject()
                    .put("model", MODEL)
                    .put("temperature", 0)
                    .put("response_format", JSONObject().put("type", "json_object"))
                    .put(
                        "messages",
                        JSONArray()
                            .put(JSONObject().put("role", "system").put("content", system))
                            .put(JSONObject().put("role", "user").put("content", user)),
                    )
                    .toString()

                val request = Request.Builder()
                    .url(CHAT_URL)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(payload.toRequestBody(JSON))
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    require(response.isSuccessful) { "Groq judge error ${response.code}" }
                    val content = JSONObject(responseBody)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    val parsed = JSONObject(content)
                    NagVerdict(
                        answered = parsed.optBoolean("answered", false),
                        summary = parsed.optString("summary", ""),
                    )
                }
            }
        }

    private const val MODEL = "llama-3.1-8b-instant"
    private const val CHAT_URL = "https://api.groq.com/openai/v1/chat/completions"
    private val JSON = "application/json".toMediaType()
}
