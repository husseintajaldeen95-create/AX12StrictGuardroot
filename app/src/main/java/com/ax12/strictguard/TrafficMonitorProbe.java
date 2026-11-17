package com.ax12.strictguard;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Read-only parser for the Archer AX12 Smart Network device-priority table. */
public final class TrafficMonitorProbe {
    private static final Pattern RATE_WITH_UNIT = Pattern.compile(
            "([-+]?[0-9]+(?:\\.[0-9]+)?)\\s*(mb/s|mbyte/s|mbps|kb/s|kbyte/s|kbps|b/s|byte/s|bps)",
            Pattern.CASE_INSENSITIVE);
    private static final String[] MAC_KEYS = {"mac", "macaddr", "mac_address", "client_mac", "host_mac"};
    private static final String[] NAME_KEYS = {"name", "hostname", "device_name", "client_name", "alias", "dev_name"};
    private static final String[] DOWN_KEYS = {"download_rate", "down_rate", "downstream_rate", "rx_rate",
            "receive_rate", "downlink_rate", "download_speed", "down_speed", "rx_speed",
            "real_time_download_rate", "real_time_down_rate", "downstream", "download"};
    private static final String[] UP_KEYS = {"upload_rate", "up_rate", "upstream_rate", "tx_rate",
            "send_rate", "uplink_rate", "upload_speed", "up_speed", "tx_speed",
            "real_time_upload_rate", "real_time_up_rate", "upstream", "upload"};

    private TrafficMonitorProbe() {}

    public static final class Sample {
        public final String mac;
        public final String name;
        public final double downloadBytesPerSecond;
        public final double uploadBytesPerSecond;
        public final boolean hasRates;

        Sample(String mac, String name, double download, double upload, boolean hasRates) {
            this.mac = mac;
            this.name = name == null ? "" : name;
            this.downloadBytesPerSecond = download;
            this.uploadBytesPerSecond = upload;
            this.hasRates = hasRates;
        }
    }

    public static final class Result {
        public final boolean endpointAnswered;
        public final boolean hasRates;
        public final List<Sample> samples;
        public final String report;

        Result(boolean answered, List<Sample> samples, String report) {
            this.endpointAnswered = answered;
            this.samples = Collections.unmodifiableList(new ArrayList<>(samples));
            boolean found = false;
            for (Sample sample : samples) if (sample.hasRates) { found = true; break; }
            this.hasRates = found;
            this.report = report;
        }
    }

    public static Result parse(JSONObject response) {
        if (response == null) return new Result(false, Collections.emptyList(),
                "لم تصل استجابة من واجهة Device Priority.");
        Object success = response.opt("success");
        boolean ok = Boolean.TRUE.equals(success)
                || (success instanceof Number && ((Number) success).intValue() == 1);
        String error = safeError(response);
        if (!ok) return new Result(false, Collections.emptyList(),
                "واجهة device_priority لم تؤكد نجاح القراءة. حالة الراوتر: "
                        + (error.isEmpty() ? "رفض غير موضح" : error)
                        + ". لم يُرسل أي طلب كتابة أو تعديل.");

        Object data = response.opt("data");
        List<JSONObject> rows = new ArrayList<>();
        collectRows(data, rows, new LinkedHashSet<>(), 0);
        if (rows.isEmpty()) {
            String keys = collectTopLevelKeys(data);
            return new Result(true, Collections.emptyList(),
                    "نجحت قراءة admin/smart_network?form=device_priority (load)، لكن لم تظهر سجلات مرتبطة بعناوين MAC."
                            + (keys.isEmpty() ? " لا توجد حقول بيانات ظاهرة." : " الحقول الظاهرة: " + keys)
                            + " قد تكون القائمة فارغة أو أن هذا Firmware يعيد بنية مختلفة.");
        }

        List<Sample> samples = new ArrayList<>();
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        int rateCount = 0;
        for (JSONObject row : rows) {
            Iterator<String> it = row.keys();
            while (it.hasNext()) fields.add(it.next());
            String mac = value(row, MAC_KEYS);
            String normalized;
            try { normalized = MacPolicy.normalize(mac); }
            catch (Exception invalid) { continue; }
            String name = value(row, NAME_KEYS);
            double down = readRate(row, DOWN_KEYS);
            double up = readRate(row, UP_KEYS);
            boolean hasRates = Double.isFinite(down) && Double.isFinite(up);
            if (hasRates) rateCount++;
            samples.add(new Sample(normalized, name, down, up, hasRates));
        }
        StringBuilder report = new StringBuilder();
        report.append("نجحت قراءة واجهة admin/smart_network?form=device_priority (load).")
                .append("\nسجلات MAC القابلة للربط: ").append(samples.size())
                .append("\nسجلات تحتوي سرعتي تنزيل ورفع بوحدات قابلة للتحويل: ").append(rateCount)
                .append("\nأسماء الحقول المستلمة: ");
        int count = 0;
        for (String field : fields) {
            if (count++ > 0) report.append(", ");
            report.append(field);
            if (count >= 45) { report.append(", …"); break; }
        }
        if (rateCount == 0) report.append("\nلم أتعرف على حقلي سرعة التنزيل والرفع بوحدات صريحة؛ لن أحوّل قيماً مجهولة الوحدة إلى أرقام استهلاك.");
        else report.append("\nتم التعرف على معدلات الرفع والتنزيل؛ يمكن بدء الجمع التراكمي المحلي عند تشغيل الحارس.");
        report.append("\nالعملية قراءة فقط؛ لم يتم تفعيل QoS أو تغيير أولوية أي جهاز.");
        return new Result(true, samples, report.toString());
    }

    private static String safeError(JSONObject response) {
        String[] keys = {"errorcode", "errorCode", "error_code", "error", "msg", "message"};
        for (String key : keys) {
            Object value = response.opt(key);
            if (value instanceof String || value instanceof Number) {
                String text = String.valueOf(value).trim();
                if (!text.isEmpty() && !"null".equalsIgnoreCase(text)) return AuthErrorLabel.safeValue(text);
            }
        }
        return "";
    }

    private static void collectRows(Object value, List<JSONObject> rows, Set<String> seen, int depth) {
        if (value == null || depth > 6) return;
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) collectRows(array.opt(i), rows, seen, depth + 1);
            return;
        }
        if (!(value instanceof JSONObject)) return;
        JSONObject object = (JSONObject) value;
        String mac = value(object, MAC_KEYS);
        if (!mac.isEmpty()) {
            try {
                String normalized = MacPolicy.normalize(mac);
                if (seen.add(normalized)) rows.add(object);
                return;
            } catch (Exception ignored) { /* continue searching nested data */ }
        }
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) collectRows(object.opt(keys.next()), rows, seen, depth + 1);
    }

    private static String collectTopLevelKeys(Object data) {
        if (!(data instanceof JSONObject)) return data == null ? "" : data.getClass().getSimpleName();
        StringBuilder out = new StringBuilder();
        Iterator<String> keys = ((JSONObject) data).keys();
        int count = 0;
        while (keys.hasNext() && count < 45) {
            if (count++ > 0) out.append(", ");
            out.append(keys.next());
        }
        return out.toString();
    }

    private static String value(JSONObject row, String[] aliases) {
        for (String alias : aliases) {
            Iterator<String> keys = row.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (alias.equalsIgnoreCase(key)) {
                    Object value = row.opt(key);
                    if (value instanceof String || value instanceof Number) return String.valueOf(value).trim();
                }
            }
        }
        return "";
    }

    private static double readRate(JSONObject row, String[] aliases) {
        for (String alias : aliases) {
            Iterator<String> keys = row.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!alias.equalsIgnoreCase(key)) continue;
                double parsed = parseRate(row.opt(key), key);
                if (Double.isFinite(parsed)) return parsed;
            }
        }
        return Double.NaN;
    }

    /** Convert only explicit rate units. Bare numbers are intentionally not guessed. */
    private static double parseRate(Object value, String key) {
        if (value == null || value == JSONObject.NULL) return Double.NaN;
        String raw = String.valueOf(value).trim().toLowerCase(Locale.ROOT).replace(",", "");
        Matcher matcher = RATE_WITH_UNIT.matcher(raw);
        if (matcher.find()) {
            double amount;
            try { amount = Double.parseDouble(matcher.group(1)); }
            catch (NumberFormatException bad) { return Double.NaN; }
            String unit = matcher.group(2).toLowerCase(Locale.ROOT);
            if (unit.equals("mb/s") || unit.equals("mbyte/s")) return amount * 1024d * 1024d;
            if (unit.equals("kb/s") || unit.equals("kbyte/s")) return amount * 1024d;
            if (unit.equals("b/s") || unit.equals("byte/s")) return amount;
            if (unit.equals("mbps")) return amount * 1000d * 1000d / 8d;
            if (unit.equals("kbps")) return amount * 1000d / 8d;
            if (unit.equals("bps")) return amount / 8d;
        }
        if (!(value instanceof Number)) return Double.NaN;
        double amount = ((Number) value).doubleValue();
        if (!Double.isFinite(amount) || amount < 0) return Double.NaN;
        String normalized = key.toLowerCase(Locale.ROOT);
        if (normalized.endsWith("_bytes_per_second") || normalized.endsWith("_bytes_sec")
                || normalized.endsWith("_byte_s") || normalized.endsWith("_bps_bytes")) return amount;
        if (normalized.endsWith("_kb_s") || normalized.endsWith("_kbytes_sec")) return amount * 1024d;
        if (normalized.endsWith("_mb_s") || normalized.endsWith("_mbytes_sec")) return amount * 1024d * 1024d;
        if (normalized.endsWith("_kbps")) return amount * 1000d / 8d;
        if (normalized.endsWith("_mbps")) return amount * 1000000d / 8d;
        if (normalized.endsWith("_bps")) return amount / 8d;
        return Double.NaN;
    }
}
