package com.otto.launcher

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
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
import com.otto.launcher.trace.domain.NextTraceActionKind
import com.otto.launcher.trace.ui.TraceCameraOverlay
import com.otto.launcher.trace.ui.TraceCaptureSheet
import com.otto.launcher.trace.ui.TraceHomeLayer
import com.otto.launcher.trace.ui.TraceSecondaryAction
import com.otto.launcher.trace.ui.TraceSettingsDialog
import com.otto.launcher.trace.ui.TraceSleepDialog
import com.otto.launcher.trace.ui.TraceTodayDialog
import com.otto.launcher.trace.ui.TraceViewModel
import com.otto.launcher.trace.ui.TraceWeeklyDialog
import com.otto.launcher.trace.ui.TraceWeightDialog
import com.otto.launcher.ui.theme.OttoLauncherTheme
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
                    LauncherScreen(
                        apps = launcherApps,
                        versionLabel = versionLabel
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        OttoDiagnostics.info(applicationContext, "MainActivity", "Launcher resumed.")
        refreshLauncherApps()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                OttoPolicyController.applyPolicies(this@MainActivity)
            }
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

    private fun refreshLauncherApps() {
        launcherApps = loadLauncherApps(packageManager)
    }
}

@Composable
private fun LauncherScreen(
    apps: List<AppInfo>,
    versionLabel: String
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
    val traceState by traceViewModel.uiState.collectAsState()
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
    var gatedLaunchApp by remember { mutableStateOf<AppInfo?>(null) }
    var launchGateInput by rememberSaveable { mutableStateOf("") }
    var launchGateCode by rememberSaveable { mutableStateOf("") }
    var launchGateError by remember { mutableStateOf<String?>(null) }
    var isUpdating by remember { mutableStateOf(false) }
    var manualProcessingActive by remember { mutableStateOf(false) }
    var manualProcessingLabel by remember { mutableStateOf(PROCESSING_LABELS.first()) }
    var manualProcessingNonce by remember { mutableStateOf(0) }
    var diagnosticsVisible by remember { mutableStateOf(false) }
    var diagnosticsText by remember { mutableStateOf("") }

    // Secret notes gesture state: 7-tap version, wait 3s, 7-tap again
    var secretTapPhase by remember { mutableStateOf(0) } // 0=idle, 1=first-7-done, 2=passkey-prompt, 3=unlocked
    var secretTapCount by remember { mutableStateOf(0) }
    var secretLastTapTime by remember { mutableStateOf(0L) }
    var secretPhaseOneTime by remember { mutableStateOf(0L) }
    var secretPasskeyInput by rememberSaveable { mutableStateOf("") }
    var secretPasskeyError by remember { mutableStateOf<String?>(null) }
    var secretNotesText by rememberSaveable { mutableStateOf("") }
    var secretActivePasskey by remember { mutableStateOf("") }
    var secretIsNewPasskey by remember { mutableStateOf(false) }
    var secretConfirmPasskey by rememberSaveable { mutableStateOf("") }
    var secretNotesRevealed by remember { mutableStateOf(false) }

    // Feedback notes state
    var feedbackVisible by remember { mutableStateOf(false) }
    var feedbackText by rememberSaveable { mutableStateOf("") }
    var feedbackLoaded by remember { mutableStateOf(false) }

    var traceCaptureSheetVisible by remember { mutableStateOf(false) }
    var traceWeightVisible by remember { mutableStateOf(false) }
    var traceSleepVisible by remember { mutableStateOf(false) }
    var traceTodayVisible by remember { mutableStateOf(false) }
    var traceWeeklyVisible by remember { mutableStateOf(false) }
    var traceSettingsVisible by remember { mutableStateOf(false) }
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
                launchGateCode = OttoPolicyController.newLaunchGateCode(app.packageName).orEmpty()
                launchGateError = null
                statusMessage = OttoPolicyController.launchGatePrompt(app.packageName)
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
            statusMessage = if (drinkOnly) "Drink photo imported." else "Food photo imported."
        }
    }

    DisposableEffect(voiceManager) {
        onDispose { voiceManager.dispose() }
    }

    DisposableEffect(lifecycleOwner, traceViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                traceViewModel.onLauncherVisible()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val normalizedQuery = remember(query) { query.trim() }
    val filteredApps = remember(normalizedQuery, apps, isVoiceMode) {
        val normalized = normalizedQuery.lowercase(Locale.getDefault())
        when {
            normalized.isBlank() -> emptyList()
            isVoiceMode -> fuzzyMatchApps(apps, normalized)
            normalized.length < 3 -> emptyList()
            else -> apps.filter { info ->
                info.label.contains(normalized, ignoreCase = true) ||
                    info.packageName.contains(normalized, ignoreCase = true)
            }
        }
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

    fun startVoiceRecording() {
        val start = {
            val result = voiceManager.startRecording()
            if (result) {
                voiceHudVisible = true
                statusMessage = "Listening..."
                isRecording = true
            } else {
                statusMessage = "Unable to access microphone."
                voiceHudVisible = false
                isVoiceMode = false
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

    fun handleTracePrimaryAction() {
        when (traceState.nextAction.kind) {
            NextTraceActionKind.CONFIRM_SLEEP -> {
                traceViewModel.confirmSleepEstimate()
                statusMessage = "Sleep saved."
            }
            NextTraceActionKind.LOG_SLEEP -> traceSleepVisible = true
            NextTraceActionKind.LOG_WEIGHT -> traceWeightVisible = true
            NextTraceActionKind.FOOD_PHOTO -> openTraceCamera(isDrinkOnly = false)
            NextTraceActionKind.DRINK_PHOTO -> openTraceCamera(isDrinkOnly = true)
            NextTraceActionKind.VIEW_TODAY -> traceTodayVisible = true
            NextTraceActionKind.OPEN_CAPTURE -> traceCaptureSheetVisible = true
        }
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
            val result = voiceManager.transcribe(file)
            isTranscribing = false
            result
                .onSuccess { text ->
                    val cleaned = text.trim()
                    if (cleaned.isNotEmpty()) {
                        handleVoiceResult(cleaned)
                    } else {
                        statusMessage = "Didn't catch that."
                        voiceHudVisible = false
                        isVoiceMode = false
                    }
                }
                .onFailure { error ->
                    statusMessage = error.message ?: "Transcription failed."
                    voiceHudVisible = false
                    isVoiceMode = false
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
                .padding(horizontal = 24.dp, vertical = 32.dp)
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
            QuickActionRow(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .navigationBarsPadding(),
                onOpenSettings = { context.openSystemSettings() },
                onOpenFeedback = {
                    if (!feedbackLoaded) {
                        feedbackText = FeedbackNoteStore.load(context)
                        feedbackLoaded = true
                    }
                    feedbackVisible = true
                },
                onOpenLogs = {
                    refreshDiagnostics()
                    diagnosticsVisible = true
                },
                onUpdate = {
                    if (isUpdating) return@QuickActionRow
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
            )

            if (normalizedQuery.isBlank()) {
                TraceHomeLayer(
                    state = traceState,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 86.dp),
                    onNextAction = { handleTracePrimaryAction() },
                    onSecondaryAction = { action ->
                        when (action) {
                            is TraceSecondaryAction.NoMeal -> {
                                traceViewModel.recordMealAbsence(action.slot)
                                statusMessage = "No ${action.slot.name.lowercase(Locale.getDefault())} recorded."
                            }
                            is TraceSecondaryAction.IgnoreMeal -> {
                                traceViewModel.ignoreMealPrompt(action.slot)
                                statusMessage = null
                            }
                            TraceSecondaryAction.AdjustSleep -> traceSleepVisible = true
                            TraceSecondaryAction.IgnoreSleep -> {
                                traceViewModel.ignoreSleepEstimate()
                                statusMessage = null
                            }
                        }
                    },
                    onOpenToday = { traceTodayVisible = true },
                    onOpenWeekly = { traceWeeklyVisible = true }
                )
            }

            if (filteredApps.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 40.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 0.dp)
                ) {
                    items(
                        items = filteredApps,
                        key = { it.activityName }
                    ) { app ->
                        AppRow(
                            appInfo = app,
                            onLaunch = { attemptLaunch(it) },
                            onLongPress = { context.openAppInfo(it) }
                        )
                    }
                }
            }

            MinimalSearchField(
                query = query,
                onQueryChange = {
                    query = it
                    if (it.isNotEmpty()) {
                        statusMessage = null
                    }
                    isVoiceMode = false
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            )

            Text(
                text = versionLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                val now = SystemClock.elapsedRealtime()
                                // Reset if tap gap > 600ms
                                if (now - secretLastTapTime > 600) {
                                    // Check if we're in phase 1 waiting period and this is a new burst
                                    if (secretTapPhase == 1) {
                                        val elapsed = now - secretPhaseOneTime
                                        if (elapsed in 2500..5000) {
                                            // Valid wait window (2.5s - 5s), start second burst
                                            secretTapCount = 1
                                            secretLastTapTime = now
                                            return@detectTapGestures
                                        } else {
                                            // Outside window, reset everything
                                            secretTapPhase = 0
                                            secretTapCount = 0
                                        }
                                    } else {
                                        secretTapCount = 0
                                    }
                                }
                                secretTapCount += 1
                                secretLastTapTime = now

                                when (secretTapPhase) {
                                    0 -> {
                                        if (secretTapCount >= 7) {
                                            secretTapPhase = 1
                                            secretPhaseOneTime = now
                                            secretTapCount = 0
                                        }
                                    }
                                    1 -> {
                                        if (secretTapCount >= 7) {
                                            // Both bursts complete — open passkey prompt
                                            secretTapPhase = 2
                                            secretTapCount = 0
                                            secretPasskeyInput = ""
                                            secretPasskeyError = null
                                            secretIsNewPasskey = !SecretNoteStore.isPasskeySet(context)
                                            secretConfirmPasskey = ""
                                        }
                                    }
                                }
                            },
                            onLongPress = {
                                triggerProcessingOverlay()
                                statusMessage = "Processing field engaged."
                            }
                        )
                    }
                    .padding(vertical = 6.dp)
            )

            statusMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 36.dp)
                )
            }

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
                                    launchGateError = OttoPolicyController.launchGateFailureMessage(app.packageName)
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
                                OttoPolicyController.launchGatePrompt(app.packageName)
                                    ?: "Type the displayed code to continue."
                            )
                            Text(
                                text = OttoPolicyController.formatLaunchGateCode(launchGateCode),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
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

            // ── Feedback notes dialog ──
            if (feedbackVisible) {
                AlertDialog(
                    onDismissRequest = {
                        FeedbackNoteStore.save(context, feedbackText)
                        feedbackVisible = false
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                FeedbackNoteStore.save(context, feedbackText)
                                feedbackVisible = false
                                Toast.makeText(context, "Feedback saved.", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                FeedbackNoteStore.save(context, feedbackText)
                                feedbackVisible = false
                            }
                        ) {
                            Text("Close")
                        }
                    },
                    title = { Text("Otto Feedback") },
                    text = {
                        val scrollState = rememberScrollState()
                        OutlinedTextField(
                            value = feedbackText,
                            onValueChange = { feedbackText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState),
                            minLines = 8,
                            maxLines = 20,
                            placeholder = { Text("What could be better about Otto?") }
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
                        statusMessage = "${"%.1f".format(it)}kg saved."
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

            if (traceTodayVisible) {
                TraceTodayDialog(
                    state = traceState,
                    onDismiss = { traceTodayVisible = false },
                    onHide = { traceId, hidden -> traceViewModel.setFoodHidden(traceId, hidden) },
                    onDrinkOnly = { traceId, drinkOnly -> traceViewModel.setDrinkOnly(traceId, drinkOnly) },
                    onUpdateNote = { traceId, note -> traceViewModel.updateNote(traceId, note) }
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
                    statusMessage = if (drinkOnly) "Drink photo saved." else "Food photo saved."
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
    onOpenFeedback: () -> Unit,
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
        QuickActionChip(
            label = "Feedback",
            onClick = onOpenFeedback
        )
        QuickActionChip(
            label = "Logs",
            onClick = onOpenLogs
        )
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
    if (OttoPolicyController.isBlockedApp(appInfo.packageName)) {
        Toast.makeText(this, "${appInfo.label} is blocked.", Toast.LENGTH_SHORT).show()
        return
    }

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
private const val PROCESSING_OVERLAY_DURATION_MS = 4200L
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

// ── Feedback Notes Storage ─────────────────────────────────────────────

private object FeedbackNoteStore {
    private const val FILENAME = "otto_feedback.txt"

    fun load(context: Context): String {
        val file = File(context.filesDir, FILENAME)
        return if (file.exists()) file.readText() else ""
    }

    fun save(context: Context, text: String) {
        File(context.filesDir, FILENAME).writeText(text)
    }
}
