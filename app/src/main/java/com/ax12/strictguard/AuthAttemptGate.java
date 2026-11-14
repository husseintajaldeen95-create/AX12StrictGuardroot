package com.ax12.strictguard;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Process-wide exclusive admin session plus a persisted failure cooldown.
 * Does not bypass router lockout; only prevents our app from aggravating it.
 */
public final class AuthAttemptGate {
    private static final String PREF = "ax12_auth_safety_v1";
    private static final String RETRY_AFTER = "retry_after_ms";
    private static boolean occupied;
    private AuthAttemptGate() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static synchronized void begin(Context context) {
        if (occupied)
            throw new IllegalStateException("جلسة الإدارة قيد الاستخدام بالفعل؛ انتظر انتهاء الفحص أو أوقف المراقبة");
        long remaining = AuthAttemptPolicy.remainingMs(System.currentTimeMillis(),
                prefs(context).getLong(RETRY_AFTER, 0L));
        if (remaining > 0) {
            long minutes = (remaining + 59999L) / 60000L;
            throw new IllegalStateException("أوقف التطبيق محاولات الدخول احترازياً بعد رفض سابق. متبقٍ نحو "
                    + minutes + " دقيقة. يمكنك استخدام واجهة الراوتر الرسمية بصورة مستقلة.");
        }
        occupied = true;
    }

    public static synchronized void succeeded(Context context) {
        prefs(context).edit().remove(RETRY_AFTER).commit();
        // Keep the exclusive gate occupied throughout the authenticated session.
        // release() is called only from RouterClient.close().
    }

    public static synchronized void failed(Context context, Exception error) {
        if (context != null) record(context, AuthAttemptPolicy.cooldownMs(error.getMessage()));
        occupied = false;
    }

    public static synchronized void markDenied(Context context, Exception error) {
        if (context != null && AuthAttemptPolicy.isDenied(error.getMessage()))
            record(context, AuthAttemptPolicy.DENIED_COOLDOWN_MS);
    }

    public static synchronized void release(Context context) {
        occupied = false;
    }

    private static void record(Context context, long durationMs) {
        SharedPreferences p = prefs(context);
        long now = System.currentTimeMillis();
        long deadline = now + durationMs;
        long old = p.getLong(RETRY_AFTER, 0L);
        p.edit().putLong(RETRY_AFTER, Math.max(old, deadline)).commit();
    }
}
