package com.ax12.strictguard;

/** Conservative classification of live network activity using fresh router snapshots only. */
public final class LiveTrafficStatus {
    public enum State { ONLINE_ACTIVE, ONLINE_IDLE, ONLINE_RATE_UNKNOWN, OFFLINE, UNKNOWN }

    private LiveTrafficStatus() {}

    public static State classify(boolean onlineSnapshotFresh, boolean connected,
                                  boolean ratesFresh, double downloadRaw, double uploadRaw) {
        boolean validRates = ratesFresh && Double.isFinite(downloadRaw) && Double.isFinite(uploadRaw)
                && downloadRaw >= 0 && uploadRaw >= 0;
        // A fresh non-zero per-MAC rate is direct evidence of current data activity,
        // even if the optional client-list endpoint has temporarily gone stale.
        if (validRates && (downloadRaw > 0 || uploadRaw > 0)) return State.ONLINE_ACTIVE;
        if (!onlineSnapshotFresh) return State.UNKNOWN;
        if (!connected) return State.OFFLINE;
        if (!validRates) return State.ONLINE_RATE_UNKNOWN;
        return State.ONLINE_IDLE;
    }

    public static String label(State state) {
        if (state == null) return "حالة الاتصال غير معروفة";
        switch (state) {
            case ONLINE_ACTIVE: return "ينقل بيانات الآن";
            case ONLINE_IDLE: return "متصل دون نقل بيانات ملحوظ";
            case ONLINE_RATE_UNKNOWN: return "متصل؛ نشاط البيانات غير متاح";
            case OFFLINE: return "غير متصل بالشبكة";
            default: return "حالة الاتصال غير محدثة";
        }
    }

    public static int sortRank(State state) {
        if (state == State.ONLINE_ACTIVE) return 0;
        if (state == State.ONLINE_IDLE) return 1;
        if (state == State.ONLINE_RATE_UNKNOWN) return 2;
        if (state == State.OFFLINE) return 3;
        return 4;
    }
}
