package com.otto.launcher.quest.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.otto.launcher.OttoConfig
import com.otto.launcher.VoiceTranscriptionManager
import com.otto.launcher.quest.QuestFocus
import com.otto.launcher.quest.data.*
import com.otto.launcher.quest.domain.*
import com.otto.launcher.quest.llm.*
import java.io.File
import kotlinx.coroutines.launch

class QuestActivity : ComponentActivity() {
    private val repo by lazy { QuestRepository(this) }
    private val voice by lazy { VoiceTranscriptionManager(this) }
    private var state by mutableStateOf(QuestUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true); setTurnScreenOn(true)
        setContent { QuestTheme { QuestSurface(state, ::record, ::pick, ::launchApp) } }
        lifecycleScope.launch {
            repo.active()?.let { active -> state = state.copy(phase = Phase.ACTIVE, active = active, steps = repo.steps(active.id)) }
            if (intent.getBooleanExtra(EXTRA_URGENT, false)) draft("urgent quests for this morning", true)
        }
    }

    private fun record(action: VoiceAction) {
        if (!state.recording) {
            if (voice.startRecording()) state = state.copy(recording = true, message = "listening…", pendingAction = action)
            return
        }
        val file = voice.stopRecording() ?: return
        state = state.copy(recording = false, busy = true, message = "understanding…")
        lifecycleScope.launch { handleTranscript(action, file) }
    }

    private suspend fun handleTranscript(action: VoiceAction, file: File) {
        val text = voice.transcribe(file, deleteAfter = false).getOrElse {
            state = state.copy(busy = false, message = "Couldn't transcribe. Recording kept at ${file.name}."); return
        }
        file.delete()
        when (action) {
            VoiceAction.CAPTURE -> {
                val classified = QuestClassifier.classify(text).getOrElse { QuestClassifier.fallback(text) }
                repo.capture(text, classified)
                state = state.copy(busy = false, message = "Quest captured.")
            }
            VoiceAction.SUMMON -> draft(text, false)
            VoiceAction.PICK -> pickSpoken(text)
            VoiceAction.FINISH -> finish(text)
        }
    }

    private suspend fun draft(text: String, urgent: Boolean) {
        state = state.copy(busy = true, message = "drawing quests…")
        val open = repo.open(urgent)
        val draft = if (OttoConfig.hasGroqKey) QuestDrafter.draft(text, open, repo.withDodgeSummaries(open)).getOrElse { QuestDrafter.fallback(open) }
            else QuestDrafter.fallback(open)
        val valid = draft.picks.filter { pick -> open.any { it.id == pick.questId } }.take(3)
        repo.markSurfaced(valid.map { it.questId })
        state = state.copy(phase = Phase.DRAFT, busy = false, summon = text,
            cards = valid.mapNotNull { pick -> open.find { it.id == pick.questId }?.let { QuestCard(it, pick.reason) } },
            nearMiss = draft.nearMiss?.let { near -> open.find { it.id == near.questId }?.let { "Nothing fits. Closest is ${it.title}: ${near.note}" } },
            message = if (open.isEmpty()) "No open quests." else null)
    }

    private fun pick(card: QuestCard) { lifecycleScope.launch {
        pickNow(card)
    } }

    private suspend fun pickNow(card: QuestCard) {
        state = state.copy(phase = Phase.REVEAL, busy = true, active = card.quest)
        val apps = installedApps()
        val plan = QuestStepPlanner.plan(card.quest, apps).getOrElse { QuestStepPlanner.fallback(card.quest) }
        val active = repo.activate(card.quest, plan)
        QuestFocus.enter(this@QuestActivity, plan.suggestedApps)
        state = state.copy(phase = Phase.ACTIVE, busy = false, active = active, steps = repo.steps(active.id))
    }

    private suspend fun pickSpoken(text: String) {
        val lower = text.lowercase()
        val index = when {
            listOf("first", "one", "1").any(lower::contains) -> 0
            listOf("second", "two", "2").any(lower::contains) -> 1
            listOf("third", "three", "3").any(lower::contains) -> 2
            else -> state.cards.indexOfFirst { lower.contains(it.quest.title.lowercase()) }
        }
        state.cards.getOrNull(index)?.let { pickNow(it) }
            ?: run { state = state.copy(busy = false, message = "Say first, second, third, or the quest name.") }
    }

    private suspend fun finish(text: String) {
        val active = state.active ?: return
        val verdict = QuestEndJudge.judge(text).getOrElse {
            QuestEndVerdict(text.trim().lowercase() in setOf("done", "finished", "finished it"), text)
        }
        repo.finish(active, verdict.completed, verdict.reason.ifBlank { text })
        QuestFocus.exit(this)
        finishAndRemoveTask()
    }

    private fun installedApps(): Map<String, String> {
        @Suppress("DEPRECATION")
        return packageManager.getInstalledApplications(0).mapNotNull { info ->
            info.packageName.takeIf { packageManager.getLaunchIntentForPackage(it) != null }?.let { it to packageManager.getApplicationLabel(info).toString() }
        }.toMap()
    }
    private fun launchApp(pkg: String) { packageManager.getLaunchIntentForPackage(pkg)?.let(::startActivity) }
    override fun onDestroy() { voice.dispose(); super.onDestroy() }

    companion object { const val EXTRA_URGENT = "urgentOnly" }
}

private enum class Phase { SUMMON, DRAFT, REVEAL, ACTIVE }
private enum class VoiceAction { CAPTURE, SUMMON, PICK, FINISH }
private data class QuestCard(val quest: QuestEntity, val reason: String)
private data class QuestUiState(val phase: Phase = Phase.SUMMON, val recording: Boolean = false, val busy: Boolean = false,
    val message: String? = null, val pendingAction: VoiceAction = VoiceAction.SUMMON, val summon: String = "",
    val cards: List<QuestCard> = emptyList(), val nearMiss: String? = null, val active: QuestEntity? = null,
    val steps: List<QuestStepEntity> = emptyList())

@Composable private fun QuestSurface(s: QuestUiState, record: (VoiceAction) -> Unit, pick: (QuestCard) -> Unit, launch: (String) -> Unit) {
    Surface(Modifier.fillMaxSize(), color = Color(0xFF090A10)) {
        AnimatedContent(s.phase, transitionSpec = { fadeIn(tween(450)) togetherWith fadeOut(tween(250)) }, label = "quest phase") { phase ->
            when (phase) {
                Phase.SUMMON -> Column(Modifier.fillMaxSize().padding(28.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                    Text("What can you do right now?", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(30.dp)); VoiceButton(s, VoiceAction.SUMMON, record, "summon")
                    Spacer(Modifier.height(20.dp)); TextButton(onClick = { record(VoiceAction.CAPTURE) }) { Text(if (s.recording && s.pendingAction == VoiceAction.CAPTURE) "stop capture" else "capture a quest") }
                    s.message?.let { Text(it, color = Color.LightGray) }
                }
                Phase.DRAFT -> Column(Modifier.fillMaxSize().padding(22.dp), Arrangement.Center) {
                    Text("Choose one", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(18.dp)); s.cards.forEachIndexed { i, card -> QuestCardView(card, i, pick); Spacer(Modifier.height(14.dp)) }
                    if (s.cards.isEmpty()) Text(s.nearMiss ?: s.message ?: "Nothing fits that window.", color = Color.LightGray)
                    s.message?.let { Text(it, color = Color.LightGray) }
                    Spacer(Modifier.height(16.dp)); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        VoiceButton(s, VoiceAction.PICK, record, "pick by voice")
                        VoiceButton(s, VoiceAction.SUMMON, record, "another")
                    }
                }
                Phase.REVEAL -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFFFFB000)); Text("\n\nBuilding the path…") }
                Phase.ACTIVE -> ActiveQuest(s, record, launch)
            }
        }
    }
}

@Composable private fun QuestCardView(card: QuestCard, index: Int, pick: (QuestCard) -> Unit) {
    val scale by animateFloatAsState(1f, tween(350 + index * 140), label = "deal")
    val colors = if (card.quest.kind == QuestKind.OBLIGATION) listOf(Color(0xFFFF3D4D), Color(0xFFFF8A00)) else listOf(Color(0xFF7048FF), Color(0xFF00A8E8))
    Card(Modifier.fillMaxWidth().scale(scale).clickable { pick(card) }, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.background(Brush.horizontalGradient(colors)).padding(22.dp)) {
            Text(card.quest.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.White)
            Text(card.reason, color = Color.White.copy(.88f)); card.quest.effortMinutes?.let { Text("~$it min", color = Color.White) }
        }
    }
}

@Composable private fun ActiveQuest(s: QuestUiState, record: (VoiceAction) -> Unit, launch: (String) -> Unit) {
    val q = s.active ?: return
    Column(Modifier.fillMaxSize().padding(26.dp)) { Text(q.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = Color(0xFFFFB000)); Spacer(Modifier.height(18.dp))
        LazyColumn(Modifier.weight(1f)) { itemsIndexed(s.steps) { i, step -> Text("${i + 1}. ${step.text}", Modifier.padding(vertical = 10.dp), style = MaterialTheme.typography.titleMedium) } }
        val apps = remember(q.suggestedAppsJson) { runCatching { org.json.JSONArray(q.suggestedAppsJson ?: "[]") }.getOrNull() }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(apps?.length() ?: 0) { i -> val pkg = apps!!.getString(i); OutlinedButton(onClick = { launch(pkg) }) { Text(pkg.substringAfterLast('.')) } } }
        VoiceButton(s, VoiceAction.FINISH, record, "done / not now")
    }
}

@Composable private fun VoiceButton(s: QuestUiState, action: VoiceAction, record: (VoiceAction) -> Unit, label: String) {
    val active = s.recording && s.pendingAction == action
    Button(onClick = { record(action) }, enabled = !s.busy, colors = ButtonDefaults.buttonColors(containerColor = if (active) Color(0xFFFF3D4D) else Color(0xFF7048FF))) {
        Icon(if (active) Icons.Default.Stop else Icons.Default.Mic, null); Spacer(Modifier.width(8.dp)); Text(if (active) "stop" else label)
    }
}

@Composable private fun QuestTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF7048FF), onSurface = Color.White), content = content) }
