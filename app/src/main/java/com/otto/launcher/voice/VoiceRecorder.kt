package com.otto.launcher.voice

import android.media.MediaRecorder
import java.io.File

/**
 * The single owner of the microphone capture config. Both the launcher's voice memo button and the
 * discreet [QuickVoiceCaptureActivity] record through this, so the audio format (m4a/AAC) lives in
 * one place instead of being duplicated per entry point. Not thread-safe: drive it from one place.
 */
internal class VoiceRecorder {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    /** Starts recording into a fresh temp file under [cacheDir]. Returns true once capture is live. */
    fun start(cacheDir: File): Boolean {
        releaseRecorder(stop = true)
        val file = File.createTempFile("otto_voice_", ".m4a", cacheDir)
        return runCatching {
            @Suppress("DEPRECATION")
            val started = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = started
            outputFile = file
            true
        }.getOrElse {
            releaseRecorder(stop = false)
            file.delete()
            outputFile = null
            false
        }
    }

    /** Stops capture and returns the recording when it holds audio, else null. */
    fun stop(): File? {
        val file = outputFile
        releaseRecorder(stop = true)
        outputFile = null
        return file?.takeIf { it.exists() && it.length() > 0 }
    }

    /** Stops and deletes any in-progress recording, keeping nothing. */
    fun discard() {
        releaseRecorder(stop = false)
        outputFile?.delete()
        outputFile = null
    }

    private fun releaseRecorder(stop: Boolean) {
        runCatching {
            recorder?.apply {
                if (stop) {
                    try {
                        stop()
                    } catch (_: RuntimeException) {
                        // stop() throws if start() never produced data; the file is then discarded.
                    }
                }
                release()
            }
        }
        recorder = null
    }
}
