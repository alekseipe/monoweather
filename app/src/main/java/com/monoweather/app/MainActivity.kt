package com.monoweather.app

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monoweather.app.data.Snapshot
import com.monoweather.app.data.Status
import com.monoweather.app.data.WeatherStore
import com.monoweather.app.location.Locator
import com.monoweather.app.widget.WeatherIcons
import com.monoweather.app.widget.WeatherWidget
import com.monoweather.app.widget.WidgetRenderer
import com.monoweather.app.work.Scheduler
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MonoTheme { WeatherScreen() } }
    }
}

private val Grey = Color(0xFF8A8A8A)

@Composable
private fun MonoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color.White,
            onPrimary = Color.Black,
            background = Color.Black,
            onBackground = Color.White,
            surface = Color.Black,
            onSurface = Color.White
        ),
        content = content
    )
}

@Composable
private fun WeatherScreen() {
    val context = LocalContext.current
    val store = remember { WeatherStore(context) }
    var snap by remember { mutableStateOf(store.snapshot()) }
    var locating by remember { mutableStateOf(false) }

    // Redraw live whenever the worker (or a widget tap) writes new data.
    DisposableEffect(store) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            snap = store.snapshot()
        }
        store.prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { store.prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun locateAndRefresh() {
        locating = true
        store.setStatus(Status.LOADING)
        Locator.current(context) { location ->
            if (location != null) {
                store.setCoords(location.latitude, location.longitude)
            } else if (store.coords() == null) {
                store.setStatus(Status.NO_LOCATION)
            }
            Scheduler.refreshNow(context, geocode = location != null)
            locating = false
        }
    }

    val askPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) locateAndRefresh() else store.setStatus(Status.NO_PERMISSION)
    }

    val onLocate = {
        if (Locator.hasPermission(context)) locateAndRefresh()
        else askPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    // Nothing is fetched automatically — not even on opening the app. The only
    // network request is the one the user asks for with the button (or widget tap).
    LaunchedEffect(Unit) { Scheduler.cancelLegacyPeriodicOnce(context) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("MonoWeather", color = Grey, fontSize = 14.sp, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.weight(1f))

        // Image, not Icon: a tint would also paint the black "mask" fill white.
        Image(
            painter = painterResource(WeatherIcons.forCode(snap.code, snap.isDay)),
            contentDescription = null,
            modifier = Modifier.size(112.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            snap.temp?.let { "${it.roundToInt()}°" } ?: "--°",
            color = Color.White,
            fontSize = 76.sp,
            fontWeight = FontWeight.Light
        )
        val description = WeatherIcons.describe(snap.code)
        if (description.isNotEmpty()) Text(description, color = Color.White, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        Text(placeLine(snap), color = Grey, fontSize = 15.sp)
        if (snap.status == Status.OK && snap.summary != null) {
            Text(WidgetRenderer.detailLine(snap), color = Color.White, fontSize = 15.sp)
        }
        Text(statusLine(snap), color = Grey, fontSize = 13.sp)

        Spacer(Modifier.weight(1f))

        MonoButton(
            text = if (locating) "Определяю место…" else "Обновить место и погоду",
            enabled = !locating,
            onClick = onLocate
        )
        Spacer(Modifier.height(10.dp))
        MonoButton(text = "Добавить виджет на экран", onClick = { pinWidget(context) })
        Spacer(Modifier.height(16.dp))
        Text("Погода: Open-Meteo.com", color = Grey, fontSize = 11.sp)
    }
}

@Composable
private fun MonoButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        border = BorderStroke(1.dp, if (enabled) Color.White else Grey),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Color.White,
            disabledContentColor = Grey
        )
    ) {
        Text(text, fontSize = 15.sp)
    }
}

private fun placeLine(s: Snapshot): String = when {
    s.place != null -> s.place
    s.lat != null && s.lon != null -> String.format(Locale.US, "%.2f, %.2f", s.lat, s.lon)
    else -> "Место не задано"
}

private fun statusLine(s: Snapshot): String = when (s.status) {
    Status.LOADING -> "Обновляю…"
    Status.ERROR -> "Нет связи — показаны последние данные"
    Status.NO_PERMISSION -> "Нет доступа к геолокации"
    Status.NO_LOCATION -> "Не удалось определить место"
    Status.OK -> if (s.updatedAt > 0) "Обновлено ${DateFormat.format("HH:mm", s.updatedAt)}" else ""
}

/** Asks the launcher to drop the widget on the home screen (Android 8+ launchers). */
private fun pinWidget(context: Context) {
    val mgr = AppWidgetManager.getInstance(context)
    if (mgr.isRequestPinAppWidgetSupported) {
        mgr.requestPinAppWidget(ComponentName(context, WeatherWidget::class.java), null, null)
    } else {
        Toast.makeText(
            context,
            "Лаунчер так не умеет — долгий тап по рабочему столу → Виджеты → MonoWeather",
            Toast.LENGTH_LONG
        ).show()
    }
}
