package com.ax12.strictguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Optional encrypted credential storage used only when the owner explicitly opts in.
 * The AES key is non-exportable and held by Android Keystore; SharedPreferences stores
 * only the host, IV, and authenticated ciphertext. No password is written in plaintext.
 */
public final class CredentialVault {
    private static final String PREF = "strict_guard_restart_credential_v1";
    private static final String KEY_ALIAS = "ax12_strict_guard_restart_aes_v1";
    private static final String KEY_ENABLED = "resume_enabled";
    private static final String KEY_HOST = "router_host";
    private static final String KEY_IV = "credential_iv";
    private static final String KEY_CIPHER = "credential_ciphertext";
    private static final int GCM_TAG_BITS = 128;

    private CredentialVault() {}

    public static final class SavedCredentials {
        public final String host;
        public final String password;
        private SavedCredentials(String host, String password) {
            this.host = host;
            this.password = password;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static boolean hasSavedCredential(Context context) {
        SharedPreferences p = prefs(context);
        return p.getBoolean(KEY_ENABLED, false)
                && !p.getString(KEY_HOST, "").isEmpty()
                && !p.getString(KEY_IV, "").isEmpty()
                && !p.getString(KEY_CIPHER, "").isEmpty();
    }

    public static boolean hasSavedCredentialForHost(Context context, String host) {
        return hasSavedCredential(context) && host != null
                && host.equals(prefs(context).getString(KEY_HOST, ""));
    }

    /** Persist only after explicit user consent. */
    public static synchronized void saveForRestart(Context context, String host, String password) {
        if (host == null || host.trim().isEmpty() || password == null || password.isEmpty())
            throw new IllegalArgumentException("تعذر حفظ بيانات الاستعادة: عنوان الراوتر أو كلمة المرور مفقود");
        try {
            SecretKey key = getOrCreateKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            cipher.updateAAD(aad(context, host));
            byte[] encrypted = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            SharedPreferences.Editor edit = prefs(context).edit()
                    .putString(KEY_HOST, host)
                    .putString(KEY_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .putString(KEY_CIPHER, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .putBoolean(KEY_ENABLED, true);
            if (!edit.commit()) throw new IllegalStateException("تعذر حفظ بيانات الاستعادة المشفرة");
        } catch (Exception e) {
            clear(context);
            if (e instanceof IllegalStateException) throw (IllegalStateException) e;
            throw new IllegalStateException("تعذر إنشاء تخزين آمن لكلمة المرور عبر Android Keystore؛ لم تُحفظ كلمة المرور", e);
        }
    }

    /** Returns null when restart recovery was not explicitly enabled. */
    public static synchronized SavedCredentials loadForRestart(Context context) {
        if (!hasSavedCredential(context)) return null;
        SharedPreferences p = prefs(context);
        String host = p.getString(KEY_HOST, "");
        try {
            byte[] iv = Base64.decode(p.getString(KEY_IV, ""), Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(p.getString(KEY_CIPHER, ""), Base64.NO_WRAP);
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            SecretKey key = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
            if (key == null) throw new IllegalStateException("مفتاح Android Keystore غير موجود");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(aad(context, host));
            String password = new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
            if (password.isEmpty()) throw new IllegalStateException("بيانات الاستعادة فارغة");
            return new SavedCredentials(host, password);
        } catch (Exception e) {
            clear(context);
            throw new IllegalStateException("تعذرت استعادة كلمة المرور المحفوظة بأمان؛ أُزيلت بيانات الاستعادة، أدخل كلمة المرور يدوياً مجدداً", e);
        }
    }

    /** Disable automatic restart and remove the encrypted credential and its Keystore key. */
    public static synchronized void clear(Context context) {
        prefs(context).edit().clear().commit();
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS);
        } catch (Exception ignored) {
            // Ciphertext is already removed; a remaining unused key cannot decrypt anything.
        }
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS))
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build();
        generator.init(spec);
        return generator.generateKey();
    }

    private static byte[] aad(Context context, String host) {
        String value = context.getPackageName() + "|AX12-StrictGuard|" + host;
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
