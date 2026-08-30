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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BoardWindowWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "com.rockaway.boardwindow.REFRESH_WIDGET";
    private static final ZoneId ZONE = ZoneId.of("America/New_York");
    private static final String[] DIRS = {"n","nne","ne","ene","e","ese","se","sse","s","ssw","sw","wsw","w","wnw","nw","nnw"};

    private static final Pattern SWELL_PATTERN = Pattern.compile("([0-9.]+)'\\s*@\\s*([0-9.]+)s\\s*([A-Z]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WIND_PATTERN = Pattern.compile("^([A-Z]+)\\s+([0-9.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIDE_PATTERN = Pattern.compile("^(.+?)\\s*([↑↓↔])?\\s*·\\s*([0-9.]+)\\s*ft", Pattern.CASE_INSENSITIVE);
    private static final Pattern FACE_PATTERN = Pattern.compile("([0-9.]+)\\s*[–-]\\s*([0-9.]+)\\s*ft", Pattern.CASE_INSENSITIVE);

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
        ForecastRepository.ForecastSnapshot snapshot = ForecastRepository.loadSnapshot(context);
        for (int id : manager.getAppWidgetIds(component)) {
            manager.updateAppWidget(id, buildResponsive(context, manager, id, snapshot, true));
        }
    }

    private static void updateWidget(Context context, AppWidgetManager manager, int id) {
        ForecastRepository.ForecastSnapshot snapshot = ForecastRepository.loadSnapshot(context);
        manager.updateAppWidget(id, buildResponsive(context, manager, id, snapshot, false));
    }

    private static RemoteViews buildResponsive(Context context, AppWidgetManager manager, int id,
                                               ForecastRepository.ForecastSnapshot snapshot,
                                               boolean refreshing) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Map<SizeF, RemoteViews> map = new HashMap<>();
            map.put(new SizeF(110f, 110f), buildView(context, R.layout.widget_compact, snapshot, refreshing));
            map.put(new SizeF(270f, 110f), buildView(context, R.layout.widget_detailed, snapshot, refreshing));
            map.put(new SizeF(270f, 175f), buildView(context, R.layout.widget_large, snapshot, refreshing));
            return new RemoteViews(map);
        }
        Bundle options = manager.getAppWidgetOptions(id);
        int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110);
        int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110);
        int layout = minWidth >= 220 ? (minHeight >= 155 ? R.layout.widget_large : R.layout.widget_detailed) : R.layout.widget_compact;
        return buildView(context, layout, snapshot, refreshing);
    }

    private static RemoteViews buildView(Context context, int layoutId,
                                         ForecastRepository.ForecastSnapshot snapshot,
                                         boolean refreshing) {
        RemoteViews v = new RemoteViews(context.getPackageName(), layoutId);
        boolean detailed = layoutId == R.layout.widget_detailed;
        boolean large = layoutId == R.layout.widget_large;
        boolean rich = detailed || large;
        ForecastRepository.BoardProfile board = ForecastRepository.selectedBoard(context);

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(context, 10, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widget_root, openPi);

        if (rich) {
            Intent refresh = new Intent(context, BoardWindowWidgetProvider.class);
            refresh.setAction(ACTION_REFRESH);
            PendingIntent refreshPi = PendingIntent.getBroadcast(context, 11, refresh,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            v.setOnClickPendingIntent(R.id.refresh_button, refreshPi);
        }

        if (snapshot == null) {
            v.setTextViewText(R.id.board_name, rich
                    ? formatBoardTitle(board.name, board.length, board.volume)
                    : board.name);
            v.setTextViewText(R.id.day_date, "—");
            v.setTextViewText(R.id.time_window, "NO FORECAST");
            v.setTextViewText(R.id.score, "—");
            applyScoreStyle(context, v, R.id.score, 50);
            v.setTextViewText(R.id.rating, refreshing ? "REFRESHING…" : "TAP TO LOAD");
            v.setTextColor(R.id.rating, context.getColor(R.color.widget_accent));
            v.setTextViewText(R.id.faces, "— FT");
            v.setTextViewText(R.id.face_band, refreshing ? "UPDATING CONDITIONS" : "OPEN APP TO LOAD");
            if (rich) {
                bindMainInstruments(context, v, "—", "—", "—");
                v.setTextViewText(R.id.updated, refreshing ? "Refreshing…" : "No cached forecast");
            }
            if (large) clearNextCards(v);
            return v;
        }

        v.setTextViewText(R.id.board_name, rich
                ? formatBoardTitle(snapshot.boardName, snapshot.boardLength, snapshot.boardVolume)
                : snapshot.boardName);
        v.setTextViewText(R.id.day_date, dayDate(snapshot.windowStart));
        v.setTextViewText(R.id.time_window, timeRange(snapshot.windowStart, snapshot.windowEnd));
        v.setTextViewText(R.id.score, String.valueOf(snapshot.score));
        applyScoreStyle(context, v, R.id.score, snapshot.score);
        v.setTextViewText(R.id.rating, snapshot.rating);
        v.setTextColor(R.id.rating, scoreColor(context, snapshot.score));
        String[] faceParts = splitFaces(snapshot.facesText);
        v.setTextViewText(R.id.faces, faceParts[0]);
        v.setTextViewText(R.id.face_band, faceParts[1]);

        if (rich) {
            bindMainInstruments(context, v, snapshot.swellText, snapshot.windText, snapshot.tideText);
            v.setTextViewText(R.id.updated, refreshing ? "Refreshing…" : updatedText(snapshot.updatedAt));
            v.setViewVisibility(R.id.refresh_button, View.VISIBLE);
        }
        if (large) bindNextCards(context, v, snapshot.nextWindows);
        return v;
    }

    private static void bindMainInstruments(Context context, RemoteViews v, String swellRaw, String windRaw, String tideRaw) {
        SwellParts swell = parseSwell(swellRaw);
        v.setTextViewText(R.id.swell_height, swell.heightText);
        v.setTextViewText(R.id.swell_period, swell.periodText);
        v.setTextViewText(R.id.swell_dir, swell.direction);
        v.setImageViewResource(R.id.swell_icon, swellDrawable(context, swell));

        WindParts wind = parseWind(windRaw);
        v.setTextViewText(R.id.wind_speed, wind.speedText);
        v.setTextViewText(R.id.wind_dir, wind.direction);
        v.setImageViewResource(R.id.wind_icon, directionDrawable(context, "wind", wind.direction, true));

        TideParts tide = parseTide(tideRaw);
        v.setTextViewText(R.id.tide_height, tide.heightText);
        v.setTextViewText(R.id.tide_phase, tide.phaseText);
        v.setImageViewResource(R.id.tide_icon, tideDrawable(context, tide));
    }

    private static void bindNextCards(Context context, RemoteViews v, List<ForecastRepository.WindowSummary> windows) {
        int[] cards = {R.id.next1_card, R.id.next2_card, R.id.next3_card};
        int[] dates = {R.id.next1_date, R.id.next2_date, R.id.next3_date};
        int[] times = {R.id.next1_time, R.id.next2_time, R.id.next3_time};
        int[] scores = {R.id.next1_score, R.id.next2_score, R.id.next3_score};
        int[] faces = {R.id.next1_faces, R.id.next2_faces, R.id.next3_faces};
        int[] bands = {R.id.next1_band, R.id.next2_band, R.id.next3_band};
        int[] swellArrows = {R.id.next1_swell_arrow, R.id.next2_swell_arrow, R.id.next3_swell_arrow};
        int[] swellTexts = {R.id.next1_swell, R.id.next2_swell, R.id.next3_swell};
        int[] windArrows = {R.id.next1_wind_arrow, R.id.next2_wind_arrow, R.id.next3_wind_arrow};
        int[] windTexts = {R.id.next1_wind, R.id.next2_wind, R.id.next3_wind};

        for (int i = 0; i < 3; i++) {
            if (windows == null || i >= windows.size()) {
                v.setViewVisibility(cards[i], View.INVISIBLE);
                continue;
            }
            ForecastRepository.WindowSummary w = windows.get(i);
            v.setViewVisibility(cards[i], View.VISIBLE);
            v.setTextViewText(dates[i], dayDate(w.windowStart));
            v.setTextViewText(times[i], timeRange(w.windowStart, w.windowEnd));
            v.setTextViewText(scores[i], String.valueOf(w.score));
            applySmallScoreStyle(context, v, scores[i], w.score);
            String[] fp = splitFaces(w.facesText);
            v.setTextViewText(faces[i], roundedFaces(w.facesText));
            v.setTextViewText(bands[i], fp[1]);

            SwellParts swell = parseSwell(w.swellText);
            v.setImageViewResource(swellArrows[i], directionDrawable(context, "dir", swell.direction, true));
            String swellShort = swell.direction.equals("—") ? "—" : swell.direction + " · " + swell.heightText + " @ " + swell.periodText;
            v.setTextViewText(swellTexts[i], swellShort);

            WindParts wind = parseWind(w.windText);
            v.setImageViewResource(windArrows[i], directionDrawable(context, "dir", wind.direction, true));
            String windShort = wind.direction.equals("—") ? "—" : wind.direction + " · " + wind.speedText + " mph";
            v.setTextViewText(windTexts[i], windShort);
        }
    }

    private static void clearNextCards(RemoteViews v) {
        v.setViewVisibility(R.id.next1_card, View.INVISIBLE);
        v.setViewVisibility(R.id.next2_card, View.INVISIBLE);
        v.setViewVisibility(R.id.next3_card, View.INVISIBLE);
    }

    private static void applySmallScoreStyle(Context context, RemoteViews v, int viewId, int score) {
        int background;
        if (score >= 75) background = R.drawable.widget_score_small_good;
        else if (score >= 60) background = R.drawable.widget_score_small_warn;
        else if (score < 45) background = R.drawable.widget_score_small_bad;
        else background = R.drawable.widget_score_small_neutral;
        v.setInt(viewId, "setBackgroundResource", background);
        v.setTextColor(viewId, context.getColor(R.color.widget_score_text));
    }

    private static int scoreColor(Context context, int score) {
        if (score >= 75) return context.getColor(R.color.widget_good);
        if (score >= 60) return context.getColor(R.color.widget_warn);
        if (score < 45) return context.getColor(R.color.widget_bad);
        return context.getColor(R.color.widget_accent);
    }

    private static void applyScoreStyle(Context context, RemoteViews v, int viewId, int score) {
        int background;
        if (score >= 75) background = R.drawable.widget_score_good;
        else if (score >= 60) background = R.drawable.widget_score_warn;
        else if (score < 45) background = R.drawable.widget_score_bad;
        else background = R.drawable.widget_score_neutral;
        v.setInt(viewId, "setBackgroundResource", background);
        v.setTextColor(viewId, context.getColor(R.color.widget_score_text));
    }

    private static String[] splitFaces(String raw) {
        if (raw == null || raw.trim().isEmpty() || "—".equals(raw.trim())) return new String[]{"— FT", "BREAKING HEIGHT UNKNOWN"};
        String[] parts = raw.split("\\s*·\\s*", 2);
        String height = parts[0].trim().toUpperCase(Locale.US).replace(" FT", " FT").replace("FT", " FT").replace("  ", " ");
        if (!height.endsWith("FT")) height = height.replace("ft", "FT");
        String band = parts.length > 1 ? parts[1].trim().toUpperCase(Locale.US) : "ESTIMATED BREAKING FACES";
        return new String[]{height, band};
    }

    private static String roundedFaces(String raw) {
        if (raw == null) return "— FT";
        Matcher m = FACE_PATTERN.matcher(raw);
        if (!m.find()) return splitFaces(raw)[0];
        int low = (int) Math.round(Double.parseDouble(m.group(1)));
        int high = (int) Math.round(Double.parseDouble(m.group(2)));
        if (high < low) high = low;
        return low == high ? low + " FT" : low + "–" + high + " FT";
    }

    private static String dayDate(long epochMs) {
        if (epochMs <= 0) return "—";
        return Instant.ofEpochMilli(epochMs).atZone(ZONE).format(DateTimeFormatter.ofPattern("EEE M/d", Locale.US));
    }

    private static String timeRange(long startMs, long endMs) {
        if (startMs <= 0 || endMs <= 0) return "—";
        ZonedDateTime start = Instant.ofEpochMilli(startMs).atZone(ZONE);
        ZonedDateTime end = Instant.ofEpochMilli(endMs).atZone(ZONE);
        DateTimeFormatter hour = DateTimeFormatter.ofPattern("h", Locale.US);
        DateTimeFormatter hourMeridiem = DateTimeFormatter.ofPattern("h a", Locale.US);
        boolean sameAmPm = start.get(ChronoField.AMPM_OF_DAY) == end.get(ChronoField.AMPM_OF_DAY);
        return (sameAmPm ? start.format(hour) : start.format(hourMeridiem)) + "–" + end.format(hourMeridiem);
    }

    private static String updatedText(long updatedAt) {
        if (updatedAt <= 0) return "Not updated";
        long mins = Math.max(0, (System.currentTimeMillis() - updatedAt) / 60_000L);
        String time = Instant.ofEpochMilli(updatedAt).atZone(ZONE).format(DateTimeFormatter.ofPattern("h:mm a", Locale.US));
        if (mins < 2) return "Updated " + time + " · just now";
        if (mins < 60) return "Updated " + time + " · " + mins + "m ago";
        long hrs = mins / 60;
        return "Updated " + time + " · " + hrs + "h ago";
    }

    private static final class SwellParts {
        String heightText = "—";
        String periodText = "—";
        String direction = "—";
        double height = Double.NaN;
        double period = Double.NaN;
    }

    private static SwellParts parseSwell(String raw) {
        SwellParts p = new SwellParts();
        if (raw == null) return p;
        Matcher m = SWELL_PATTERN.matcher(raw.trim());
        if (!m.find()) return p;
        p.height = parseDouble(m.group(1));
        p.period = parseDouble(m.group(2));
        p.heightText = Double.isNaN(p.height) ? "—" : String.format(Locale.US, "%.1f'", p.height);
        p.periodText = Double.isNaN(p.period) ? "—" : String.format(Locale.US, "%.0fs", p.period);
        p.direction = normalizeDirection(m.group(3));
        return p;
    }

    private static final class WindParts {
        String speedText = "—";
        String direction = "—";
    }

    private static WindParts parseWind(String raw) {
        WindParts p = new WindParts();
        if (raw == null) return p;
        Matcher m = WIND_PATTERN.matcher(raw.trim());
        if (!m.find()) return p;
        p.direction = normalizeDirection(m.group(1));
        double speed = parseDouble(m.group(2));
        p.speedText = Double.isNaN(speed) ? "—" : String.format(Locale.US, "%.0f", speed);
        return p;
    }

    private static final class TideParts {
        String heightText = "—";
        String phaseText = "—";
        int level = 2;
        String motion = "still";
    }

    private static TideParts parseTide(String raw) {
        TideParts p = new TideParts();
        if (raw == null) return p;
        Matcher m = TIDE_PATTERN.matcher(raw.trim());
        if (!m.find()) return p;
        String phase = m.group(1).trim().toUpperCase(Locale.US);
        String arrow = m.group(2) == null ? "↔" : m.group(2);
        p.heightText = m.group(3);
        if (phase.contains("LOW")) p.level = 1;
        else if (phase.contains("HIGH")) p.level = 3;
        else p.level = 2;
        p.motion = "↑".equals(arrow) ? "up" : "↓".equals(arrow) ? "down" : "still";
        p.phaseText = phase + " " + arrow;
        return p;
    }

    private static int swellDrawable(Context context, SwellParts p) {
        int level = 2;
        if ((!Double.isNaN(p.height) && p.height < 2) || (!Double.isNaN(p.period) && p.period < 8)) level = 1;
        if ((!Double.isNaN(p.height) && p.height >= 3) || (!Double.isNaN(p.period) && p.period >= 12)) level = 3;
        String target = oppositeDirection(p.direction);
        return drawableByName(context, "swell_" + level + "_" + target.toLowerCase(Locale.US), R.drawable.swell_2_wnw);
    }

    private static int tideDrawable(Context context, TideParts p) {
        return drawableByName(context, "tide_" + p.level + "_" + p.motion, R.drawable.tide_2_up);
    }

    private static int directionDrawable(Context context, String prefix, String fromDirection, boolean travelDirection) {
        String direction = travelDirection ? oppositeDirection(fromDirection) : normalizeDirection(fromDirection);
        return drawableByName(context, prefix + "_" + direction.toLowerCase(Locale.US), R.drawable.dir_n);
    }

    private static int drawableByName(Context context, String name, int fallback) {
        int id = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
        return id == 0 ? fallback : id;
    }

    private static String normalizeDirection(String raw) {
        if (raw == null) return "—";
        String u = raw.trim().toUpperCase(Locale.US);
        for (String d : DIRS) if (d.equalsIgnoreCase(u)) return d.toUpperCase(Locale.US);
        return "—";
    }

    private static String oppositeDirection(String from) {
        String n = normalizeDirection(from);
        if ("—".equals(n)) return "N";
        for (int i = 0; i < DIRS.length; i++) {
            if (DIRS[i].equalsIgnoreCase(n)) return DIRS[(i + 8) % 16].toUpperCase(Locale.US);
        }
        return "N";
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s); }
        catch (Exception e) { return Double.NaN; }
    }

    private static String formatBoardTitle(String name, String length, double volume) {
        String safeName = name == null || name.trim().isEmpty() ? "Board" : name.trim();
        String safeLength = length == null ? "" : length.trim();
        boolean nameHasLength = !safeLength.isEmpty() && safeName.toLowerCase(Locale.US).contains(safeLength.toLowerCase(Locale.US));
        if (nameHasLength) return safeName + " · " + trimVolume(volume) + " L";
        if (!safeLength.isEmpty()) return safeName + " · " + safeLength + " · " + trimVolume(volume) + " L";
        return safeName + " · " + trimVolume(volume) + " L";
    }

    private static String trimVolume(double value) {
        return Math.abs(value - Math.rint(value)) < 0.05
                ? String.format(Locale.US, "%.0f", value)
                : String.format(Locale.US, "%.1f", value);
    }
}
