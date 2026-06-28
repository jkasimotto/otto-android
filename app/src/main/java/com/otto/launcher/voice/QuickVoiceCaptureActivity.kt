package com.otto.launcher.voice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.otto.launcher.trace.data.TraceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The fast, discreet front door for a voice note. Launched by the assist gesture (Otto registers
 * itself as the device assistant), it starts recording as soon as it is in the foreground, shows a
 * deliberately plain "call-like" screen (black background, running timer), and blanks the screen via
 * the proximity sensor when held to the ear, which both disguises it and makes accidental taps
 * impossible. Ending requires a double-tap, so talking near the screen never stops it by accident.
 *
 * Lifecycle: recording is bound to the resumed state (start in [onResume], stop+save in [onPause]).
 * A proximity screen-off does not pause the activity, so recording continues at the ear; only a real
 * background does, which is when the note is saved. Recordings under [MIN_SAVE_MS] are dropped so a
 * launch that never really showed cannot leave an empty memo. Saved notes land as QUEUED voice
 * memos; the launcher's resume-drain transcribes them and routes any feedback or to-dos.
 */
class QuickVoiceCaptureActivity : ComponentActivity() {

    private val recorder = VoiceRecorder()
    private var wakeLock: PowerManager.WakeLock? = null
    private var recording = false
    private var startedAtMs = 0L
    private var micAvailable = true
    private var hasBuzzedStart = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showWhenLockedAndTurnScreenOn()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // The assist flow has no good moment to prompt; open Otto once to grant the mic.
            micAvailable = false
            Toast.makeText(this, "Open Otto once to grant microphone access.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            var elapsedSeconds by remember { mutableLongStateOf(0L) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(1000)
                    elapsedSeconds += 1
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        // Only a double-tap ends it; single taps are ignored on purpose.
                        detectTapGestures(onDoubleTap = { finish() })
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = formatTimer(elapsedSeconds),
                        color = Color.White,
                        fontSize = 44.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Double-tap to end",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (micAvailable) startRecording()
    }

    override fun onPause() {
        super.onPause()
        // Fires on a real background (and on our own finish()), but NOT on a proximity screen-off,
        // so the note keeps recording while at the ear and is saved only when the screen truly goes.
        stopAndSave()
    }

    override fun onStop() {
        super.onStop()
        // Genuinely backgrounded (or occluded by the keyguard): close so it never lingers recording.
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseProximityLock()
        if (recording) {
            recording = false
            recorder.discard()
        }
    }

    private fun startRecording() {
        if (recording) return
        if (!recorder.start(cacheDir)) {
            micAvailable = false
            Toast.makeText(this, "Couldn't access the microphone.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        recording = true
        startedAtMs = SystemClock.elapsedRealtime()
        if (!hasBuzzedStart) {
            hasBuzzedStart = true
            buzz(START_BUZZ_MS)
        }
        acquireProximityLock()
    }

    private fun stopAndSave() {
        if (!recording) return
        recording = false
        val file = recorder.stop()
        releaseProximityLock()
        val durationMs = SystemClock.elapsedRealtime() - startedAtMs
        if (file == null || durationMs < MIN_SAVE_MS) {
            file?.delete()
            return
        }
        buzz(STOP_BUZZ_MS)
        // Persist on a process-lifetime scope so the save completes even as the activity finishes.
        saveScope.launch {
            runCatching { TraceRepository(applicationContext).recordVoiceMemo(file) }
        }
    }

    private fun showWhenLockedAndTurnScreenOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun acquireProximityLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(POWER_SERVICE) as? PowerManager ?: return
        if (!power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
        wakeLock = power.newWakeLock(
            PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
            "otto:voice-capture"
        ).also { it.acquire(MAX_RECORDING_MS) }
    }

    private fun releaseProximityLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun buzz(durationMs: Long) {
        // A haptic cue is a nicety, never worth crashing capture over, so failures are swallowed.
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as? Vibrator
            } ?: return
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun formatTimer(totalSeconds: Long): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    companion object {
        private const val START_BUZZ_MS = 30L
        private const val STOP_BUZZ_MS = 60L
        private const val MIN_SAVE_MS = 700L
        private const val MAX_RECORDING_MS = 10 * 60 * 1000L

        // Survives the activity so a save started at finish() always lands.
        private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
