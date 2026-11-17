package com.ax12.strictguard;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Read-only parser for the Archer AX12 Smart Network device-priority table. */
public final class TrafficMonitorProbe {
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
        /** Raw firmware values. Their unit is deliberately not inferred. */
        public final double rawDownload;
        public final double rawUpload;
        public final boolean hasRawRates;

        Sample(String mac, String name, double download, double upload, boolean hasRawRates) {
            this.mac = mac;
            this.name = name == null ? "" : name;
            this.rawDownload = download;
            this.rawUpload = upload;
            this.hasRawRates = hasRawRates;
        }
    }

    public static final class Result {
        public final boolean endpointAnswered;
        public final boolean hasRawRates;
        public final List<Sample> samples;
        public final String report;

        Result(boolean answered, List<Sample> samples, String report) {
            this.endpointAnswered = answered;
            this.samples = Collections.unmodifiableList(new ArrayList<>(samples));
            boolean found = false;
            for (Sample sample : samples) if (sample.hasRawRates) { found = true; break; }
            this.hasRawRates = found;
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
            double down = readRawNumber(row, DOWN_KEYS);
            double up = readRawNumber(row, UP_KEYS);
            boolean hasRates = Double.isFinite(down) && Double.isFinite(up) && down >= 0 && up >= 0;
            if (hasRates) rateCount++;
            samples.add(new Sample(normalized, name, down, up, hasRates));
        }
        StringBuilder report = new StringBuilder();
        report.append("نجحت قراءة واجهة admin/smart_network?form=device_priority (load).")
                .append("\nسجلات MAC القابلة للربط: ").append(samples.size())
                .append("\nسجلات تحتوي قيمتي تنزيل ورفع رقميتين: ").append(rateCount)
                .append("\nأسماء الحقول المستلمة: ");
        int count = 0;
        for (String field : fields) {
            if (count++ > 0) report.append(", ");
            report.append(field);
            if (count >= 45) { report.append(", …"); break; }
        }
        if (rateCount == 0) {
            report.append("\nلم تظهر قيم رقمية صالحة لحقلي التنزيل والرفع في السجلات.");
            report.append("\nعينات القيم الخام لأول ثلاثة سجلات (للتشخيص فقط):");
            for (int i = 0; i < samples.size() && i < 3; i++) {
                Sample sample = samples.get(i);
                JSONObject row = findRowByMac(rows, sample.mac);
                if (row == null) continue;
                report.append("\n").append(maskedMac(sample.mac)).append(" | ")
                        .append(rawFieldDiagnostic(row, "downloadSpeed", "download_speed", "download_rate"))
                        .append(" | ").append(rawFieldDiagnostic(row, "uploadSpeed", "upload_speed", "upload_rate"));
            }
            report.append("\nلا توجد قراءة نشاط قابلة للاستخدام بعد؛ لم يتم تغيير أي إعداد.");
        } else {
            report.append("\nتم التعرف على القيم الرقمية الخام للرفع والتنزيل. ستُستخدم فقط لمعرفة وجود نشاط (قيمة أكبر من صفر)، دون افتراض وحدة أو عرض سرعة رقمية.");
        }
        report.append("\nالعملية قراءة فقط؛ لم يتم تفعيل QoS أو تغيير أولوية أي جهاز.");
        return new Result(true, samples, report.toString());
    }

    private static JSONObject findRowByMac(List<JSONObject> rows, String targetMac) {
        for (JSONObject row : rows) {
            String candidate = value(row, MAC_KEYS);
            try {
                if (MacPolicy.normalize(candidate).equals(targetMac)) return row;
            } catch (Exception ignored) { }
        }
        return null;
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
                if (TrafficFieldNames.matches(key, alias)) {
                    Object value = row.opt(key);
                    if (value instanceof String || value instanceof Number) return String.valueOf(value).trim();
                }
            }
        }
        return "";
    }

    private static double readRawNumber(JSONObject row, String[] aliases) {
        for (String alias : aliases) {
            Iterator<String> keys = row.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!TrafficFieldNames.matches(key, alias)) continue;
                Object value = row.opt(key);
                if (value instanceof Number) {
                    double number = ((Number) value).doubleValue();
                    return Double.isFinite(number) && number >= 0 ? number : Double.NaN;
                }
                if (value instanceof String) {
                    String raw = ((String) value).trim();
                    try {
                        double number = Double.parseDouble(raw);
                        return Double.isFinite(number) && number >= 0 ? number : Double.NaN;
                    } catch (NumberFormatException ignored) { return Double.NaN; }
                }
            }
        }
        return Double.NaN;
    }

    private static String rawFieldDiagnostic(JSONObject row, String... aliases) {
        for (String alias : aliases) {
            Iterator<String> keys = row.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!TrafficFieldNames.matches(key, alias)) continue;
                Object value = row.opt(key);
                if (value == null || value == JSONObject.NULL) return key + "=null";
                String type = value instanceof Number ? "Number"
                        : value instanceof String ? "String"
                        : value instanceof Boolean ? "Boolean" : value.getClass().getSimpleName();
                String rendered = String.valueOf(value).replaceAll("[\r\n\t]", " ").trim();
                if (rendered.length() > 72) rendered = rendered.substring(0, 72) + "…";
                return key + "=" + rendered + " (" + type + ")";
            }
        }
        return "غير موجود";
    }

    private static String maskedMac(String mac) {
        if (mac == null || mac.length() < 8) return "MAC مخفي";
        return mac.substring(0, 8) + "-**-**-**";
    }

}
