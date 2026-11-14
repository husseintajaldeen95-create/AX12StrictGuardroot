package com.ax12.strictguard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only comparison: router allow list, current client snapshot and manual trust list.
 *  No network calls and no changes to device authorization are made here.
 */
public final class DeviceInventory {
    private DeviceInventory() {}

    public static final class ConnectedDevice {
        public final String mac;
        public final String name;
        public final String ip;
        public final String medium;

        public ConnectedDevice(String mac, String name, String ip, String medium) {
            this.mac = MacPolicy.normalize(mac);
            this.name = clean(name);
            this.ip = clean(ip);
            this.medium = clean(medium);
        }
    }

    public static final class Entry {
        public final String mac;
        public final String name;
        public final String ip;
        public final String medium;
        public final boolean allowedByRouter;
        public final boolean connected;
        public final boolean trustedLocally;
        public final boolean locallyAdministered;

        private Entry(String mac, String name, String ip, String medium,
                boolean allowedByRouter, boolean connected, boolean trustedLocally) {
            this.mac = mac;
            this.name = name;
            this.ip = ip;
            this.medium = medium;
            this.allowedByRouter = allowedByRouter;
            this.connected = connected;
            this.trustedLocally = trustedLocally;
            this.locallyAdministered = MacPolicy.locallyAdministered(mac);
        }
    }

    public static final class Report {
        public final List<Entry> entries;
        public final int allowedCount;
        public final int connectedCount;
        public final int connectedAllowedCount;
        public final int connectedOutsideAllowCount;
        public final int allowedNotObservedCount;
        public final int allowedNotTrustedCount;
        public final int trustedNotAllowedCount;

        private Report(List<Entry> entries, int allowed, int connected, int connectedAllowed,
                int connectedOutside, int allowedNotObserved, int allowedNotTrusted,
                int trustedNotAllowed) {
            this.entries = Collections.unmodifiableList(entries);
            this.allowedCount = allowed;
            this.connectedCount = connected;
            this.connectedAllowedCount = connectedAllowed;
            this.connectedOutsideAllowCount = connectedOutside;
            this.allowedNotObservedCount = allowedNotObserved;
            this.allowedNotTrustedCount = allowedNotTrusted;
            this.trustedNotAllowedCount = trustedNotAllowed;
        }
    }

    /** Router-allowed MAC -> optional name from router. Null/blank names are accepted. */
    public static Report compare(Map<String, String> routerAllowed,
                                 Collection<ConnectedDevice> liveClients,
                                 Collection<String> manualTrusted) {
        if (routerAllowed == null || liveClients == null || manualTrusted == null)
            throw new IllegalArgumentException("بيانات المقارنة غير مكتملة");

        Map<String, String> allowed = new LinkedHashMap<>();
        for (Map.Entry<String, String> row : routerAllowed.entrySet())
            allowed.put(MacPolicy.normalize(row.getKey()), clean(row.getValue()));

        Map<String, ConnectedDevice> online = new LinkedHashMap<>();
        for (ConnectedDevice device : liveClients) {
            if (device == null) throw new IllegalArgumentException("جهاز متصل بلا بيانات");
            ConnectedDevice previous = online.get(device.mac);
            // Duplicate MAC rows can occur when the router reports multiple interfaces.
            // Keep the row with more useful display information; never double-count it.
            if (previous == null || quality(device) > quality(previous)) online.put(device.mac, device);
        }
        Set<String> trusted = MacPolicy.canonicalTrusted(manualTrusted);
        Set<String> all = new LinkedHashSet<>();
        all.addAll(allowed.keySet()); // Show ALL router allow entries first.
        all.addAll(online.keySet());  // Then unexpected connected clients.
        all.addAll(trusted);          // Finally manually trusted MACs missing from router.

        List<Entry> entries = new ArrayList<>();
        int connectedAllowed = 0, outside = 0, notObserved = 0, notTrusted = 0, trustedMissing = 0;
        for (String mac : all) {
            ConnectedDevice client = online.get(mac);
            boolean inRouter = allowed.containsKey(mac);
            boolean connected = client != null;
            boolean locallyTrusted = trusted.contains(mac);
            if (inRouter && connected) connectedAllowed++;
            if (connected && !inRouter) outside++;
            if (inRouter && !connected) notObserved++;
            if (inRouter && !locallyTrusted) notTrusted++;
            if (locallyTrusted && !inRouter) trustedMissing++;
            String routerName = inRouter ? allowed.get(mac) : "";
            String name = client != null && !client.name.isEmpty() ? client.name : routerName;
            entries.add(new Entry(mac, name, client == null ? "" : client.ip,
                    client == null ? "" : client.medium, inRouter, connected, locallyTrusted));
        }
        return new Report(entries, allowed.size(), online.size(), connectedAllowed,
                outside, notObserved, notTrusted, trustedMissing);
    }

    private static int quality(ConnectedDevice device) {
        return (!device.name.isEmpty() ? 1 : 0) + (!device.ip.isEmpty() ? 1 : 0)
                + (!device.medium.isEmpty() ? 1 : 0);
    }

    private static String clean(String value) {
        if (value == null) return "";
        // Router-provided device labels are untrusted UI text. Bound their size and
        // strip control characters to keep the screen legible.
        String s = value.replaceAll("[\\p{Cntrl}]", " ").trim();
        return s.length() > 96 ? s.substring(0, 96) : s;
    }
}
