package com.ax12.strictguard;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Explicitly approved globally-administered MACs only. No auto-import from router observations. */
public final class TrustedStore {
    private static final String PREF = "strict_guard_trusted_v1";
    private final SharedPreferences prefs;

    public TrustedStore(Context context) {
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /**
     * Returns the stored values for review/removal, including legacy local-administered entries.
     * This method deliberately does not treat them as trusted.
     */
    public synchronized List<String> getStoredForReview() {
        List<String> out = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString("trusted", "[]"));
            for (int i = 0; i < array.length(); i++) out.add(MacPolicy.normalize(array.getString(i)));
        } catch (Exception e) {
            throw new IllegalStateException("تعذر قراءة قائمة الثقة المحفوظة", e);
        }
        return out;
    }

    /** Valid trust entries only; useful for read-only inventory while legacy entries are reviewed. */
    public synchronized Set<String> getValid() {
        List<String> valid = new ArrayList<>();
        for (String mac : getStoredForReview())
            if (!MacPolicy.locallyAdministered(mac)) valid.add(mac);
        return MacPolicy.canonicalTrusted(valid);
    }

    /** Strict trust read. Legacy private/randomized MACs cause a fail-closed error until removed. */
    public synchronized Set<String> get() {
        List<String> stored = getStoredForReview();
        for (String mac : stored) {
            if (MacPolicy.locallyAdministered(mac))
                throw new IllegalStateException("توجد عناوين MAC محلية الإدارة قديمة في قائمة الثقة؛ احذفها قبل تشغيل الحارس أو إرسال أي MAC إلى الراوتر");
        }
        return MacPolicy.canonicalTrusted(stored);
    }

    private synchronized void save(Set<String> addresses) {
        JSONArray array = new JSONArray();
        for (String address : addresses) array.put(address);
        if (!prefs.edit().putString("trusted", array.toString()).commit())
            throw new IllegalStateException("تعذر حفظ القائمة المعتمدة");
    }

    public synchronized void add(String mac) {
        LinkedHashSet<String> copy = new LinkedHashSet<>(get());
        copy.add(MacPolicy.normalizeTrusted(mac));
        save(copy);
    }

    /** Validate ALL new addresses before changing the local trust store. */
    public synchronized void addAll(java.util.Collection<String> macs) {
        LinkedHashSet<String> validated = new LinkedHashSet<>(get());
        for (String mac : macs) validated.add(MacPolicy.normalizeTrusted(mac));
        if (validated.size() > 128) throw new IllegalArgumentException("القائمة كبيرة على نحو غير متوقع");
        save(validated);
    }

    /** Removal intentionally accepts a legacy local-administered entry so it can be cleaned up. */
    public synchronized void remove(String mac) {
        LinkedHashSet<String> copy = new LinkedHashSet<>(getStoredForReview());
        copy.remove(MacPolicy.normalize(mac));
        save(copy);
    }

    public synchronized void setHost(String host) {
        prefs.edit().putString("router_host", host).apply();
    }

    public String getHost() { return prefs.getString("router_host", "192.168.0.1"); }
}
