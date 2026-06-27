package com.otto.launcher.data.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.otto.launcher.domain.weather.DailyWeather
import com.otto.launcher.domain.weather.weatherSymbolForCode
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Fetches a short daily forecast from Open-Meteo (free, no API key) for the device's current
 * coarse location. Everything degrades to an empty list: no permission, no location fix, or a
 * network error simply means no weather row is shown.
 */
class WeatherRepository(context: Context) {
    private val appContext = context.applicationContext
    private val client = OkHttpClient()

    suspend fun upcomingForecast(days: Int = 7): List<DailyWeather> = withContext(Dispatchers.IO) {
        val location = lastKnownLocation() ?: return@withContext emptyList()
        runCatching { fetch(location.latitude, location.longitude, days) }.getOrDefault(emptyList())
    }

    private fun fetch(latitude: Double, longitude: Double, days: Int): List<DailyWeather> {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$latitude&longitude=$longitude" +
            "&daily=weather_code&timezone=auto&forecast_days=$days"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val daily = JSONObject(response.body?.string().orEmpty()).optJSONObject("daily")
                ?: return emptyList()
            val times = daily.optJSONArray("time") ?: return emptyList()
            val codes = daily.optJSONArray("weather_code") ?: return emptyList()
            val count = minOf(times.length(), codes.length())
            return (0 until count).mapNotNull { i ->
                val date = runCatching { LocalDate.parse(times.getString(i)) }.getOrNull()
                    ?: return@mapNotNull null
                DailyWeather(date = date, symbol = weatherSymbolForCode(codes.getInt(i)))
            }
        }
    }

    private fun lastKnownLocation(): Location? {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
            LocationManager.GPS_PROVIDER
        )
        return providers
            .mapNotNull { provider ->
                runCatching {
                    if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider) else null
                }.getOrNull()
            }
            .maxByOrNull { it.time }
    }
}
