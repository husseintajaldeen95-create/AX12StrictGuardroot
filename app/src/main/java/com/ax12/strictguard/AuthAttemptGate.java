package com.ax12.strictguard;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * One admin session at a time, with a LOCAL retry delay after errors.
 * Critically: no local delay represents or predicts the router's own lockout.
 */
public final class AuthAttemptGate {
    private static final String PREF = "ax12_auth_safety_v1";
    private static final String RETRY_AFTER = "retry_after_ms";
    private static final String LAST_DENIED = "last_denied_ms";
    private static final String FIRST_DENIED = "first_denied_ms";
    private static final String LAST_SUCCESS = "last_success_ms";
    private static final String SERVER_RETRY_SECONDS = "router_retry_seconds";
    private static boolean occupied;
    private AuthAttemptGate() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static synchronized void begin(Context context) {
        if (occupied) throw new IllegalStateException("جلسة إدارة أخرى قيد الاستخدام، لا تبدأ محاولتين متزامنتين");
        long remaining = AuthAttemptPolicy.remainingMs(System.currentTimeMillis(),
                prefs(context).getLong(RETRY_AFTER, 0L));
        if (remaining > 0) {
            long minutes = (remaining + 59999L) / 60000L;
            throw new IllegalStateException("مهلة حماية محلية داخل التطبيق؛ متبقٍ نحو " + minutes
                    + " دقيقة قبل السماح باختبار يدوي آخر. هذه ليست مدة حظر الراوتر؛ وقت رفع حظره غير معروف.");
        }
        occupied = true;
    }

    /** Read-only compatibility checks share the same single-flight gate. */
    public static synchronized void beginProbe() {
        if (occupied) throw new IllegalStateException(
                "طلب إدارة آخر قيد التنفيذ؛ لن يتداخل فحص البروتوكول مع تسجيل الدخول");
        occupied = true;
    }

    public static synchronized void endProbe() { occupied = false; }

    public static synchronized void succeeded(Context context) {
        prefs(context).edit().remove(RETRY_AFTER).putLong(LAST_SUCCESS, System.currentTimeMillis())
                .putLong(SERVER_RETRY_SECONDS, -1L).commit();
    }

    public static synchronized void failed(Context context, Exception error) {
        if (context != null) record(context, error);
        occupied = false;
    }

    public static synchronized void markDenied(Context context, Exception error) {
        if (context != null && error != null && AuthAttemptPolicy.isDenied(error.getMessage()))
            record(context, error);
    }

    public static synchronized void release(Context context) { occupied = false; }

    private static void record(Context context, Exception error) {
        String message = error == null ? null : error.getMessage();
        long now = System.currentTimeMillis();
        long duration = AuthAttemptPolicy.effectiveCooldownMs(message);
        long serverWaitSeconds = AuthAttemptPolicy.serverRetrySecondsFromError(message);
        SharedPreferences current = prefs(context);
        long previous = current.getLong(RETRY_AFTER, 0L);
        SharedPreferences.Editor edit = current.edit().putLong(RETRY_AFTER, Math.max(previous, now + duration));
        if (AuthAttemptPolicy.isDenied(message)) {
            long priorDenied = current.getLong(LAST_DENIED, 0L);
            long priorSuccess = current.getLong(LAST_SUCCESS, 0L);
            if (priorDenied == 0L || priorSuccess >= priorDenied)
                edit.putLong(FIRST_DENIED, now);
            edit.putLong(LAST_DENIED, now).putLong(SERVER_RETRY_SECONDS, serverWaitSeconds);
        }
        edit.commit();
    }

    private static String time(long value) {
        return new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(new Date(value));
    }

    /** Remaining local cooldown for a service retry; this never contacts the router. */
    public static synchronized long remainingCooldownMs(Context context) {
        if (context == null) return 0L;
        SharedPreferences p = prefs(context);
        return AuthAttemptPolicy.remainingMs(System.currentTimeMillis(), p.getLong(RETRY_AFTER, 0L));
    }

    /** This status requires no network access and never sends a password. */
    public static synchronized String status(Context context) {
        SharedPreferences p = prefs(context);
        long now = System.currentTimeMillis();
        long remaining = AuthAttemptPolicy.remainingMs(now, p.getLong(RETRY_AFTER, 0L));
        long denied = p.getLong(LAST_DENIED, 0L);
        long firstDenied = p.getLong(FIRST_DENIED, 0L);
        long success = p.getLong(LAST_SUCCESS, 0L);
        long serverRetry = p.getLong(SERVER_RETRY_SECONDS, -1L);
        if (denied == 0L && success == 0L) return "لم تُسجل محاولة دخول ناجحة أو حالة رفض سابقة.";
        if (success >= denied && success > 0L) return "آخر اتصال ناجح: " + time(success)
                + ". مراقبة القائمة البيضاء تتطلب جلسة إدارة صالحة.";
        String state = "أول رفض رصده التطبيق في السلسلة الحالية: "
                + time(firstDenied > 0L ? firstDenied : denied)
                + ". آخر رفض: " + time(denied) + ". ";
        if (serverRetry >= 0L)
            state += "الراوتر أرسل Retry-After=" + serverRetry + " ثانية. ";
        else
            state += "لم يعلن الراوتر مدة رفع التقييد في المعلومات التي وصلتنا. ";
        if (remaining > 0L)
            state += "المهلة المحلية المتبقية في التطبيق: " + ((remaining + 59999L) / 60000L) + " دقيقة. ";
        else
            state += "انتهت مهلة التطبيق المحلية، لكن لا دليل أن الراوتر يقبل الدخول الآن. ";
        return state + "لا تختبر كلمة المرور مراراً: المصادقة نفسها ما زالت غير متحققة.";
    }
}
