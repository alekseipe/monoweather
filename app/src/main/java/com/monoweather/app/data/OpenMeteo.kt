package com.monoweather.app.data

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Open-Meteo (https://open-meteo.com): free, no API key, CC BY 4.0 data.
 * One request (~2 KB) per refresh: current conditions + UV, plus hourly
 * temperature/weather 24h back and 24h ahead for the "next 24h" line.
 */
object OpenMeteo {

    data class Weather(
        val temperature: Double,
        val weatherCode: Int,
        val isDay: Boolean,
        val uvIndex: Double,
        val summary: String
    )

    /**
     * Blocking — call off the main thread. Coordinates are rounded to 2 decimals
     * (~1 km) before they leave the device; that is plenty for weather.
     */
    fun fetch(lat: Double, lon: Double): Weather {
        // Locale.US so the decimal separator is always "." (a Russian locale would give ",").
        val url = URL(
            String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f" +
                    "&current=temperature_2m,weather_code,is_day,uv_index" +
                    "&hourly=temperature_2m,weather_code" +
                    "&past_hours=24&forecast_hours=25&timezone=auto",
                lat, lon
            )
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("Open-Meteo HTTP $code")
            return parse(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(body: String): Weather {
        val root = JSONObject(body)
        val cur = root.getJSONObject("current")
        val hourly = root.getJSONObject("hourly")

        val timesJson = hourly.getJSONArray("time")
        val tempsJson = hourly.getJSONArray("temperature_2m")
        val codesJson = hourly.getJSONArray("weather_code")
        val times = List(timesJson.length()) { timesJson.getString(it) }
        val temps = List(tempsJson.length()) { tempsJson.optDouble(it, Double.NaN) }
        val codes = List(codesJson.length()) { codesJson.optInt(it, 0) }

        // Current time is 15-minutely ("…T10:15"); find its hour in the hourly list.
        val currentCode = cur.getInt("weather_code")
        val hourKey = cur.optString("time").take(13) + ":00"
        val nowIndex = times.indexOf(hourKey).takeIf { it >= 0 } ?: minOf(24, times.lastIndex)

        return Weather(
            temperature = cur.getDouble("temperature_2m"),
            weatherCode = currentCode,
            isDay = cur.optInt("is_day", 1) == 1,
            uvIndex = cur.optDouble("uv_index", 0.0),
            summary = Forecast.summarize(currentCode, times, temps, codes, nowIndex)
        )
    }
}
