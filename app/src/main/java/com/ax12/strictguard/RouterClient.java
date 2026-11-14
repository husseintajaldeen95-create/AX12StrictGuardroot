package com.ax12.strictguard;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

/**
 * Experimental TP-Link LuCI adapter.
 * Detects the SG/CE-RED encryption profile when the router exposes its certification flags.
 * Compatibility with every regional firmware is not guaranteed. Use only on a router you administer.
 */
public final class RouterClient {
    private final String host;
    private final Context appContext;
    private boolean ownsLoginGate;
    private String token;
    private String password; // in-memory only; cleared by close()
    private final Map<String, String> cookies = new LinkedHashMap<>();

    private boolean enhancedMode;
    private String aesKey;
    private String aesIv;
    private String rollingHash;
    private long sequence;
    private String authModulus;
    private String authExponent;

    private static final String USERNAME = "admin";
    private static final String PATH = "admin/access_control";
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final int HMAC_CHUNK_SIZE = 53;
    private static final int RSA_OAEP_LEGACY_CHUNK_SIZE = 53;

    public RouterClient(String ip) {
        this(null, ip);
    }

    public RouterClient(Context context, String ip) {
        this.appContext = context == null ? null : context.getApplicationContext();
        if (ip == null || !ip.matches("(?:\\d{1,3}\\.){3}\\d{1,3}"))
            throw new IllegalArgumentException("أدخل عنوان IPv4 للراوتر");
        try {
            InetAddress address = InetAddress.getByName(ip);
            byte[] bytes = address.getAddress();
            int first = bytes[0] & 255, second = bytes[1] & 255;
            if (!(first == 10 || (first == 172 && second >= 16 && second <= 31) || (first == 192 && second == 168)))
                throw new IllegalArgumentException("يجب استخدام IP محلي خاص بالراوتر فقط");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("عنوان IPv4 غير صالح", e);
        }
        this.host = ip;
    }

    /**
     * Exactly one login attempt per call. Shared lock prevents concurrent app/service logins;
     * denied attempts create a persisted cooldown. Authentication profile compatibility
     * must still be verified on the actual router firmware.
     */
    public void login(String userPassword) throws Exception {
        if (userPassword == null || userPassword.isEmpty())
            throw new IllegalArgumentException("كلمة مرور الإدارة مطلوبة");
        if (appContext == null)
            throw new IllegalStateException("يجب إنشاء عميل الراوتر باستخدام سياق Android قبل تسجيل الدخول");
        if (ownsLoginGate)
            throw new IllegalStateException("هناك جلسة إدارة مفتوحة مسبقاً؛ أغلقها أولاً");
        AuthAttemptGate.begin(appContext);
        ownsLoginGate = true;
        clearSession();
        this.password = userPassword;
        try {
            this.enhancedMode = detectEnhancedProfile();
            if (enhancedMode) loginEnhanced(userPassword);
            else loginLegacy(userPassword);
            if (token == null || !token.matches("[A-Za-z0-9_-]{8,128}"))
                throw new IllegalStateException("رمز جلسة الراوتر غير متوقع");
            AuthAttemptGate.succeeded(appContext);
        } catch (Exception e) {
            AuthAttemptGate.failed(appContext, e);
            ownsLoginGate = false;
            clearSession();
            throw e;
        }
    }

    /** Read-only probe, does NOT submit a password or create an administrator session. */
    public String probeCompatibility() throws Exception {
        String raw = postRaw("device_config?form=config", encodeForm(form("operation", "read")), true, false);
        JSONObject root;
        try { root = new JSONObject(raw); }
        catch (JSONException e) { throw new IllegalStateException("ردّ الفحص ليس JSON؛ لا يمكن تأكيد نوع المصادقة دون فحص أدق", e); }
        JSONObject data = root.optJSONObject("data");
        JSONArray cert = data == null ? null : data.optJSONArray("certification");
        if (cert == null)
            return "استجاب الراوتر، لكنه لم يعلن شهادة بروتوكول المصادقة. لا يمكن اعتبار وضع RSA التقليدي مؤكداً.";
        for (int i = 0; i < cert.length(); i++) {
            String item = cert.optString(i, "").trim().toUpperCase(Locale.ROOT);
            if ("EU CE RED".equals(item) || "SG CLS L1 STAGE2".equals(item))
                return "أعلن الراوتر شهادة SG/CE RED؛ ما زال التوافق الفعلي للتوقيع بحاجة إلى تحقق.";
        }
        return "استجاب الراوتر، ولم يعلن SG/CE RED ضمن الشهادات المعروفة؛ لم يثبت بذلك نوع المصادقة.";
    }

    public String getProtocolName() {
        return enhancedMode ? "SG/CE RED (SHA-256 + AES-CBC + RSA-OAEP/HMAC)" : "LuCI التقليدي (RSA-PKCS1)";
    }

    /** Avoid guessing a login protocol when the router has not confirmed its profile. */
    private boolean detectEnhancedProfile() throws Exception {
        String raw = postRaw("device_config?form=config", encodeForm(form("operation", "read")), true, false);
        JSONObject config;
        try { config = new JSONObject(raw); }
        catch (JSONException e) {
            throw new IllegalStateException("لا يمكن تحديد بروتوكول المصادقة: استجابة التهيئة ليست JSON. لم تُرسل كلمة المرور.", e);
        }
        JSONObject data = config.optJSONObject("data");
        if (data == null)
            throw new IllegalStateException("لا يمكن تحديد بروتوكول المصادقة من رد التهيئة. لم تُرسل كلمة المرور.");
        JSONArray certification = data.optJSONArray("certification");
        if (certification == null)
            throw new IllegalStateException("لا توجد معلومات شهادة كافية لاختيار بروتوكول الدخول. لم تُرسل كلمة المرور.");
        for (int i = 0; i < certification.length(); i++) {
            String item = certification.optString(i, "").trim().toUpperCase(Locale.ROOT);
            if ("EU CE RED".equals(item) || "SG CLS L1 STAGE2".equals(item)) return true;
        }
        // The existing project implements RSA-PKCS1 as its legacy profile. This is
        // a single choice based on the disclosed flags, not a fallback after a 403.
        return false;
    }

    private void loginLegacy(String userPassword) throws Exception {
        JSONObject keysResponse = requestPlain("login?form=keys", form("operation", "read"), true, true);
        JSONObject keyData = requiredData(keysResponse);
        JSONArray key = keyData.optJSONArray("password");
        if (key == null || key.length() < 2)
            throw new IllegalStateException("استجابة مفاتيح الدخول التقليدية غير متوقعة؛ قد يختلف Firmware");

        requestPlain("login?form=auth", form("operation", "read"), true, true);

        String encryptedPassword = rsaEncryptPkcs1(userPassword, key.getString(0), key.getString(1));
        Map<String, String> body = form("operation", "login");
        body.put("password", encryptedPassword);
        JSONObject response = requestPlain("login?form=login", body, true, true);
        this.token = requiredData(response).getString("stok");
    }

    /** SG/CE-RED login: SHA-256 + AES-CBC + RSA-OAEP login signature. */
    private void loginEnhanced(String userPassword) throws Exception {
        // Match the documented SG client: keys/auth use an operation=read QUERY,
        // with an empty POST body. Do not try a second login variant on failure.
        JSONObject passwordKeysResponse = requestPlain("login?form=keys&operation=read", formEmpty(), true, true);
        JSONObject passwordKeyData = requiredData(passwordKeysResponse);
        JSONArray passwordKey = passwordKeyData.optJSONArray("password");
        if (passwordKey == null || passwordKey.length() < 2)
            throw new IllegalStateException("بروتوكول SG: لم تصل مفاتيح تشفير كلمة المرور");

        JSONObject authResponse = requestPlain("login?form=auth&operation=read", formEmpty(), true, true);
        JSONObject authData = requiredData(authResponse);
        JSONArray authKey = authData.optJSONArray("key");
        if (authKey == null || authKey.length() < 2)
            throw new IllegalStateException("بروتوكول SG: لم تصل مفاتيح التوقيع");
        this.sequence = Long.parseLong(authData.getString("seq"));
        this.authModulus = authKey.getString(0);
        this.authExponent = authKey.getString(1);

        this.rollingHash = sha256Hex((USERNAME + userPassword).getBytes(StandardCharsets.UTF_8));
        this.aesKey = randomDigits(16);
        this.aesIv = randomDigits(16);

        String encryptedPassword = rsaEncryptPkcs1(userPassword, passwordKey.getString(0), passwordKey.getString(1));
        String loginPlaintext = "operation=login&password=" + encryptedPassword + "&confirm=true";
        String encryptedData = aesEncrypt(loginPlaintext);
        // Login signature uses SHA-256(username + password); the rolling hash is
        // replaced with SHA-256(encrypted request data) only for subsequent API calls.
        String signatureText = "k=" + aesKey + "&i=" + aesIv + "&h=" + rollingHash + "&s=" + (sequence + encryptedData.length());
        String signature = rsaOaepSign(signatureText, authModulus, authExponent);
        String requestBody = "sign=" + signature + "&data=" + URLEncoder.encode(encryptedData, "UTF-8");
        String rawResponse = postRaw("login?form=login", requestBody, true, true);

        JSONObject wrapper = new JSONObject(rawResponse);
        String encryptedResponse = wrapper.getString("data");
        JSONObject decoded = new JSONObject(aesDecrypt(encryptedResponse));
        requireSuccess(decoded, "login?form=login");
        this.token = requiredData(decoded).getString("stok");
    }

    private static String rsaEncryptPkcs1(String clear, String modulus, String exponent) throws Exception {
        PublicKey publicKey = rsaPublicKey(modulus, exponent);
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return toHex(cipher.doFinal(clear.getBytes(StandardCharsets.UTF_8)));
    }

    private static String rsaOaepSign(String value, String modulus, String exponent) throws Exception {
        PublicKey publicKey = rsaPublicKey(modulus, exponent);
        int modulusBytes = modulus.length() / 2;
        int maxOaepPayload = modulusBytes - 42; // SHA-1 OAEP: 2 * 20 + 2 bytes overhead
        int chunkSize = Math.min(RSA_OAEP_LEGACY_CHUNK_SIZE, maxOaepPayload);
        if (chunkSize < 1) throw new IllegalStateException("مفتاح RSA الخاص بالتوقيع أصغر من المتوقع");

        StringBuilder signature = new StringBuilder();
        for (int offset = 0; offset < value.length(); offset += chunkSize) {
            String chunk = value.substring(offset, Math.min(value.length(), offset + chunkSize));
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
            OAEPParameterSpec spec = new OAEPParameterSpec("SHA-1", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, spec);
            signature.append(toHex(cipher.doFinal(chunk.getBytes(StandardCharsets.UTF_8))));
        }
        return signature.toString();
    }

    private static PublicKey rsaPublicKey(String modulus, String exponent) throws Exception {
        return KeyFactory.getInstance("RSA").generatePublic(
                new RSAPublicKeySpec(new BigInteger(modulus, 16), new BigInteger(exponent, 16)));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format(Locale.ROOT, "%02x", value & 255));
        return out.toString();
    }

    private String aesEncrypt(String plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey.getBytes(StandardCharsets.UTF_8), "AES"),
                new IvParameterSpec(aesIv.getBytes(StandardCharsets.UTF_8)));
        return android.util.Base64.encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)), android.util.Base64.NO_WRAP);
    }

    private String aesDecrypt(String ciphertext) throws Exception {
        byte[] encrypted = android.util.Base64.decode(ciphertext, android.util.Base64.DEFAULT);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey.getBytes(StandardCharsets.UTF_8), "AES"),
                new IvParameterSpec(aesIv.getBytes(StandardCharsets.UTF_8)));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private static String sha256Hex(byte[] data) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(data);
        return toHex(digest);
    }

    private static String randomDigits(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder value = new StringBuilder(length);
        for (int i = 0; i < length; i++) value.append((char) ('0' + random.nextInt(10)));
        return value.toString();
    }

    private String hmacSignature(int encryptedLength) throws Exception {
        String text = "h=" + rollingHash + "&s=" + (sequence + encryptedLength);
        byte[] key = ("k=" + aesKey + "&i=" + aesIv).getBytes(StandardCharsets.UTF_8);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        StringBuilder result = new StringBuilder();
        for (int offset = 0; offset < text.length(); offset += HMAC_CHUNK_SIZE) {
            String chunk = text.substring(offset, Math.min(text.length(), offset + HMAC_CHUNK_SIZE));
            result.append(toHex(mac.doFinal(chunk.getBytes(StandardCharsets.UTF_8))));
        }
        return result.toString();
    }

    private static Map<String, String> form(String key, String value) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put(key, value);
        return result;
    }

    private static String encodeForm(Map<String, String> values) throws Exception {
        StringBuilder encoded = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (encoded.length() > 0) encoded.append('&');
            encoded.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            encoded.append('=');
            encoded.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        return encoded.toString();
    }

    static String buildRequestUrl(String host, String path, String sessionToken, boolean loginPhase) {
        try {
            return RouterUrl.build(host, path, sessionToken, loginPhase);
        } catch (IllegalStateException e) {
            throw new IllegalStateException("لم يُسجّل الدخول إلى الراوتر", e);
        }
    }

    private JSONObject requestPlain(String path, Map<String, String> values, boolean loginPhase, boolean loginReferer) throws Exception {
        String raw = postRaw(path, encodeForm(values), loginPhase, loginReferer);
        JSONObject json;
        try {
            json = new JSONObject(raw);
        } catch (JSONException e) {
            throw new IllegalStateException("استجابة غير JSON من الراوتر أثناء " + path + ": " + compact(raw, 220), e);
        }
        requireSuccess(json, path);
        return json;
    }

    private JSONObject requestEnhanced(String path, Map<String, String> values) throws Exception {
        if (aesKey == null || aesIv == null || rollingHash == null)
            throw new IllegalStateException("جلسة SG غير مكتملة");
        String plaintext = encodeForm(values);
        String encryptedData = aesEncrypt(plaintext);
        rollingHash = sha256Hex(encryptedData.getBytes(StandardCharsets.UTF_8));
        String signature = hmacSignature(encryptedData.length());
        String body = "sign=" + signature + "&data=" + URLEncoder.encode(encryptedData, "UTF-8");
        String raw = postRaw(path, body, false, false);

        JSONObject wrapper;
        try {
            wrapper = new JSONObject(raw);
            String decodedText = aesDecrypt(wrapper.getString("data"));
            JSONObject decoded = new JSONObject(decodedText);
            requireSuccess(decoded, path);
            return decoded;
        } catch (JSONException e) {
            throw new IllegalStateException("تعذر فك استجابة الراوتر المشفرة أثناء " + path + ": " + compact(raw, 180), e);
        }
    }

    private String postRaw(String path, String body, boolean loginPhase, boolean loginReferer) throws Exception {
        String endpoint = buildRequestUrl(host, path, token, loginPhase);
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(9000);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json, text/plain, */*");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36");
            connection.setRequestProperty("Origin", "http://" + host);
            connection.setRequestProperty("Referer", "http://" + host + (loginReferer ? "/webpages/login.html" : "/webpages/index.html"));
            connection.setRequestProperty("Connection", "keep-alive");
            if (!cookies.isEmpty()) connection.setRequestProperty("Cookie", cookieHeader());

            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }

            int status = connection.getResponseCode();
            captureCookies(connection);
            String responseText = readResponse(connection, status);
            if (status != HttpURLConnection.HTTP_OK) {
                String location = connection.getHeaderField("Location");
                String message = "HTTP " + status + " من الراوتر أثناء " + path;
                if (location != null && !location.isEmpty()) message += " (الراوتر طلب إعادة توجيه)";
                if (responseText.toLowerCase(Locale.ROOT).contains("exceeded max attempts"))
                    message += " (exceeded max attempts)";
                String retryHeader = connection.getHeaderField("Retry-After");
                long serverSeconds = AuthAttemptPolicy.parseServerRetrySeconds(retryHeader);
                if (serverSeconds >= 0) message += " [router-retry-seconds:" + serverSeconds + "]";
                else message += "؛ الراوتر لم يرسل مدة Retry-After رقمية قابلة للاستخدام";
                if (status == HttpURLConnection.HTTP_FORBIDDEN)
                    message += "؛ قد تكون المصادقة أو التوقيع غير متوافقين، وليس بالضرورة حظراً لمدة محددة";
                throw new IllegalStateException(message);
            }
            return responseText;
        } finally {
            connection.disconnect();
        }
    }

    private String cookieHeader() {
        StringBuilder header = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (header.length() > 0) header.append("; ");
            header.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return header.toString();
    }

    private void captureCookies(HttpURLConnection connection) {
        Map<String, List<String>> headers = connection.getHeaderFields();
        if (headers == null) return;
        for (Map.Entry<String, List<String>> header : headers.entrySet()) {
            if (header.getKey() == null || !"Set-Cookie".equalsIgnoreCase(header.getKey()) || header.getValue() == null) continue;
            for (String raw : header.getValue()) {
                if (raw == null) continue;
                String pair = raw.split(";", 2)[0].trim();
                int equals = pair.indexOf('=');
                if (equals <= 0) continue;
                String name = pair.substring(0, equals).trim();
                String value = pair.substring(equals + 1).trim();
                if (!name.isEmpty()) cookies.put(name, value);
            }
        }
    }

    private static String readResponse(HttpURLConnection connection, int status) throws Exception {
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) return "";
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
                if (output.size() > MAX_RESPONSE_BYTES) throw new IllegalStateException("استجابة الراوتر كبيرة جداً");
            }
            return output.toString("UTF-8");
        }
    }

    private static String compact(String value, int max) {
        if (value == null) return "";
        String cleaned = value.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").trim();
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max) + "…";
    }

    private static void requireSuccess(JSONObject response, String path) {
        Object success = response.opt("success");
        if (Boolean.TRUE.equals(success) || (success instanceof Number && ((Number) success).intValue() == 1)) return;
        String error = firstNonEmpty(response, "errorcode", "errorCode", "error_code", "error");
        String detail = error.toLowerCase(Locale.ROOT).contains("exceeded max attempts")
                ? "exceeded max attempts" : "رفض المصادقة/الطلب";
        throw new IllegalStateException("رفض الراوتر الطلب أثناء " + path + " (" + detail + ")؛ لا تكرر المحاولة قبل تشخيص التوافق");
    }

    private static String firstNonEmpty(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.optString(key, "");
            if (!value.isEmpty() && !"null".equalsIgnoreCase(value)) return value;
        }
        return "unknown";
    }

    private static JSONObject requiredData(JSONObject response) throws JSONException {
        JSONObject data = response.optJSONObject("data");
        if (data == null) throw new JSONException("الراوتر لم يرجع data ككائن");
        return data;
    }

    private JSONObject api(String formName, String operation, Map<String, String> data) throws Exception {
        Map<String, String> fields = form("operation", operation);
        fields.putAll(data);
        try {
            if (enhancedMode) return requestEnhanced(PATH + "?form=" + formName, fields);
            return requestPlain(PATH + "?form=" + formName, fields, false, false);
        } catch (Exception e) {
            // Never auto-login again after a failed read/write. The router can count
            // each attempt and lock out this client even while browser login works.
            AuthAttemptGate.markDenied(appContext, e);
            throw e;
        }
    }

    public static final class Settings {
        public final boolean enabled;
        public final String mode;
        public final String protectedHost;
        Settings(boolean enabled, String mode, String host) {
            this.enabled = enabled;
            this.mode = mode;
            this.protectedHost = host;
        }
    }

    public Settings readSettings() throws Exception {
        JSONObject enabled = requiredData(api("enable", "read", formEmpty()));
        JSONObject mode = requiredData(api("mode", "read", formEmpty()));
        return new Settings("on".equalsIgnoreCase(enabled.optString("enable")),
                mode.optString("access_mode"), enabled.optString("host_mac"));
    }

    public void enforceWhiteMode() throws Exception {
        api("mode", "write", form("access_mode", "white"));
        if (!"white".equalsIgnoreCase(readSettings().mode))
            throw new IllegalStateException("لم يؤكد الراوتر التحويل للقائمة البيضاء");
    }

    private static Map<String, String> formEmpty() {
        return new LinkedHashMap<>();
    }

    private static List<JSONObject> rows(JSONObject response) throws Exception {
        Object data = response.opt("data");
        List<JSONObject> entries = new ArrayList<>();
        if (data instanceof JSONArray) {
            JSONArray array = (JSONArray) data;
            for (int i = 0; i < array.length(); i++) {
                JSONObject row = array.optJSONObject(i);
                if (row == null) throw new IllegalStateException("عنصر قائمة غير متوقع");
                entries.add(row);
            }
        } else if (data instanceof JSONObject) {
            JSONObject object = (JSONObject) data;
            java.util.Iterator<String> iterator = object.keys();
            while (iterator.hasNext()) {
                String key = iterator.next();
                if ("others".equalsIgnoreCase(key)) continue; // router-side capacity metadata, not a MAC row
                Object value = object.opt(key);
                if (value instanceof JSONObject) entries.add((JSONObject) value);
                else throw new IllegalStateException("صيغة قائمة الراوتر غير معروفة؛ أُوقف الحذف احترازياً");
            }
        } else {
            throw new IllegalStateException("لا يمكن تفسير رد قائمة السماح؛ أُوقف الحذف احترازياً");
        }
        return entries;
    }

    private static String rowMac(JSONObject row) {
        String mac = row.optString("mac", row.optString("macaddr", ""));
        return MacPolicy.normalize(mac);
    }

    public Set<String> readAllowed() throws Exception {
        List<JSONObject> entries = rows(api("white_list", "load", formEmpty()));
        Set<String> seen = new LinkedHashSet<>();
        for (JSONObject row : entries) seen.add(rowMac(row));
        return seen;
    }

    public void removeAllowed(String mac) throws Exception {
        String target = MacPolicy.normalize(mac);
        List<JSONObject> entries = rows(api("white_list", "load", formEmpty()));
        int index = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (target.equals(rowMac(entries.get(i)))) {
                index = i;
                break;
            }
        }
        if (index < 0) return;
        Map<String, String> body = form("key", target);
        body.put("index", Integer.toString(index));
        api("white_list", "remove", body);
        if (readAllowed().contains(target))
            throw new IllegalStateException("لم يؤكد الراوتر إزالة MAC " + target);
    }

    public void allowObserved(String mac) throws Exception {
        String target = MacPolicy.normalize(mac);
        List<JSONObject> entries = rows(api("white_devices", "load", formEmpty()));
        for (int i = 0; i < entries.size(); i++) {
            if (target.equals(rowMac(entries.get(i)))) {
                Map<String, String> body = form("data", entries.get(i).toString());
                body.put("index", Integer.toString(i));
                api("white_devices", "access", body);
                if (!readAllowed().contains(target))
                    throw new IllegalStateException("لم تُحفظ الإضافة في الراوتر");
                return;
            }
        }
        throw new IllegalStateException("هذا الجهاز لم يره الراوتر بعد؛ أضف MAC من واجهة الإدارة الرسمية");
    }

    private void clearSession() {
        token = null;
        password = null;
        cookies.clear();
        enhancedMode = false;
        aesKey = null;
        aesIv = null;
        rollingHash = null;
        sequence = 0;
        authModulus = null;
        authExponent = null;
    }

    public void close() {
        // Best-effort logout frees the router's single administrator session before
        // the next read test or foreground monitor starts. Failure is non-fatal.
        if (token != null) {
            try {
                Map<String, String> body = form("operation", "write");
                if (enhancedMode) requestEnhanced("admin/system?form=logout", body);
                else requestPlain("admin/system?form=logout", body, false, false);
            } catch (Exception ignored) {
                // The router may already have expired or replaced this session.
            }
        }
        clearSession();
        if (ownsLoginGate) {
            ownsLoginGate = false;
            AuthAttemptGate.release(appContext);
        }
    }
}
