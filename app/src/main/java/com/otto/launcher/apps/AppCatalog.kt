package com.otto.launcher.apps

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
import java.util.Locale
internal val ALLOWED_SYSTEM_PACKAGES = setOf(
    "com.android.settings", "com.samsung.android.settings", "com.android.permissioncontroller",
    "com.google.android.permissioncontroller", "com.samsung.android.packageinstaller",
    "com.google.android.packageinstaller", "com.google.android.apps.maps", "com.google.android.dialer",
    "com.android.dialer", "com.samsung.android.dialer", "com.google.android.contacts",
    "com.android.contacts", "com.samsung.android.contacts"
)
private val ALLOWED_SYSTEM_LABEL_KEYWORDS = listOf("settings", "maps", "phone", "dialer", "contacts")

internal object LauncherAppsCache {
    private const val CACHE_TTL_MS = 30_000L
    private val cacheLock = Any()

    @Volatile
    private var cachedApps: List<AppInfo>? = null

    @Volatile
    private var cacheExpiresAtElapsedRealtime = 0L

    fun load(context: Context, forceRefresh: Boolean = false): List<AppInfo> {
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
                buildLauncherApps(context).also { freshApps ->
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
    context: Context,
    forceRefresh: Boolean = false
): List<AppInfo> {
    return LauncherAppsCache.load(context, forceRefresh)
}

internal fun buildLauncherApps(context: Context): List<AppInfo> {
    val packageManager = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    val visible = queryActivities(packageManager, launcherIntent)
        .distinctBy { it.activityName }
        .filter { shouldDisplayApp(packageManager, it) }

    return injectHiddenGatedApps(context, visible)
        .sortedBy { it.label.lowercase(Locale.getDefault()) }
}

private const val GATED_APP_CACHE_PREFS = "otto_gated_app_cache"
private const val GATED_APP_CACHE_DELIMITER = "\u0001"

/**
 * Time-gated apps (currently Slack) are hidden during their blocked window so they leave the
 * system recents list. Hidden apps vanish from the launcher query too, which would remove the only
 * path to the unlock-code dialog. To keep them reachable, cache each gated app's launch details
 * while it is visible, then re-inject a tile from that cache while it is hidden. Tapping the tile
 * routes through the after-hours gate, which unhides the app on a correct code.
 */
internal fun injectHiddenGatedApps(context: Context, visible: List<AppInfo>): List<AppInfo> {
    // During lockdown nothing outside the allowlist should be reachable, not even a gated tile.
    if (OttoPolicyController.isLockdownActive(context)) return visible
    val gatedPackages = OttoPolicyController.hideableTimeGatedPackages(context)
    if (gatedPackages.isEmpty()) return visible

    val prefs = context.getSharedPreferences(GATED_APP_CACHE_PREFS, Context.MODE_PRIVATE)
    val visibleByKey = visible.associateBy { it.packageName.lowercase(Locale.US) }

    val editor = prefs.edit()
    gatedPackages.forEach { pkg ->
        val app = visibleByKey[pkg.lowercase(Locale.US)] ?: return@forEach
        if (app.activityName.isNotBlank()) {
            editor.putString(
                pkg.lowercase(Locale.US),
                listOf(app.packageName, app.activityName, app.label).joinToString(GATED_APP_CACHE_DELIMITER)
            )
        }
    }
    editor.apply()

    val injected = gatedPackages.mapNotNull { pkg ->
        val key = pkg.lowercase(Locale.US)
        if (key in visibleByKey) return@mapNotNull null
        val parts = prefs.getString(key, null)?.split(GATED_APP_CACHE_DELIMITER) ?: return@mapNotNull null
        if (parts.size != 3) return@mapNotNull null
        AppInfo(label = parts[2], packageName = parts[0], activityName = parts[1])
    }

    return visible + injected
}

internal fun queryActivities(
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

internal fun shouldDisplayApp(packageManager: PackageManager, appInfo: AppInfo): Boolean {
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
