# Rockaway Board Window — Android + Home-screen Widget

This project packages the existing Rockaway Board Window web app as a small Android application and adds a native, resizable Android home-screen widget.

## Widget behavior

There is **one responsive widget** with two useful layouts:

- **2×2 compact:** selected board, 0–100 score, rating, best 3-hour window, and estimated breaking-face height.
- **4×2 detailed:** everything above plus primary swell, wind, NOAA tide phase/height, last-updated time, and a manual refresh button.

On Android 12+ the widget uses Android's responsive `RemoteViews` sizing. Resize it horizontally on the home screen and it switches between the compact and detailed presentations. Older supported Android versions choose the layout from the widget's reported width.

The widget refreshes on a best-effort 30-minute WorkManager schedule. The refresh button requests an immediate network update. Android may defer background work under battery-saving conditions, so the refresh time is not an exact clock schedule.

## Board synchronization

The full app is bundled locally in `app/src/main/assets/`. The selected board and its scoring settings are passed through a small JavaScript/native bridge. If you switch between the 7'4 Torq, the 6'6 progression board, or a board you add yourself, the widget re-scores the forecast for that selected board.

No surf-session history is sent to the native layer. Session logs remain in the web app's local storage.

## Data used by the widget

The native widget currently uses:

- Open-Meteo marine forecast for wave/swell data.
- Open-Meteo weather forecast for wind and daylight windows.
- NOAA CO-OPS Sandy Hook station 8531680 for hourly tide predictions, with Open-Meteo sea level as a fallback.
- The same Rockaway direction, wind, energy, volume, and estimated breaking-height scoring logic used in the full app.

The widget displays the best upcoming daylight 3-hour window over the next seven days for the currently selected board. It is a board-selection aid, not a surf-safety rating.

## Requirements

- Android 8.0 / API 26 or newer on the phone.
- Android Studio with Android SDK 36 for local development.
- JDK 17.
- Internet access for fresh forecast/tide data.

The project uses Android Gradle Plugin 9.3.0, AndroidX WebKit 1.17.0, and WorkManager 2.11.2.

## Easiest build: GitHub Actions

1. Create a GitHub repository and upload this project, preserving the `.github/workflows` folder.
2. Push to the `main` branch.
3. Open the repository's **Actions** tab.
4. Select **Build Android APK** and run it, or let the push trigger it automatically.
5. When the workflow finishes, download the `rockaway-board-window-debug` artifact.
6. Unzip it and transfer `app-debug.apk` to your Android phone.
7. On Android, allow installation from the app you used to open the APK if prompted, then install it.

The workflow installs the required Gradle/Android tooling and builds the APK in the cloud.

## Local build with Android Studio

1. Open this folder in Android Studio.
2. Make sure **Android SDK Platform 36** and **Build Tools 36.0.0** are installed in SDK Manager.
3. Use JDK 17 (Android Studio's bundled JBR is normally fine).
4. Sync the project with Gradle. This source package does not bundle the Gradle distribution; configure Gradle 9.5 if Android Studio asks for a Gradle version.
5. Connect your Android phone with USB debugging enabled and press **Run**, or use **Build > Build APK(s)**.

### Windows command-line convenience build

If Android Studio is installed, you can also right-click/run `build-debug.ps1` in PowerShell. The script locates the usual Android Studio JDK/SDK paths, downloads Gradle 9.5.0 into a project-local tools folder, and builds:

`app\build\outputs\apk\debug\app-debug.apk`

If PowerShell blocks local scripts, run it from a PowerShell window with an appropriate execution policy for the current process.

## Put the widget on the home screen

1. Install and open **Rockaway Board Window** once. This synchronizes the selected board and requests the first widget forecast.
2. Return to the Android home screen.
3. Long-press an empty area and choose **Widgets**.
4. Find **Rockaway Board Window**.
5. Drag it to the home screen.
6. Keep it around **2×2** for the compact layout, or resize it horizontally to about **4×2** for the detailed layout.
7. Tap anywhere on the widget to open the full app. In detailed mode, tap the refresh icon to request fresh conditions.

## Important storage note

The full app's board/session data remains local to the Android WebView. Uninstalling the Android app or clearing its app data will remove that local data. Continue using CSV export for session backups.

## Project structure

- `app/src/main/assets/index.html` — the full Rockaway Board Window app.
- `MainActivity.java` — local WebView wrapper.
- `BoardBridge.java` — syncs selected-board settings to native preferences.
- `ForecastRepository.java` — forecast retrieval and native scoring model.
- `BoardWindowWidgetProvider.java` — compact/detailed responsive widget rendering.
- `WidgetUpdateWorker.java` — background forecast refresh.
- `WidgetUpdateScheduler.java` — periodic/manual WorkManager scheduling.
- `.github/workflows/build-apk.yml` — optional cloud APK builder.

## Current limitation

The widget intentionally focuses on the **best forecast window** and does not yet display the live NOAA 44065 buoy observation. The full bundled app still contains the buoy panel. A later widget revision can add a small live-buoy/current-conditions row if desired.
