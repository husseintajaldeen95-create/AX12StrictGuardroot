package com.ax12.strictguard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects stable arrivals/departures from complete router snapshots only.
 * The first complete snapshot is a baseline and never generates notifications.
 * A change must appear in two consecutive complete snapshots to avoid transient
 * firmware omissions or one-poll fluctuations being reported as real events.
 */
public final class DevicePresenceTracker {
    public enum Type { ARRIVED, DEPARTED }

    public static final class Event {
        public final Type type;
        public final DeviceInventory.ConnectedDevice device;

        private Event(Type type, DeviceInventory.ConnectedDevice device) {
            this.type = type;
            this.device = device;
        }
    }

    private static final int REQUIRED_CONSECUTIVE_SAMPLES = 2;
    private final Map<String, DeviceInventory.ConnectedDevice> confirmedOnline = new LinkedHashMap<>();
    private final Map<String, Integer> pendingArrivals = new LinkedHashMap<>();
    private final Map<String, Integer> pendingDepartures = new LinkedHashMap<>();
    private boolean initialized;

    public synchronized boolean hasBaseline() { return initialized; }

    /**
     * Drop the old baseline after a prolonged period in which router status could
     * not be read. The next complete snapshot becomes a silent baseline so that
     * devices changing during the outage do not generate misleading late events.
     */
    public synchronized void reset() {
        confirmedOnline.clear();
        pendingArrivals.clear();
        pendingDepartures.clear();
        initialized = false;
    }

    /**
     * Observe one router snapshot. Incomplete snapshots are ignored completely:
     * they cannot create arrivals or falsely mark devices as disconnected.
     */
    public synchronized List<Event> observe(
            Collection<DeviceInventory.ConnectedDevice> devices, boolean completeSnapshot) {
        if (!completeSnapshot) return Collections.emptyList();
        if (devices == null) throw new IllegalArgumentException("قائمة الأجهزة المتصلة مفقودة");

        Map<String, DeviceInventory.ConnectedDevice> current = new LinkedHashMap<>();
        for (DeviceInventory.ConnectedDevice device : devices) {
            if (device == null) throw new IllegalArgumentException("تحتوي لقطة الراوتر على جهاز غير صالح");
            current.put(device.mac, device);
        }

        if (!initialized) {
            confirmedOnline.putAll(current);
            initialized = true;
            return Collections.emptyList();
        }

        List<Event> events = new ArrayList<>();

        // A pending arrival must be present in consecutive complete snapshots.
        for (String mac : new ArrayList<>(pendingArrivals.keySet())) {
            if (!current.containsKey(mac)) pendingArrivals.remove(mac);
        }
        for (Map.Entry<String, DeviceInventory.ConnectedDevice> row : current.entrySet()) {
            String mac = row.getKey();
            DeviceInventory.ConnectedDevice device = row.getValue();
            if (confirmedOnline.containsKey(mac)) {
                confirmedOnline.put(mac, device); // Refresh name/IP/medium for future departure notices.
                pendingDepartures.remove(mac);
                continue;
            }
            int samples = pendingArrivals.containsKey(mac) ? pendingArrivals.get(mac) + 1 : 1;
            if (samples >= REQUIRED_CONSECUTIVE_SAMPLES) {
                confirmedOnline.put(mac, device);
                pendingArrivals.remove(mac);
                events.add(new Event(Type.ARRIVED, device));
            } else {
                pendingArrivals.put(mac, samples);
            }
        }

        // A confirmed device is considered gone only after two consecutive complete snapshots omit it.
        for (String mac : new ArrayList<>(confirmedOnline.keySet())) {
            if (current.containsKey(mac)) continue;
            int samples = pendingDepartures.containsKey(mac) ? pendingDepartures.get(mac) + 1 : 1;
            if (samples >= REQUIRED_CONSECUTIVE_SAMPLES) {
                DeviceInventory.ConnectedDevice lastSeen = confirmedOnline.remove(mac);
                pendingDepartures.remove(mac);
                events.add(new Event(Type.DEPARTED, lastSeen));
            } else {
                pendingDepartures.put(mac, samples);
            }
        }

        return Collections.unmodifiableList(events);
    }

    public synchronized int confirmedOnlineCount() { return confirmedOnline.size(); }
}
