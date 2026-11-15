package com.ax12.strictguard;

import java.util.Locale;

/** Conservative reconnect policy for the foreground guard service. */
public final class GuardRecoveryPolicy {
    public static final long SESSION_TIMEOUT_RECONNECT_MS = 30_000L;
    public static final long SESSION_REJECTED_RECONNECT_MS = 120_000L;
    public static final long GENERAL_RECONNECT_MS = 120_000L;

    private GuardRecoveryPolicy() {}

    /**
     * A plain network timeout can be retried relatively soon. An authenticated router
     * response that invalidates the session gets a longer pause. Explicit authentication
     * denials use the app's 10-minute minimum or a longer server Retry-After.
     */
    public static long reconnectDelayMs(String message, long gateRemainingMs) {
        long wait;
        if (AuthAttemptPolicy.isDenied(message)) {
            wait = Math.max(AuthAttemptPolicy.DENIED_COOLDOWN_MS,
                    AuthAttemptPolicy.effectiveCooldownMs(message));
        } else if (isNetworkTimeout(message)) {
            wait = SESSION_TIMEOUT_RECONNECT_MS;
        } else {
            wait = SESSION_REJECTED_RECONNECT_MS;
        }
        return Math.max(wait, Math.max(0L, gateRemainingMs));
    }

    public static long generalFailureDelayMs(long gateRemainingMs) {
        return Math.max(GENERAL_RECONNECT_MS, Math.max(0L, gateRemainingMs));
    }

    private static boolean isNetworkTimeout(String message) {
        if (message == null) return false;
        String value = message.toLowerCase(Locale.ROOT);
        return value.contains("timeout") || value.contains("timed out")
                || value.contains("connection reset") || value.contains("failed to connect")
                || value.contains("network is unreachable") || value.contains("no route to host");
    }
}
