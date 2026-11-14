package com.ax12.strictguard;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Explicitly approved MACs only. No auto-import from online clients or router allow list. */
public final class TrustedStore {
    private static final String PREF = "strict_guard_trusted_v1";
    private final SharedPreferences prefs;
    public TrustedStore(Context context) { prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE); }

    public synchronized Set<String> get() {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.getString("trusted", "[]"));
            for (int i=0; i<a.length(); i++) out.add(a.getString(i));
        } catch (Exception e) { throw new IllegalStateException("تعذر قراءة قائمة الثقة المحفوظة", e); }
        return MacPolicy.canonical(out);
    }
    private synchronized void save(Set<String> addresses) {
        JSONArray arr = new JSONArray(); for (String a : addresses) arr.put(a);
        if (!prefs.edit().putString("trusted", arr.toString()).commit())
            throw new IllegalStateException("تعذر حفظ القائمة المعتمدة");
    }
    public synchronized void add(String mac) {
        java.util.LinkedHashSet<String> copy = new java.util.LinkedHashSet<>(get());
        copy.add(MacPolicy.normalize(mac)); save(copy);
    }
    /** Validate ALL MACs before changing the local trust store. Never writes to router. */
    public synchronized void addAll(java.util.Collection<String> macs) {
        java.util.LinkedHashSet<String> validated = new java.util.LinkedHashSet<>(get());
        for (String mac : macs) validated.add(MacPolicy.normalize(mac));
        if (validated.size() > 128) throw new IllegalArgumentException("القائمة كبيرة على نحو غير متوقع");
        save(validated);
    }
    public synchronized void remove(String mac) {
        java.util.LinkedHashSet<String> copy = new java.util.LinkedHashSet<>(get());
        copy.remove(MacPolicy.normalize(mac)); save(copy);
    }
    public synchronized void setHost(String h) { prefs.edit().putString("router_host", h).apply(); }
    public String getHost() { return prefs.getString("router_host", "192.168.0.1"); }
}
