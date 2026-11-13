package com.ax12.strictguard;

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
import java.security.spec.RSAPublicKeySpec;
import javax.crypto.Cipher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Independent proof-of-concept LuCI adapter. Reconstructed from public AX12 v1 API research.
 * Not verified on EU Firmware 1.5.3; only use against a router under your administration.
 * No firmware writes, no admin password storage. Local HTTP on LAN only.
 */
public final class RouterClient {
    private final String host;
    private String token;
    private String password; // session lifetime only
    private static final String PATH="admin/access_control";

    public RouterClient(String ip) {
        if (ip == null || !ip.matches("(?:\\d{1,3}\\.){3}\\d{1,3}"))
            throw new IllegalArgumentException("أدخل عنوان IPv4 للراوتر");
        try {
            InetAddress a = InetAddress.getByName(ip);
            byte[] b=a.getAddress(); int x=b[0]&255, y=b[1]&255;
            if (!(x==10 || (x==172 && y>=16 && y<=31) || (x==192 && y==168)))
                throw new IllegalArgumentException("يجب استخدام IP محلي خاص بالراوتر فقط");
        } catch (IllegalArgumentException e) {throw e;}
        catch (Exception e) {throw new IllegalArgumentException("عنوان IPv4 غير صالح", e);}
        this.host=ip;
    }

    public void login(String userPassword) throws Exception {
        if (userPassword == null || userPassword.isEmpty()) throw new IllegalArgumentException("كلمة مرور الإدارة مطلوبة");
        this.password=userPassword;
        this.token=null;
        JSONObject k = request("login?form=keys", form("operation","read"),true);
        JSONObject keyData = requiredData(k);
        JSONArray key = keyData.optJSONArray("password");
        if (key==null || key.length()<2) throw new IllegalStateException("صيغة مفاتيح تشفير الراوتر مختلفة");
        request("login?form=auth", form("operation","read"),true);
        String encrypted = encryptRsa(userPassword, key.getString(0), key.getString(1));
        Map<String,String> body=form("operation","login");body.put("password", encrypted);
        JSONObject r = request("login?form=login", body,true);
        this.token=requiredData(r).getString("stok");
        if (!token.matches("[A-Za-z0-9_-]{8,128}")) throw new IllegalStateException("رمز جلسة الراوتر غير متوقع");
    }

    private static String encryptRsa(String clear,String modulus,String exponent) throws Exception {
        PublicKey pub=KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(new BigInteger(modulus,16),new BigInteger(exponent,16)));
        Cipher cipher=Cipher.getInstance("RSA/ECB/PKCS1Padding");cipher.init(Cipher.ENCRYPT_MODE,pub);
        byte[] bytes=cipher.doFinal(clear.getBytes(StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder(bytes.length*2);
        for (byte b:bytes) out.append(String.format(java.util.Locale.ROOT,"%02x", b&255));
        return out.toString();
    }
    private static Map<String,String> form(String a,String b) {Map<String,String> m=new LinkedHashMap<>();m.put(a,b);return m;}
    private JSONObject request(String path,Map<String,String> values,boolean loginPhase) throws Exception {
        String session = loginPhase ? "/login" : token;
        if (session==null) throw new IllegalStateException("لم يُسجّل الدخول إلى الراوتر");
        // The semicolon and empty stok prefix match the AX12 web LuCI routing convention.
        URL url=new URL("http://"+host+"/cgi-bin/luci/;stok="+session+"/"+path);
        HttpURLConnection conn=(HttpURLConnection)url.openConnection();
        try {
            conn.setRequestMethod("POST");conn.setConnectTimeout(7000);conn.setReadTimeout(9000);
            conn.setUseCaches(false);conn.setInstanceFollowRedirects(false);conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");
            conn.setRequestProperty("Referer","http://"+host+"/webpages/index.html");
            StringBuilder encoded=new StringBuilder();
            for (Map.Entry<String,String> en:values.entrySet()) {
                if (encoded.length()>0) encoded.append('&');
                encoded.append(URLEncoder.encode(en.getKey(),"UTF-8")).append('=').append(URLEncoder.encode(en.getValue(),"UTF-8"));
            }
            byte[] bytes=encoded.toString().getBytes(StandardCharsets.UTF_8);
            try(OutputStream out=conn.getOutputStream()) {out.write(bytes);}
            int status=conn.getResponseCode();
            if(status!=200) throw new IllegalStateException("HTTP " + status + " من الراوتر");
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=conn.getInputStream()) {
                byte[] buffer=new byte[2048];int n;
                while((n=in.read(buffer))!=-1) {out.write(buffer,0,n);if(out.size()>1024*1024) throw new IllegalStateException("استجابة الراوتر كبيرة جداً");}
            }
            JSONObject json=new JSONObject(out.toString("UTF-8"));
            Object ok=json.opt("success");
            if (!Boolean.TRUE.equals(ok) && !(ok instanceof Number && ((Number)ok).intValue()==1)) {
                String err=json.optString("errorcode", json.optString("error", ""));
                throw new IllegalStateException("رفض الراوتر الطلب ("+err+")؛ تحقق من توافق Firmware");
            }
            return json;
        } finally {conn.disconnect();}
    }
    private static JSONObject requiredData(JSONObject r) throws JSONException {
        JSONObject d=r.optJSONObject("data"); if(d==null) throw new JSONException("الراوتر لم يرجع data ككائن");return d;
    }
    private JSONObject api(String form,String operation,Map<String,String> data) throws Exception {
        Map<String,String> fields=form("operation",operation);fields.putAll(data);
        try {return request(PATH+"?form="+form,fields,false);}
        catch (IllegalStateException ex) {
            if (password==null || !(ex.getMessage().contains("permission") || ex.getMessage().contains("timeout") || ex.getMessage().contains("HTTP 403"))) throw ex;
            login(password);return request(PATH+"?form="+form,fields,false);
        }
    }
    public static final class Settings {
        public final boolean enabled;
        public final String mode;
        public final String protectedHost;
        Settings(boolean enabled,String mode,String host) {this.enabled=enabled;this.mode=mode;this.protectedHost=host;}
    }
    public Settings readSettings() throws Exception {
        JSONObject en = requiredData(api("enable","read",formEmpty()));
        JSONObject mo = requiredData(api("mode","read",formEmpty()));
        return new Settings("on".equalsIgnoreCase(en.optString("enable")),mo.optString("access_mode"),en.optString("host_mac"));
    }
    public void enforceWhiteMode() throws Exception {
        api("mode","write",form("access_mode","white"));
        if (!"white".equals(readSettings().mode)) throw new IllegalStateException("لم يؤكد الراوتر التحويل للقائمة البيضاء");
    }
    private static Map<String,String> formEmpty() {return new LinkedHashMap<>();}
    private static List<JSONObject> rows(JSONObject response) throws Exception {
        Object data=response.opt("data");
        List<JSONObject> entries=new ArrayList<>();
        if (data instanceof JSONArray) {
            JSONArray arr=(JSONArray)data;
            for(int i=0;i<arr.length();i++) {JSONObject row=arr.optJSONObject(i);if(row==null)throw new IllegalStateException("عنصر قائمة غير متوقع");entries.add(row);}
        } else if(data instanceof JSONObject) {
            JSONObject obj=(JSONObject)data;
            java.util.Iterator<String> it=obj.keys();
            while(it.hasNext()) {String key=it.next();Object val=obj.opt(key);
                if (val instanceof JSONObject) entries.add((JSONObject)val);
                else throw new IllegalStateException("صيغة قائمة الراوتر غير معروفة؛ لا حذف دون تحقق");
            }
        } else throw new IllegalStateException("لا يمكن تفسير رد قائمة السماح، أُوقف الحذف احترازياً");
        return entries;
    }
    private static String rowMac(JSONObject row) throws Exception {
        String mac=row.optString("mac",row.optString("macaddr",""));
        return MacPolicy.normalize(mac);
    }
    public Set<String> readAllowed() throws Exception {
        List<JSONObject> arr=rows(api("white_list","load",formEmpty()));
        Set<String> seen=new LinkedHashSet<>();for(JSONObject r:arr)seen.add(rowMac(r));return seen;
    }
    public void removeAllowed(String mac) throws Exception {
        String target=MacPolicy.normalize(mac);
        List<JSONObject> arr=rows(api("white_list","load",formEmpty()));
        int index=-1;
        for(int i=0;i<arr.size();i++)if(target.equals(rowMac(arr.get(i)))){index=i;break;}
        if(index<0)return;
        Map<String,String> body=form("key",target);body.put("index",Integer.toString(index));
        api("white_list","remove",body);
        if(readAllowed().contains(target))throw new IllegalStateException("لم يؤكد الراوتر إزالة MAC " + target);
    }
    public void allowObserved(String mac) throws Exception {
        String target=MacPolicy.normalize(mac);
        List<JSONObject> rows=rows(api("white_devices","load",formEmpty()));
        for(int i=0;i<rows.size();i++)if(target.equals(rowMac(rows.get(i)))) {
            Map<String,String> body=form("data",rows.get(i).toString());body.put("index",Integer.toString(i));
            api("white_devices","access",body);
            if(!readAllowed().contains(target)) throw new IllegalStateException("لم تُحفظ الإضافة في الراوتر");
            return;
        }
        throw new IllegalStateException("هذا الجهاز لم يره الراوتر بعد؛ أضف MAC من واجهة الإدارة الرسمية");
    }
    public void close() {token=null; password=null;}
}
