package com.rockaway.boardwindow;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.SizeF;
import android.view.View;
import android.widget.RemoteViews;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class BoardWindowWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "com.rockaway.boardwindow.REFRESH_WIDGET";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        WidgetUpdateScheduler.ensurePeriodic(context);
        for (int id : appWidgetIds) updateWidget(context, manager, id);
        if (ForecastRepository.loadSnapshot(context) == null) WidgetUpdateScheduler.requestImmediate(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int appWidgetId, Bundle newOptions) {
        updateWidget(context, manager, appWidgetId);
    }

    @Override
    public void onEnabled(Context context) {
        WidgetUpdateScheduler.ensurePeriodic(context);
        WidgetUpdateScheduler.requestImmediate(context);
    }

    @Override
    public void onDisabled(Context context) {
        WidgetUpdateScheduler.cancelPeriodic(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            markRefreshing(context);
            WidgetUpdateScheduler.requestImmediate(context);
        }
    }

    public static void updateAllWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, BoardWindowWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        for (int id : ids) updateWidget(context, manager, id);
    }

    private static void markRefreshing(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, BoardWindowWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        ForecastRepository.ForecastSnapshot snapshot = ForecastRepository.loadSnapshot(context);
        for (int id : ids) {
            RemoteViews views = buildResponsive(context, manager, id, snapshot, true);
            manager.updateAppWidget(id, views);
        }
    }

    private static void updateWidget(Context context, AppWidgetManager manager, int id) {
        ForecastRepository.ForecastSnapshot snapshot = ForecastRepository.loadSnapshot(context);
        RemoteViews views = buildResponsive(context, manager, id, snapshot, false);
        manager.updateAppWidget(id, views);
    }

    private static RemoteViews buildResponsive(Context context, AppWidgetManager manager, int id,
                                                ForecastRepository.ForecastSnapshot snapshot,
                                                boolean refreshing) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Map<SizeF, RemoteViews> map = new HashMap<>();
            map.put(new SizeF(110f, 110f), buildView(context, R.layout.widget_compact, snapshot, refreshing));
            map.put(new SizeF(250f, 110f), buildView(context, R.layout.widget_detailed, snapshot, refreshing));
            return new RemoteViews(map);
        }
        Bundle options = manager.getAppWidgetOptions(id);
        int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110);
        return buildView(context, minWidth >= 220 ? R.layout.widget_detailed : R.layout.widget_compact, snapshot, refreshing);
    }

    private static RemoteViews buildView(Context context, int layoutId,
                                         ForecastRepository.ForecastSnapshot snapshot,
                                         boolean refreshing) {
        RemoteViews v = new RemoteViews(context.getPackageName(), layoutId);
        ForecastRepository.BoardProfile board = ForecastRepository.selectedBoard(context);

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(context, 10, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widget_root, openPi);

        if (layoutId == R.layout.widget_detailed) {
            Intent refresh = new Intent(context, BoardWindowWidgetProvider.class);
            refresh.setAction(ACTION_REFRESH);
            PendingIntent refreshPi = PendingIntent.getBroadcast(context, 11, refresh,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            v.setOnClickPendingIntent(R.id.refresh_button, refreshPi);
        }

        if (snapshot == null) {
            v.setTextViewText(R.id.board_name, layoutId == R.layout.widget_detailed
                    ? board.name + " · " + board.length + " · " + trimVolume(board.volume) + " L"
                    : board.name);
            v.setTextViewText(R.id.score, "—");
            v.setInt(R.id.score, "setBackgroundResource", R.drawable.widget_score_neutral);
            v.setTextViewText(R.id.rating, refreshing ? "REFRESHING…" : "TAP TO LOAD");
            v.setTextColor(R.id.rating, context.getColor(R.color.widget_accent));
            v.setTextViewText(R.id.window, "Forecast not cached");
            v.setTextViewText(R.id.faces, "— FT");
            v.setTextViewText(R.id.face_band, refreshing ? "UPDATING CONDITIONS" : "OPEN APP TO LOAD");
            if (layoutId == R.layout.widget_detailed) {
                v.setTextViewText(R.id.swell, "—");
                v.setTextViewText(R.id.wind, "—");
                v.setTextViewText(R.id.tide, "—");
                v.setTextViewText(R.id.updated, refreshing ? "Refreshing forecast…" : "Open app or tap refresh");
            }
            return v;
        }

        v.setTextViewText(R.id.board_name, layoutId == R.layout.widget_detailed
                ? snapshot.boardName + " · " + snapshot.boardLength + " · " + trimVolume(snapshot.boardVolume) + " L"
                : snapshot.boardName);
        v.setTextViewText(R.id.score, String.valueOf(snapshot.score));
        applyScoreStyle(context, v, snapshot.score);
        v.setTextViewText(R.id.rating, snapshot.rating);
        v.setTextColor(R.id.rating, scoreColor(context, snapshot.score));
        v.setTextViewText(R.id.window, compactWindow(snapshot));
        String[] faceParts = splitFaces(snapshot.facesText);
        v.setTextViewText(R.id.faces, faceParts[0]);
        v.setTextViewText(R.id.face_band, faceParts[1]);

        if (layoutId == R.layout.widget_detailed) {
            v.setTextViewText(R.id.swell, snapshot.swellText);
            v.setTextViewText(R.id.wind, snapshot.windText);
            v.setTextViewText(R.id.tide, snapshot.tideText);
            v.setTextViewText(R.id.updated, refreshing ? "Refreshing…" : updatedText(snapshot));
            v.setViewVisibility(R.id.refresh_button, View.VISIBLE);
        }
        return v;
    }

    private static int scoreColor(Context context, int score) {
        if (score >= 75) return context.getColor(R.color.widget_good);
        if (score >= 60) return context.getColor(R.color.widget_warn);
        if (score < 45) return context.getColor(R.color.widget_bad);
        return context.getColor(R.color.widget_accent);
    }

    private static void applyScoreStyle(Context context, RemoteViews v, int score) {
        int background;
        if (score >= 75) background = R.drawable.widget_score_good;
        else if (score >= 60) background = R.drawable.widget_score_warn;
        else if (score < 45) background = R.drawable.widget_score_bad;
        else background = R.drawable.widget_score_neutral;
        v.setInt(R.id.score, "setBackgroundResource", background);
        v.setTextColor(R.id.score, context.getColor(R.color.widget_score_text));
    }

    private static String[] splitFaces(String raw) {
        if (raw == null || raw.trim().isEmpty() || "—".equals(raw.trim())) {
            return new String[]{"— FT", "BREAKING HEIGHT UNKNOWN"};
        }
        String[] parts = raw.split("\\s*·\\s*", 2);
        String height = parts[0].trim().replace(" ft", " FT").replace("ft", "FT");
        String band = parts.length > 1 ? parts[1].trim().toUpperCase(Locale.US) : "ESTIMATED BREAKING FACES";
        return new String[]{height, band};
    }

    private static String compactWindow(ForecastRepository.ForecastSnapshot s) {
        if (s.windowStart <= 0 || s.windowEnd <= 0) return s.windowText;
        ZoneId zone = ZoneId.of("America/New_York");
        java.time.ZonedDateTime start = Instant.ofEpochMilli(s.windowStart).atZone(zone);
        java.time.ZonedDateTime end = Instant.ofEpochMilli(s.windowEnd).atZone(zone);
        DateTimeFormatter day = DateTimeFormatter.ofPattern("EEE", Locale.US);
        DateTimeFormatter hour = DateTimeFormatter.ofPattern("h", Locale.US);
        DateTimeFormatter hourMeridiem = DateTimeFormatter.ofPattern("h a", Locale.US);
        String startTime = start.get(java.time.temporal.ChronoField.AMPM_OF_DAY) == end.get(java.time.temporal.ChronoField.AMPM_OF_DAY)
                ? start.format(hour)
                : start.format(hourMeridiem);
        return start.format(day) + " · " + startTime + "–" + end.format(hourMeridiem);
    }

    private static String trimVolume(double v) {
        return Math.abs(v - Math.rint(v)) < 0.05 ? String.format(Locale.US, "%.0f", v) : String.format(Locale.US, "%.1f", v);
    }

    private static String updatedText(ForecastRepository.ForecastSnapshot s) {
        if (s.updatedAt <= 0) return s.sourceText;
        ZoneId zone = ZoneId.of("America/New_York");
        String t = Instant.ofEpochMilli(s.updatedAt).atZone(zone)
                .format(DateTimeFormatter.ofPattern("h:mm a", Locale.US));
        long ageMinutes = Math.max(0, (System.currentTimeMillis() - s.updatedAt) / 60_000L);
        String age = ageMinutes < 60 ? ageMinutes + "m ago" : (ageMinutes / 60) + "h ago";
        return "Updated " + t + " · " + age + " · " + s.sourceText;
    }
}
