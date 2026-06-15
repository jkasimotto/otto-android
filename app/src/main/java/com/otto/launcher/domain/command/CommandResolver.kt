package com.otto.launcher.domain.command

import com.otto.launcher.domain.policy.AppDescriptor
import com.otto.launcher.domain.policy.AppGate
import com.otto.launcher.domain.policy.AppPolicyEngine
import kotlin.math.max
import kotlin.math.min
import java.util.Locale

class CommandResolver(
    private val parser: CommandParser = CommandParser(),
    private val policyEngine: AppPolicyEngine = AppPolicyEngine()
) {
    fun resolve(input: String, apps: List<AppDescriptor>): CommandResult {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return CommandResult.Empty
        parser.parse(trimmed)?.let { return CommandResult.BuiltIn(it) }
        if (trimmed.length < 3) return CommandResult.Empty

        val query = trimmed.lowercase(Locale.US)
        val matches = apps
            .asSequence()
            .mapNotNull { app -> scoreApp(app, query)?.let { app to it } }
            .filter { (app, _) -> policyEngine.shouldShowForQuery(app, query) }
            .map { (app, score) ->
                val policy = policyEngine.policyFor(app)
                AppCommandResult(
                    label = app.label,
                    packageName = app.packageName,
                    activityName = app.activityName,
                    tier = policy.tier,
                    gate = policyEngine.gateFor(policy),
                    score = score
                )
            }
            .sortedWith(compareByDescending<AppCommandResult> { it.score }.thenBy { it.label.lowercase(Locale.US) })
            .take(12)
            .toList()

        return if (matches.isEmpty()) CommandResult.NoResult else CommandResult.AppResults(matches)
    }

    private fun scoreApp(app: AppDescriptor, query: String): Int? {
        val label = app.label.lowercase(Locale.US)
        val pkg = app.packageName.lowercase(Locale.US)
        if (label == query || pkg == query) return 100
        if (label.startsWith(query)) return 80
        if (label.contains(query)) return 65
        if (pkg.contains(query)) return 45

        val labelScore = similarityScore(query, label)
        val packageScore = similarityScore(query, pkg)
        val best = max(labelScore, packageScore)
        return best.takeIf { it >= FUZZY_THRESHOLD }
    }

    private fun similarityScore(query: String, candidate: String): Int {
        if (candidate.isEmpty()) return 0
        val trimmedCandidate = if (candidate.length > query.length * 2) {
            candidate.substring(0, query.length * 2)
        } else {
            candidate
        }
        val distance = levenshteinDistance(query, trimmedCandidate)
        val maxLen = max(query.length, trimmedCandidate.length)
        return ((maxLen - distance).coerceAtLeast(0) * 100) / maxLen
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val previous = IntArray(b.length + 1) { it }
        val current = IntArray(b.length + 1)

        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = min(
                    min(current[j] + 1, previous[j + 1] + 1),
                    previous[j] + cost
                )
            }
            for (k in previous.indices) previous[k] = current[k]
        }
        return previous[b.length]
    }

    companion object {
        private const val FUZZY_THRESHOLD = 58
    }
}

