package com.otto.launcher.nag

import com.otto.launcher.core.llm.GroqClient

/** The judge's ruling on a spoken reply. [summary] holds the extracted answer, or what was missing. */
data class NagVerdict(val answered: Boolean, val summary: String)

/**
 * Asks Groq whether a transcribed reply genuinely answered a prompt. This is the difference between a
 * plain reminder and Otto's nag: an evasive or partial reply comes back [NagVerdict.answered] = false
 * so [NagAnswerActivity] leaves the prompt open and the buzzing continues. Reuses the same Groq key
 * and chat endpoint the launcher already uses for its voice agent.
 */
object NagJudge {
    suspend fun judge(prompt: NagPrompt, transcript: String): Result<NagVerdict> {
        val system = "You decide whether a spoken reply answered a specific question. " +
            "Rule for a valid answer: ${prompt.judgeHint} " +
            "Reply ONLY with compact JSON: " +
            "{\"answered\": true|false, \"summary\": \"<extracted answer, or what is missing>\"}."
        val user = "Question: ${prompt.question}\nSpoken reply: \"$transcript\""
        return GroqClient.chatJson(system, user).map { parsed ->
            NagVerdict(parsed.optBoolean("answered", false), parsed.optString("summary", ""))
        }
    }
}
