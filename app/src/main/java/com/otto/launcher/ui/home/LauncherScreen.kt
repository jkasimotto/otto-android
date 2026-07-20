package com.otto.launcher.ui.home

import com.otto.launcher.*
import com.otto.launcher.apps.*
import com.otto.launcher.core.config.OttoConfig
import com.otto.launcher.feedback.FeedbackSubmitter
import com.otto.launcher.guard.*
import com.otto.launcher.notes.SecretNoteStore
import com.otto.launcher.updater.OttoUpdater
import com.otto.launcher.voice.ReminderExtractionAgent
import com.otto.launcher.voice.UseCaseExtractionAgent
import com.otto.launcher.voice.VoiceTranscriptionManager
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
internal fun LauncherScreen(
    apps: List<AppInfo>
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val voiceManager = remember(context.applicationContext) {
        VoiceTranscriptionManager(context.applicationContext)
    }
    val voiceAgent = remember { VoiceLaunchAgent() }
    val traceViewModel: TraceViewModel = viewModel()
    val launcherViewModel: LauncherViewModel = viewModel()
    val traceState by traceViewModel.uiState.collectAsState()
    val queuedMemoCount by traceViewModel.queuedMemoCount.collectAsState()
    val homeState by launcherViewModel.uiState.collectAsState()
    var tapCount by remember { mutableStateOf(0) }
    var tapTimeoutJob by remember { mutableStateOf<Job?>(null) }
    var lastTapTimestamp by remember { mutableStateOf(0L) }

    var query by rememberSaveable { mutableStateOf("") }
    var voiceHudVisible by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var isMemoRecording by remember { mutableStateOf(false) }
    var isTranscribing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var pendingPermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var isVoiceMode by remember { mutableStateOf(false) }
    var voiceCaptureKind by remember { mutableStateOf<InboxKind?>(null) }
    var gatedLaunchApp by remember { mutableStateOf<AppInfo?>(null) }
    var gatedDistractionApp by remember { mutableStateOf<AppCommandResult?>(null) }
    var launchGateInput by rememberSaveable { mutableStateOf("") }
    var launchGateCode by rememberSaveable { mutableStateOf("") }
    var launchGateReason by rememberSaveable { mutableStateOf("") }
    var launchGateError by remember { mutableStateOf<String?>(null) }
    var isUpdating by remember { mutableStateOf(false) }
    var manualProcessingActive by remember { mutableStateOf(false) }
    var manualProcessingLabel by remember { mutableStateOf(PROCESSING_LABELS.first()) }
    var manualProcessingNonce by remember { mutableStateOf(0) }
    var diagnosticsVisible by remember { mutableStateOf(false) }
    var diagnosticsText by remember { mutableStateOf("") }
    var feedbackVisible by remember { mutableStateOf(false) }
    var feedbackText by remember { mutableStateOf("") }
    var feedbackSending by remember { mutableStateOf(false) }
    val installedVersionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "dev"
    }
    val reminderAgent = remember { ReminderExtractionAgent() }
    val useCaseAgent = remember { UseCaseExtractionAgent() }
    // A transcribed voice memo never leaves the device for GitHub. Transcripts routinely mix personal
    // talk with feature requests, and an LLM cannot be trusted to decide what is safe to publish to a
    // PUBLIC repo, so no transcript or model summary is ever posted. Spoken to-dos go only to the
    // local inbox; app feedback is triaged off-device by reading the transcripts directly (plug the
    // phone in). The in-app "Send feedback" button stays the only voice-to-GitHub path, and that is
    // text the user typed and chose to send.
    val processVoiceTranscript: suspend (String) -> Unit = remember {
        { transcript ->
            reminderAgent.extract(transcript).forEach { task ->
                launcherViewModel.saveTask(task)
            }
        }
    }
    val weatherRepository = remember { WeatherRepository(context) }
    var weeklyWeather by remember { mutableStateOf<List<DailyWeather>>(emptyList()) }
    var greyscaleEnabled by remember { mutableStateOf(context.isGreyscaleEnabled()) }
    var greyscaleDisableVisible by remember { mutableStateOf(false) }
    var greyscaleDisableCode by remember { mutableStateOf("") }
    var greyscaleDisableInput by remember { mutableStateOf("") }
    var greyscaleDisableError by remember { mutableStateOf<String?>(null) }

    // Secret notes gesture state: 7-tap version, wait 3s, 7-tap again
    var secretTapPhase by remember { mutableStateOf(0) } // 0=idle, 1=first-7-done, 2=passkey-prompt, 3=unlocked
    var secretPasskeyInput by rememberSaveable { mutableStateOf("") }
    var secretPasskeyError by remember { mutableStateOf<String?>(null) }
    var secretNotesText by rememberSaveable { mutableStateOf("") }
    var secretActivePasskey by remember { mutableStateOf("") }
    var secretIsNewPasskey by remember { mutableStateOf(false) }
    var secretConfirmPasskey by rememberSaveable { mutableStateOf("") }
    var secretNotesRevealed by remember { mutableStateOf(false) }

    var traceCaptureSheetVisible by remember { mutableStateOf(false) }
    var traceWeightVisible by remember { mutableStateOf(false) }
    var traceSleepVisible by remember { mutableStateOf(false) }
    var traceTodayVisible by remember { mutableStateOf(false) }
    var traceWeeklyVisible by remember { mutableStateOf(false) }
    var traceSettingsVisible by remember { mutableStateOf(false) }
    var noteCaptureVisible by remember { mutableStateOf(false) }
    var sleepStartVisible by remember { mutableStateOf(false) }
    var foodReviewVisible by remember { mutableStateOf(false) }
    var inboxReviewVisible by remember { mutableStateOf(false) }
    var transcriptViewerVisible by remember { mutableStateOf(false) }
    var todayV2Visible by remember { mutableStateOf(false) }
    var weekV2Visible by remember { mutableStateOf(false) }
    var startBlockVisible by remember { mutableStateOf(false) }
    var timeBudgetVisible by remember { mutableStateOf(false) }
    var maintenanceSection by remember { mutableStateOf<MaintenanceSection?>(null) }
    var editSleepDay by remember { mutableStateOf<WeeklySleepDay?>(null) }
    var traceCameraDrinkOnly by remember { mutableStateOf<Boolean?>(null) }
    var pendingCameraDrinkOnly by remember { mutableStateOf<Boolean?>(null) }
    var pendingImportDrinkOnly by remember { mutableStateOf<Boolean?>(null) }

    fun triggerProcessingOverlay(label: String = PROCESSING_LABELS[manualProcessingNonce % PROCESSING_LABELS.size]) {
        manualProcessingLabel = label
        manualProcessingActive = true
        manualProcessingNonce += 1
    }

    fun refreshDiagnostics() {
        diagnosticsText = OttoDiagnostics.readRecent(context)
    }

    LaunchedEffect(manualProcessingActive, manualProcessingNonce) {
        if (!manualProcessingActive) return@LaunchedEffect
        val activeNonce = manualProcessingNonce
        delay(PROCESSING_OVERLAY_DURATION_MS)
        if (manualProcessingNonce == activeNonce) {
            manualProcessingActive = false
        }
    }

    val processingActive = manualProcessingActive || isUpdating || isTranscribing || isRecording
    val processingLabel = when {
        isUpdating -> "SYNCING"
        isTranscribing -> "TRANSCRIBING"
        isMemoRecording -> "RECORDING"
        isRecording -> "LISTENING"
        else -> manualProcessingLabel
    }

    fun attemptLaunch(app: AppInfo): Boolean {
        return when {
            OttoPolicyController.isBlockedApp(app.packageName) -> {
                statusMessage = "${app.label} is blocked."
                Toast.makeText(context, "${app.label} is blocked.", Toast.LENGTH_SHORT).show()
                false
            }

            OttoPolicyController.requiresLaunchGateCode(context, app.packageName) -> {
                gatedLaunchApp = app
                launchGateInput = ""
                launchGateCode = OttoPolicyController.newLaunchGateCode(context, app.packageName).orEmpty()
                launchGateError = null
                statusMessage = OttoPolicyController.launchGatePrompt(context, app.packageName)
                false
            }

            else -> {
                context.launchApp(app)
                true
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingPermissionAction
        pendingPermissionAction = null
        if (granted) {
            action?.invoke()
        } else {
            statusMessage = "Microphone permission is required for voice search."
            voiceHudVisible = false
            isVoiceMode = false
            voiceCaptureKind = null
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val drinkOnly = pendingCameraDrinkOnly
        pendingCameraDrinkOnly = null
        if (granted && drinkOnly != null) {
            traceCameraDrinkOnly = drinkOnly
        } else if (!granted) {
            statusMessage = "Camera permission is required for photo capture."
        }
    }

    fun loadWeather() {
        scope.launch {
            weeklyWeather = weatherRepository.upcomingForecast()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadWeather()
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            loadWeather()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val drinkOnly = pendingImportDrinkOnly ?: false
        pendingImportDrinkOnly = null
        uri?.let {
            traceViewModel.importPhoto(it, drinkOnly)
            statusMessage = if (drinkOnly) "Drink saved." else "Food saved. Review later."
        }
    }

    DisposableEffect(voiceManager) {
        onDispose { voiceManager.dispose() }
    }

    DisposableEffect(lifecycleOwner, traceViewModel, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                traceViewModel.onLauncherVisible()
                // Drain any voice memos recorded offline: now that the launcher is foreground again,
                // transcribe whatever is still queued if a key and connectivity are available.
                if (OttoConfig.hasGroqKey) {
                    traceViewModel.processQueuedMemos(
                        transcriber = { audio -> voiceManager.transcribe(audio, deleteAfter = false) },
                        onTranscript = processVoiceTranscript
                    )
                    // Mine transcribed memos for recurring Otto use cases into local-only storage.
                    // Backfills the existing transcript history on first run, then only handles new
                    // memos; nothing here is ever published. Stays decoupled from capture and from the
                    // transcription drain above (it reads already-PROCESSED memos), so memos transcribed
                    // this resume are mined on the next one.
                    traceViewModel.extractUseCases(useCaseAgent::extract)
                }
                greyscaleEnabled = context.isGreyscaleEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Claim the assist gesture once per launch so raising the phone and triggering the assistant
    // jumps straight into a discreet voice note. The slot starts empty on this device; no-ops if
    // it's already Otto or the write isn't permitted.
    LaunchedEffect(Unit) {
        if (!context.isOttoAssistant()) {
            context.registerOttoAsAssistant()
        }
    }

    val normalizedQuery = remember(query) { query.trim() }
    val appDescriptors = remember(apps) {
        apps.map { app ->
            AppDescriptor(
                label = app.label,
                packageName = app.packageName,
                activityName = app.activityName
            )
        }
    }
    LaunchedEffect(appDescriptors) {
        launcherViewModel.seedApps(appDescriptors)
    }
    val commandResult = remember(normalizedQuery, appDescriptors, homeState.policies) {
        launcherViewModel.resolveCommand(normalizedQuery, appDescriptors)
    }
    fun handleVoiceResult(cleaned: String) {
        isVoiceMode = true
        query = cleaned
        statusMessage = "Working on \"$cleaned\"..."
        triggerProcessingOverlay("PROCESSING")
        scope.launch {
            val result = voiceAgent.resolve(cleaned, apps)
            result
                .onSuccess { app ->
                    if (attemptLaunch(app)) {
                        statusMessage = "Opening ${app.label}"
                    }
                }
                .onFailure { error ->
                    statusMessage = error.message ?: "Couldn't pick an app."
                }
            if (!isRecording && !isTranscribing) {
                voiceHudVisible = false
            }
        }
    }

    fun startVoiceRecording(captureKind: InboxKind? = null) {
        val start = {
            val result = voiceManager.startRecording()
            if (result) {
                voiceCaptureKind = captureKind
                voiceHudVisible = true
                statusMessage = if (captureKind == null) "Listening..." else "Listening..."
                isRecording = true
            } else {
                statusMessage = "Unable to access microphone."
                voiceHudVisible = false
                isVoiceMode = false
                voiceCaptureKind = null
            }
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            start()
        } else {
            pendingPermissionAction = start
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun startVoiceMemoRecording() {
        val start = {
            val result = voiceManager.startRecording()
            if (result) {
                isMemoRecording = true
                isRecording = true
                voiceHudVisible = true
                statusMessage = "Recording note..."
            } else {
                statusMessage = "Unable to access microphone."
                isMemoRecording = false
                isRecording = false
                voiceHudVisible = false
            }
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            start()
        } else {
            pendingPermissionAction = start
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun stopVoiceMemoRecording() {
        val file = voiceManager.stopRecording()
        isMemoRecording = false
        isRecording = false
        voiceHudVisible = false
        if (file == null) {
            statusMessage = "No audio captured."
            return
        }
        // Transcribe with Groq Whisper when a key is configured; otherwise just queue the audio.
        val transcriber: (suspend (File) -> Result<String>)? =
            if (OttoConfig.hasGroqKey) {
                { audio -> voiceManager.transcribe(audio, deleteAfter = false) }
            } else {
                null
            }
        traceViewModel.recordVoiceMemo(file, transcriber, processVoiceTranscript)
        statusMessage = if (transcriber != null) "Note saved." else "Note queued."
    }

    fun openTraceCamera(isDrinkOnly: Boolean) {
        val start = { traceCameraDrinkOnly = isDrinkOnly }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            start()
        } else {
            pendingCameraDrinkOnly = isDrinkOnly
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun importTracePhoto(isDrinkOnly: Boolean) {
        pendingImportDrinkOnly = isDrinkOnly
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    fun findAppInfo(result: AppCommandResult): AppInfo? {
        return apps.firstOrNull {
            it.packageName == result.packageName && it.activityName == result.activityName
        } ?: apps.firstOrNull { it.packageName == result.packageName }
    }

    fun startOttoUpdate() {
        if (isUpdating) return
        isUpdating = true
        statusMessage = "Checking for Otto updates..."
        scope.launch {
            val result = OttoUpdater.downloadLatestRelease(context)
            isUpdating = false
            result
                .onSuccess { apkFile ->
                    triggerProcessingOverlay("INSTALLING")
                    statusMessage = "Installing update..."
                    context.installApk(apkFile)
                }
                .onFailure { error ->
                    statusMessage = error.message ?: "Update failed."
                }
        }
    }

    fun applyV2PolicyNow() {
        scope.launch {
            withContext(Dispatchers.IO) {
                OttoPolicyController.markPolicyDirty(packageStateChanged = true)
                OttoPolicyController.applyPolicies(context, force = true)
                PolicyRuntime.applyCurrentPolicy(context)
            }
        }
    }

    fun openMaintenance(section: MaintenanceSection) {
        when (section) {
            MaintenanceSection.SETTINGS -> maintenanceSection = section
            MaintenanceSection.LOGS -> {
                refreshDiagnostics()
                diagnosticsVisible = true
            }
            MaintenanceSection.UPDATE -> startOttoUpdate()
        }
    }

    fun startTimeMode(mode: TimeMode) {
        launcherViewModel.startTimeBlock(mode) {
            applyV2PolicyNow()
        }
        statusMessage = when (mode) {
            TimeMode.FOCUS_WORK -> "Focus work"
            TimeMode.RELATIONSHIP -> "Social"
            TimeMode.MOVEMENT -> "Movement"
            TimeMode.REST -> "Rest"
            TimeMode.OPEN -> "Open"
            TimeMode.WIND_DOWN -> "Wind-down"
            TimeMode.SLEEP -> "Sleep"
        }
    }

    fun startTimeCategory(categoryId: String, label: String) {
        launcherViewModel.startCategoryBlock(categoryId, label) {
            applyV2PolicyNow()
        }
        statusMessage = label.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    fun finishTimeBlock() {
        launcherViewModel.finishActiveTimeBlock { minutes ->
            statusMessage = minutes?.let { "Saved: ${formatMinutesHuman(it)}" } ?: "No active block."
            applyV2PolicyNow()
        }
    }

    fun handleAppResult(result: AppCommandResult) {
        // Time-gated apps (Slack, browsers) outside their allowed hours: tapping anywhere shows the
        // 30-letter unlock code instead of a dead-end message. Covers the hidden-Slack tile too.
        val gatedApp = findAppInfo(result)
        if (gatedApp != null && OttoPolicyController.requiresLaunchGateCode(context, gatedApp.packageName)) {
            gatedLaunchApp = gatedApp
            launchGateInput = ""
            launchGateCode = OttoPolicyController.newLaunchGateCode(context, gatedApp.packageName).orEmpty()
            launchGateError = null
            statusMessage = OttoPolicyController.launchGatePrompt(context, gatedApp.packageName)
            query = ""
            return
        }
        when (val gate = result.gate) {
            AppGate.Allowed -> {
                val app = findAppInfo(result)
                if (app != null) {
                    context.launchApp(app)
                    statusMessage = "Opening ${app.label}"
                    query = ""
                } else {
                    statusMessage = "Unable to launch ${result.label}."
                }
            }
            AppGate.AdminHidden -> {
                openMaintenance(MaintenanceSection.SETTINGS)
                query = ""
            }
            AppGate.Blocked -> {
                statusMessage = "blocked by Shield"
            }
            is AppGate.WorkWindowClosed -> {
                statusMessage = "available ${gate.label}"
            }
            is AppGate.Distraction -> {
                gatedDistractionApp = result
                launchGateCode = newDistractionGateCode()
                launchGateInput = ""
                launchGateReason = ""
                launchGateError = null
            }
        }
    }

    fun executeCommand(command: OttoCommand) {
        when (command) {
            OttoCommand.CaptureFood -> openTraceCamera(isDrinkOnly = false)
            is OttoCommand.CaptureFoodWithEnergy -> {
                launcherViewModel.recordManualFoodEnergy(command.kJ)
                statusMessage = "Food saved. Review later."
            }
            OttoCommand.CaptureDrink -> openTraceCamera(isDrinkOnly = true)
            is OttoCommand.SaveWeight -> {
                if (command.kg in 20.0..300.0) {
                    traceViewModel.recordWeight(command.kg)
                    statusMessage = "Weight saved."
                } else {
                    statusMessage = "Weight not saved."
                }
            }
            OttoCommand.StartSleep -> sleepStartVisible = true
            OttoCommand.EndSleep -> {
                launcherViewModel.endSleep { minutes ->
                    statusMessage = minutes?.let { "Sleep saved: ${formatMinutesHuman(it)}" } ?: "Sleep saved."
                    applyV2PolicyNow()
                }
            }
            is OttoCommand.SaveNote -> {
                launcherViewModel.saveNote(command.text)
                statusMessage = "Note saved."
            }
            is OttoCommand.SaveTask -> {
                launcherViewModel.saveTask(command.text)
                statusMessage = "Task saved."
            }
            OttoCommand.OpenToday -> todayV2Visible = true
            OttoCommand.OpenReview -> todayV2Visible = true
            OttoCommand.OpenWeek -> weekV2Visible = true
            OttoCommand.OpenTimeBudget -> timeBudgetVisible = true
            OttoCommand.OpenTimeReview -> todayV2Visible = true
            is OttoCommand.StartTimeBlock -> startTimeMode(command.mode)
            is OttoCommand.StartTimeCategoryBlock -> startTimeCategory(command.categoryId, command.label)
            OttoCommand.FinishTimeBlock -> finishTimeBlock()
            is OttoCommand.SaveTimeBlockDuration -> {
                launcherViewModel.recordTimeDuration(command.categoryId, command.minutes, command.label)
                statusMessage = "Saved: ${formatMinutesHuman(command.minutes)}"
            }
            is OttoCommand.SetMode -> {
                launcherViewModel.setMode(command.mode)
                statusMessage = if (command.mode == OttoMode.FOCUS) "Focus mode" else "Open mode"
            }
            is OttoCommand.LaunchApp -> {
                apps.firstOrNull { it.packageName == command.packageName }?.let(context::launchApp)
            }
            is OttoCommand.ExplainBlockedApp -> {
                statusMessage = "blocked by Shield"
            }
            is OttoCommand.OpenMaintenance -> openMaintenance(command.section)
        }
        query = ""
    }

    fun submitCommand() {
        when (val result = launcherViewModel.resolveCommand(query, appDescriptors)) {
            is CommandResult.BuiltIn -> executeCommand(result.command)
            is CommandResult.AppResults -> result.results.firstOrNull()?.let(::handleAppResult)
                ?: run { statusMessage = "No result." }
            CommandResult.Empty -> Unit
            CommandResult.NoResult -> statusMessage = "No result."
        }
    }

    fun handleTracePrimaryAction() {
        traceCaptureSheetVisible = true
    }

    fun stopRecordingAndTranscribe() {
        val file = voiceManager.stopRecording()
        isRecording = false
        if (file == null) {
            statusMessage = "No audio captured."
            voiceHudVisible = false
            isVoiceMode = false
            return
        }
        isTranscribing = true
        statusMessage = "Transcribing..."
        scope.launch {
            val captureKind = voiceCaptureKind
            val result = voiceManager.transcribe(file)
            isTranscribing = false
            result
                .onSuccess { text ->
                    val cleaned = text.trim()
                    if (cleaned.isNotEmpty()) {
                        if (captureKind == InboxKind.NOTE) {
                            launcherViewModel.saveNote(cleaned)
                            statusMessage = "Note saved."
                            voiceHudVisible = false
                            isVoiceMode = false
                            voiceCaptureKind = null
                        } else if (captureKind == InboxKind.TASK) {
                            launcherViewModel.saveTask(cleaned)
                            statusMessage = "Task saved."
                            voiceHudVisible = false
                            isVoiceMode = false
                            voiceCaptureKind = null
                        } else {
                            handleVoiceResult(cleaned)
                        }
                    } else {
                        statusMessage = "Didn't catch that."
                        voiceHudVisible = false
                        isVoiceMode = false
                        voiceCaptureKind = null
                    }
                }
                .onFailure { error ->
                    statusMessage = error.message ?: "Transcription failed."
                    voiceHudVisible = false
                    isVoiceMode = false
                    voiceCaptureKind = null
                }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ProcessingOverlay(
            active = processingActive,
            label = processingLabel,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastTapTimestamp > TRIPLE_TAP_WINDOW_MS) {
                                tapCount = 0
                            }
                            lastTapTimestamp = now
                            tapCount += 1

                            tapTimeoutJob?.cancel()
                            tapTimeoutJob = scope.launch {
                                delay(TRIPLE_TAP_WINDOW_MS)
                                when (tapCount) {
                                    1 -> if (!isVoiceMode) statusMessage = null
                                    2 -> {
                                        handleTracePrimaryAction()
                                    }
                                    3 -> {
                                        transcriptViewerVisible = true
                                    }
                                }
                                tapCount = 0
                                tapTimeoutJob = null
                            }
                        }
                    )
                }
        ) {
            HomeScreenV2(
                state = homeState,
                commandResult = commandResult,
                query = query,
                statusMessage = statusMessage,
                onQueryChange = {
                    query = it
                    if (it.isNotEmpty()) statusMessage = null
                    isVoiceMode = false
                },
                onSubmitCommand = { submitCommand() },
                onFastCapture = { action ->
                    when (action) {
                        FastCaptureAction.FOOD -> openTraceCamera(isDrinkOnly = false)
                        FastCaptureAction.WEIGHT -> traceWeightVisible = true
                        FastCaptureAction.MOVE -> startTimeMode(TimeMode.MOVEMENT)
                        FastCaptureAction.NOTE -> noteCaptureVisible = true
                    }
                },
                onFastCaptureLongPress = { action ->
                    if (action == FastCaptureAction.NOTE) {
                        startVoiceRecording(InboxKind.NOTE)
                    }
                },
                onLedgerAction = { action ->
                    when (action) {
                        LedgerAction.NOW -> startBlockVisible = true
                        LedgerAction.SLEEP -> {
                            if (homeState.mode == OttoMode.SLEEP) {
                                launcherViewModel.endSleep { minutes ->
                                    statusMessage = minutes?.let { "Sleep saved: ${formatMinutesHuman(it)}" } ?: "Sleep saved."
                                    applyV2PolicyNow()
                                }
                            } else {
                                sleepStartVisible = true
                            }
                        }
                        LedgerAction.PEOPLE -> startTimeMode(TimeMode.RELATIONSHIP)
                        LedgerAction.MOVE -> startTimeMode(TimeMode.MOVEMENT)
                        LedgerAction.DRIFT -> {
                            if (homeState.timeLedger.hasUsageAccess) {
                                todayV2Visible = true
                            } else {
                                launcherViewModel.openUsageAccessSettings()
                            }
                        }
                    }
                },
                onTapSleepDay = { day -> editSleepDay = day },
                onAppResult = { handleAppResult(it) },
                onAppLongPress = { result ->
                    findAppInfo(result)?.let { context.openAppInfo(it) }
                },
                onOttoLongPress = { openMaintenance(MaintenanceSection.SETTINGS) },
                onQuest = { context.startActivity(Intent(context, QuestActivity::class.java)) },
                onWake = {
                    launcherViewModel.endSleep { minutes ->
                        statusMessage = minutes?.let { "Sleep saved: ${formatMinutesHuman(it)}" } ?: "Sleep saved."
                        applyV2PolicyNow()
                    }
                },
                onEmergency = {
                    launcherViewModel.endSleep {
                        statusMessage = "Sleep saved."
                        applyV2PolicyNow()
                    }
                    context.openSystemSettings()
                },
                weeklyWeather = weeklyWeather,
                greyscaleEnabled = greyscaleEnabled,
                onToggleGreyscale = {
                    if (greyscaleEnabled) {
                        // Turning greyscale off is deliberately high-friction: type a code.
                        greyscaleDisableCode = newDistractionGateCode()
                        greyscaleDisableInput = ""
                        greyscaleDisableError = null
                        greyscaleDisableVisible = true
                    } else {
                        if (context.setGreyscaleEnabled(true)) {
                            greyscaleEnabled = true
                            statusMessage = "Greyscale on."
                        } else {
                            statusMessage = "Couldn't enable greyscale."
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (voiceHudVisible || isRecording || isTranscribing) {
                VoiceControlChip(
                    isRecording = isRecording,
                    isTranscribing = isTranscribing,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .imePadding(),
                    onTap = {
                        if (isRecording) {
                            if (isMemoRecording) {
                                stopVoiceMemoRecording()
                            } else {
                                stopRecordingAndTranscribe()
                            }
                        }
                    }
                )
            }

            gatedDistractionApp?.let { app ->
                val driftRow = homeState.timeLedger.rows.firstOrNull { it.categoryId == TimeCategoryIds.DIGITAL_DRIFT }
                val driftCap = homeState.timeLedger.digitalDriftCapMinutes
                DistractionGateDialog(
                    app = app,
                    challengeCode = launchGateCode,
                    driftText = driftRow?.value,
                    isOverDriftCap = driftCap != null && homeState.timeLedger.digitalDriftMinutes >= driftCap,
                    onDismiss = {
                        gatedDistractionApp = null
                        launchGateInput = ""
                        launchGateReason = ""
                        launchGateCode = ""
                        launchGateError = null
                    },
                    onOpen = { reason, timeboxMinutes ->
                        launcherViewModel.recordDistractionSession(
                            packageName = app.packageName,
                            reason = reason,
                            timeboxMinutes = timeboxMinutes
                        )
                        DeviceOwnerController(context).hidePackage(app.packageName, false)
                        DeviceOwnerController(context).suspendPackages(listOf(app.packageName), false)
                        findAppInfo(app)?.let { context.launchApp(it) }
                        gatedDistractionApp = null
                        launchGateInput = ""
                        launchGateReason = ""
                        launchGateCode = ""
                        launchGateError = null
                        query = ""
                        statusMessage = "Opening ${app.label}"
                    }
                )
            }

            gatedLaunchApp?.let { app ->
                AlertDialog(
                    onDismissRequest = {
                        gatedLaunchApp = null
                        launchGateInput = ""
                        launchGateCode = ""
                        launchGateError = null
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val isValid = OttoPolicyController.verifyLaunchGateCode(
                                    context = context,
                                    packageName = app.packageName,
                                    attemptedCode = launchGateInput,
                                    expectedCode = launchGateCode
                                )
                                if (isValid) {
                                    gatedLaunchApp = null
                                    launchGateError = null
                                    launchGateInput = ""
                                    launchGateCode = ""
                                    context.launchApp(app)
                                    statusMessage = "Opening ${app.label}"
                                } else {
                                    launchGateError = OttoPolicyController.launchGateFailureMessage(
                                        context,
                                        app.packageName
                                    )
                                }
                            }
                        ) {
                            Text("Open")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                gatedLaunchApp = null
                                launchGateInput = ""
                                launchGateCode = ""
                                launchGateError = null
                            }
                        ) {
                            Text("Cancel")
                        }
                    },
                    title = {
                        Text("After-Hours Gate")
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                OttoPolicyController.launchGatePrompt(context, app.packageName)
                                    ?: "Type the displayed code to continue."
                            )
                            Text(
                                text = OttoPolicyController.formatLaunchGateCode(launchGateCode),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = launchGateInput,
                                onValueChange = {
                                    launchGateInput = OttoPolicyController
                                        .normalizeLaunchGateCode(it)
                                        .take(launchGateCode.length)
                                    launchGateError = null
                                },
                                label = { Text("Code") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters,
                                    imeAction = ImeAction.Done
                                )
                            )
                            launchGateError?.let { error ->
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                )
            }

            if (greyscaleDisableVisible) {
                AlertDialog(
                    onDismissRequest = {
                        greyscaleDisableVisible = false
                        greyscaleDisableInput = ""
                        greyscaleDisableError = null
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (greyscaleDisableInput == greyscaleDisableCode) {
                                    if (context.setGreyscaleEnabled(false)) {
                                        greyscaleEnabled = false
                                        statusMessage = "Greyscale off."
                                    } else {
                                        statusMessage = "Couldn't disable greyscale."
                                    }
                                    greyscaleDisableVisible = false
                                    greyscaleDisableInput = ""
                                    greyscaleDisableError = null
                                } else {
                                    greyscaleDisableError = "Code does not match."
                                }
                            }
                        ) {
                            Text("Disable")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                greyscaleDisableVisible = false
                                greyscaleDisableInput = ""
                                greyscaleDisableError = null
                            }
                        ) {
                            Text("Keep greyscale")
                        }
                    },
                    title = { Text("Disable greyscale?") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Type the code to turn colour back on.")
                            Text(
                                text = OttoPolicyController.formatLaunchGateCode(greyscaleDisableCode),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = greyscaleDisableInput,
                                onValueChange = {
                                    greyscaleDisableInput = OttoPolicyController
                                        .normalizeLaunchGateCode(it)
                                        .take(greyscaleDisableCode.length)
                                    greyscaleDisableError = null
                                },
                                label = { Text("Code") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters,
                                    imeAction = ImeAction.Done
                                )
                            )
                            greyscaleDisableError?.let { error ->
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                )
            }

            // ── Passkey prompt dialog ──
            if (secretTapPhase == 2) {
                AlertDialog(
                    onDismissRequest = {
                        secretTapPhase = 0
                        secretPasskeyInput = ""
                        secretConfirmPasskey = ""
                        secretPasskeyError = null
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (secretIsNewPasskey) {
                                    if (secretPasskeyInput.length < 4) {
                                        secretPasskeyError = "Passkey must be at least 4 characters."
                                    } else if (secretPasskeyInput != secretConfirmPasskey) {
                                        secretPasskeyError = "Passkeys don't match."
                                    } else {
                                        SecretNoteStore.setPasskey(context, secretPasskeyInput)
                                        secretActivePasskey = secretPasskeyInput
                                        secretNotesText = ""
                                        secretTapPhase = 3
                                        secretPasskeyError = null
                                    }
                                } else {
                                    if (SecretNoteStore.verifyPasskey(context, secretPasskeyInput)) {
                                        secretActivePasskey = secretPasskeyInput
                                        secretNotesText = SecretNoteStore.loadNotes(context, secretPasskeyInput)
                                        secretTapPhase = 3
                                        secretPasskeyError = null
                                    } else {
                                        secretPasskeyError = "Wrong passkey."
                                    }
                                }
                            }
                        ) {
                            Text("Unlock")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                secretTapPhase = 0
                                secretPasskeyInput = ""
                                secretConfirmPasskey = ""
                                secretPasskeyError = null
                            }
                        ) {
                            Text("Cancel")
                        }
                    },
                    title = { Text(if (secretIsNewPasskey) "Create Passkey" else "Enter Passkey") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (secretIsNewPasskey) {
                                Text("Set a passkey for your secret notes.", style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedTextField(
                                value = secretPasskeyInput,
                                onValueChange = {
                                    secretPasskeyInput = it
                                    secretPasskeyError = null
                                },
                                label = { Text("Passkey") },
                                singleLine = true,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                            )
                            if (secretIsNewPasskey) {
                                OutlinedTextField(
                                    value = secretConfirmPasskey,
                                    onValueChange = {
                                        secretConfirmPasskey = it
                                        secretPasskeyError = null
                                    },
                                    label = { Text("Confirm Passkey") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation()
                                )
                            }
                            secretPasskeyError?.let { error ->
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                )
            }

            // ── Secret notes editor dialog ──
            if (secretTapPhase == 3) {
                AlertDialog(
                    onDismissRequest = {
                        SecretNoteStore.saveNotes(context, secretActivePasskey, secretNotesText)
                        secretTapPhase = 0
                        secretActivePasskey = ""
                        secretNotesText = ""
                        secretPasskeyInput = ""
                        secretNotesRevealed = false
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                SecretNoteStore.saveNotes(context, secretActivePasskey, secretNotesText)
                                secretTapPhase = 0
                                secretActivePasskey = ""
                                secretNotesText = ""
                                secretPasskeyInput = ""
                                secretNotesRevealed = false
                                Toast.makeText(context, "Saved.", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Save & Close")
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Notes")
                            TextButton(onClick = { secretNotesRevealed = !secretNotesRevealed }) {
                                Text(if (secretNotesRevealed) "Hide" else "Show")
                            }
                        }
                    },
                    text = {
                        val scrollState = rememberScrollState()
                        OutlinedTextField(
                            value = secretNotesText,
                            onValueChange = { secretNotesText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState),
                            minLines = 8,
                            maxLines = 20,
                            placeholder = { Text("Write something...") },
                            visualTransformation = if (secretNotesRevealed) {
                                androidx.compose.ui.text.input.VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            }
                        )
                    }
                )
            }

            if (diagnosticsVisible) {
                AlertDialog(
                    onDismissRequest = { diagnosticsVisible = false },
                    confirmButton = {
                        TextButton(onClick = { diagnosticsVisible = false }) {
                            Text("Close")
                        }
                    },
                    title = {
                        Text("Diagnostics")
                    },
                    text = {
                        val scrollState = rememberScrollState()
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Local Otto logs are stored on-device and also emitted to Logcat under tag Otto.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "ADB: adb logcat -s Otto\nFile: ${OttoDiagnostics.filePath(context)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { refreshDiagnostics() }) {
                                    Text("Refresh")
                                }
                                TextButton(
                                    onClick = {
                                        context.sharePlainText(
                                            title = "Otto Diagnostics",
                                            text = diagnosticsText.ifBlank { "No diagnostics yet." }
                                        )
                                    }
                                ) {
                                    Text("Share")
                                }
                                TextButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(diagnosticsText))
                                        Toast.makeText(context, "Diagnostics copied.", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text("Copy")
                                }
                                TextButton(
                                    onClick = {
                                        OttoDiagnostics.clear(context)
                                        refreshDiagnostics()
                                    }
                                ) {
                                    Text("Clear")
                                }
                            }
                            SelectionContainer {
                                Text(
                                    text = diagnosticsText.ifBlank { "No diagnostics yet." },
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(scrollState)
                                )
                            }
                        }
                    }
                )
            }

            if (feedbackVisible) {
                AlertDialog(
                    onDismissRequest = {
                        if (!feedbackSending) {
                            feedbackVisible = false
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = !feedbackSending && feedbackText.isNotBlank(),
                            onClick = {
                                val note = feedbackText
                                feedbackSending = true
                                scope.launch {
                                    val result = FeedbackSubmitter.submit(note, installedVersionName)
                                    feedbackSending = false
                                    result
                                        .onSuccess {
                                            feedbackText = ""
                                            feedbackVisible = false
                                            Toast.makeText(context, "Feedback sent.", Toast.LENGTH_SHORT).show()
                                        }
                                        .onFailure {
                                            Toast.makeText(
                                                context,
                                                "Couldn't send feedback. Check connection or token.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                }
                            }
                        ) {
                            Text(if (feedbackSending) "Sending..." else "Send")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            enabled = !feedbackSending,
                            onClick = { feedbackVisible = false }
                        ) {
                            Text("Cancel")
                        }
                    },
                    title = { Text("Send feedback") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!FeedbackSubmitter.isConfigured) {
                                Text(
                                    text = "No feedback token configured. Add GITHUB_FEEDBACK_TOKEN to .env and rebuild.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            OutlinedTextField(
                                value = feedbackText,
                                onValueChange = { feedbackText = it },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 4,
                                maxLines = 12,
                                enabled = !feedbackSending,
                                placeholder = { Text("What's working, what's not, what you want...") }
                            )
                        }
                    }
                )
            }

            if (noteCaptureVisible) {
                NoteCaptureSheet(
                    onDismiss = { noteCaptureVisible = false },
                    onSaveNote = {
                        launcherViewModel.saveNote(it)
                        statusMessage = "Note saved."
                    }
                )
            }

            if (sleepStartVisible) {
                SleepStartDialog(
                    onDismiss = { sleepStartVisible = false },
                    onStartSleep = {
                        launcherViewModel.startSleep {
                            applyV2PolicyNow()
                        }
                        statusMessage = "Sleep mode"
                    }
                )
            }

            if (foodReviewVisible) {
                FoodReviewScreen(
                    entries = homeState.unresolvedFood,
                    onDismiss = { foodReviewVisible = false },
                    onSetEnergy = { id, energy ->
                        launcherViewModel.updateFoodEnergy(id, energy)
                        statusMessage = if (energy == null) "Saved." else "Saved."
                    }
                )
            }

            if (inboxReviewVisible) {
                InboxReviewScreen(
                    items = homeState.inbox,
                    onDismiss = { inboxReviewVisible = false },
                    onState = { id, state -> launcherViewModel.updateInboxState(id, state) }
                )
            }

            if (transcriptViewerVisible) {
                TranscriptViewerScreen(
                    memos = homeState.recentTranscripts,
                    onDismiss = { transcriptViewerVisible = false },
                    onCopy = { memo ->
                        clipboardManager.setText(AnnotatedString(memo.transcript.orEmpty()))
                        transcriptViewerVisible = false
                        statusMessage = "Copied."
                    }
                )
            }

            if (todayV2Visible) {
                TodayTimeReviewScreen(
                    review = homeState.dailyTimeReview,
                    onDismiss = { todayV2Visible = false },
                    onPulse = { launcherViewModel.recordTimeAffluence(it) }
                )
            }

            if (weekV2Visible) {
                WeeklyTimeReviewScreen(
                    ledger = homeState.weeklyTimeLedger,
                    onDismiss = { weekV2Visible = false }
                )
            }

            if (timeBudgetVisible) {
                TimeBudgetScreen(
                    today = homeState.timeLedger,
                    week = homeState.weeklyTimeLedger,
                    onDismiss = { timeBudgetVisible = false }
                )
            }

            if (startBlockVisible) {
                StartBlockDialog(
                    onDismiss = { startBlockVisible = false },
                    onStartMode = {
                        startBlockVisible = false
                        startTimeMode(it)
                    },
                    onStartCategory = { categoryId, label ->
                        startBlockVisible = false
                        startTimeCategory(categoryId, label)
                    }
                )
            }

            maintenanceSection?.let { section ->
                SettingsHome(
                    section = when (section) {
                        MaintenanceSection.SETTINGS -> "Settings"
                        MaintenanceSection.LOGS -> "Logs"
                        MaintenanceSection.UPDATE -> "Update"
                    },
                    currentVersion = installedVersionName,
                    lockdownRemaining = if (OttoPolicyController.isLockdownActive(context)) {
                        formatMinutesHuman((OttoPolicyController.lockdownRemainingMillis(context) / 60_000L).toInt())
                    } else {
                        null
                    },
                    onStartLockdown = { minutes ->
                        OttoPolicyController.startLockdown(context, minutes)
                        maintenanceSection = null
                        applyV2PolicyNow()
                        statusMessage = "Locked down for ${formatMinutesHuman(minutes)}."
                    },
                    onDismiss = { maintenanceSection = null },
                    onOpenSystemSettings = { context.openSystemSettings() },
                    onOpenUsageAccess = { launcherViewModel.openUsageAccessSettings() },
                    onOpenLogs = {
                        refreshDiagnostics()
                        diagnosticsVisible = true
                    },
                    onSendFeedback = {
                        maintenanceSection = null
                        feedbackVisible = true
                    },
                    onUpdate = { startOttoUpdate() }
                )
            }

            if (traceCaptureSheetVisible) {
                TraceCaptureSheet(
                    state = traceState,
                    onDismiss = { traceCaptureSheetVisible = false },
                    onFoodCamera = {
                        traceCaptureSheetVisible = false
                        openTraceCamera(isDrinkOnly = false)
                    },
                    onDrinkCamera = {
                        traceCaptureSheetVisible = false
                        openTraceCamera(isDrinkOnly = true)
                    },
                    onImportFood = {
                        traceCaptureSheetVisible = false
                        importTracePhoto(isDrinkOnly = false)
                    },
                    onImportDrink = {
                        traceCaptureSheetVisible = false
                        importTracePhoto(isDrinkOnly = true)
                    },
                    onConfirmSleep = {
                        traceCaptureSheetVisible = false
                        traceViewModel.confirmSleepEstimate()
                        statusMessage = "Sleep saved."
                    },
                    onWeight = {
                        traceCaptureSheetVisible = false
                        traceWeightVisible = true
                    },
                    onSleep = {
                        traceCaptureSheetVisible = false
                        traceSleepVisible = true
                    },
                    onRecordMemo = {
                        traceCaptureSheetVisible = false
                        startVoiceMemoRecording()
                    },
                    queuedMemoCount = queuedMemoCount,
                    onToday = {
                        traceCaptureSheetVisible = false
                        traceTodayVisible = true
                    },
                    onSettings = {
                        traceCaptureSheetVisible = false
                        traceSettingsVisible = true
                    }
                )
            }

            if (traceWeightVisible) {
                TraceWeightDialog(
                    lastWeightKg = traceState.lastWeightKg,
                    onDismiss = { traceWeightVisible = false },
                    onSave = {
                        traceViewModel.recordWeight(it)
                        statusMessage = "Weight saved."
                    }
                )
            }

            if (traceSleepVisible) {
                TraceSleepDialog(
                    estimate = traceState.sleepEstimate,
                    onDismiss = { traceSleepVisible = false },
                    onSave = { startAt, endAt, adjusted ->
                        traceViewModel.recordSleep(startAt, endAt, adjusted)
                        statusMessage = "Sleep saved."
                    }
                )
            }

            editSleepDay?.let { day ->
                val zone = ZoneId.systemDefault()
                val estimate = if (day.startAt != null && day.endAt != null) {
                    SleepEstimate(day.startAt, day.endAt)
                } else {
                    val sleepStart = day.date.atTime(23, 0).atZone(zone).toInstant()
                    val sleepEnd = day.date.plusDays(1).atTime(7, 0).atZone(zone).toInstant()
                    SleepEstimate(sleepStart, sleepEnd)
                }
                TraceSleepDialog(
                    estimate = estimate,
                    onDismiss = { editSleepDay = null },
                    onSave = { startAt, endAt, _ ->
                        if (day.sessionId != null) {
                            launcherViewModel.updateSleepSession(day.sessionId, startAt, endAt)
                        } else {
                            launcherViewModel.recordSleepSession(startAt, endAt)
                        }
                        editSleepDay = null
                        statusMessage = "Sleep saved."
                    }
                )
            }

            if (traceTodayVisible) {
                TraceTodayDialog(
                    state = traceState,
                    onDismiss = { traceTodayVisible = false },
                    onHide = { traceId, hidden -> traceViewModel.setFoodHidden(traceId, hidden) },
                    onDrinkOnly = { traceId, drinkOnly -> traceViewModel.setDrinkOnly(traceId, drinkOnly) },
                    onUpdateNote = { traceId, note -> traceViewModel.updateNote(traceId, note) },
                    onUpdateWeight = { traceId, kilograms ->
                        traceViewModel.updateWeight(traceId, kilograms)
                        statusMessage = "Weight saved."
                    },
                    onUpdateSleep = { traceId, startAt, endAt, adjusted ->
                        traceViewModel.updateSleep(traceId, startAt, endAt, adjusted)
                        statusMessage = "Sleep saved."
                    },
                    onDelete = { traceId ->
                        traceViewModel.deleteTrace(traceId)
                        statusMessage = "Trace deleted."
                    }
                )
            }

            if (traceWeeklyVisible) {
                TraceWeeklyDialog(
                    state = traceState,
                    onDismiss = { traceWeeklyVisible = false }
                )
            }

            if (traceSettingsVisible) {
                TraceSettingsDialog(
                    state = traceState,
                    onDismiss = { traceSettingsVisible = false },
                    onSetCategory = { category, enabled -> traceViewModel.setCategoryEnabled(category, enabled) }
                )
            }
        }

        traceCameraDrinkOnly?.let { drinkOnly ->
                TraceCameraOverlay(
                isDrinkOnly = drinkOnly,
                createOutputFile = { traceViewModel.createCameraImageFile() },
                onCaptured = { file ->
                    traceViewModel.recordCameraPhoto(file, drinkOnly)
                    traceCameraDrinkOnly = null
                    statusMessage = if (drinkOnly) "Drink saved." else "Food saved. Review later."
                },
                onCancel = { traceCameraDrinkOnly = null },
                onError = { statusMessage = it }
            )
        }
    }
}


private fun handleDoubleTap(apps: List<AppInfo>, onLaunch: (AppInfo) -> Boolean): String {
    val target = findPreferredApp(apps)
    return if (target != null) {
        if (onLaunch(target)) "Opening ${target.label}" else "Open ${target.label} from the gate prompt."
    } else "\\\"$TRIPLE_TAP_APP_LABEL\\\" app not found."
}
private fun newDistractionGateCode(): String {
    val random = SecureRandom()
    return buildString(DISTRACTION_GATE_CODE_LENGTH) { repeat(DISTRACTION_GATE_CODE_LENGTH) { append(DISTRACTION_GATE_ALPHABET[random.nextInt(DISTRACTION_GATE_ALPHABET.length)]) } }
}
private fun formatMinutesHuman(minutes: Int): String { val safe = minutes.coerceAtLeast(0); return "${safe / 60}h %02dm".format(safe % 60) }
private const val TRIPLE_TAP_WINDOW_MS = 400L
private const val TRIPLE_TAP_APP_LABEL = "Pad"
private const val DISTRACTION_GATE_CODE_LENGTH = 15
private const val DISTRACTION_GATE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ"
private const val PROCESSING_OVERLAY_DURATION_MS = 4200L
private val PROCESSING_LABELS = listOf("PROCESSING", "VECTORING", "SCANNING", "CALIBRATING")
