package com.otto.launcher.notes

import com.otto.launcher.*
import com.otto.launcher.core.config.OttoConfig
import com.otto.launcher.core.http.HttpClientProvider
import com.otto.launcher.core.llm.GroqClient
import android.Manifest
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.otto.launcher.voice.VoiceRecorder
import com.otto.launcher.voice.isOttoAssistant
import com.otto.launcher.voice.registerOttoAsAssistant
import com.otto.launcher.quest.ui.QuestActivity
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Tonality
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.otto.launcher.trace.ui.TraceCameraOverlay
import com.otto.launcher.trace.ui.TraceCaptureSheet
import com.otto.launcher.trace.ui.TraceHomeLayer
import com.otto.launcher.trace.ui.TraceSettingsDialog
import com.otto.launcher.trace.ui.TraceSleepDialog
import com.otto.launcher.trace.ui.TraceTodayDialog
import com.otto.launcher.trace.ui.TraceViewModel
import com.otto.launcher.trace.ui.TraceWeeklyDialog
import com.otto.launcher.trace.ui.TraceWeightDialog
import com.otto.launcher.domain.command.AppCommandResult
import com.otto.launcher.domain.command.CommandResult
import com.otto.launcher.domain.command.MaintenanceSection
import com.otto.launcher.domain.command.OttoCommand
import com.otto.launcher.data.weather.WeatherRepository
import com.otto.launcher.domain.mode.OttoMode
import com.otto.launcher.domain.weather.DailyWeather
import com.otto.launcher.domain.policy.AppDescriptor
import com.otto.launcher.domain.policy.AppGate
import com.otto.launcher.domain.time.TimeCategoryIds
import com.otto.launcher.domain.time.TimeMode
import com.otto.launcher.domain.trace.InboxKind
import com.otto.launcher.domain.trace.InboxState
import com.otto.launcher.domain.trace.UseCaseCandidate
import com.otto.launcher.domain.trace.WeeklySleepDay
import com.otto.launcher.trace.domain.SleepEstimate
import java.time.ZoneId
import com.otto.launcher.data.policy.PolicyRuntime
import com.otto.launcher.guard.DeviceOwnerController
import com.otto.launcher.guard.OttoPolicyController
import com.otto.launcher.ui.capture.NoteCaptureSheet
import com.otto.launcher.ui.capture.SleepStartDialog
import com.otto.launcher.ui.home.DistractionGateDialog
import com.otto.launcher.ui.home.FastCaptureAction
import com.otto.launcher.ui.home.HomeScreenV2
import com.otto.launcher.ui.home.LauncherViewModel
import com.otto.launcher.ui.home.LedgerAction
import com.otto.launcher.ui.review.FoodReviewScreen
import com.otto.launcher.ui.review.InboxReviewScreen
import com.otto.launcher.ui.review.TranscriptViewerScreen
import com.otto.launcher.ui.review.TodayScreen
import com.otto.launcher.ui.review.WeekScreen
import com.otto.launcher.ui.settings.SettingsHome
import com.otto.launcher.ui.theme.OttoLauncherTheme
import com.otto.launcher.ui.time.StartBlockDialog
import com.otto.launcher.ui.time.TimeBudgetScreen
import com.otto.launcher.ui.time.TodayTimeReviewScreen
import com.otto.launcher.ui.time.WeeklyTimeReviewScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.max
import kotlin.math.min
internal object SecretNoteStore {
    private const val PREFS_NAME = "otto_secret_notes"
    private const val KEY_SALT = "enc_salt"
    private const val KEY_DATA = "enc_data"
    private const val KEY_VERIFY = "enc_verify"
    private const val VERIFY_PLAINTEXT = "otto_verified"
    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    fun isPasskeySet(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_VERIFY)
    }

    fun setPasskey(context: Context, passkey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passkey, salt)
        val verifyEncrypted = encrypt(VERIFY_PLAINTEXT.toByteArray(Charsets.UTF_8), key)
        prefs.edit()
            .putString(KEY_SALT, android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
            .putString(KEY_VERIFY, android.util.Base64.encodeToString(verifyEncrypted, android.util.Base64.NO_WRAP))
            .putString(KEY_DATA, "")
            .apply()
    }

    fun verifyPasskey(context: Context, passkey: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saltB64 = prefs.getString(KEY_SALT, null) ?: return false
        val verifyB64 = prefs.getString(KEY_VERIFY, null) ?: return false
        val salt = android.util.Base64.decode(saltB64, android.util.Base64.NO_WRAP)
        val verifyData = android.util.Base64.decode(verifyB64, android.util.Base64.NO_WRAP)
        val key = deriveKey(passkey, salt)
        val decrypted = decrypt(verifyData, key) ?: return false
        return String(decrypted, Charsets.UTF_8) == VERIFY_PLAINTEXT
    }

    fun loadNotes(context: Context, passkey: String): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saltB64 = prefs.getString(KEY_SALT, null) ?: return ""
        val dataB64 = prefs.getString(KEY_DATA, null)
        if (dataB64.isNullOrBlank()) return ""
        val salt = android.util.Base64.decode(saltB64, android.util.Base64.NO_WRAP)
        val data = android.util.Base64.decode(dataB64, android.util.Base64.NO_WRAP)
        val key = deriveKey(passkey, salt)
        val decrypted = decrypt(data, key) ?: return ""
        return String(decrypted, Charsets.UTF_8)
    }

    fun saveNotes(context: Context, passkey: String, notes: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saltB64 = prefs.getString(KEY_SALT, null) ?: return
        val salt = android.util.Base64.decode(saltB64, android.util.Base64.NO_WRAP)
        val key = deriveKey(passkey, salt)
        val encrypted = encrypt(notes.toByteArray(Charsets.UTF_8), key)
        prefs.edit()
            .putString(KEY_DATA, android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP))
            .apply()
    }

    private fun deriveKey(passkey: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passkey.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun encrypt(data: ByteArray, key: SecretKeySpec): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val encrypted = cipher.doFinal(data)
        return iv + encrypted
    }

    private fun decrypt(data: ByteArray, key: SecretKeySpec): ByteArray? {
        if (data.size < GCM_IV_LENGTH + 1) return null
        return runCatching {
            val iv = data.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = data.copyOfRange(GCM_IV_LENGTH, data.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            cipher.doFinal(ciphertext)
        }.getOrNull()
    }
}
