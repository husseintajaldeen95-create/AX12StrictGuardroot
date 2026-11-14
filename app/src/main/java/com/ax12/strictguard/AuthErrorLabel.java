package com.ax12.strictguard;

import java.util.Locale;

/** Non-secret login rejection code for support diagnostics, never a raw router payload. */
public final class AuthErrorLabel {
    private AuthErrorLabel() {}

    /** Only brief human-readable codes are shown. Anything resembling a token is hidden. */
    public static String safeValue(String value) {
        if (value == null) return "غير معلن";
        String v = value.trim();
        if (v.isEmpty() || "null".equalsIgnoreCase(v)) return "غير معلن";
        String lower = v.toLowerCase(Locale.ROOT);
        if (lower.contains("exceeded max attempts")) return "exceeded max attempts";
        if (lower.contains("too many attempts")) return "too many attempts";
        if (v.matches("[+-]?[0-9]{1,10}")) return v;
        // The router may send short English error strings such as "bad password" or
        // "timeout". Avoid any raw JSON, IPs, URLs, email, hex, base64 or secrets.
        if (v.length() <= 64 && v.matches("[A-Za-z][A-Za-z0-9 _-]*")
                && !lower.matches(".*(token|cookie|session|stok|sysauth|secret|key=|password=).*"))
            return v;
        return "قيمة محجوبة حفاظاً على بيانات الجلسة";
    }
}
