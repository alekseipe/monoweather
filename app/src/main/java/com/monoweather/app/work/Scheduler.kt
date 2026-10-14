package com.monoweather.app.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.monoweather.app.data.WeatherStore

/**
 * The app never updates on its own — no periodic jobs, no retries. The only
 * network request is the one triggered by the user's tap.
 */
object Scheduler {

    private const val NOW = "weather-now"
    private const val LEGACY_PERIODIC = "weather-periodic" // scheduled by v1.0

    /**
     * Fetch right now. No network constraint on purpose: with no connection it
     * fails fast and the widget keeps the last good data instead of hanging on "…".
     */
    fun refreshNow(context: Context, geocode: Boolean) {
        val request = OneTimeWorkRequestBuilder<WeatherWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf(WeatherWorker.KEY_GEOCODE to geocode))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }

    /** Remove the hourly job left behind by v1.0 — only ever done once. */
    fun cancelLegacyPeriodicOnce(context: Context) {
        val store = WeatherStore(context)
        if (store.legacyPeriodicCancelled) return
        WorkManager.getInstance(context).cancelUniqueWork(LEGACY_PERIODIC)
        store.legacyPeriodicCancelled = true
    }
}
