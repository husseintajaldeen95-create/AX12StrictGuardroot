package com.ax12.strictguard;

/** Pure-Java URL builder for TP-Link LuCI routes; kept separate for regression tests. */
public final class RouterUrl {
    private RouterUrl() {}

    public static String build(String host, String path, String sessionToken, boolean loginPhase) {
        if (host == null || host.trim().isEmpty()) throw new IllegalArgumentException("Router host is required");
        if (path == null || path.isEmpty() || path.startsWith("/")) throw new IllegalArgumentException("Invalid LuCI path");
        String session = loginPhase ? "" : sessionToken;
        if (!loginPhase && (session == null || session.isEmpty()))
            throw new IllegalStateException("Router session token is missing");
        return "http://" + host + "/cgi-bin/luci/;stok=" + session + "/" + path;
    }
}
