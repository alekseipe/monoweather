package com.monoweather.app.location

import android.content.Context
import android.location.Geocoder
import java.util.Locale

object Places {

    /** Blocking reverse-geocode to a short place name ("Berlin"); null if unavailable. */
    fun name(context: Context, lat: Double, lon: Double): String? = try {
        if (!Geocoder.isPresent()) {
            null
        } else {
            @Suppress("DEPRECATION")
            val a = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
            a?.locality ?: a?.subAdminArea ?: a?.adminArea
        }
    } catch (e: Exception) {
        null
    }
}
