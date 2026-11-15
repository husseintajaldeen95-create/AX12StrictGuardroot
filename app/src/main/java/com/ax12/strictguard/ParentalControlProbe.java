package com.ax12.strictguard;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Read-only diagnostic catalogue for AX12 Smart Network/Patrol forms that may
 * expose profile, device, or time-limit information. These names are candidates,
 * not a claim that every firmware maps them to Parental Controls.
 */
public final class ParentalControlProbe {
    private ParentalControlProbe() {}

    public enum Outcome {
        READABLE_WITH_DATA,
        READABLE_EMPTY,
        UNSUPPORTED,
        NEEDS_ARGUMENT,
        REFUSED,
        INCONCLUSIVE
    }

    public static final class EndpointSpec {
        public final String form;
        public final String operation;
        public final String label;

        private EndpointSpec(String form, String operation, String label) {
            this.form = form;
            this.operation = operation;
            this.label = label;
        }
    }

    private static final List<EndpointSpec> CANDIDATES = Collections.unmodifiableList(Arrays.asList(
            new EndpointSpec("patrol_enable", "read", "حالة واجهة Patrol"),
            new EndpointSpec("patrol_limit", "read", "حدود الوقت"),
            new EndpointSpec("patrol_devices", "load", "أجهزة الملفات"),
            new EndpointSpec("patrol_owner_list", "load", "ملفات المستخدمين")
    ));

    public static List<EndpointSpec> candidates() { return CANDIDATES; }

    public static Outcome classify(boolean success, String errorCode, boolean hasData) {
        if (success) return hasData ? Outcome.READABLE_WITH_DATA : Outcome.READABLE_EMPTY;
        String error = errorCode == null ? "" : errorCode.toLowerCase(Locale.ROOT);
        if (error.contains("no such callback") || error.contains("not supported")
                || error.contains("unsupported")) return Outcome.UNSUPPORTED;
        if (error.contains("invalid args") || error.contains("invalid parameter")
                || error.contains("missing parameter")) return Outcome.NEEDS_ARGUMENT;
        if (error.contains("permission denied") || error.contains("unauthorized")
                || error.contains("login failed") || error.contains("access denied")) return Outcome.REFUSED;
        return Outcome.INCONCLUSIVE;
    }

    public static boolean isSessionFailure(String errorCode) {
        if (errorCode == null) return false;
        String error = errorCode.toLowerCase(Locale.ROOT);
        return error.contains("timeout") || error.contains("permission denied")
                || error.contains("unauthorized") || error.contains("invalid token")
                || error.contains("login failed") || error.contains("session expired");
    }

    public static boolean isScheduleFieldName(String name) {
        if (name == null) return false;
        String key = name.replaceAll("([a-z])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT).replace('-', '_');
        if (key.contains("password") || key.contains("passwd") || key.contains("secret")
                || key.contains("token") || key.contains("cookie") || key.contains("psk")
                || key.contains("mac") || key.contains("ssid") || key.endsWith("_key")) return false;
        return key.contains("schedule") || key.contains("bedtime") || key.contains("bed_time")
                || key.contains("start_time") || key.contains("end_time")
                || key.contains("time_limit") || key.contains("online_time")
                || key.contains("offline_time") || key.contains("week_day")
                || key.contains("weekday") || key.contains("weekend")
                || key.contains("day_of_week") || key.contains("days_of_week")
                || key.equals("day") || key.equals("days") || key.contains("duration")
                || key.contains("start_hour") || key.contains("end_hour")
                || key.contains("time_start") || key.contains("time_end")
                || key.contains("time_range") || key.contains("effective_time")
                || key.contains("time_control") || key.contains("daily_limit")
                || key.contains("limit_minutes") || key.contains("online_limit");
    }

    public static String outcomeArabic(Outcome outcome) {
        if (outcome == null) return "غير محدد";
        switch (outcome) {
            case READABLE_WITH_DATA: return "استجابة قراءة ناجحة وبها بيانات";
            case READABLE_EMPTY: return "الواجهة استجابت، لكن البيانات فارغة";
            case UNSUPPORTED: return "الواجهة غير مدعومة في هذا البرنامج الثابت";
            case NEEDS_ARGUMENT: return "الواجهة موجودة لكنها تطلب معاملات إضافية";
            case REFUSED: return "الراوتر رفض طلب القراءة";
            default: return "نتيجة غير حاسمة";
        }
    }
}
