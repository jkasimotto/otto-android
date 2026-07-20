package com.otto.launcher.ui.home

import com.otto.launcher.apps.AppInfo
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
import java.util.Locale

@Composable
internal fun QuickActionRow(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit,
    greyscaleEnabled: Boolean,
    onToggleGreyscale: () -> Unit,
    onOpenLogs: () -> Unit,
    onUpdate: () -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickActionChip(
            label = "Update",
            onClick = onUpdate
        )
        QuickActionChip(
            label = "Settings",
            onClick = onOpenSettings
        )
        QuickActionIcon(
            greyscaleEnabled = greyscaleEnabled,
            onClick = onToggleGreyscale
        )
        QuickActionChip(
            label = "Logs",
            onClick = onOpenLogs
        )
    }
}

@Composable
internal fun QuickActionIcon(
    greyscaleEnabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        tonalElevation = 3.dp,
        modifier = Modifier
            .size(34.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Tonality,
                contentDescription = if (greyscaleEnabled) "Disable greyscale" else "Enable greyscale",
                tint = if (greyscaleEnabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
internal fun QuickActionChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        tonalElevation = 3.dp,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun AppRow(
    appInfo: AppInfo,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onLaunch(appInfo) },
                onLongClick = { onLongPress(appInfo) },
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true)
            )
            .padding(vertical = 12.dp)
    ) {
        Text(text = appInfo.label, style = MaterialTheme.typography.titleMedium)
        Text(
            text = appInfo.packageName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun MinimalSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.padding(vertical = 12.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.displaySmall.copy(
            color = MaterialTheme.colorScheme.onBackground,
            letterSpacing = 0.sp
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Search,
            capitalization = KeyboardCapitalization.Words
        ),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                innerTextField()
            }
        }
    )
}

@Composable
internal fun VoiceControlChip(
    isRecording: Boolean,
    isTranscribing: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onTap)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isTranscribing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = "Transcribing...",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp)
                )
            } else {
                Icon(
                    imageVector = if (isRecording) Icons.Filled.Mic else Icons.Filled.MicNone,
                    contentDescription = null,
                    tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isRecording) "Tap to finish" else "Tap to speak",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
