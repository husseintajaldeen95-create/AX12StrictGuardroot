import com.ax12.strictguard.DeviceControlStatus;

public final class DeviceControlStatusTest {
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        String mac = "08:11:22:33:44:55";
        DeviceControlStatus blocked = new DeviceControlStatus(mac, "Kids", "owner-1", true, "bedtime=21:00-07:00");
        check(blocked.mac.equals("08-11-22-33-44-55"), "MAC normalized");
        check(blocked.internetStateArabic().contains("محجوب"), "router says blocked");
        check(blocked.profileName.equals("Kids") && blocked.ownerId.equals("owner-1"), "profile association retained");
        DeviceControlStatus allowed = new DeviceControlStatus(mac, "", "", false, "");
        check(allowed.internetStateArabic().contains("غير محجوب"), "router says not blocked");
        DeviceControlStatus unknown = new DeviceControlStatus(mac, null, null, null, null);
        check(unknown.internetStateArabic().contains("غير معروف"), "missing evidence remains unknown");
        check(unknown.scheduleSummary.isEmpty(), "missing schedule remains empty");
        System.out.println("PASS: " + checks + " DeviceControlStatus tests");
    }
}
