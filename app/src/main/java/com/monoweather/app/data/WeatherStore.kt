package com.monoweather.app.data

import android.content.Context
import android.content.SharedPreferences

enum class Status { OK, LOADING, ERROR, NO_PERMISSION, NO_LOCATION }

/** Everything the widget and the app screen need to draw themselves. */
data class Snapshot(
    val lat: Double?,
    val lon: Double?,
    val place: String?,
    val temp: Double?,
    val code: Int?,
    val isDay: Boolean,
    val uv: Double?,
    val summary: String?,
    val updatedAt: Long,
    val status: Status
)

/**
 * The saved place + last weather, in SharedPreferences. The widget, the worker
 * and the app all run in one process, so they share the same in-memory instance
 * and see each other's writes immediately.
 */
class WeatherStore(context: Context) {

    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun snapshot() = Snapshot(
        lat = prefs.getString(K_LAT, null)?.toDoubleOrNull(),
        lon = prefs.getString(K_LON, null)?.toDoubleOrNull(),
        place = prefs.getString(K_PLACE, null),
        temp = if (prefs.contains(K_TEMP)) prefs.getFloat(K_TEMP, 0f).toDouble() else null,
        code = if (prefs.contains(K_CODE)) prefs.getInt(K_CODE, 0) else null,
        isDay = prefs.getBoolean(K_DAY, true),
        uv = if (prefs.contains(K_UV)) prefs.getFloat(K_UV, 0f).toDouble() else null,
        summary = prefs.getString(K_SUMMARY, null),
        updatedAt = prefs.getLong(K_UPDATED, 0L),
        status = runCatching { Status.valueOf(prefs.getString(K_STATUS, null) ?: Status.OK.name) }
            .getOrDefault(Status.OK)
    )

    fun coords(): Pair<Double, Double>? {
        val lat = prefs.getString(K_LAT, null)?.toDoubleOrNull() ?: return null
        val lon = prefs.getString(K_LON, null)?.toDoubleOrNull() ?: return null
        return lat to lon
    }

    fun setCoords(lat: Double, lon: Double) {
        prefs.edit().putString(K_LAT, lat.toString()).putString(K_LON, lon.toString()).apply()
    }

    fun setPlace(place: String) {
        prefs.edit().putString(K_PLACE, place).apply()
    }

    fun setStatus(status: Status) {
        prefs.edit().putString(K_STATUS, status.name).apply()
    }

    fun saveWeather(w: OpenMeteo.Weather) {
        prefs.edit()
            .putFloat(K_TEMP, w.temperature.toFloat())
            .putInt(K_CODE, w.weatherCode)
            .putBoolean(K_DAY, w.isDay)
            .putFloat(K_UV, w.uvIndex.toFloat())
            .putString(K_SUMMARY, w.summary)
            .putLong(K_UPDATED, System.currentTimeMillis())
            .putString(K_STATUS, Status.OK.name)
            .apply()
    }

    /** One-time flag: v1.0 scheduled an hourly job that must be cancelled once. */
    var legacyPeriodicCancelled: Boolean
        get() = prefs.getBoolean(K_LEGACY_CANCELLED, false)
        set(v) { prefs.edit().putBoolean(K_LEGACY_CANCELLED, v).apply() }

    private companion object {
        const val NAME = "weather"
        const val K_LAT = "lat"
        const val K_LON = "lon"
        const val K_PLACE = "place"
        const val K_TEMP = "temp"
        const val K_CODE = "code"
        const val K_DAY = "is_day"
        const val K_UV = "uv"
        const val K_SUMMARY = "summary"
        const val K_UPDATED = "updated_at"
        const val K_STATUS = "status"
        const val K_LEGACY_CANCELLED = "legacy_periodic_cancelled"
    }
}
