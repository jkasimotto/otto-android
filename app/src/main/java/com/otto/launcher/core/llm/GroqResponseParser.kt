package com.otto.launcher.core.llm

import org.json.JSONObject

internal fun parseChatContent(body: String): String? = runCatching {
    JSONObject(body)
        .optJSONArray("choices")
        ?.optJSONObject(0)
        ?.optJSONObject("message")
        ?.optString("content")
        ?.takeIf { it.isNotBlank() }
}.getOrNull()

internal fun extractJsonObject(content: String): JSONObject? {
    val start = content.indexOf('{')
    val end = content.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return runCatching { JSONObject(content.substring(start, end + 1)) }.getOrNull()
}
