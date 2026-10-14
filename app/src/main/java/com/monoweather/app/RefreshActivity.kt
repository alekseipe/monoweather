package com.monoweather.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.monoweather.app.data.Status
import com.monoweather.app.data.WeatherStore
import com.monoweather.app.location.Locator
import com.monoweather.app.widget.WidgetRenderer
import com.monoweather.app.work.Scheduler

/**
 * Started by a tap on the widget. Transparent and short-lived: it exists only so
 * the app counts as "in the foreground" while it reads the location (approximate
 * location with "while in use" permission is unavailable from the background).
 * First tap ever also shows the system location-permission dialog from here.
 */
class RefreshActivity : ComponentActivity() {

    private lateinit var store: WeatherStore
    private val handler = Handler(Looper.getMainLooper())

    private val askPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                locate()
            } else {
                store.setStatus(Status.NO_PERMISSION)
                WidgetRenderer.renderAll(this)
                close()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 34) overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        store = WeatherStore(this)

        // Restored after process death: don't risk an invisible activity lingering
        // over the home screen — drop it; another tap starts over.
        if (savedInstanceState != null) {
            close()
            return
        }

        store.setStatus(Status.LOADING)
        WidgetRenderer.renderAll(this) // widget shows "…" right away

        if (Locator.hasPermission(this)) locate()
        else askPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun locate() {
        handler.postDelayed({ close() }, HARD_TIMEOUT_MS) // safety net
        Locator.current(this) { location ->
            if (location != null) {
                store.setCoords(location.latitude, location.longitude)
            } else if (store.coords() == null) {
                store.setStatus(Status.NO_LOCATION)
            }
            // Even without a fresh fix, refresh the weather for the saved place.
            Scheduler.refreshNow(applicationContext, geocode = location != null)
            close()
        }
    }

    private fun close() {
        handler.removeCallbacksAndMessages(null)
        if (isFinishing) return
        finish()
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private companion object {
        const val HARD_TIMEOUT_MS = 15_000L
    }
}
