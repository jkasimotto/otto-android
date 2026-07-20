package com.otto.launcher.voice

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
internal class ReminderExtractionAgent {
    suspend fun extract(transcript: String): List<String> {
        if (transcript.isBlank()) return emptyList()

        val systemPrompt = "You extract concrete to-dos and reminders from a transcribed voice memo. Include only actionable tasks the speaker needs to do or remember, such as fixing, buying, or following up on something. Ignore feedback about apps, observations, feelings, and general narration. Respond strictly with JSON: {\"tasks\": [\"short imperative task\"]}. Use an empty array when there are no actionable tasks."
        val userPrompt = "Voice memo transcript:\n\"$transcript\""

        return GroqClient.chatJson(systemPrompt, userPrompt)
            .map { parsed -> parseTasks(parsed.toString()) }
            .getOrDefault(emptyList())
    }

    private fun parseTasks(content: String): List<String> {
        val jsonText = extractJson(content) ?: return emptyList()
        val obj = runCatching { JSONObject(jsonText) }.getOrNull() ?: return emptyList()
        val array = obj.optJSONArray("tasks") ?: return emptyList()
        return (0 until array.length())
            .mapNotNull { array.optString(it).trim().takeIf { task -> task.isNotEmpty() } }
    }

    private fun extractJson(content: String): String? {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        return if (start >= 0 && end > start) content.substring(start, end + 1) else null
    }

    companion object {
    }
}

/**
 * Pulls candidate Otto "use cases" out of a transcribed memo: durable capabilities the speaker wants
 * the app to provide and recurring life/work focuses worth reflecting back, as opposed to one-off
 * to-dos (those go to the inbox via [ReminderExtractionAgent]). Each candidate carries a stable
 * kebab-case theme slug so that the same idea, mentioned across different memos, collapses to one
 * theme and its repeat count becomes the signal that it is worth building. Like everything touching a
 * transcript, this runs and stores entirely on-device; nothing is ever published.
 */
