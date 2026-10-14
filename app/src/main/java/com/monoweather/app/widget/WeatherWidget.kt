package com.monoweather.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.monoweather.app.work.Scheduler

/** Only redraws from saved data; fetching happens exclusively on a user tap. */
class WeatherWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetRenderer.render(context, appWidgetManager, appWidgetIds)
        Scheduler.cancelLegacyPeriodicOnce(context)
    }
}
