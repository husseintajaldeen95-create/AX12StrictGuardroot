import com.ax12.strictguard.DeviceInventory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DeviceInventoryTest {
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static DeviceInventory.ConnectedDevice online(String mac, String name, String ip, String medium) {
        return new DeviceInventory.ConnectedDevice(mac, name, ip, medium);
    }
    private static DeviceInventory.Entry find(DeviceInventory.Report r, String mac) {
        String normalized = com.ax12.strictguard.MacPolicy.normalize(mac);
        for (DeviceInventory.Entry e:r.entries) if(e.mac.equals(normalized)) return e;
        throw new AssertionError("missing entry: " + normalized);
    }
    private static void invalidConnected(String mac) {
        try { online(mac, "", "", ""); throw new AssertionError("accepted invalid " + mac); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    public static void main(String[] args) {
        String a = "08-11-22-33-44-01", b = "08-11-22-33-44-02";
        String c = "0A-11-22-33-44-03", d = "08-11-22-33-44-04";
        Map<String,String> router = new LinkedHashMap<>();
        router.put(a, "Allowed mobile"); router.put(b, "Dormant laptop");
        List<DeviceInventory.ConnectedDevice> clients = new ArrayList<>();
        clients.add(online("08:11:22:33:44:01", "mobile", "192.168.0.10", "5G"));
        clients.add(online(c, "unknown", "192.168.0.66", "2.4G"));
        List<String> trusted = new ArrayList<>(Arrays.asList(a,b,d));
        DeviceInventory.Report report=DeviceInventory.compare(router,clients,trusted);
        check(report.entries.size()==4,"union of router, online and manual lists");
        check(report.allowedCount==2,"router count");
        check(report.connectedCount==2,"connected count");
        check(report.connectedAllowedCount==1,"allowed and online intersection");
        check(report.connectedOutsideAllowCount==1,"outside allow connected");
        check(report.allowedNotObservedCount==1,"router allowed but not observed");
        check(report.allowedNotTrustedCount==0,"allowed but not manually trusted");
        check(report.trustedNotAllowedCount==1,"manually trusted absent from router");
        check(report.entries.get(0).mac.equals(a) && report.entries.get(1).mac.equals(b),"router allow entries first");
        check(find(report,a).connected && find(report,a).trustedLocally && find(report,a).allowedByRouter,"approved online");
        check(find(report,a).name.equals("mobile") && find(report,a).ip.equals("192.168.0.10"),"client details preferred");
        check(find(report,b).name.equals("Dormant laptop") && !find(report,b).connected,"offline router label preserved");
        check(find(report,c).locallyAdministered && !find(report,c).allowedByRouter,"random-looking connected outside list flagged");
        check(!find(report,d).connected && find(report,d).trustedLocally && !find(report,d).allowedByRouter,"manual-only listing");
        check(router.size()==2 && clients.size()==2 && trusted.size()==3,"does not change input lists");
        List<DeviceInventory.ConnectedDevice> duplicates=Arrays.asList(
                online(a,"","",""),online(a,"office","192.168.0.20","wired"));
        DeviceInventory.Report noDuplicates=DeviceInventory.compare(Collections.singletonMap(a, "routerName"),duplicates,Arrays.asList(a));
        check(noDuplicates.connectedCount==1 && noDuplicates.entries.size()==1,"duplicates not counted twice");
        check(find(noDuplicates,a).name.equals("office"),"richer duplicate wins");
        DeviceInventory.Report untrustedAllowed=DeviceInventory.compare(Collections.singletonMap(b,""),Collections.emptyList(),Collections.emptyList());
        check(untrustedAllowed.allowedNotTrustedCount==1,"untrusted allow list identified");
        DeviceInventory.Report empty=DeviceInventory.compare(Collections.emptyMap(),Collections.emptyList(),Collections.emptyList());
        check(empty.entries.isEmpty() && empty.connectedOutsideAllowCount==0,"empty snapshot");
        DeviceInventory.ConnectedDevice cleaned=online(a,"\nFriendly\tName", "192.168.0.1", "5G");
        check(!cleaned.name.contains("\n") && !cleaned.name.contains("\t"),"control characters sanitized");
        invalidConnected("\n");invalidConnected("FF:FF:FF:FF:FF:FF");
        try { DeviceInventory.compare(null,Collections.emptyList(),Collections.emptyList());throw new AssertionError("accepted null snapshot"); }
        catch (IllegalArgumentException expected) { checks++; }
        try { DeviceInventory.compare(Collections.emptyMap(),Arrays.asList((DeviceInventory.ConnectedDevice)null),Collections.emptyList());throw new AssertionError("accepted null client"); }
        catch (IllegalArgumentException expected) { checks++; }
        System.out.println("PASS: " + checks + " DeviceInventory tests");
    }
}
