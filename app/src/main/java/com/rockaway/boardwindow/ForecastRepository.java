package com.rockaway.boardwindow;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ForecastRepository {
    public static final String PREFS_NAME = "rockaway_widget_prefs";
    public static final String KEY_PWA_STATE = "pwa_state";
    private static final String KEY_SNAPSHOT = "forecast_snapshot";

    private static final double LAT = 40.586;
    private static final double LON = -73.813;
    private static final String TZ = "America/New_York";
    private static final ZoneId ZONE = ZoneId.of(TZ);
    private static final int FORECAST_DAYS = 7;

    private ForecastRepository() {}

    public static final class BoardProfile {
        public String name = "6'6 Progression";
        public String length = "6'6";
        public double volume = 40;
        public String mode = "demo";
        public double energyLow = 40;
        public double energyHigh = 85;
        public double periodLow = 8;
        public double periodHigh = 11;
        public double maxWind = 11;
    }

    public static final class ForecastSnapshot {
        public long updatedAt;
        public long windowStart;
        public long windowEnd;
        public String boardName;
        public String boardLength;
        public double boardVolume;
        public int score;
        public String rating;
        public String windowText;
        public String facesText;
        public String swellText;
        public String windText;
        public String tideText;
        public String sourceText;

        JSONObject toJson() throws JSONException {
            JSONObject j = new JSONObject();
            j.put("updatedAt", updatedAt);
            j.put("windowStart", windowStart);
            j.put("windowEnd", windowEnd);
            j.put("boardName", boardName);
            j.put("boardLength", boardLength);
            j.put("boardVolume", boardVolume);
            j.put("score", score);
            j.put("rating", rating);
            j.put("windowText", windowText);
            j.put("facesText", facesText);
            j.put("swellText", swellText);
            j.put("windText", windText);
            j.put("tideText", tideText);
            j.put("sourceText", sourceText);
            return j;
        }

        static ForecastSnapshot fromJson(JSONObject j) {
            ForecastSnapshot s = new ForecastSnapshot();
            s.updatedAt = j.optLong("updatedAt", 0);
            s.windowStart = j.optLong("windowStart", 0);
            s.windowEnd = j.optLong("windowEnd", 0);
            s.boardName = j.optString("boardName", "6'6 Progression");
            s.boardLength = j.optString("boardLength", "6'6");
            s.boardVolume = j.optDouble("boardVolume", 40);
            s.score = j.optInt("score", 0);
            s.rating = j.optString("rating", "Forecast unavailable");
            s.windowText = j.optString("windowText", "—");
            s.facesText = j.optString("facesText", "—");
            s.swellText = j.optString("swellText", "—");
            s.windText = j.optString("windText", "—");
            s.tideText = j.optString("tideText", "—");
            s.sourceText = j.optString("sourceText", "Open-Meteo + NOAA");
            return s;
        }
    }

    private static final class TideInfo {
        String label;
        double score;
        TideInfo(String label, double score) { this.label = label; this.score = score; }
    }

    private static final class BreakEstimate {
        double low;
        double high;
        String band;
    }

    private static final class HourRow {
        LocalDateTime time;
        Double waveHeight, wavePeriod, waveDir;
        Double swellHeight, swellPeriod, swellDir;
        Double secondaryHeight, secondaryPeriod, secondaryDir;
        Double windSpeed, windDir;
        Double energy;
        TideInfo tide;
        BreakEstimate breaking;
        int score;
    }

    private static final class Window {
        LocalDateTime start, end;
        int score;
        Double swellHeight, period, swellDir, energy;
        Double secondaryHeight, secondaryPeriod, secondaryDir;
        Double waveHeight, wavePeriod, waveDir;
        Double windSpeed, windDir;
        String tideLabel;
        double tideScore;
        BreakEstimate breaking;
    }

    public static BoardProfile selectedBoard(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_PWA_STATE, null);
        if (raw == null) return new BoardProfile();
        try {
            JSONObject root = new JSONObject(raw);
            String activeId = root.optString("activeBoardId", "progression-66");
            JSONArray boards = root.optJSONArray("boards");
            if (boards == null) return new BoardProfile();
            JSONObject found = null;
            for (int i = 0; i < boards.length(); i++) {
                JSONObject b = boards.optJSONObject(i);
                if (b != null && activeId.equals(b.optString("id"))) { found = b; break; }
            }
            if (found == null && boards.length() > 0) found = boards.optJSONObject(0);
            if (found == null) return new BoardProfile();
            BoardProfile b = new BoardProfile();
            b.name = found.optString("name", b.name);
            b.length = found.optString("boardLength", b.length);
            b.volume = found.optDouble("boardVolume", b.volume);
            b.mode = found.optString("mode", b.mode);
            b.energyLow = found.optDouble("energyLow", b.energyLow);
            b.energyHigh = found.optDouble("energyHigh", b.energyHigh);
            b.periodLow = found.optDouble("periodLow", b.periodLow);
            b.periodHigh = found.optDouble("periodHigh", b.periodHigh);
            b.maxWind = found.optDouble("maxWind", b.maxWind);
            return b;
        } catch (JSONException ignored) {
            return new BoardProfile();
        }
    }

    public static void saveSnapshot(Context context, ForecastSnapshot snapshot) {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putString(KEY_SNAPSHOT, snapshot.toJson().toString()).apply();
        } catch (JSONException ignored) {}
    }

    public static ForecastSnapshot loadSnapshot(Context context) {
        String raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SNAPSHOT, null);
        if (raw == null) return null;
        try { return ForecastSnapshot.fromJson(new JSONObject(raw)); }
        catch (JSONException e) { return null; }
    }

    public static ForecastSnapshot refresh(Context context) throws Exception {
        BoardProfile board = selectedBoard(context);
        String tz = URLEncoder.encode(TZ, StandardCharsets.UTF_8);
        String marineUrl = "https://marine-api.open-meteo.com/v1/marine?latitude=" + LAT +
                "&longitude=" + LON +
                "&hourly=wave_height,wave_direction,wave_period,swell_wave_height,swell_wave_direction,swell_wave_period,secondary_swell_wave_height,secondary_swell_wave_period,secondary_swell_wave_direction,sea_level_height_msl" +
                "&length_unit=imperial&timezone=" + tz + "&forecast_days=" + FORECAST_DAYS + "&cell_selection=sea";
        String weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=" + LAT +
                "&longitude=" + LON +
                "&hourly=wind_speed_10m,wind_direction_10m,wind_gusts_10m&daily=sunrise,sunset" +
                "&wind_speed_unit=mph&timezone=" + tz + "&forecast_days=" + FORECAST_DAYS;

        JSONObject marine = getJson(marineUrl);
        JSONObject weather = getJson(weatherUrl);

        JSONObject noaa = null;
        try { noaa = getJson(noaaUrl()); } catch (Exception ignored) {}

        BuildResult result = buildRows(marine, weather, noaa, board);
        List<Window> windows = buildWindows(result.rows, board, result.sunrise, result.sunset);
        if (windows.isEmpty()) throw new IOException("No usable forecast windows returned.");
        windows.sort(Comparator.comparingInt((Window w) -> w.score).reversed().thenComparing(w -> w.start));
        Window best = windows.get(0);

        ForecastSnapshot s = new ForecastSnapshot();
        s.updatedAt = System.currentTimeMillis();
        s.windowStart = best.start.atZone(ZONE).toInstant().toEpochMilli();
        s.windowEnd = best.end.atZone(ZONE).toInstant().toEpochMilli();
        s.boardName = board.name;
        s.boardLength = board.length;
        s.boardVolume = board.volume;
        s.score = best.score;
        s.rating = scoreLabel(best.score);
        s.windowText = formatWindow(best.start, best.end);
        s.facesText = best.breaking == null ? "—" : String.format(Locale.US, "%.1f–%.1f ft · %s", best.breaking.low, best.breaking.high, best.breaking.band);
        s.swellText = best.swellHeight == null || best.period == null ? "—" :
                String.format(Locale.US, "%.1f' @ %.0fs %s", best.swellHeight, best.period, compass(best.swellDir));
        s.windText = best.windSpeed == null ? "—" : String.format(Locale.US, "%s %.0f mph", compass(best.windDir), best.windSpeed);
        s.tideText = best.tideLabel == null ? "—" : best.tideLabel;
        s.sourceText = result.usedNoaa ? "Open-Meteo · NOAA tides" : "Open-Meteo · tide fallback";
        return s;
    }

    private static String noaaUrl() {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyyMMdd");
        LocalDate start = LocalDate.now(ZONE);
        LocalDate end = start.plusDays(FORECAST_DAYS + 1L);
        return "https://api.tidesandcurrents.noaa.gov/api/prod/datagetter?begin_date=" + f.format(start) +
                "&end_date=" + f.format(end) + "&station=8531680&product=predictions&datum=MLLW" +
                "&time_zone=lst_ldt&interval=h&units=english&application=RockawayBoardWindowAndroid&format=json";
    }

    private static JSONObject getJson(String urlString) throws IOException, JSONException {
        HttpURLConnection c = (HttpURLConnection) new URL(urlString).openConnection();
        c.setConnectTimeout(12_000);
        c.setReadTimeout(15_000);
        c.setRequestProperty("User-Agent", "RockawayBoardWindow/1.0 Android");
        c.setRequestProperty("Accept", "application/json");
        try {
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            return new JSONObject(sb.toString());
        } finally {
            c.disconnect();
        }
    }

    private static final class BuildResult {
        List<HourRow> rows = new ArrayList<>();
        Map<LocalDate, LocalDateTime> sunrise = new HashMap<>();
        Map<LocalDate, LocalDateTime> sunset = new HashMap<>();
        boolean usedNoaa;
    }

    private static BuildResult buildRows(JSONObject marine, JSONObject weather, JSONObject noaa, BoardProfile board) throws JSONException {
        BuildResult out = new BuildResult();
        JSONObject mh = marine.getJSONObject("hourly");
        JSONObject wh = weather.getJSONObject("hourly");
        JSONArray mtimes = mh.getJSONArray("time");
        JSONArray wtimes = wh.getJSONArray("time");

        Map<String, Integer> weatherIndex = new HashMap<>();
        for (int i = 0; i < wtimes.length(); i++) weatherIndex.put(wtimes.getString(i), i);

        JSONObject daily = weather.optJSONObject("daily");
        if (daily != null) {
            JSONArray dates = daily.optJSONArray("time");
            JSONArray sunrises = daily.optJSONArray("sunrise");
            JSONArray sunsets = daily.optJSONArray("sunset");
            if (dates != null && sunrises != null && sunsets != null) {
                for (int i = 0; i < dates.length(); i++) {
                    try {
                        LocalDate d = LocalDate.parse(dates.getString(i));
                        out.sunrise.put(d, LocalDateTime.parse(sunrises.getString(i)));
                        out.sunset.put(d, LocalDateTime.parse(sunsets.getString(i)));
                    } catch (Exception ignored) {}
                }
            }
        }

        Map<String, Double> noaaMap = new HashMap<>();
        if (noaa != null) {
            JSONArray p = noaa.optJSONArray("predictions");
            if (p != null) {
                for (int i = 0; i < p.length(); i++) {
                    JSONObject item = p.optJSONObject(i);
                    if (item == null) continue;
                    String key = item.optString("t", "").replace(' ', 'T');
                    double v = item.optDouble("v", Double.NaN);
                    if (!Double.isNaN(v)) noaaMap.put(key, v);
                }
                out.usedNoaa = !noaaMap.isEmpty();
            }
        }

        Double[] tideHeights = new Double[mtimes.length()];
        JSONArray modeledTide = mh.optJSONArray("sea_level_height_msl");
        for (int i = 0; i < mtimes.length(); i++) {
            String t = mtimes.getString(i);
            Double n = noaaMap.get(t);
            tideHeights[i] = n != null ? n : valueAt(modeledTide, i);
        }

        for (int i = 0; i < mtimes.length(); i++) {
            String t = mtimes.getString(i);
            Integer wi = weatherIndex.get(t);
            HourRow r = new HourRow();
            r.time = LocalDateTime.parse(t);
            r.waveHeight = valueAt(mh.optJSONArray("wave_height"), i);
            r.wavePeriod = valueAt(mh.optJSONArray("wave_period"), i);
            r.waveDir = valueAt(mh.optJSONArray("wave_direction"), i);
            r.swellHeight = firstNonNull(valueAt(mh.optJSONArray("swell_wave_height"), i), r.waveHeight);
            r.swellPeriod = firstNonNull(valueAt(mh.optJSONArray("swell_wave_period"), i), r.wavePeriod);
            r.swellDir = valueAt(mh.optJSONArray("swell_wave_direction"), i);
            r.secondaryHeight = valueAt(mh.optJSONArray("secondary_swell_wave_height"), i);
            r.secondaryPeriod = valueAt(mh.optJSONArray("secondary_swell_wave_period"), i);
            r.secondaryDir = valueAt(mh.optJSONArray("secondary_swell_wave_direction"), i);
            r.windSpeed = wi == null ? null : valueAt(wh.optJSONArray("wind_speed_10m"), wi);
            r.windDir = wi == null ? null : valueAt(wh.optJSONArray("wind_direction_10m"), wi);
            r.energy = r.swellHeight != null && r.swellPeriod != null ? r.swellHeight * r.swellHeight * r.swellPeriod : null;
            r.tide = stageTide(tideHeights, i);
            r.breaking = breakingEstimate(r.waveHeight, r.wavePeriod, r.waveDir,
                    r.swellHeight, r.swellPeriod, r.swellDir,
                    r.secondaryHeight, r.secondaryPeriod, r.secondaryDir, r.windSpeed);
            r.score = scoreHour(r, board);
            out.rows.add(r);
        }
        return out;
    }

    private static Double valueAt(JSONArray arr, int i) {
        if (arr == null || i < 0 || i >= arr.length() || arr.isNull(i)) return null;
        double v = arr.optDouble(i, Double.NaN);
        return Double.isNaN(v) || v <= -999 ? null : v;
    }

    private static Double firstNonNull(Double a, Double b) { return a != null ? a : b; }

    private static TideInfo stageTide(Double[] vals, int i) {
        Double v = vals[i];
        if (v == null) return new TideInfo("Unknown", 60);
        int lo = Math.max(0, i - 6), hi = Math.min(vals.length - 1, i + 6);
        double mn = Double.POSITIVE_INFINITY, mx = Double.NEGATIVE_INFINITY;
        for (int x = lo; x <= hi; x++) {
            if (vals[x] != null) { mn = Math.min(mn, vals[x]); mx = Math.max(mx, vals[x]); }
        }
        if (!Double.isFinite(mn) || !Double.isFinite(mx)) return new TideInfo("Unknown", 60);
        double pct = mx > mn ? (v - mn) / (mx - mn) : .5;
        Double prev = vals[Math.max(0, i - 1)], next = vals[Math.min(vals.length - 1, i + 1)];
        Boolean incoming = prev != null && next != null ? next > prev : null;
        String stage = pct < .2 ? "Low" : pct < .62 ? "Mid" : pct < .82 ? "Mid-high" : "High";
        double score = pct < .15 ? 82 : pct < .62 ? 96 : pct < .82 ? 74 : 55;
        if (Boolean.TRUE.equals(incoming) && pct < .68) score = Math.min(100, score + 4);
        String arrow = incoming == null ? "" : incoming ? " ↑" : " ↓";
        return new TideInfo(String.format(Locale.US, "%s%s · %.1f ft", stage, arrow, v), score);
    }

    private static List<Window> buildWindows(List<HourRow> rows, BoardProfile board,
                                              Map<LocalDate, LocalDateTime> sunrise,
                                              Map<LocalDate, LocalDateTime> sunset) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        List<HourRow> usable = new ArrayList<>();
        for (HourRow r : rows) {
            if (r.time.isBefore(now)) continue;
            LocalDateTime rise = sunrise.get(r.time.toLocalDate());
            LocalDateTime set = sunset.get(r.time.toLocalDate());
            boolean daylight = rise == null || set == null ? r.time.getHour() >= 6 && r.time.getHour() <= 18
                    : !r.time.isBefore(rise) && r.time.isBefore(set);
            if (daylight) usable.add(r);
        }

        List<Window> out = windowsFromRows(usable, board);
        if (!out.isEmpty()) return out;
        List<HourRow> future = new ArrayList<>();
        for (HourRow r : rows) if (!r.time.isBefore(now)) future.add(r);
        return windowsFromRows(future, board);
    }

    private static List<Window> windowsFromRows(List<HourRow> rows, BoardProfile board) {
        List<Window> out = new ArrayList<>();
        for (int i = 0; i + 2 < rows.size(); i++) {
            HourRow a = rows.get(i), b = rows.get(i + 1), c = rows.get(i + 2);
            if (Duration.between(a.time, c.time).toHours() != 2) continue;
            if (!a.time.toLocalDate().equals(c.time.toLocalDate())) continue;
            out.add(aggregate(a, b, c, board));
        }
        return out;
    }

    private static Window aggregate(HourRow a, HourRow b, HourRow c, BoardProfile board) {
        HourRow[] r = {a, b, c};
        Window w = new Window();
        w.start = a.time;
        w.end = c.time.plusHours(1);
        w.score = (int) Math.round((a.score + b.score + c.score) / 3.0);
        w.swellHeight = mean(r, x -> x.swellHeight);
        w.period = mean(r, x -> x.swellPeriod);
        w.swellDir = circularMean(r, x -> x.swellDir);
        w.energy = mean(r, x -> x.energy);
        w.secondaryHeight = mean(r, x -> x.secondaryHeight);
        w.secondaryPeriod = mean(r, x -> x.secondaryPeriod);
        w.secondaryDir = circularMean(r, x -> x.secondaryDir);
        w.waveHeight = mean(r, x -> x.waveHeight);
        w.wavePeriod = mean(r, x -> x.wavePeriod);
        w.waveDir = circularMean(r, x -> x.waveDir);
        w.windSpeed = mean(r, x -> x.windSpeed);
        w.windDir = circularMean(r, x -> x.windDir);
        w.tideScore = (a.tide.score + b.tide.score + c.tide.score) / 3.0;
        w.tideLabel = b.tide.label;
        w.breaking = breakingEstimate(w.waveHeight, w.wavePeriod, w.waveDir,
                w.swellHeight, w.period, w.swellDir,
                w.secondaryHeight, w.secondaryPeriod, w.secondaryDir, w.windSpeed);
        return w;
    }

    private interface DoublePicker { Double get(HourRow row); }

    private static Double mean(HourRow[] rows, DoublePicker p) {
        double sum = 0; int n = 0;
        for (HourRow r : rows) { Double v = p.get(r); if (v != null) { sum += v; n++; } }
        return n == 0 ? null : sum / n;
    }

    private static Double circularMean(HourRow[] rows, DoublePicker p) {
        double x = 0, y = 0; int n = 0;
        for (HourRow r : rows) {
            Double d = p.get(r); if (d == null) continue;
            double rad = Math.toRadians(d); x += Math.cos(rad); y += Math.sin(rad); n++;
        }
        if (n == 0) return null;
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    private static int scoreHour(HourRow r, BoardProfile board) {
        double eScore = energyScore(r.energy, board);
        double pScore = trapezoid(r.swellPeriod, board.periodLow, board.periodHigh, 5.5,
                "demo".equals(board.mode) ? 15 : 17);
        double dScore = directionScore(r.swellDir);
        double wScore = windScore(r.windDir, r.windSpeed, board);
        double tScore = r.tide == null ? 60 : r.tide.score;
        double whScore = totalWaveScore(r.waveHeight, board);
        double total = .30 * eScore + .15 * pScore + .15 * dScore + .25 * wScore + .10 * tScore + .05 * whScore;
        if ("demo".equals(board.mode)) {
            if (r.energy != null && r.energy > 145) total = Math.min(total, 48);
            if (r.swellPeriod != null && r.swellHeight != null && r.swellPeriod >= 14 && r.swellHeight >= 2.5) total = Math.min(total, 52);
            if (r.waveHeight != null && r.waveHeight >= 4.5) total = Math.min(total, 50);
        }
        if (board.volume >= 50 && r.breaking != null) {
            if (r.breaking.high >= 6.5) total = Math.min(total, 42);
            else if (r.breaking.high >= 5.2) total = Math.min(total, 58);
        }
        return (int) Math.round(clamp(total, 0, 100));
    }

    private static double energyScore(Double e, BoardProfile board) {
        double upperHard = "demo".equals(board.mode) ? Math.max(board.energyHigh + 45, 130) : Math.max(board.energyHigh + 70, 170);
        return trapezoid(e, board.energyLow, board.energyHigh, 12, upperHard);
    }

    private static double totalWaveScore(Double h, BoardProfile board) {
        if (h == null) return 60;
        boolean highVolume = board.volume >= 50;
        double hi = "demo".equals(board.mode) ? 3.8 : (highVolume ? 4.2 : 5.0);
        double low = highVolume ? 0.8 : 1.4;
        return trapezoid(h, low, 3.0, .4, hi);
    }

    private static double trapezoid(Double x, double low, double high, double softLow, double softHigh) {
        if (x == null || Double.isNaN(x)) return 50;
        if (x >= low && x <= high) return 100;
        if (x < low) return clamp(100 * (x - softLow) / (low - softLow), 0, 100);
        return clamp(100 * (softHigh - x) / (softHigh - high), 0, 100);
    }

    private static double directionScore(Double d) {
        if (d == null) return 55;
        double x = ((d % 360) + 360) % 360;
        if (x >= 85 && x <= 140) return 100;
        if (x >= 65 && x < 85) return 70 + (x - 65) * 1.5;
        if (x > 140 && x <= 165) return 100 - (x - 140) * 1.6;
        if (x >= 45 && x < 65) return 40 + (x - 45) * 1.5;
        if (x > 165 && x <= 190) return 60 - (x - 165) * 1.2;
        return 30;
    }

    private static double windScore(Double dir, Double speed, BoardProfile board) {
        if (speed == null) return 55;
        if (speed <= 3) return 100;
        double d = dir == null ? 0 : ((dir % 360) + 360) % 360;
        double directional;
        if (d >= 290 || d <= 30) directional = 100;
        else if ((d >= 270 && d < 290) || (d > 30 && d <= 55)) directional = 78;
        else if ((d >= 250 && d < 270) || (d > 55 && d <= 80)) directional = 55;
        else directional = 25;
        double speedFactor = speed <= 7 ? 1 : speed <= board.maxWind ? (1 - (speed - 7) * .035)
                : clamp(.82 - (speed - board.maxWind) * .09, .18, .82);
        return clamp(directional * speedFactor, 0, 100);
    }

    private static double directionalTransmission(Double d) {
        if (d == null || Double.isNaN(d)) return .65;
        double ds = directionScore(d) / 100.0;
        return clamp(Math.pow(ds, 1.35), .18, 1);
    }

    private static BreakEstimate breakingEstimate(Double totalH, Double totalT, Double totalD,
                                                  Double h1, Double t1, Double d1,
                                                  Double h2, Double t2, Double d2,
                                                  Double windSpeed) {
        double e1 = h1 != null && t1 != null ? h1 * h1 * t1 * directionalTransmission(d1) : 0;
        double e2 = h2 != null && t2 != null ? h2 * h2 * t2 * directionalTransmission(d2) : 0;
        double eTotal = totalH != null && totalT != null ? totalH * totalH * totalT * directionalTransmission(totalD) : 0;
        double effective = Math.max(e1 + e2, eTotal);
        if (!(effective > 0)) return null;
        double center = 1.50 * Math.sqrt(effective / 10.0);
        double primaryShare = (e1 + e2) > 0 ? e1 / (e1 + e2) : 1;
        double uncertainty = .20;
        if ((t1 != null && t1 < 7) || directionScore(d1) < 60) uncertainty += .07;
        if (primaryShare < .72) uncertainty += .05;
        if (windSpeed != null && windSpeed > 14) uncertainty += .05;
        uncertainty = clamp(uncertainty, .18, .36);
        BreakEstimate b = new BreakEstimate();
        b.low = Math.max(.5, center * (1 - uncertainty));
        b.high = center * (1 + uncertainty);
        String lowBand = breakBand(b.low), highBand = breakBand(b.high);
        b.band = lowBand.equals(highBand) ? lowBand : lowBand + "–" + highBand;
        return b;
    }

    private static String breakBand(double ft) {
        if (ft < 1.3) return "knee";
        if (ft < 1.9) return "thigh";
        if (ft < 2.7) return "waist";
        if (ft < 3.5) return "chest";
        if (ft < 4.4) return "shoulder";
        if (ft < 5.4) return "head";
        if (ft < 6.5) return "slightly OH";
        if (ft < 8.0) return "overhead";
        if (ft < 10.0) return "well OH";
        return "DOH+";
    }

    private static double clamp(double x, double a, double b) { return Math.max(a, Math.min(b, x)); }

    private static String compass(Double d) {
        if (d == null || Double.isNaN(d)) return "—";
        String[] p = {"N","NNE","NE","ENE","E","ESE","SE","SSE","S","SSW","SW","WSW","W","WNW","NW","NNW"};
        int idx = (int) Math.round((((d % 360) + 360) % 360) / 22.5) % 16;
        return p[idx];
    }

    private static String scoreLabel(int score) {
        if (score >= 85) return "EXCELLENT";
        if (score >= 75) return "GOOD";
        if (score >= 60) return "MARGINAL";
        if (score >= 45) return "POOR FIT";
        return "SKIP";
    }

    private static String formatWindow(LocalDateTime start, LocalDateTime end) {
        DateTimeFormatter day = DateTimeFormatter.ofPattern("EEE MMM d", Locale.US);
        DateTimeFormatter tm = DateTimeFormatter.ofPattern("h a", Locale.US);
        return start.format(day) + " · " + start.format(tm) + "–" + end.format(tm);
    }
}
