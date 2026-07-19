package com.otto.launcher.nag

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The "yell": an alarm that is hard to ignore. It loops the system alarm tone at full alarm volume,
 * vibrates in a hard repeating pattern, and strobes the camera flash. Screen strobing is the
 * activity's job (it owns the UI); this class owns everything else. Sound and vibration use
 * USAGE_ALARM so they fire through Do Not Disturb and the silent ringer, and the previous alarm
 * volume is restored on [stop]. Always call [stop] (onDestroy) so nothing keeps blaring in the
 * background.
 */
class NagSiren(context: Context) {
    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(AudioManager::class.java)
    private val cameraManager = appContext.getSystemService(CameraManager::class.java)

    private var player: MediaPlayer? = null
    private var previousAlarmVolume = -1
    private var strobeThread: Thread? = null

    @Volatile
    private var strobing = false

    fun start() {
        startSound()
        startVibration()
        startTorchStrobe()
    }

    fun stop() {
        strobing = false
        runCatching { strobeThread?.join(300) }
        strobeThread = null
        torch(false)

        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        if (previousAlarmVolume >= 0) {
            runCatching {
                audio.setStreamVolume(AudioManager.STREAM_ALARM, previousAlarmVolume, 0)
            }
            previousAlarmVolume = -1
        }

        vibrator()?.cancel()
    }

    private fun startSound() {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(appContext, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        runCatching {
            previousAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            audio.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0,
            )
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(appContext, uri)
                isLooping = true
                prepare()
                start()
            }
        }
    }

    private fun startVibration() {
        val vibrator = vibrator() ?: return
        val pattern = longArrayOf(0, 700, 300, 700, 300)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .build()
        runCatching {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0), attributes)
        }
    }

    private fun startTorchStrobe() {
        val cameraId = runCatching {
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull() ?: return

        strobing = true
        strobeThread = Thread {
            var on = false
            while (strobing) {
                on = !on
                runCatching { cameraManager.setTorchMode(cameraId, on) }
                runCatching { Thread.sleep(130) }
            }
            runCatching { cameraManager.setTorchMode(cameraId, false) }
        }.also { it.start() }
    }

    private fun torch(on: Boolean) {
        val cameraId = runCatching {
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull() ?: return
        runCatching { cameraManager.setTorchMode(cameraId, on) }
    }

    private fun vibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Vibrator::class.java)
        }
    }
}
