package com.ax12.strictguard;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Local, per-MAC traffic history derived only from router-reported byte/s rates. */
public final class TrafficUsageStore {
    private static final String PREFS = "traffic_usage_history_v1";
    private static final String KEY_ENABLED = "sampling_enabled";
    private static final String KEY_HISTORY = "daily_history";
    private static final String KEY_LAST = "last_rates";
    private static final long MAX_SAMPLE_GAP_MS = 90000L;

    private TrafficUsageStore() {}

    public static final class Row {
        public final String mac;
        public final String name;
        public final double downloadBytesPerSecond;
        public final double uploadBytesPerSecond;
        public final boolean currentRateFresh;
        public final long downloadBytes;
        public final long uploadBytes;
        public final long lastSampleTime;

        Row(String mac, String name, double down, double up, boolean fresh,
            long downBytes, long upBytes, long lastSampleTime) {
            this.mac = mac; this.name = name;
            this.downloadBytesPerSecond = down; this.uploadBytesPerSecond = up;
            this.currentRateFresh = fresh; this.downloadBytes = downBytes;
            this.uploadBytes = upBytes; this.lastSampleTime = lastSampleTime;
        }
        public long totalBytes() { return safeAdd(downloadBytes, uploadBytes); }
    }

    public static void setSamplingEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static boolean isSamplingEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static synchronized void recordSamples(Context context,
            List<TrafficMonitorProbe.Sample> samples, long nowMillis) {
        if (samples == null || samples.isEmpty()) return;
        SharedPreferences prefs = prefs(context);
        JSONObject history = readObject(prefs.getString(KEY_HISTORY, "{}"));
        JSONObject last = readObject(prefs.getString(KEY_LAST, "{}"));
        String day = dayKey(nowMillis);
        JSONObject today = history.optJSONObject(day);
        if (today == null) today = new JSONObject();

        for (TrafficMonitorProbe.Sample sample : samples) {
            if (sample == null || !sample.hasRates) continue;
            JSONObject previous = last.optJSONObject(sample.mac);
            if (previous != null) {
                long previousTime = previous.optLong("time", 0L);
                long elapsed = nowMillis - previousTime;
                double previousDown = previous.optDouble("down", Double.NaN);
                double previousUp = previous.optDouble("up", Double.NaN);
                long downDelta = TrafficRateMath.integrateBytes(previousDown,
                        sample.downloadBytesPerSecond, elapsed, MAX_SAMPLE_GAP_MS);
                long upDelta = TrafficRateMath.integrateBytes(previousUp,
                        sample.uploadBytesPerSecond, elapsed, MAX_SAMPLE_GAP_MS);
                if (downDelta > 0 || upDelta > 0) {
                    JSONObject totals = today.optJSONObject(sample.mac);
                    if (totals == null) totals = new JSONObject();
                    try {
                        totals.put("name", sample.name);
                        totals.put("down", safeAdd(totals.optLong("down", 0L), downDelta));
                        totals.put("up", safeAdd(totals.optLong("up", 0L), upDelta));
                        today.put(sample.mac, totals);
                    } catch (JSONException ignored) { }
                }
            }
            JSONObject state = new JSONObject();
            try {
                state.put("name", sample.name);
                state.put("down", sample.downloadBytesPerSecond);
                state.put("up", sample.uploadBytesPerSecond);
                state.put("time", nowMillis);
                last.put(sample.mac, state);
            } catch (JSONException ignored) { }
        }

        try { history.put(day, today); } catch (JSONException ignored) { }
        pruneOldDays(history, nowMillis);
        prefs.edit().putString(KEY_HISTORY, history.toString())
                .putString(KEY_LAST, last.toString()).apply();
    }

    public static synchronized List<Row> getRows(Context context, int periodDays, long nowMillis) {
        SharedPreferences prefs = prefs(context);
        JSONObject history = readObject(prefs.getString(KEY_HISTORY, "{}"));
        JSONObject last = readObject(prefs.getString(KEY_LAST, "{}"));
        long start = startOfDay(nowMillis);
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(start);
        calendar.add(Calendar.DAY_OF_YEAR, -(Math.max(1, periodDays) - 1));
        String startKey = dayKey(calendar.getTimeInMillis());

        JSONObject totalsByMac = new JSONObject();
        Iterator<String> days = history.keys();
        while (days.hasNext()) {
            String day = days.next();
            if (day.compareTo(startKey) < 0) continue;
            JSONObject dayRows = history.optJSONObject(day);
            if (dayRows == null) continue;
            Iterator<String> macs = dayRows.keys();
            while (macs.hasNext()) {
                String mac = macs.next();
                JSONObject value = dayRows.optJSONObject(mac);
                if (value == null) continue;
                JSONObject total = totalsByMac.optJSONObject(mac);
                if (total == null) total = new JSONObject();
                try {
                    total.put("name", value.optString("name", ""));
                    total.put("down", safeAdd(total.optLong("down", 0L), value.optLong("down", 0L)));
                    total.put("up", safeAdd(total.optLong("up", 0L), value.optLong("up", 0L)));
                    totalsByMac.put(mac, total);
                } catch (JSONException ignored) { }
            }
        }

        List<Row> rows = new ArrayList<>();
        Iterator<String> macs = last.keys();
        while (macs.hasNext()) {
            String mac = macs.next();
            JSONObject state = last.optJSONObject(mac);
            if (state == null) continue;
            JSONObject totals = totalsByMac.optJSONObject(mac);
            long lastTime = state.optLong("time", 0L);
            boolean fresh = lastTime > 0 && nowMillis - lastTime <= 45000L;
            rows.add(new Row(mac, state.optString("name", ""),
                    fresh ? state.optDouble("down", Double.NaN) : Double.NaN,
                    fresh ? state.optDouble("up", Double.NaN) : Double.NaN,
                    fresh,
                    totals == null ? 0L : totals.optLong("down", 0L),
                    totals == null ? 0L : totals.optLong("up", 0L), lastTime));
        }
        Collections.sort(rows, Comparator.comparingLong(Row::totalBytes).reversed());
        return rows;
    }

    public static String formatBytes(long bytes) {
        if (bytes < 0) return "غير متاح";
        if (bytes < 1024L) return bytes + " B";
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unit = 0;
        while (value >= 1024d && unit < units.length - 1) { value /= 1024d; unit++; }
        return String.format(Locale.US, "%.2f %s", value, units[unit]);
    }

    public static String formatRate(double bytesPerSecond) {
        if (!Double.isFinite(bytesPerSecond) || bytesPerSecond < 0) return "غير متاح";
        if (bytesPerSecond < 1024d) return String.format(Locale.US, "%.0f B/s", bytesPerSecond);
        if (bytesPerSecond < 1024d * 1024d)
            return String.format(Locale.US, "%.1f KB/s", bytesPerSecond / 1024d);
        return String.format(Locale.US, "%.2f MB/s", bytesPerSecond / (1024d * 1024d));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static JSONObject readObject(String value) {
        try { return new JSONObject(value == null ? "{}" : value); }
        catch (JSONException ignored) { return new JSONObject(); }
    }

    private static String dayKey(long time) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return format.format(new Date(time));
    }

    private static long startOfDay(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static void pruneOldDays(JSONObject history, long nowMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(startOfDay(nowMillis));
        calendar.add(Calendar.DAY_OF_YEAR, -31);
        String keepFrom = dayKey(calendar.getTimeInMillis());
        List<String> remove = new ArrayList<>();
        Iterator<String> keys = history.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.compareTo(keepFrom) < 0) remove.add(key);
        }
        for (String key : remove) history.remove(key);
    }

    private static long safeAdd(long a, long b) {
        if (b > 0 && a > Long.MAX_VALUE - b) return Long.MAX_VALUE;
        if (b < 0 && a < Long.MIN_VALUE - b) return Long.MIN_VALUE;
        return a + b;
    }
}
