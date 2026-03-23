package com.otto.launcher

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.otto.launcher.ui.theme.OttoLauncherTheme
import androidx.lifecycle.lifecycleScope
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
import kotlin.math.max
import kotlin.math.min
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var launcherApps by mutableStateOf<List<AppInfo>>(emptyList())
    private var packageChangeReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launcherApps = loadLauncherApps(packageManager)
        val versionLabel = currentVersionName()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        registerPackageChangeReceiver()

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
        refreshLauncherApps()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterPackageChangeReceiver()
    }

    private fun refreshLauncherApps() {
        launcherApps = loadLauncherApps(packageManager)
    }

    private fun registerPackageChangeReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        packageChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                refreshLauncherApps()
                lifecycleScope.launch(Dispatchers.IO) {
                    enforceAppBlocking(this@MainActivity)
                }
            }
        }.also { receiver ->
            registerReceiver(receiver, filter)
        }
    }

    private fun unregisterPackageChangeReceiver() {
        packageChangeReceiver?.let { receiver ->
            runCatching { unregisterReceiver(receiver) }
        }
        packageChangeReceiver = null
    }
}

@Composable
private fun LauncherScreen(
    apps: List<AppInfo>,
    versionLabel: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val voiceManager = remember(context.applicationContext) {
        VoiceTranscriptionManager(context.applicationContext)
    }
    val voiceAgent = remember { VoiceLaunchAgent() }
    var tapCount by remember { mutableStateOf(0) }
    var tapTimeoutJob by remember { mutableStateOf<Job?>(null) }
    var lastTapTimestamp by remember { mutableStateOf(0L) }

    var query by rememberSaveable { mutableStateOf("") }
    var voiceHudVisible by rememberSaveable { mutableStateOf(false) }
    var isRecording by rememberSaveable { mutableStateOf(false) }
    var isTranscribing by rememberSaveable { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var pendingPermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var isVoiceMode by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
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

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { enforceAppBlocking(context) }
    }

    DisposableEffect(voiceManager) {
        onDispose { voiceManager.dispose() }
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
        scope.launch {
            val result = voiceAgent.resolve(cleaned, apps)
            result
                .onSuccess { app ->
                    statusMessage = "Opening ${app.label}"
                    context.launchApp(app)
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
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
                                    val message = handleDoubleTap(context, apps)
                                    statusMessage = message
                                    isVoiceMode = false
                                    voiceHudVisible = false
                                    isRecording = false
                                    isTranscribing = false
                                    query = ""
                                }
                                else -> {
                                    if (!isRecording && !isTranscribing) {
                                        isVoiceMode = true
                                        voiceHudVisible = true
                                        statusMessage = "Voice search ready."
                                        startVoiceRecording()
                                    }
                                }
                            }
                            tapCount = 0
                            tapTimeoutJob = null
                        }
                    }
                )
            }
    ) {
        if (filteredApps.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 0.dp)
            ) {
                items(filteredApps) { app ->
                    AppRow(
                        appInfo = app,
                        onLaunch = { context.launchApp(it) },
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
            modifier = Modifier.align(Alignment.TopStart)
        )

        statusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopEnd)
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

private fun loadLauncherApps(packageManager: PackageManager): List<AppInfo> {
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
    if (BLOCKED_PACKAGES.any { normalizedPackage.startsWith(it) }) {
        return false
    }
    if (normalizedPackage in BLOCKED_APPS) {
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
    if (appInfo.packageName.lowercase(Locale.getDefault()) in BLOCKED_APPS) {
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

private fun handleDoubleTap(
    context: Context,
    apps: List<AppInfo>
): String {
    val target = findPreferredApp(apps)
    return if (target != null) {
        context.launchApp(target)
        "Opening ${target.label}"
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
    val client: OkHttpClient by lazy { OkHttpClient() }
}

/** Suspend blocked apps via Device Owner API. Requires one-time adb setup:
 *  adb shell dpm set-device-owner com.otto.launcher/.OttoDeviceAdminReceiver */
private fun enforceAppBlocking(context: Context) {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent = ComponentName(context, OttoDeviceAdminReceiver::class.java)
    if (!dpm.isDeviceOwnerApp(context.packageName)) return
    dpm.setPackagesSuspended(adminComponent, BLOCKED_APPS.toTypedArray(), true)
}

private val BLOCKED_PACKAGES = setOf(
    "com.android.server",
    "com.sec.android.app.desktoplauncher",
    "com.sec.android.app.dexonpc",
    "com.samsung.desktopsystemui"
)

/** Apps intentionally blocked from appearing or launching. */
private val BLOCKED_APPS = setOf(
    "com.reddit.frontpage",
    "com.zhiliaoapp.musically",     // TikTok
    "com.ss.android.ugc.trill",     // TikTok (regional variant)
    "com.linkedin.android",
    "com.instagram.android",
)

private val ALLOWED_SYSTEM_PACKAGES = setOf(
    "com.google.android.apps.maps",
    "com.google.android.dialer",
    "com.android.dialer",
    "com.samsung.android.dialer",
    "com.google.android.contacts",
    "com.android.contacts",
    "com.samsung.android.contacts"
)

private val ALLOWED_SYSTEM_LABEL_KEYWORDS = listOf("maps", "phone", "dialer", "contacts")

private const val TRIPLE_TAP_WINDOW_MS = 400L
private const val TRIPLE_TAP_APP_LABEL = "Pad"
