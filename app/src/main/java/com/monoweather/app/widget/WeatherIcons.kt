package com.monoweather.app.widget

import androidx.annotation.DrawableRes
import com.monoweather.app.R

/** WMO weather codes (as returned by Open-Meteo) → glyph and short description. */
object WeatherIcons {

    @DrawableRes
    fun forCode(code: Int?, isDay: Boolean): Int = when (code) {
        0, 1 -> if (isDay) R.drawable.ic_w_sun else R.drawable.ic_w_moon
        2 -> if (isDay) R.drawable.ic_w_partly else R.drawable.ic_w_cloud
        45, 48 -> R.drawable.ic_w_fog
        51, 53, 55, 56, 57 -> R.drawable.ic_w_drizzle
        61, 63, 65, 66, 67, 80, 81, 82 -> R.drawable.ic_w_rain
        71, 73, 75, 77, 85, 86 -> R.drawable.ic_w_snow
        95, 96, 99 -> R.drawable.ic_w_storm
        else -> R.drawable.ic_w_cloud // 3 = overcast, and "no data yet"
    }

    fun describe(code: Int?): String = when (code) {
        0 -> "Ясно"
        1 -> "Преимущественно ясно"
        2 -> "Переменная облачность"
        3 -> "Пасмурно"
        45, 48 -> "Туман"
        51, 53, 55 -> "Морось"
        56, 57 -> "Ледяная морось"
        61, 63, 65 -> "Дождь"
        66, 67 -> "Ледяной дождь"
        71, 73, 75, 77 -> "Снег"
        80, 81, 82 -> "Ливень"
        85, 86 -> "Снегопад"
        95 -> "Гроза"
        96, 99 -> "Гроза с градом"
        else -> ""
    }
}
