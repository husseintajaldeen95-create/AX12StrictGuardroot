import com.ax12.strictguard.DeviceInventory;
import com.ax12.strictguard.DevicePresenceTracker;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class DevicePresenceTrackerTest {
    private static DeviceInventory.ConnectedDevice d(String mac, String name) {
        return new DeviceInventory.ConnectedDevice(mac, name, "192.168.0.20", "لاسلكي");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void run() {
        String a = "02:11:22:33:44:55";
        String b = "04:11:22:33:44:66";
        DevicePresenceTracker tracker = new DevicePresenceTracker();

        // First full snapshot is a baseline, not a burst of false arrival notifications.
        check(tracker.observe(Collections.singletonList(d(a, "A")), true).isEmpty(), "baseline must be silent");
        check(tracker.hasBaseline() && tracker.confirmedOnlineCount() == 1, "baseline state");

        // A new client is announced only after two consecutive complete observations.
        check(tracker.observe(Arrays.asList(d(a, "A"), d(b, "B")), true).isEmpty(), "arrival first sample pending");
        List<DevicePresenceTracker.Event> arrived = tracker.observe(Arrays.asList(d(a, "A"), d(b, "B")), true);
        check(arrived.size() == 1 && arrived.get(0).type == DevicePresenceTracker.Type.ARRIVED,
                "arrival after second sample");
        check(arrived.get(0).device.mac.equals("04-11-22-33-44-66"), "arrival MAC");
        check(tracker.observe(Arrays.asList(d(a, "A"), d(b, "B")), true).isEmpty(), "no duplicate arrival");

        // One missing sample followed by a return is not a departure.
        check(tracker.observe(Collections.singletonList(d(a, "A")), true).isEmpty(), "departure first sample pending");
        check(tracker.observe(Arrays.asList(d(a, "A"), d(b, "B")), true).isEmpty(), "single-sample gap cancelled");

        // Partial snapshots are ignored and cannot advance a pending departure.
        check(tracker.observe(Collections.singletonList(d(a, "A")), true).isEmpty(), "departure pending again");
        check(tracker.observe(Collections.<DeviceInventory.ConnectedDevice>emptyList(), false).isEmpty(), "partial snapshot ignored");
        List<DevicePresenceTracker.Event> departed = tracker.observe(Collections.singletonList(d(a, "A")), true);
        check(departed.size() == 1 && departed.get(0).type == DevicePresenceTracker.Type.DEPARTED,
                "departure after two complete absences");
        check(departed.get(0).device.mac.equals("04-11-22-33-44-66"), "departure MAC");
        check(tracker.observe(Collections.singletonList(d(a, "A")), true).isEmpty(), "no duplicate departure");

        // A short-lived arrival that is not present in the next complete snapshot is ignored.
        check(tracker.observe(Arrays.asList(d(a, "A"), d(b, "B")), true).isEmpty(), "transient arrival pending");
        check(tracker.observe(Collections.singletonList(d(a, "A")), true).isEmpty(), "transient arrival cancelled");

        // A complete empty list is valid and confirms all remaining devices have disappeared.
        check(tracker.observe(Collections.<DeviceInventory.ConnectedDevice>emptyList(), true).isEmpty(), "empty first absence pending");
        departed = tracker.observe(Collections.<DeviceInventory.ConnectedDevice>emptyList(), true);
        check(departed.size() == 1 && departed.get(0).type == DevicePresenceTracker.Type.DEPARTED,
                "empty complete snapshots confirm departure");

        // After a prolonged status-endpoint outage, reset and take a new silent baseline.
        DevicePresenceTracker resumed = new DevicePresenceTracker();
        check(resumed.observe(Collections.singletonList(d(a, "A")), true).isEmpty(),
                "pre-outage baseline is silent");
        resumed.reset();
        check(!resumed.hasBaseline() && resumed.confirmedOnlineCount() == 0,
                "reset clears stale baseline and pending state");
        check(resumed.observe(Collections.singletonList(d(b, "B")), true).isEmpty()
                        && resumed.confirmedOnlineCount() == 1,
                "first snapshot after reset is a new silent baseline");
        System.out.println("PASS: 20 DevicePresenceTracker checks");
    }
    public static void main(String[] args) { run(); }
}
