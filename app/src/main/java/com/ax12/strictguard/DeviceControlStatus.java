package com.ax12.strictguard;

/** Read-only per-device evidence from the router's Smart Network/Patrol data. */
public final class DeviceControlStatus {
    public final String mac;
    public final String profileName;
    public final String ownerId;
    public final Boolean internetBlocked;
    public final String scheduleSummary;

    public DeviceControlStatus(String mac, String profileName, String ownerId,
                               Boolean internetBlocked, String scheduleSummary) {
        this.mac = MacPolicy.normalize(mac);
        this.profileName = clean(profileName);
        this.ownerId = clean(ownerId);
        this.internetBlocked = internetBlocked;
        this.scheduleSummary = clean(scheduleSummary);
    }

    public String internetStateArabic() {
        if (internetBlocked == null) return "غير معروف من بيانات الراوتر";
        return internetBlocked ? "محجوب حسب سجل الراوتر" : "غير محجوب حسب سجل الراوتر";
    }

    private static String clean(String value) {
        if (value == null) return "";
        String s = value.replaceAll("[\\p{Cntrl}]", " ").trim();
        return s.length() > 100 ? s.substring(0, 100) : s;
    }
}
