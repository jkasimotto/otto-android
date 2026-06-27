package com.otto.launcher.trace.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TraceMediaStore(private val context: Context) {
    private val appContext = context.applicationContext

    fun createCameraImageFile(): File {
        val timestamp = fileTimestampFormatter.format(Instant.now())
        return File(mediaDir(), "trace_camera_$timestamp.jpg")
    }

    suspend fun createAssetFromCameraFile(file: File, capturedAt: Instant): MediaAssetEntity =
        withContext(Dispatchers.IO) {
            createAssetFromPrivateFile(file, capturedAt, "image/jpeg")
        }

    suspend fun importImage(uri: Uri, capturedAt: Instant): MediaAssetEntity =
        withContext(Dispatchers.IO) {
            val mimeType = appContext.contentResolver.getType(uri) ?: "image/jpeg"
            val extension = extensionForMimeType(mimeType)
            val file = File(mediaDir(), "trace_import_${UUID.randomUUID()}.$extension")
            appContext.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to open selected image." }
                file.outputStream().use { output -> input.copyTo(output) }
            }
            createAssetFromPrivateFile(file, capturedAt, mimeType)
        }

    private fun createAssetFromPrivateFile(
        file: File,
        capturedAt: Instant,
        mimeType: String
    ): MediaAssetEntity {
        file.parentFile?.mkdirs()
        val id = UUID.randomUUID().toString()
        val bounds = imageBounds(file)
        val thumbnail = writeThumbnail(id, file)
        return MediaAssetEntity(
            id = id,
            localUri = file.absolutePath,
            thumbnailUri = thumbnail?.absolutePath,
            mimeType = mimeType,
            capturedAt = capturedAt,
            width = bounds.first,
            height = bounds.second,
            sizeBytes = file.length().takeIf { it > 0L },
            sha256 = sha256(file)
        )
    }

    /**
     * Moves a freshly recorded voice memo out of the volatile recorder temp file (cacheDir, which
     * the OS may clear) into durable app-private storage, and reads its duration. Used by the voice
     * memo queue, which keeps raw audio for later processing instead of transcribing at capture.
     */
    suspend fun persistAudio(tempFile: File): PersistedAudio = withContext(Dispatchers.IO) {
        val dest = File(audioDir(), "trace_audio_${UUID.randomUUID()}.m4a")
        tempFile.inputStream().use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        tempFile.delete()
        PersistedAudio(
            path = dest.absolutePath,
            durationMs = readDurationMs(dest),
            sizeBytes = dest.length().takeIf { it > 0L }
        )
    }

    private fun readDurationMs(file: File): Long? {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            } finally {
                retriever.release()
            }
        }.getOrNull()
    }

    private fun audioDir(): File {
        return File(appContext.filesDir, "trace/audio").apply { mkdirs() }
    }

    private fun mediaDir(): File {
        return File(appContext.filesDir, "trace/media").apply { mkdirs() }
    }

    private fun thumbnailDir(): File {
        return File(appContext.filesDir, "trace/thumbnails").apply { mkdirs() }
    }

    private fun writeThumbnail(id: String, source: File): File? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        val sampleSize = when {
            maxSide > 2400 -> 8
            maxSide > 1200 -> 4
            maxSide > 600 -> 2
            else -> 1
        }
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeFile(source.absolutePath, decodeOptions) ?: return null
        val targetWidth: Int
        val targetHeight: Int
        if (decoded.width >= decoded.height) {
            targetWidth = 320
            targetHeight = (decoded.height * (320f / decoded.width)).toInt().coerceAtLeast(1)
        } else {
            targetHeight = 320
            targetWidth = (decoded.width * (320f / decoded.height)).toInt().coerceAtLeast(1)
        }
        val scaled = Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true)
        if (scaled != decoded) decoded.recycle()

        val target = File(thumbnailDir(), "$id.jpg")
        target.outputStream().use { output ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, output)
        }
        scaled.recycle()
        return target
    }

    private fun imageBounds(file: File): Pair<Int?, Int?> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val width = options.outWidth.takeIf { it > 0 }
        val height = options.outHeight.takeIf { it > 0 }
        return width to height
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun extensionForMimeType(mimeType: String): String {
        return MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mimeType)
            ?.lowercase(Locale.US)
            ?: "jpg"
    }

    companion object {
        private val fileTimestampFormatter = DateTimeFormatter
            .ofPattern("yyyyMMdd_HHmmss_SSS")
            .withZone(ZoneId.systemDefault())
    }
}

/** Result of moving a recorded voice memo into durable private storage. */
data class PersistedAudio(
    val path: String,
    val durationMs: Long?,
    val sizeBytes: Long?
)
