package com.ax12.strictguard;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.text.DateFormat;
import java.util.Date;

/** Last 120 events; no passwords, session tokens, or Wi-Fi credentials. */
public final class GuardLog {
    private final SharedPreferences prefs;
    public GuardLog(Context c) { prefs = c.getSharedPreferences("guard_activity_v1", Context.MODE_PRIVATE); }
    public synchronized void add(String message) {
        JSONArray arr;
        try { arr = new JSONArray(prefs.getString("events", "[]")); }
        catch (Exception e) { arr = new JSONArray(); }
        JSONArray next = new JSONArray();
        next.put(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(new Date()) + " • " + message);
        for (int i=0; i<Math.min(arr.length(),119); i++) next.put(arr.optString(i));
        prefs.edit().putString("events", next.toString()).apply();
    }
    public synchronized String text() {
        StringBuilder out = new StringBuilder();
        try { JSONArray a = new JSONArray(prefs.getString("events", "[]"));
            for (int i=0;i<a.length();i++) out.append(a.optString(i)).append('\n');
        } catch (Exception ignored) {}
        return out.toString();
    }
}
