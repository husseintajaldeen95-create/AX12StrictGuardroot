package com.ax12.strictguard;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Set;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** Arabic UI. Manual MAC approvals are independent of whatever the router auto-adds. */
public final class MainActivity extends Activity {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private EditText hostField,passwordField,macField;
    private TextView trustedView,eventsView,statusView;
    private TrustedStore store;
    private GuardLog log;
    private boolean routerReadTestPassed=false;
    private boolean testingConnection=false;
    private String testedHost;
    private String testedPasswordFingerprint;
    private final Runnable refresh=new Runnable(){@Override public void run(){
        if(trustedView!=null) updateLists();ui.postDelayed(this,3500);
    }};
    @Override protected void onCreate(Bundle bundle){
        super.onCreate(bundle);store=new TrustedStore(this);log=new GuardLog(this);
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!= PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);
        showUi();
    }
    private TextView text(String s,int size,boolean strong){
        TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.rgb(36,48,61));
        t.setTextSize(size);if(strong)t.setTypeface(null, Typeface.BOLD);
        t.setGravity(Gravity.RIGHT);t.setPadding(4,8,4,8);return t;
    }
    private Button button(String label,Runnable run){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setOnClickListener(v->run.run());return b;}
    private EditText field(String hint,int input){EditText f=new EditText(this);f.setHint(hint);f.setSingleLine(true);f.setTextDirection(View.TEXT_DIRECTION_LTR);f.setInputType(input);return f;}
    private void showUi(){
        ScrollView scroll=new ScrollView(this);LinearLayout col=new LinearLayout(this);col.setPadding(20,20,20,25);col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);scroll.addView(col);
        TextView title=text("AX12 STRICT GUARD",24,true); title.setTextColor(Color.rgb(18,94,122)); col.addView(title);
        col.addView(text("الحارس المستقل للقائمة البيضاء – إصدار تجريبي للاختبار",14,false));
        col.addView(text("لا يُضاف أي MAC تلقائياً إلى قائمة الثقة. عند تشغيل الحارس تُراجع قائمة الراوتر كل 15 ثانية وتُزال الإدخالات غير المعتمدة.",14,false));
        col.addView(text("مهم: لا يمكن منع أول اتصال إذا سمح الراوتر به قبل الفحص؛ ولا يمكن كشف انتحال MAC معتمد. لا تشغّل الحارس قبل نجاح اختبار القراءة فقط.",13,true));
        col.addView(text("عنوان الراوتر المحلي",16,true));
        hostField=field("192.168.0.1",InputType.TYPE_CLASS_PHONE);hostField.setText(store.getHost());col.addView(hostField);
        col.addView(text("كلمة مرور إدارة الراوتر (لن تُحفظ)",16,true));
        passwordField=field("كلمة مرور الإدارة",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD); col.addView(passwordField);
        col.addView(button("فحص بروتوكول الراوتر دون تسجيل الدخول",this::probeCompatibility));
        col.addView(button("1. اختبار الاتصال (قراءة فقط)",this::testConnection));
        col.addView(text("أضف عناوين MAC المعتمدة يدوياً",16,true));
        macField=field("AA:BB:CC:DD:EE:FF",InputType.TYPE_CLASS_TEXT);col.addView(macField);
        col.addView(button("إضافة MAC للقائمة المعتمدة محلياً",this::addMac));
        col.addView(button("حذف MAC من القائمة المعتمدة محلياً",this::removeMac));
        col.addView(button("إرسال MAC معتمد إلى الراوتر (بموافقة صريحة)",this::confirmAllow));
        trustedView=text("",15,false);col.addView(trustedView);
        col.addView(button("2. تشغيل المراقبة الصارمة",this::startGuard));
        col.addView(button("إيقاف المراقبة",this::stopGuard));
        statusView=text("الحالة: غير متصلة",14,true);col.addView(statusView);
        col.addView(text("سجل الأحداث",17,true));
        eventsView=text("",13,false);col.addView(eventsView);
        setContentView(scroll);updateLists();
    }
    private void updateLists(){
        try {StringBuilder b=new StringBuilder("قائمتك المعتمدة ("+store.get().size()+"):\n");
            for(String mac:store.get())b.append("• ").append(mac).append('\n');trustedView.setText(b.toString());}
        catch(Exception e){trustedView.setText("خطأ في قائمة الثقة: "+e.getMessage());}
        eventsView.setText(log.text());
    }
    private String selectedMac(){return MacPolicy.normalize(macField.getText().toString());}
    private void addMac(){try{
        String m=selectedMac();
        if(MacPolicy.locallyAdministered(m))new AlertDialog.Builder(this).setTitle("تنبيه MAC محلي")
           .setMessage("هذا العنوان محلي الإدارة وقد يكون عشوائياً. السماح به لا يثبت هوية الجهاز. هل اعتمدته يدوياً؟")
           .setPositiveButton("أعتمده",(d,w)->{store.add(m);log.add("اعتماد يدوي: "+m);updateLists();})
           .setNegativeButton("إلغاء",null).show();
        else {store.add(m);log.add("اعتماد يدوي: "+m);updateLists();}
    }catch(Exception e){error(e.getMessage());}}
    private void removeMac(){try{
        String m=selectedMac();new AlertDialog.Builder(this).setTitle("إزالة اعتماد MAC")
            .setMessage("إزالة "+m+" من القائمة المحلية تعني أن الحارس سيحذفه من قائمة الراوتر في الدورة التالية. تابع؟")
            .setPositiveButton("إزالة",(d,w)->{store.remove(m);log.add("سُحب اعتماد: "+m);updateLists();})
            .setNegativeButton("إلغاء",null).show();
    }catch(Exception e){error(e.getMessage());}}
    private String routerHost(){String ip=hostField.getText().toString().trim();new RouterClient(ip);store.setHost(ip);return ip;}
    private void requirePassword(){if(passwordField.getText().length()==0)throw new IllegalArgumentException("أدخل كلمة مرور إدارة الراوتر");}
    private void error(String s){new AlertDialog.Builder(this).setTitle("تنبيه").setMessage(s).setPositiveButton("موافق",null).show();}
    private void probeCompatibility() {
        try {
            if (GuardService.isRunning() || testingConnection)
                throw new IllegalStateException("أوقف المراقبة أو اختبار الدخول أولاً");
            String host=routerHost();
            statusView.setText("جارٍ فحص التهيئة دون إرسال كلمة مرور...");
            new Thread(() -> {
                try {
                    String result=new RouterClient(host).probeCompatibility();
                    ui.post(() -> {statusView.setText(result);log.add("تشخيص بلا كلمة مرور: " + result);updateLists();});
                } catch(Exception e) {
                    String message=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
                    ui.post(() -> {statusView.setText("فشل فحص البروتوكول دون تسجيل دخول");log.add("تشخيص بلا كلمة مرور: " + message);updateLists();});
                }
            },"AX12-NoLoginProbe").start();
        } catch(Exception e) {error(e.getMessage());}
    }
    private void testConnection(){
        try{
            if (testingConnection) throw new IllegalStateException("اختبار الدخول جارٍ بالفعل؛ لا تكرر الضغط");
            if(GuardService.isRunning())throw new IllegalStateException("أوقف المراقبة أولاً قبل إجراء اختبار اتصال جديد حتى لا تتعارض جلستا الإدارة");
            requirePassword();String host=routerHost(),pwd=passwordField.getText().toString();
            routerReadTestPassed=false;testedHost=null;testedPasswordFingerprint=null;
            statusView.setText("جاري اختبار الاتصال والقراءة فقط...");
            testingConnection=true;
            new Thread(()->{
                RouterClient client=null;
                try{
                    client=new RouterClient(this,host);
                    client.login(pwd);
                    String protocol=client.getProtocolName();
                    RouterClient.Settings s=client.readSettings();
                    Set<String> allow=client.readAllowed();
                    String fingerprint=passwordFingerprint(pwd);
                    ui.post(()->{routerReadTestPassed=true;testedHost=host;testedPasswordFingerprint=fingerprint;
                        statusView.setText("متصل عبر "+protocol+" / enabled="+s.enabled+" / mode="+s.mode+" / Allow List="+allow.size());
                        log.add("نجح اختبار القراءة عبر "+protocol+"؛ القائمة البيضاء: "+allow.size()+" عنوان");updateLists();});
                }catch(Exception e){
                    String message=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
                    ui.post(()->{routerReadTestPassed=false;testedHost=null;testedPasswordFingerprint=null;
                        statusView.setText("فشل اختبار التوافق/الاتصال؛ راجع سجل الأحداث");log.add("فشل اختبار الاتصال: "+message);updateLists();});
                }finally{
                    if(client!=null)client.close();
                    ui.post(()->testingConnection=false);
                }
            },"AX12-ReadTest").start();
        }catch(Exception e){error(e.getMessage());}
    }
    private void confirmAllow(){
        try {
            if(GuardService.isRunning())throw new IllegalStateException("أوقف المراقبة أولاً قبل إرسال MAC إلى الراوتر");
            requirePassword();String mac=selectedMac();
            if(!store.get().contains(mac))throw new IllegalArgumentException("اعتمد MAC محلياً قبل إرساله");
            String host=routerHost(),pass=passwordField.getText().toString();
            requireSuccessfulReadTest(host,pass);
            new AlertDialog.Builder(this).setTitle("موافقة صريحة لإضافة MAC")
              .setMessage("إضافة "+mac+" إلى القائمة البيضاء للراوتر؟ هذا إجراء إضافي اختياري.")
              .setPositiveButton("أضف الآن",(d,w)->new Thread(()->{
                  RouterClient client=null;
                  try{
                      client=new RouterClient(this,host);client.login(pass);client.allowObserved(mac);
                      ui.post(()->{log.add("أُرسل MAC المعتمد إلى الراوتر: "+mac);updateLists();});
                  }catch(Exception e){
                      String message=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
                      ui.post(()->error("تعذر إضافة MAC إلى الراوتر: "+message));
                  }finally{if(client!=null)client.close();}
              },"AX12-Approve").start())
              .setNegativeButton("إلغاء",null).show();
        }catch(Exception e){error(e.getMessage());}
    }
    private void requireSuccessfulReadTest(String host,String password){
        if(!routerReadTestPassed || !host.equals(testedHost) || !passwordFingerprint(password).equals(testedPasswordFingerprint))
            throw new IllegalStateException("يجب نجاح اختبار الاتصال والقراءة فقط بنفس عنوان الراوتر وكلمة المرور قبل أي تعديل أو تشغيل للمراقبة");
    }
    private static String passwordFingerprint(String value){
        try{
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder(digest.length*2);
            for(byte b:digest)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            return out.toString();
        }catch(Exception e){throw new IllegalStateException("تعذر التحقق من جلسة الاختبار",e);}
    }
    private void startGuard(){
        try {
            if(GuardService.isRunning())throw new IllegalStateException("المراقبة تعمل بالفعل");
            requirePassword();String host=routerHost();if(store.get().isEmpty())throw new IllegalStateException("أدخل عنوان MAC معتمداً واحداً على الأقل بما فيه جهاز الإدارة");
            String password=passwordField.getText().toString();
            requireSuccessfulReadTest(host,password);
            new AlertDialog.Builder(this).setTitle("تشغيل الحارس")
              .setMessage("يتطلب نجاح اختبار الاتصال والقراءة أولاً، وتفعيل Allow List مسبقاً في الراوتر. سيحذف الحارس عنواناً واحداً غير معتمد في كل دورة، وقد ينقطع اتصال الأجهزة غير المدرجة. تأكد أن MAC جهاز الإدارة ضمن قائمتك. إذا ظهر HTTP 403 فلا تشغّل الحارس.")
              .setPositiveButton("تشغيل",(d,w)->{
                  Intent i=new Intent(this,GuardService.class);i.setAction(GuardService.START);
                  i.putExtra(GuardService.EXTRA_HOST,host);i.putExtra(GuardService.EXTRA_PASS,password);
                  startForegroundService(i);passwordField.setText("");statusView.setText("طلب تشغيل الحارس أُرسل؛ تحقق من السجل");
              }).setNegativeButton("إلغاء",null).show();
        }catch(Exception e){error(e.getMessage());}
    }
    private void stopGuard(){Intent i=new Intent(this,GuardService.class);i.setAction(GuardService.STOP);startService(i);statusView.setText("طُلب إيقاف المراقبة");}
    @Override protected void onResume(){super.onResume();ui.removeCallbacks(refresh);ui.post(refresh);}
    @Override protected void onPause(){ui.removeCallbacks(refresh);super.onPause();}
}
