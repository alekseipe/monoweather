# MonoWeather

A tiny black-and-white weather widget for Android. One row: a small black tile
the size of an app icon (white weather glyph + degrees), with the place name and
a "next 24 hours" line next to it — the current UV index always closes the line:

```
[☀ 14°]  Kreuzberg
         Rain at 15:00 · UV 3
```

- **Updates ONLY when you tap it** (the widget, or the button in the app). No
  background jobs, no schedules, no retries — without a tap there is not a
  single request. One tap = one ~2 KB request.
- **A tap** = find the current place + download the weather. Approximate location
  only, and only during the tap; no background location access.
- **The "next 24h" line**: precipitation now → `Rain until 18:00`; coming later →
  `Rain at 15:00` (`Drizzle` / `Snow` / `Storm`); dry → mean temperature of the
  next 24 hours vs the past 24 hours: `Next 24h: 4° warmer` / `3° colder` / `same`.
  The logic lives in `data/Forecast.kt`.
- **Data** — [Open-Meteo](https://open-meteo.com): free, no API key. The only
  thing that leaves the phone is the coordinates, rounded to ~1 km.
- Widget margins are tuned for **Niagara Launcher** (the tile lines up with the
  icon column, the text with the app-label column).

## How it works

```
app/src/main/java/com/monoweather/app/
├── MainActivity.kt          — app screen (Compose): weather, "update place", "add widget"
├── RefreshActivity.kt       — invisible activity opened by a widget tap
├── data/
│   ├── OpenMeteo.kt         — one request: current weather + UV + hourly ±24h (HttpURLConnection + org.json)
│   ├── Forecast.kt          — hourly forecast → "Rain at 15:00" / "Next 24h: 4° warmer"
│   └── WeatherStore.kt      — saved place + last weather (SharedPreferences)
├── location/
│   ├── Locator.kt           — one-shot approximate location (platform LocationManager)
│   └── Places.kt            — place name from coordinates (Geocoder)
├── work/
│   ├── WeatherWorker.kt     — fetch weather for the saved place and redraw the widgets
│   └── Scheduler.kt         — one-off job after a tap (no schedules, no retries)
└── widget/
    ├── WeatherWidget.kt     — AppWidgetProvider
    ├── WidgetRenderer.kt    — draws the widget from saved data
    └── WeatherIcons.kt      — WMO weather code → glyph / description
```

**Why a tap opens an invisible activity.** With "while using the app" location
permission, Android only hands out the location while the app is in the
foreground. So a widget tap opens a transparent activity for a split second: it
grabs the coordinates (asking for the permission on the very first tap) and
closes right away. WorkManager then downloads the weather.

## Build and install

```bash
./gradlew assembleDebug
```

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then: open MonoWeather → allow location → tap «Добавить виджет на экран»
("Add widget to home screen"). The in-app screen is currently in Russian; the
widget itself is in English.

## Licenses and attribution

- Weather data: [Open-Meteo.com](https://open-meteo.com), licensed under
  [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
- Weather glyphs are based on [Feather Icons](https://feathericons.com)
  (MIT License, © 2013–2017 Cole Bemis) — full license text in
  [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
