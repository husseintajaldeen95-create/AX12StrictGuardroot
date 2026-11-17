package com.ax12.strictguard;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Stores only the latest read-only router snapshots; no cumulative usage history is retained. */
public final class LiveTrafficStore {
    private static final String PREFS = "live_network_status_v1";
    private static final String KEY_ENABLED = "monitoring_enabled";
    private static final String KEY_TRAFFIC = "traffic_rows";
    private static final String KEY_TRAFFIC_TIME = "traffic_time";
    private static final String KEY_ONLINE = "online_rows";
    private static final String KEY_ONLINE_TIME = "online_time";
    public static final long FRESH_WINDOW_MS = 45000L;

    private LiveTrafficStore() {}

    public static final class Row {
        public final String mac;
        public final String name;
        public final String ip;
        public final String medium;
        public final double downloadRaw;
        public final double uploadRaw;
        public final boolean hasRates;
        public final LiveTrafficStatus.State state;
        public final long lastUpdated;

        Row(String mac, String name, String ip, String medium, double down, double up,
            boolean hasRates, LiveTrafficStatus.State state, long lastUpdated) {
            this.mac = mac; this.name = name; this.ip = ip; this.medium = medium;
            this.downloadRaw = down; this.uploadRaw = up; this.hasRates = hasRates;
            this.state = state; this.lastUpdated = lastUpdated;
        }

        public String activitySummary() {
            if (!hasRates) return "لا تتوفر قراءة نشاط حديثة";
            if (downloadRaw > 0 && uploadRaw > 0) return "تنزيل ورفع نشطان";
            if (downloadRaw > 0) return "يوجد تنزيل";
            if (uploadRaw > 0) return "يوجد رفع";
            return "لا يوجد نقل بيانات ملحوظ الآن";
        }
    }

    public static void setMonitoringEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static boolean isMonitoringEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static synchronized void saveTrafficSamples(Context context,
            List<TrafficMonitorProbe.Sample> samples, long nowMillis) {
        if (samples == null || samples.isEmpty()) return;
        JSONObject rows = new JSONObject();
        for (TrafficMonitorProbe.Sample sample : samples) {
            if (sample == null || sample.mac == null || sample.mac.isEmpty()) continue;
            JSONObject item = new JSONObject();
            try {
                item.put("name", sample.name);
                item.put("down", sample.rawDownload);
                item.put("up", sample.rawUpload);
                item.put("has_rates", sample.hasRawRates);
                rows.put(sample.mac, item);
            } catch (JSONException ignored) { }
        }
        prefs(context).edit().putString(KEY_TRAFFIC, rows.toString())
                .putLong(KEY_TRAFFIC_TIME, nowMillis).apply();
    }

    /** Only complete router snapshots are accepted as evidence of online/offline state. */
    public static synchronized void saveOnlineSnapshot(Context context,
            List<DeviceInventory.ConnectedDevice> devices, long nowMillis) {
        if (devices == null) return;
        JSONObject rows = new JSONObject();
        for (DeviceInventory.ConnectedDevice device : devices) {
            if (device == null) continue;
            JSONObject item = new JSONObject();
            try {
                item.put("name", device.name);
                item.put("ip", device.ip);
                item.put("medium", device.medium);
                rows.put(device.mac, item);
            } catch (JSONException ignored) { }
        }
        prefs(context).edit().putString(KEY_ONLINE, rows.toString())
                .putLong(KEY_ONLINE_TIME, nowMillis).apply();
    }

    public static synchronized List<Row> getRows(Context context, long nowMillis) {
        SharedPreferences prefs = prefs(context);
        JSONObject traffic = readObject(prefs.getString(KEY_TRAFFIC, "{}"));
        JSONObject online = readObject(prefs.getString(KEY_ONLINE, "{}"));
        long trafficTime = prefs.getLong(KEY_TRAFFIC_TIME, 0L);
        long onlineTime = prefs.getLong(KEY_ONLINE_TIME, 0L);
        boolean trafficFresh = isFresh(trafficTime, nowMillis);
        boolean onlineFresh = isFresh(onlineTime, nowMillis);

        Set<String> macs = new LinkedHashSet<>();
        addKeys(online, macs);
        addKeys(traffic, macs);
        List<Row> result = new ArrayList<>();
        for (String mac : macs) {
            JSONObject t = traffic.optJSONObject(mac);
            JSONObject o = online.optJSONObject(mac);
            boolean connected = onlineFresh && o != null;
            boolean hasRates = trafficFresh && t != null && t.optBoolean("has_rates", false);
            double down = t == null ? Double.NaN : t.optDouble("down", Double.NaN);
            double up = t == null ? Double.NaN : t.optDouble("up", Double.NaN);
            LiveTrafficStatus.State state = LiveTrafficStatus.classify(
                    onlineFresh, connected, hasRates, down, up);
            String name = firstNonBlank(o == null ? "" : o.optString("name", ""),
                    t == null ? "" : t.optString("name", ""));
            String ip = o == null ? "" : o.optString("ip", "");
            String medium = o == null ? "" : o.optString("medium", "");
            long updated = Math.max(onlineFresh ? onlineTime : 0L, trafficFresh ? trafficTime : 0L);
            result.add(new Row(mac, name, ip, medium, down, up, hasRates, state, updated));
        }
        Collections.sort(result, Comparator
                .comparingInt((Row row) -> LiveTrafficStatus.sortRank(row.state))
                .thenComparing(row -> row.name == null ? "" : row.name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(row -> row.mac));
        return result;
    }

    public static long lastTrafficReadTime(Context context) {
        return prefs(context).getLong(KEY_TRAFFIC_TIME, 0L);
    }

    public static long lastOnlineReadTime(Context context) {
        return prefs(context).getLong(KEY_ONLINE_TIME, 0L);
    }

    private static boolean isFresh(long timestamp, long now) {
        return timestamp > 0 && now >= timestamp && now - timestamp <= FRESH_WINDOW_MS;
    }

    private static void addKeys(JSONObject object, Set<String> out) {
        Iterator<String> iterator = object.keys();
        while (iterator.hasNext()) out.add(iterator.next());
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.trim().isEmpty() ? first : (second == null ? "" : second);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static JSONObject readObject(String value) {
        try { return new JSONObject(value == null ? "{}" : value); }
        catch (JSONException ignored) { return new JSONObject(); }
    }
}
