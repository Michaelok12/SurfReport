# Rockaway Board Window — Android

Native Android wrapper + responsive home-screen widget for the Rockaway Board Window surf forecast app.

## Widget sizes

The same widget can be resized by the launcher:

- **2×2 compact** — board, date/time, board-fit score/status, estimated breaking faces.
- **5×2 detailed** — adds graphical swell, wind, and tide instruments.
- **5×3 forecast stack** — adds three ranked, non-overlapping next-best surf windows.

Direction labels use the normal meteorological/ocean convention (where wind/swell **comes from**). Graphical arrows point where the wind/swell is **traveling toward**.

The graphical tide instrument shows:

- 1 lower water line = low tide
- 2 lower water lines = mid tide
- 3 water lines = high tide
- arrow = rising/falling

## Easiest build: GitHub Actions

1. Create or open a GitHub repository.
2. Upload the **contents of this folder to the repository root**. Do not put them inside another folder.
3. Verify this file exists in GitHub:
   `.github/workflows/build-apk.yml`
4. Open **Actions → Build Android APK → Run workflow**.
5. Wait for the build to finish.
6. Open the completed workflow run and download the `rockaway-board-window-debug` artifact.
7. Unzip it and install `app-debug.apk` on Android.

If Android refuses to install this build over an older debug APK because the signatures differ, uninstall the older app once and install the new APK. The workflow now caches its debug signing key, so subsequent builds from the same repository should normally install as updates.

## Add the widget

After installing and opening the app once:

1. Long-press an empty area of the Android home screen.
2. Choose **Widgets**.
3. Find **Rockaway Board Window**.
4. Add the widget.
5. Resize it to approximately **2×2**, **5×2**, or **5×3** to switch layouts.

Android launcher cell dimensions vary slightly, so resize until the desired responsive layout appears.

## Updating the forecast

- Android WorkManager performs best-effort background refreshes.
- The detailed and forecast-stack widgets include a manual refresh button.
- Tapping elsewhere on the widget opens the full app.
- Changing the selected board in the full app triggers a widget refresh using that board's scoring profile.

## Data

The native widget uses the same underlying forecast/scoring approach as the web app:

- Open-Meteo marine forecast
- Open-Meteo wind forecast
- NOAA Sandy Hook tide predictions, with model fallback
- Rockaway-specific swell direction, wind, tide, and board-fit scoring
- Estimated breaking-face range

## UI preview

`widget-preview.html` is included at the repository root for quick design previews without rebuilding the APK. It is only a mockup; the actual Android layouts are under `app/src/main/res/layout/`.
