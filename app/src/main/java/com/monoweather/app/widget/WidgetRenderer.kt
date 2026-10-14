package com.monoweather.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.monoweather.app.R
import com.monoweather.app.RefreshActivity
import com.monoweather.app.data.Snapshot
import com.monoweather.app.data.Status
import com.monoweather.app.data.WeatherStore
import kotlin.math.roundToInt

/** Draws every widget instance from the stored snapshot. Never touches the network. */
object WidgetRenderer {

    fun renderAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, WeatherWidget::class.java))
        render(context, mgr, ids)
    }

    fun render(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        mgr.updateAppWidget(ids, build(context))
    }

    private fun build(context: Context): RemoteViews {
        val s = WeatherStore(context).snapshot()
        return RemoteViews(context.packageName, R.layout.widget_weather).apply {
            setImageViewResource(R.id.icon, WeatherIcons.forCode(s.code, s.isDay))
            setTextViewText(R.id.temp, tempLabel(s))
            setTextViewText(R.id.place, placeLine(s))
            setTextViewText(R.id.detail, detailLine(s))
            setOnClickPendingIntent(R.id.widget_root, refreshIntent(context))
        }
    }

    private fun tempLabel(s: Snapshot): String = when {
        s.status == Status.LOADING -> "…"
        s.temp != null -> "${s.temp.roundToInt()}°"
        else -> "--°"
    }

    fun placeLine(s: Snapshot): String = s.place ?: if (s.lat == null) "Tap to locate" else "MonoWeather"

    /** "Rain at 15:00 · UV 3" — the UV always closes the line. */
    fun detailLine(s: Snapshot): String = when (s.status) {
        Status.LOADING -> "Updating…"
        Status.NO_PERMISSION -> "No location access"
        Status.NO_LOCATION -> "Location unavailable"
        Status.ERROR -> "Update failed · tap to retry"
        Status.OK -> if (s.summary != null && s.uv != null) {
            "${s.summary} · UV ${s.uv.roundToInt()}"
        } else {
            "Tap to update"
        }
    }

    /** A tap anywhere on the widget = re-locate + refresh. */
    private fun refreshIntent(context: Context): PendingIntent {
        val intent = Intent(context, RefreshActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
