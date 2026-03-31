package com.otto.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Tracks app usage: opens, closes (returns to launcher), time per app, daily distributions.
 *
 * Storage: AES-256-GCM encrypted with PBKDF2-derived key. Only the salt is stored —
 * no passkey or verification token. Wrong password → decryption fails → empty/garbled result.
 * The key is cached in memory for the session so events can be flushed without re-entering password.
 */
internal object ActivityTracker {

    private const val PREFS_NAME = "otto_activity_tracker"
    private const val KEY_SALT = "tracker_salt"
    private const val KEY_DATA = "tracker_data"
    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    // In-memory buffer for events recorded before password is entered
    private val pendingEvents = mutableListOf<TrackingEvent>()

    // Cached derived key (lives only in memory, lost on process death)
    @Volatile
    private var cachedKey: SecretKeySpec? = null

    data class TrackingEvent(
        val type: String, // "open" or "close"
        val packageName: String = "",
        val label: String = "",
        val timestamp: Long = System.currentTimeMillis()
    )

    fun recordAppOpen(context: Context, packageName: String, label: String) {
        val event = TrackingEvent("open", packageName, label)
        synchronized(pendingEvents) { pendingEvents.add(event) }
        tryFlush(context)
    }

    fun recordReturn(context: Context) {
        val event = TrackingEvent("close")
        synchronized(pendingEvents) { pendingEvents.add(event) }
        tryFlush(context)
    }

    fun isPasswordSet(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_SALT)
    }

    /**
     * Initialize tracking with a password. Generates a fresh salt, caches the derived key.
     * If data already exists with a different password, it will be unreadable (by design).
     */
    fun initPassword(context: Context, password: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_SALT)) {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            prefs.edit()
                .putString(KEY_SALT, android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
                .apply()
        }
        cachedKey = deriveKey(context, password)
        tryFlush(context)
    }

    /**
     * Unlock with password for this session. Caches key in memory.
     * Returns the decrypted stats, or null if decryption failed (wrong password).
     */
    fun unlock(context: Context, password: String): ActivityStats? {
        val key = deriveKey(context, password) ?: return null
        cachedKey = key

        // Decrypt stored data
        val events = loadEvents(context, key)

        // Flush any pending in-memory events
        val pending = synchronized(pendingEvents) {
            val copy = pendingEvents.toList()
            pendingEvents.clear()
            copy
        }
        val allEvents = events + pending
        saveEvents(context, key, allEvents)

        return computeStats(allEvents)
    }

    private fun tryFlush(context: Context) {
        val key = cachedKey ?: return
        val pending = synchronized(pendingEvents) {
            if (pendingEvents.isEmpty()) return
            val copy = pendingEvents.toList()
            pendingEvents.clear()
            copy
        }
        val existing = loadEvents(context, key)
        saveEvents(context, key, existing + pending)
    }

    private fun deriveKey(context: Context, password: String): SecretKeySpec? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saltB64 = prefs.getString(KEY_SALT, null) ?: return null
        val salt = android.util.Base64.decode(saltB64, android.util.Base64.NO_WRAP)
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun loadEvents(context: Context, key: SecretKeySpec): List<TrackingEvent> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dataB64 = prefs.getString(KEY_DATA, null)
        if (dataB64.isNullOrBlank()) return emptyList()
        val data = android.util.Base64.decode(dataB64, android.util.Base64.NO_WRAP)
        val decrypted = decrypt(data, key) ?: return emptyList()
        return parseEvents(String(decrypted, Charsets.UTF_8))
    }

    private fun saveEvents(context: Context, key: SecretKeySpec, events: List<TrackingEvent>) {
        val json = serializeEvents(events)
        val encrypted = encrypt(json.toByteArray(Charsets.UTF_8), key)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_DATA, android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP))
            .apply()
    }

    private fun serializeEvents(events: List<TrackingEvent>): String {
        val arr = JSONArray()
        for (e in events) {
            arr.put(JSONObject().apply {
                put("t", e.type)
                put("p", e.packageName)
                put("l", e.label)
                put("ts", e.timestamp)
            })
        }
        return arr.toString()
    }

    private fun parseEvents(json: String): List<TrackingEvent> {
        val result = mutableListOf<TrackingEvent>()
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            result.add(
                TrackingEvent(
                    type = obj.optString("t", ""),
                    packageName = obj.optString("p", ""),
                    label = obj.optString("l", ""),
                    timestamp = obj.optLong("ts", 0)
                )
            )
        }
        return result
    }

    private fun encrypt(data: ByteArray, key: SecretKeySpec): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return iv + cipher.doFinal(data)
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

    // ── Stats computation ─────────────────────────────────────────────

    data class ActivityStats(
        val totalOpens: Int,
        val totalCloses: Int,
        val todayOpens: Int,
        val todayCloses: Int,
        val appTimeMinutes: Map<String, Long>, // label → total minutes today
        val hourlyOpens: IntArray, // 24 slots, count per hour today
        val hourlyCloses: IntArray, // 24 slots, count per hour today
        val totalDays: Int,
        val allTimeAppMinutes: Map<String, Long> // label → total minutes all time
    ) {
        fun format(): String {
            val sb = StringBuilder()
            val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
            val today = dateFormat.format(Date())

            sb.appendLine("═══ Activity Stats ═══")
            sb.appendLine()
            sb.appendLine("── Today ($today) ──")
            sb.appendLine("Opens: $todayOpens  |  Closes: $todayCloses")

            if (appTimeMinutes.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine("Time per app (today):")
                appTimeMinutes.entries
                    .sortedByDescending { it.value }
                    .take(10)
                    .forEach { (label, mins) ->
                        sb.appendLine("  ${formatDuration(mins)}  $label")
                    }
            }

            sb.appendLine()
            sb.appendLine("Hourly distribution (today):")
            val maxCount = (hourlyOpens.max()).coerceAtLeast(1)
            for (h in 0..23) {
                val opens = hourlyOpens[h]
                val closes = hourlyCloses[h]
                if (opens == 0 && closes == 0) continue
                val bar = "█".repeat((opens * 20) / maxCount)
                sb.appendLine("  ${h.toString().padStart(2, '0')}:00  $bar  ↑$opens ↓$closes")
            }

            sb.appendLine()
            sb.appendLine("── All Time ──")
            sb.appendLine("Total opens: $totalOpens  |  Total closes: $totalCloses")
            sb.appendLine("Days tracked: $totalDays")

            if (allTimeAppMinutes.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine("Top apps (all time):")
                allTimeAppMinutes.entries
                    .sortedByDescending { it.value }
                    .take(15)
                    .forEach { (label, mins) ->
                        sb.appendLine("  ${formatDuration(mins)}  $label")
                    }
            }

            return sb.toString()
        }

        private fun formatDuration(minutes: Long): String {
            return when {
                minutes < 1 -> "<1m"
                minutes < 60 -> "${minutes}m"
                else -> "${minutes / 60}h ${minutes % 60}m"
            }.padStart(6)
        }
    }

    private fun computeStats(events: List<TrackingEvent>): ActivityStats {
        val cal = Calendar.getInstance()
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        var totalOpens = 0
        var totalCloses = 0
        var todayOpens = 0
        var todayCloses = 0
        val hourlyOpens = IntArray(24)
        val hourlyCloses = IntArray(24)
        val todayAppTime = mutableMapOf<String, Long>()
        val allTimeAppTime = mutableMapOf<String, Long>()
        val days = mutableSetOf<String>()
        val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        var lastOpen: TrackingEvent? = null

        for (event in events.sortedBy { it.timestamp }) {
            days.add(dayFormat.format(Date(event.timestamp)))

            when (event.type) {
                "open" -> {
                    totalOpens++
                    cal.timeInMillis = event.timestamp
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    if (event.timestamp >= todayStart) {
                        todayOpens++
                        hourlyOpens[hour]++
                    }
                    // If there was a previous open without a close, compute time for it
                    lastOpen?.let { prev ->
                        val duration = (event.timestamp - prev.timestamp) / 60_000
                        if (duration in 0..480) { // cap at 8 hours
                            allTimeAppTime[prev.label] = (allTimeAppTime[prev.label] ?: 0) + duration
                            if (prev.timestamp >= todayStart) {
                                todayAppTime[prev.label] = (todayAppTime[prev.label] ?: 0) + duration
                            }
                        }
                    }
                    lastOpen = event
                }
                "close" -> {
                    totalCloses++
                    cal.timeInMillis = event.timestamp
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    if (event.timestamp >= todayStart) {
                        todayCloses++
                        hourlyCloses[hour]++
                    }
                    lastOpen?.let { prev ->
                        val duration = (event.timestamp - prev.timestamp) / 60_000
                        if (duration in 0..480) {
                            allTimeAppTime[prev.label] = (allTimeAppTime[prev.label] ?: 0) + duration
                            if (prev.timestamp >= todayStart) {
                                todayAppTime[prev.label] = (todayAppTime[prev.label] ?: 0) + duration
                            }
                        }
                    }
                    lastOpen = null
                }
            }
        }

        return ActivityStats(
            totalOpens = totalOpens,
            totalCloses = totalCloses,
            todayOpens = todayOpens,
            todayCloses = todayCloses,
            appTimeMinutes = todayAppTime,
            hourlyOpens = hourlyOpens,
            hourlyCloses = hourlyCloses,
            totalDays = days.size.coerceAtLeast(1),
            allTimeAppMinutes = allTimeAppTime
        )
    }
}
