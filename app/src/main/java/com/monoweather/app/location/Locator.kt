package com.monoweather.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper

/**
 * One-shot approximate location from the platform LocationManager (no Google
 * Play Services dependency). Always calls back exactly once, on the main thread,
 * within [timeoutMs]; if no fresh fix arrives it falls back to the newest
 * last-known location.
 */
object Locator {

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun current(context: Context, timeoutMs: Long = 10_000, onResult: (Location?) -> Unit) {
        val lm = context.getSystemService(LocationManager::class.java)
        val handler = Handler(Looper.getMainLooper())
        var done = false
        var cleanup: () -> Unit = {}

        fun finish(location: Location?) {
            if (done) return
            done = true
            handler.removeCallbacksAndMessages(null)
            runCatching { cleanup() }
            onResult(location ?: lastKnown(lm))
        }

        val provider = if (lm != null && hasPermission(context)) pickProvider(lm) else null
        if (lm == null || provider == null) {
            finish(null)
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val signal = CancellationSignal()
                cleanup = { signal.cancel() }
                lm.getCurrentLocation(provider, signal, context.mainExecutor) { finish(it) }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) = finish(location)

                    // Abstract before API 30 — must be implemented or older devices crash.
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                cleanup = { lm.removeUpdates(listener) }
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
            handler.postDelayed({ finish(null) }, timeoutMs)
        } catch (e: Exception) {
            finish(null)
        }
    }

    private fun pickProvider(lm: LocationManager): String? {
        val candidates = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            if (lm.hasProvider(LocationManager.FUSED_PROVIDER)) candidates += LocationManager.FUSED_PROVIDER
        }
        candidates += LocationManager.NETWORK_PROVIDER
        // Coarse-only apps may use GPS (fuzzed) from Android 12; before that it needs FINE.
        if (Build.VERSION.SDK_INT >= 31) candidates += LocationManager.GPS_PROVIDER
        return candidates.firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(lm: LocationManager?): Location? {
        lm ?: return null
        return lm.allProviders
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }
}
