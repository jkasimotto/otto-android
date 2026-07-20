package com.otto.launcher.nag

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.otto.launcher.voice.VoiceTranscriptionManager
import com.otto.launcher.trace.data.TraceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The screen that answers a nag by voice, opened full-screen by the nag notification. It lands in an
 * ALARM phase: [NagSiren] blares the alarm tone, vibrates, and strobes the camera flash while the
 * screen itself strobes red. Tapping "answer" kills the racket first (so the mic captures your voice,
 * not the siren), then records a reply, transcribes it, and has [NagJudge] rule on whether it actually
 * answered. Only an accepted answer closes the prompt and cancels the re-nag; anything vague leaves it
 * open so the alarm returns on schedule. The recording is saved as a normal voice memo so the answer
 * reaches Trace and the audio is never lost.
 */
class NagAnswerActivity : ComponentActivity() {

    private enum class Phase { ALARM, RECORDING, CHECKING, DONE }

    private val transcription by lazy { VoiceTranscriptionManager(applicationContext) }
    private val siren by lazy { NagSiren(applicationContext) }
    // Survives the activity so a submitted answer still gets judged and the prompt still gets closed
    // even if the screen is dismissed mid-check.
    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var prompt: NagPrompt
    private var recording = false
    private var sirenOn = false

    private var phase by mutableStateOf(Phase.ALARM)
    private var resultMessage by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val resolved = intent.getStringExtra(EXTRA_PROMPT_ID)?.let { NagPrompt.byId(it) }
        if (resolved == null) {
            finish()
            return
        }
        prompt = resolved
        showWhenLockedAndTurnScreenOn()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 1f }

        setContent {
            val flashing = phase == Phase.ALARM
            var flashOn by mutableStateOf(false)
            LaunchedEffect(flashing) {
                while (flashing) {
                    flashOn = !flashOn
                    delay(120)
                }
            }
            val background by animateColorAsState(
                targetValue = when {
                    phase == Phase.ALARM && flashOn -> Color(0xFFFF1744)
                    phase == Phase.ALARM -> Color.Black
                    else -> Color.Black
                },
                label = "nag-flash",
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(background)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onScreenTap() })
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp),
                ) {
                    Text(
                        text = prompt.question,
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    when (phase) {
                        Phase.ALARM -> Text(
                            text = "TAP ANYWHERE TO ANSWER",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Phase.RECORDING -> Text(
                            text = "Listening… tap when you're done",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        )
                        Phase.CHECKING -> CircularProgressIndicator(color = Color.White)
                        Phase.DONE -> Text(
                            text = resultMessage,
                            color = Color.White,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (phase == Phase.ALARM && !sirenOn) {
            siren.start()
            sirenOn = true
        }
    }

    override fun onPause() {
        super.onPause()
        stopSiren()
        // Left mid-recording (back / home): drop the take and let the nag fire again. A submitted
        // answer has already moved past RECORDING, so it is not discarded here.
        if (recording && phase == Phase.RECORDING) {
            recording = false
            transcription.stopRecording()?.delete()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSiren()
    }

    private fun stopSiren() {
        if (sirenOn) {
            siren.stop()
            sirenOn = false
        }
    }

    private fun onScreenTap() {
        when (phase) {
            Phase.ALARM -> beginAnswering()
            Phase.RECORDING -> submitAnswer()
            else -> Unit
        }
    }

    private fun beginAnswering() {
        stopSiren()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Open Otto once to grant microphone access.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (!transcription.startRecording()) {
            Toast.makeText(this, "Couldn't access the microphone.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        recording = true
        phase = Phase.RECORDING
    }

    private fun submitAnswer() {
        if (!recording) return
        recording = false
        val file = transcription.stopRecording()
        if (file == null) {
            resultMessage = "Didn't catch that. I'll ask again."
            phase = Phase.DONE
            finishShortly()
            return
        }
        phase = Phase.CHECKING
        processScope.launch {
            val transcript = transcription.transcribe(file, deleteAfter = false)
                .getOrNull()?.trim().orEmpty()

            // Keep the audio + answer in Trace. Reuse the transcript we already have so it is stored
            // PROCESSED without a second Whisper call; an empty transcript stays QUEUED for a retry.
            runCatching {
                val repository = TraceRepository(applicationContext)
                if (transcript.isNotEmpty()) {
                    repository.recordVoiceMemo(file, transcriber = { Result.success(transcript) })
                } else {
                    repository.recordVoiceMemo(file)
                }
            }

            val verdict = when {
                transcript.isEmpty() -> NagVerdict(false, "I couldn't hear an answer")
                else -> NagJudge.judge(prompt, transcript)
                    .getOrElse { NagVerdict(false, "Couldn't check your answer") }
            }

            var loggedSleep = false
            if (verdict.answered) {
                if (prompt.recordsSleepSession) {
                    loggedSleep = runCatching {
                        NagSleepRecorder.record(applicationContext, transcript)
                    }.getOrDefault(false)
                }
                NagStore(applicationContext).close(prompt.id)
                NagScheduler.cancelRenag(applicationContext, prompt.id)
                NagNotifier(applicationContext).cancel(prompt.id)
            }

            withContext(Dispatchers.Main) {
                resultMessage = when {
                    verdict.answered && loggedSleep -> "Logged. ${verdict.summary}"
                    verdict.answered -> "Got it. ${verdict.summary}"
                    else -> "Not quite: ${verdict.summary}. I'll ask again."
                }
                phase = Phase.DONE
                finishShortly()
            }
        }
    }

    private fun finishShortly() {
        window.decorView.postDelayed({ if (!isFinishing) finish() }, 2200)
    }

    private fun showWhenLockedAndTurnScreenOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    companion object {
        const val EXTRA_PROMPT_ID = "promptId"
    }
}
