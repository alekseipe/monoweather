package com.monoweather.app.data

import kotlin.math.roundToInt

/** Boils the hourly forecast down to one short line about the next 24 hours. */
object Forecast {

    private enum class Wet(val label: String) {
        DRIZZLE("Drizzle"), RAIN("Rain"), SNOW("Snow"), STORM("Storm")
    }

    /** WMO weather code → kind of precipitation, or null if it's dry. */
    private fun wet(code: Int): Wet? = when (code) {
        in 51..57 -> Wet.DRIZZLE
        in 61..67, in 80..82 -> Wet.RAIN
        in 71..77, 85, 86 -> Wet.SNOW
        in 95..99 -> Wet.STORM
        else -> null
    }

    /**
     * Precipitation beats temperature:
     *  - wet now     → "Rain until 18:00" / "Rain all day"
     *  - wet ahead   → "Rain at 15:00"
     *  - dry         → mean of the next 24h vs mean of the past 24h:
     *                  "Next 24h: 4° warmer" / "Next 24h: 3° colder" / "Next 24h: same"
     * Comparing day-to-day (not "now vs later") keeps the night/day swing out of it.
     *
     * @param times    hourly local times ("2026-10-14T15:00"), 24h back … 24h ahead
     * @param nowIndex index of the current hour in [times]
     */
    fun summarize(
        currentCode: Int,
        times: List<String>,
        temps: List<Double>,
        codes: List<Int>,
        nowIndex: Int
    ): String {
        val next = (nowIndex + 1)..minOf(nowIndex + 24, times.lastIndex)

        val wetNow = wet(currentCode)
        if (wetNow != null) {
            val dryAt = next.firstOrNull { wet(codes[it]) == null }
            return if (dryAt == null) "${wetNow.label} all day"
            else "${wetNow.label} until ${hour(times[dryAt])}"
        }

        for (i in next) {
            val kind = wet(codes[i]) ?: continue
            return "${kind.label} at ${hour(times[i])}"
        }

        val past = (maxOf(0, nowIndex - 24) until nowIndex).map { temps[it] }.filterNot { it.isNaN() }
        val ahead = next.map { temps[it] }.filterNot { it.isNaN() }
        if (past.isEmpty() || ahead.isEmpty()) return "Next 24h: same"
        val delta = (ahead.average() - past.average()).roundToInt()
        return when {
            delta >= 2 -> "Next 24h: $delta° warmer"
            delta <= -2 -> "Next 24h: ${-delta}° colder"
            else -> "Next 24h: same"
        }
    }

    private fun hour(time: String) = time.substringAfter('T').take(5)
}
