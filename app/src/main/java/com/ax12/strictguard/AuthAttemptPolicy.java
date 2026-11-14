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

    /** A Retry-After header is useful only if the router actually supplies it. */
    public static long parseServerRetrySeconds(String value) {
        if (value == null || !value.matches("[0-9]{1,8}")) return -1L;
        try {
            long seconds = Long.parseLong(value);
            return seconds <= 86400L ? seconds : -1L;
        } catch (NumberFormatException ex) { return -1L; }
    }

    public static long serverRetrySecondsFromError(String message) {
        if (message == null) return -1L;
        java.util.regex.Matcher match = java.util.regex.Pattern.compile(
                "\\[router-retry-seconds:([0-9]{1,8})\\]").matcher(message);
        if (!match.find()) return -1L;
        return parseServerRetrySeconds(match.group(1));
    }

    public static long remainingMs(long nowEpochMs, long retryAfterEpochMs) {
        return Math.max(0L, retryAfterEpochMs - nowEpochMs);
    }
}
