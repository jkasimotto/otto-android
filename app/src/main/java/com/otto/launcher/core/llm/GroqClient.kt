package com.otto.launcher.core.llm

import com.otto.launcher.core.config.OttoConfig
import com.otto.launcher.core.http.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

internal object GroqClient {
    const val DEFAULT_MODEL = "llama-3.1-8b-instant"
    private const val CHAT_URL = "https://api.groq.com/openai/v1/chat/completions"
    private const val TRANSCRIPTION_URL = "https://api.groq.com/openai/v1/audio/transcriptions"

    suspend fun chatJson(
        system: String,
        user: String,
        model: String = DEFAULT_MODEL,
        temperature: Double = 0.0,
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = OttoConfig.groqApiKey
            if (apiKey.isBlank()) throw IllegalStateException("Missing Groq API key.")
            val payload = JSONObject().apply {
                put("model", model)
                put("temperature", temperature)
                put("response_format", JSONObject().put("type", "json_object"))
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", system))
                    put(JSONObject().put("role", "user").put("content", user))
                })
            }
            val request = Request.Builder()
                .url(CHAT_URL)
                .header("Authorization", "Bearer $apiKey")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            HttpClientProvider.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Groq error ${response.code}")
                val content = parseChatContent(response.body?.string().orEmpty())
                    ?: throw IOException("Groq returned no chat content.")
                extractJsonObject(content)
                    ?: throw IOException("Groq returned no parseable JSON object.")
            }
        }
    }

    suspend fun transcribeChunk(file: File): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = OttoConfig.groqApiKey
            if (apiKey.isBlank()) throw IllegalStateException("Missing Groq API key.")
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", "whisper-large-v3-turbo")
                .addFormDataPart("file", file.name, file.asRequestBody("audio/mp4".toMediaTypeOrNull()))
                .build()
            val request = Request.Builder()
                .url(TRANSCRIPTION_URL)
                .header("Authorization", "Bearer $apiKey")
                .post(body)
                .build()
            HttpClientProvider.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Groq error ${response.code}")
                JSONObject(response.body?.string().orEmpty()).optString("text")
            }
        }
    }
}
