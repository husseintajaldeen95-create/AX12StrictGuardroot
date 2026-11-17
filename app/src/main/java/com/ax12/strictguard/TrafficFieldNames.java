package com.ax12.strictguard;

import java.util.Locale;

/** Matches router JSON field names independent of camelCase, snake_case or separators. */
public final class TrafficFieldNames {
    private TrafficFieldNames() {}

    public static boolean matches(String actual, String alias) {
        return canonical(actual).equals(canonical(alias));
    }

    public static String canonical(String value) {
        if (value == null) return "";
        String lower = value.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) out.append(c);
        }
        return out.toString();
    }
}
