package com.monoweather.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.monoweather.app.R
import com.monoweather.app.data.OpenMeteo
import com.monoweather.app.data.Status
import com.monoweather.app.data.WeatherStore
import com.monoweather.app.location.Places
import com.monoweather.app.widget.WidgetRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetches weather for the SAVED place (it never reads the location itself),
 * stores it and redraws every widget. Runs once per user tap — never on a
 * schedule and never retried, so there is no background traffic at all.
 */
class WeatherWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val store = WeatherStore(ctx)
        val coords = store.coords()
        if (coords == null) {
            store.setStatus(Status.NO_LOCATION)
            WidgetRenderer.renderAll(ctx)
            return Result.success()
        }
        val (lat, lon) = coords

        return try {
            val weather = withContext(Dispatchers.IO) { OpenMeteo.fetch(lat, lon) }
            store.saveWeather(weather)
            if (inputData.getBoolean(KEY_GEOCODE, false)) {
                withContext(Dispatchers.IO) { Places.name(ctx, lat, lon) }?.let(store::setPlace)
            }
            WidgetRenderer.renderAll(ctx)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Weather update failed", e)
            store.setStatus(Status.ERROR) // keeps showing the last good data
            WidgetRenderer.renderAll(ctx)
            Result.failure() // no automatic retry: the next tap is the retry
        }
    }

    /** Only used on Android < 12, where expedited work runs as a brief foreground service. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Обновление погоды", NotificationManager.IMPORTANCE_MIN)
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_w_cloud)
            .setContentTitle("Обновляю погоду…")
            .setSilent(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        const val KEY_GEOCODE = "geocode"
        private const val TAG = "MonoWeather"
        private const val CHANNEL_ID = "weather_update"
        private const val NOTIFICATION_ID = 1
    }
}
