package com.ax12.strictguard;

import java.util.Locale;

/** Pure Java safety policy for local router authentication attempts. */
public final class AuthAttemptPolicy {
    private AuthAttemptPolicy() {}
    public static final long DENIED_COOLDOWN_MS = 30L * 60L * 1000L;
    public static final long OTHER_FAILURE_COOLDOWN_MS = 2L * 60L * 1000L;

    public static boolean isDenied(String message) {
        if (message == null) return false;
        String value = message.toLowerCase(Locale.ROOT);
        return value.contains("exceeded max attempts")
                || value.contains("http 403")
                || value.contains("too many attempts")
                || value.contains("rate limit")
                || value.contains("locked out");
    }

    public static long cooldownMs(String message) {
        return isDenied(message) ? DENIED_COOLDOWN_MS : OTHER_FAILURE_COOLDOWN_MS;
    }

    public static long remainingMs(long nowEpochMs, long retryAfterEpochMs) {
        return Math.max(0L, retryAfterEpochMs - nowEpochMs);
    }
}
