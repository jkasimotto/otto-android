package com.otto.launcher

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
import android.media.MediaRecorder
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
import com.otto.launcher.domain.mode.OttoMode
import com.otto.launcher.domain.policy.AppDescriptor
import com.otto.launcher.domain.policy.AppGate
import com.otto.launcher.domain.time.TimeCategoryIds
import com.otto.launcher.domain.time.TimeMode
import com.otto.launcher.domain.trace.InboxKind
import com.otto.launcher.domain.trace.InboxState
import com.otto.launcher.domain.trace.WeeklySleepDay
import com.otto.launcher.trace.domain.SleepEstimate
import java.time.ZoneId
import com.otto.launcher.data.policy.PolicyRuntime
import com.otto.launcher.device.DeviceOwnerController
import com.otto.launcher.ui.capture.NoteCaptureSheet
import com.otto.launcher.ui.capture.SleepStartDialog
import com.otto.launcher.ui.home.DistractionGateDialog
import com.otto.launcher.ui.home.FastCaptureAction
import com.otto.launcher.ui.home.HomeScreenV2
import com.otto.launcher.ui.home.LauncherViewModel
import com.otto.launcher.ui.home.LedgerAction
import com.otto.launcher.ui.review.FoodReviewScreen
import com.otto.launcher.ui.review.InboxReviewScreen
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

class MainActivity : ComponentActivity() {
    private var launcherApps by mutableStateOf<List<AppInfo>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launcherApps = loadLauncherApps(packageManager)
        val versionLabel = currentVersionName()
        OttoDiagnostics.info(
            applicationContext,
            "MainActivity",
            "Activity created for Otto $versionLabel; restored=${savedInstanceState != null}; " +
                OttoDiagnostics.processMarker(applicationContext)
        )
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            OttoLauncherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LauncherScreen(apps = launcherApps)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        OttoDiagnostics.info(applicationContext, "MainActivity", "Launcher resumed.")
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                OttoPolicyController.applyPolicies(this@MainActivity)
                PolicyRuntime.applyCurrentPolicy(this@MainActivity)
            }
            refreshLauncherApps(forceRefresh = true)
            OttoPolicyController.startWebsiteVpnIfNeeded(this@MainActivity)
            OttoPolicyController.syncLockTaskMode(this@MainActivity)
        }
    }

    override fun onDestroy() {
        OttoDiagnostics.info(
            applicationContext,
            "MainActivity",
            "Activity destroyed; finishing=$isFinishing changingConfigurations=$isChangingConfigurations"
        )
        super.onDestroy()
    }

    private fun refreshLauncherApps(forceRefresh: Boolean = false) {
        launcherApps = loadLauncherApps(packageManager, forceRefresh)
    }
}

@Composable
private fun LauncherScreen(
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
    val homeState by launcherViewModel.uiState.collectAsState()
    var tapCount by remember { mutableStateOf(0) }
    var tapTimeoutJob by remember { mutableStateOf<Job?>(null) }
    var lastTapTimestamp by remember { mutableStateOf(0L) }

    var query by rememberSaveable { mutableStateOf("") }
    var voiceHudVisible by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
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
    var greyscaleEnabled by remember { mutableStateOf(context.isGreyscaleEnabled()) }

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
                greyscaleEnabled = context.isGreyscaleEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                            stopRecordingAndTranscribe()
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
                    onDismiss = { maintenanceSection = null },
                    onOpenSystemSettings = { context.openSystemSettings() },
                    onOpenUsageAccess = { launcherViewModel.openUsageAccessSettings() },
                    onOpenTimeBudget = {
                        maintenanceSection = null
                        timeBudgetVisible = true
                    },
                    onOpenTimeReview = {
                        maintenanceSection = null
                        todayV2Visible = true
                    },
                    onOpenWeeklyTimeReview = {
                        maintenanceSection = null
                        weekV2Visible = true
                    },
                    onOpenLogs = {
                        refreshDiagnostics()
                        diagnosticsVisible = true
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

@Composable
private fun QuickActionRow(
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
private fun QuickActionIcon(
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
private fun QuickActionChip(
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
private fun AppRow(
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
private fun MinimalSearchField(
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
private fun VoiceControlChip(
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

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String
)

private object LauncherAppsCache {
    private const val CACHE_TTL_MS = 30_000L
    private val cacheLock = Any()

    @Volatile
    private var cachedApps: List<AppInfo>? = null

    @Volatile
    private var cacheExpiresAtElapsedRealtime = 0L

    fun load(packageManager: PackageManager, forceRefresh: Boolean = false): List<AppInfo> {
        val now = SystemClock.elapsedRealtime()
        val cached = cachedApps
        if (!forceRefresh && cached != null && now < cacheExpiresAtElapsedRealtime) {
            return cached
        }

        return synchronized(cacheLock) {
            val synchronizedNow = SystemClock.elapsedRealtime()
            val synchronizedCached = cachedApps
            if (!forceRefresh && synchronizedCached != null && synchronizedNow < cacheExpiresAtElapsedRealtime) {
                synchronizedCached
            } else {
                buildLauncherApps(packageManager).also { freshApps ->
                    cachedApps = freshApps
                    cacheExpiresAtElapsedRealtime = synchronizedNow + CACHE_TTL_MS
                }
            }
        }
    }

    fun invalidate() {
        synchronized(cacheLock) {
            cachedApps = null
            cacheExpiresAtElapsedRealtime = 0L
        }
    }
}

internal fun invalidateLauncherAppsCache() {
    LauncherAppsCache.invalidate()
}

internal fun loadLauncherApps(
    packageManager: PackageManager,
    forceRefresh: Boolean = false
): List<AppInfo> {
    return LauncherAppsCache.load(packageManager, forceRefresh)
}

private fun buildLauncherApps(packageManager: PackageManager): List<AppInfo> {
    val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    val launcherApps = queryActivities(packageManager, launcherIntent)

    return launcherApps
        .distinctBy { it.activityName }
        .filter { shouldDisplayApp(packageManager, it) }
        .sortedBy { it.label.lowercase(Locale.getDefault()) }
}

private fun queryActivities(
    packageManager: PackageManager,
    intent: Intent
): List<AppInfo> {
    val resolvedActivities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
    }

    return resolvedActivities.map { resolveInfo ->
        val label = resolveInfo.loadLabel(packageManager)?.toString()
            ?: resolveInfo.activityInfo.loadLabel(packageManager).toString()
        AppInfo(
            label = label,
            packageName = resolveInfo.activityInfo.packageName,
            activityName = resolveInfo.activityInfo.name
        )
    }
}

private fun shouldDisplayApp(packageManager: PackageManager, appInfo: AppInfo): Boolean {
    if (appInfo.packageName.contains("webapk", ignoreCase = true)) {
        return true
    }

    val normalizedPackage = appInfo.packageName.lowercase(Locale.getDefault())
    if (OttoPolicyController.shouldHideFromLauncher(normalizedPackage)) {
        return false
    }

    return runCatching {
        val app = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                appInfo.packageName,
                PackageManager.ApplicationInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(appInfo.packageName, 0)
        }
        if (!app.enabled) return@runCatching false

        val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystem = (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        if (!isSystem || isUpdatedSystem) return@runCatching true

        val normalizedLabel = appInfo.label.lowercase(Locale.getDefault())
        val isAllowlisted = ALLOWED_SYSTEM_PACKAGES.any { normalizedPackage.startsWith(it) } ||
            ALLOWED_SYSTEM_LABEL_KEYWORDS.any { keyword ->
                normalizedLabel.contains(keyword) || normalizedPackage.contains(keyword)
            }

        isAllowlisted
    }.getOrDefault(false)
}

private fun Context.launchApp(appInfo: AppInfo) {
    val launchIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        setClassName(appInfo.packageName, appInfo.activityName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    runCatching { startActivity(launchIntent) }
        .onFailure {
            Toast.makeText(
                this,
                getString(R.string.launch_error, appInfo.label),
                Toast.LENGTH_SHORT
            ).show()
        }
}

private fun Context.openAppInfo(appInfo: AppInfo) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${appInfo.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { startActivity(intent) }
        .onFailure {
            Toast.makeText(
                this,
                getString(R.string.app_info_error, appInfo.label),
                Toast.LENGTH_SHORT
            ).show()
        }
}

private fun Context.openSystemSettings() {
    val intent = Intent(Settings.ACTION_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { startActivity(intent) }
        .onFailure {
            Toast.makeText(
                this,
                "Unable to open Settings.",
                Toast.LENGTH_SHORT
            ).show()
        }
}

private fun Context.isGreyscaleEnabled(): Boolean {
    val enabled = Settings.Secure.getInt(
        contentResolver,
        SETTING_DISPLAY_DALTONIZER_ENABLED,
        0
    ) == 1
    val daltonizer = Settings.Secure.getInt(
        contentResolver,
        SETTING_DISPLAY_DALTONIZER,
        DALTONIZER_DISABLED
    )
    return enabled && daltonizer == DALTONIZER_SIMULATE_MONOCHROMACY
}

private fun Context.setGreyscaleEnabled(enabled: Boolean): Boolean {
    return setGreyscaleViaDevicePolicy(enabled) || setGreyscaleViaSecureSettings(enabled)
}

private fun Context.setGreyscaleViaDevicePolicy(enabled: Boolean): Boolean {
    val appContext = applicationContext
    val dpm = appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    if (!dpm.isDeviceOwnerApp(appContext.packageName)) return false

    val admin = ComponentName(appContext, OttoDeviceAdminReceiver::class.java)
    val daltonizer = if (enabled) DALTONIZER_SIMULATE_MONOCHROMACY else DALTONIZER_DISABLED
    val enabledValue = if (enabled) 1 else 0
    return runCatching {
        dpm.setSecureSetting(admin, SETTING_DISPLAY_DALTONIZER, daltonizer.toString())
        dpm.setSecureSetting(admin, SETTING_DISPLAY_DALTONIZER_ENABLED, enabledValue.toString())
        appContext.hasGreyscaleState(enabled)
    }.getOrDefault(false)
}

private fun Context.setGreyscaleViaSecureSettings(enabled: Boolean): Boolean {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_SECURE_SETTINGS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }

    val daltonizer = if (enabled) DALTONIZER_SIMULATE_MONOCHROMACY else DALTONIZER_DISABLED
    val enabledValue = if (enabled) 1 else 0
    return runCatching {
        Settings.Secure.putInt(contentResolver, SETTING_DISPLAY_DALTONIZER, daltonizer) &&
            Settings.Secure.putInt(contentResolver, SETTING_DISPLAY_DALTONIZER_ENABLED, enabledValue) &&
            hasGreyscaleState(enabled)
    }.getOrDefault(false)
}

private fun Context.hasGreyscaleState(enabled: Boolean): Boolean {
    return if (enabled) {
        isGreyscaleEnabled()
    } else {
        Settings.Secure.getInt(contentResolver, SETTING_DISPLAY_DALTONIZER_ENABLED, 0) == 0
    }
}

private fun Context.openGreyscaleSettings(): Boolean {
    val intents = listOf(
        Intent(COLOR_CORRECTION_SETTINGS_ACTION),
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        Intent(Settings.ACTION_SETTINGS)
    ).map { intent ->
        intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }

    return intents.any { intent ->
        runCatching { startActivity(intent) }.isSuccess
    }
}

private fun Context.sharePlainText(title: String, text: String) {
    val chooserIntent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        },
        title
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    runCatching { startActivity(chooserIntent) }
        .onFailure {
            Toast.makeText(
                this,
                "Unable to share diagnostics.",
                Toast.LENGTH_SHORT
            ).show()
        }
}

private fun Context.installApk(apkFile: File) {
    val packageInstaller = packageManager.packageInstaller
    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
        setAppPackageName(packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
    }
    val sessionId = packageInstaller.createSession(params)

    try {
        packageInstaller.openSession(sessionId).use { session ->
            apkFile.inputStream().use { input ->
                session.openWrite("base.apk", 0, apkFile.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }

            val statusIntent = Intent(this, OttoPackageInstallReceiver::class.java)
            val statusReceiver = PendingIntent.getBroadcast(
                this,
                sessionId,
                statusIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            session.commit(statusReceiver.intentSender)
        }
    } catch (error: Exception) {
        runCatching { packageInstaller.abandonSession(sessionId) }
        Toast.makeText(
            this,
            error.message ?: "Unable to install update.",
            Toast.LENGTH_SHORT
        ).show()
    }
}

private fun handleDoubleTap(
    apps: List<AppInfo>,
    onLaunch: (AppInfo) -> Boolean
): String {
    val target = findPreferredApp(apps)
    return if (target != null) {
        if (onLaunch(target)) {
            "Opening ${target.label}"
        } else {
            "Open ${target.label} from the gate prompt."
        }
    } else {
        "\"$TRIPLE_TAP_APP_LABEL\" app not found."
    }
}

private fun findPreferredApp(apps: List<AppInfo>): AppInfo? {
    val labelMatch = apps.firstOrNull { it.label.equals(TRIPLE_TAP_APP_LABEL, ignoreCase = true) }
    if (labelMatch != null) return labelMatch
    return apps.firstOrNull {
        it.packageName.contains("org.chromium.webapk", ignoreCase = true) &&
            it.label.contains(TRIPLE_TAP_APP_LABEL, ignoreCase = true)
    }
}

private fun fuzzyMatchApps(apps: List<AppInfo>, normalizedQuery: String): List<AppInfo> {
    val scored = apps.map { info ->
        val labelTarget = info.label.lowercase(Locale.getDefault())
        val packageTarget = info.packageName.lowercase(Locale.getDefault())
        val containsBoost = if (labelTarget.contains(normalizedQuery) || packageTarget.contains(normalizedQuery)) 25 else 0
        val labelScore = similarityScore(normalizedQuery, labelTarget)
        val packageScore = similarityScore(normalizedQuery, packageTarget)
        val score = max(labelScore, packageScore) + containsBoost
        info to score
    }.filter { it.second > 0 }

    return scored
        .sortedByDescending { it.second }
        .take(30)
        .map { it.first }
}

private fun similarityScore(query: String, candidate: String): Int {
    if (candidate.isEmpty()) return 0
    val trimmedCandidate = if (candidate.length > query.length * 2) {
        candidate.substring(0, query.length * 2)
    } else {
        candidate
    }
    val distance = levenshteinDistance(query, trimmedCandidate)
    val maxLen = max(query.length, trimmedCandidate.length)
    return (maxLen - distance).coerceAtLeast(0)
}

private fun levenshteinDistance(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length

    val previous = IntArray(b.length + 1) { it }
    val current = IntArray(b.length + 1)

    for (i in a.indices) {
        current[0] = i + 1
        for (j in b.indices) {
            val cost = if (a[i] == b[j]) 0 else 1
            current[j + 1] = min(
                min(current[j] + 1, previous[j + 1] + 1),
                previous[j] + cost
            )
        }
        for (k in previous.indices) {
            previous[k] = current[k]
        }
    }
    return previous[b.length]
}

private fun newDistractionGateCode(): String {
    val random = SecureRandom()
    return buildString(DISTRACTION_GATE_CODE_LENGTH) {
        repeat(DISTRACTION_GATE_CODE_LENGTH) {
            append(DISTRACTION_GATE_ALPHABET[random.nextInt(DISTRACTION_GATE_ALPHABET.length)])
        }
    }
}

private fun formatMinutesHuman(minutes: Int): String {
    val safe = minutes.coerceAtLeast(0)
    return "${safe / 60}h %02dm".format(safe % 60)
}

private class VoiceTranscriptionManager(private val context: Context) {
    private val client = HttpClientProvider.client
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun startRecording(): Boolean {
        stopRecorder(stop = true)
        var initializedRecorder: MediaRecorder? = null
        val file = File.createTempFile("otto_voice_", ".m4a", context.cacheDir)
        val started: Boolean = runCatching {
            @Suppress("DEPRECATION")
            val recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            initializedRecorder = recorder
            outputFile = file
            this.recorder = recorder
            true
        }.getOrElse {
            initializedRecorder?.release()
            file.delete()
            outputFile = null
            false
        }
        if (!started) {
            stopRecorder(stop = false)
        }
        return started
    }

    fun stopRecording(): File? {
        val file = outputFile
        stopRecorder(stop = true)
        outputFile = null
        return file?.takeIf { it.exists() && it.length() > 0 }
    }

    suspend fun transcribe(file: File): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = OttoConfig.groqApiKey
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Missing Groq API key."))
        }

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", "whisper-large-v3-turbo")
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody("audio/mp4".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url(GROQ_TRANSCRIBE_URL)
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        val result: Result<String> = runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Groq error ${response.code}")
                }
                val payload = response.body?.string().orEmpty()
                val text = JSONObject(payload).optString("text")
                if (text.isBlank()) {
                    throw IOException("Groq returned an empty transcription.")
                }
                text
            }
        }
        file.delete()
        result
    }

    fun dispose() {
        stopRecorder(stop = false)
        outputFile?.delete()
        outputFile = null
    }

    private fun stopRecorder(stop: Boolean) {
        runCatching {
            recorder?.apply {
                if (stop) {
                    try {
                        stop()
                    } catch (_: RuntimeException) {
                    }
                }
                release()
            }
        }
        recorder = null
    }

    companion object {
        private const val GROQ_TRANSCRIBE_URL =
            "https://api.groq.com/openai/v1/audio/transcriptions"
    }
}

private fun ComponentActivity.currentVersionName(): String {
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(0)
            ).versionName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName
        }
    }.getOrDefault("dev")
}

private class VoiceLaunchAgent {
    private val client = HttpClientProvider.client

    suspend fun resolve(query: String, apps: List<AppInfo>): Result<AppInfo> =
        withContext(Dispatchers.IO) {
            val fallback = fuzzyMatchApps(apps, query.lowercase(Locale.getDefault()))
                .firstOrNull()
                ?: return@withContext Result.failure(IllegalStateException("No apps match \"$query\""))

            val apiKey = OttoConfig.groqApiKey
            if (apiKey.isBlank()) {
                return@withContext Result.success(fallback)
            }

            val appList = apps.take(MAX_APP_SAMPLE).joinToString(separator = "\n") {
                "- ${it.label} (${it.packageName})"
            }

            val systemPrompt = "You are Otto Launcher on Android. Pick the best matching app from the provided list for the user's intent. Respond strictly with JSON like {\"package\":\"com.example.app\"}."
            val userPrompt = "Apps:\n$appList\n\nUser request: \"$query\"."

            val payload = JSONObject().apply {
                put("model", VOICE_AGENT_MODEL)
                put("temperature", 0.15)
                put(
                    "messages",
                    JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", userPrompt)
                        })
                    }
                )
            }.toString()

            val request = Request.Builder()
                .url(GROQ_CHAT_URL)
                .header("Authorization", "Bearer $apiKey")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            val mapped = runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Groq agent error ${response.code}")
                    }
                    val body = response.body?.string().orEmpty()
                    val message = JSONObject(body)
                        .optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content")
                        .orEmpty()

                    parsePackageFromAgent(message, apps) ?: fallback
                }
            }.recoverCatching { fallback }.getOrElse {
                return@withContext Result.success(fallback)
            }

            Result.success(mapped)
        }

    private fun parsePackageFromAgent(content: String, apps: List<AppInfo>): AppInfo? {
        val jsonText = extractJson(content) ?: return null
        val jsonObject = runCatching { JSONObject(jsonText) }.getOrNull() ?: return null
        val pkg = jsonObject.optString("package")
        val label = jsonObject.optString("label")
        val matchByPackage = pkg.takeIf { it.isNotBlank() }?.let { packageName ->
            apps.firstOrNull { it.packageName.equals(packageName, ignoreCase = true) }
        }
        if (matchByPackage != null) return matchByPackage

        return label.takeIf { it.isNotBlank() }?.let { targetLabel ->
            apps.firstOrNull { it.label.equals(targetLabel, ignoreCase = true) }
        }
    }

    private fun extractJson(content: String): String? {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1)
        }
        return content.trim().takeIf { it.startsWith("{") && it.endsWith("}") }
    }

    companion object {
        private const val MAX_APP_SAMPLE = 40
        private const val VOICE_AGENT_MODEL = "llama-3.1-8b-instant"
        private const val GROQ_CHAT_URL = "https://api.groq.com/openai/v1/chat/completions"
    }
}

private object OttoConfig {
    private const val BUILD_CONFIG_CLASS = "com.otto.launcher.BuildConfig"
    val groqApiKey: String by lazy {
        readBuildConfigField("GROQ_API_KEY") ?: System.getenv("GROQ_API_KEY").orEmpty()
    }

    private fun readBuildConfigField(name: String): String? {
        return runCatching {
            val clazz = Class.forName(BUILD_CONFIG_CLASS)
            clazz.getField(name).get(null) as? String
        }.getOrNull()
    }
}

private object HttpClientProvider {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

private object OttoUpdater {
    private const val RELEASES_URL = "https://api.github.com/repos/jkasimotto/otto-android/releases/latest"
    private const val APK_ASSET_NAME = "app-debug.apk"

    suspend fun downloadLatestRelease(context: Context): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val release = fetchLatestRelease()
            val assetUrl = release.assetUrl
            val targetDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val targetFile = File(targetDir, APK_ASSET_NAME)

            val request = Request.Builder()
                .url(assetUrl)
                .header("Accept", "application/octet-stream")
                .build()

            HttpClientProvider.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Update download failed with ${response.code}")
                }
                val body = response.body ?: throw IOException("Update download returned no body")
                body.byteStream().use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            targetFile
        }
    }

    private fun fetchLatestRelease(): ReleaseInfo {
        val request = Request.Builder()
            .url(RELEASES_URL)
            .header("Accept", "application/vnd.github+json")
            .build()

        HttpClientProvider.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Release check failed with ${response.code}")
            }

            val body = response.body?.string().orEmpty()
            val root = JSONObject(body)
            val assets = root.optJSONArray("assets") ?: throw IOException("No release assets found")
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                if (name == APK_ASSET_NAME && url.isNotBlank()) {
                    return ReleaseInfo(assetUrl = url)
                }
            }
            throw IOException("Latest release does not contain $APK_ASSET_NAME")
        }
    }

    private data class ReleaseInfo(
        val assetUrl: String
    )
}

class OttoPackageInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        OttoDiagnostics.info(context.applicationContext, "Updater", "Package install callback status=$status")
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                Toast.makeText(context, "Otto updated.", Toast.LENGTH_SHORT).show()
                OttoPolicyController.markPolicyDirty(
                    packageStateChanged = true,
                    staticPoliciesChanged = true
                )
                OttoPolicyController.applyPolicies(context.applicationContext)
            }

            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmationIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirmationIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { confirmationIntent?.let(context::startActivity) }
            }

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?: "Update failed."
                OttoDiagnostics.warn(context.applicationContext, "Updater", "Package install failed: $message")
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }
}

internal val ALLOWED_SYSTEM_PACKAGES = setOf(
    "com.android.settings",
    "com.samsung.android.settings",
    "com.android.permissioncontroller",
    "com.google.android.permissioncontroller",
    "com.samsung.android.packageinstaller",
    "com.google.android.packageinstaller",
    "com.google.android.apps.maps",
    "com.google.android.dialer",
    "com.android.dialer",
    "com.samsung.android.dialer",
    "com.google.android.contacts",
    "com.android.contacts",
    "com.samsung.android.contacts"
)

private val ALLOWED_SYSTEM_LABEL_KEYWORDS = listOf("settings", "maps", "phone", "dialer", "contacts")

private const val TRIPLE_TAP_WINDOW_MS = 400L
private const val TRIPLE_TAP_APP_LABEL = "Pad"
private const val DISTRACTION_GATE_CODE_LENGTH = 15
private const val DISTRACTION_GATE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ"
private const val PROCESSING_OVERLAY_DURATION_MS = 4200L
private const val SETTING_DISPLAY_DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
private const val SETTING_DISPLAY_DALTONIZER = "accessibility_display_daltonizer"
private const val DALTONIZER_DISABLED = -1
private const val DALTONIZER_SIMULATE_MONOCHROMACY = 0
private const val COLOR_CORRECTION_SETTINGS_ACTION = "com.android.settings.ACCESSIBILITY_COLOR_SPACE_SETTINGS"
private val PROCESSING_LABELS = listOf(
    "PROCESSING",
    "VECTORING",
    "SCANNING",
    "CALIBRATING"
)

// ── Encrypted Notes Storage ────────────────────────────────────────────

private object SecretNoteStore {
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
