package com.ax12.strictguard;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.text.DateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** Arabic UI. Manual MAC approvals are independent of whatever the router auto-adds. */
public final class MainActivity extends Activity {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private EditText hostField,passwordField,macField;
    private TextView trustedView,eventsView,statusView,authStateView,inventorySummaryView,inventoryMetaView;
    private LinearLayout inventoryRows;
    private TrustedStore store;
    private GuardLog log;
    private boolean routerReadTestPassed=false;
    private boolean testingConnection=false;
    private boolean probingCompatibility=false;
    private boolean loadingInventory=false;
    private boolean modifyingRouter=false;
    private Map<String, String> lastRouterAllowed;
    private List<DeviceInventory.ConnectedDevice> lastConnected;
    private boolean lastSnapshotComplete;
    private String lastSnapshotHost;
    private long lastSnapshotTime;
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
        col.addView(text("الحارس المستقل للقائمة البيضاء – 0.1.6 device inventory / تجريبي",14,false));
        col.addView(text("لا يُضاف أي MAC تلقائياً إلى قائمة الثقة. عند تشغيل الحارس تُراجع قائمة الراوتر كل 15 ثانية وتُزال الإدخالات غير المعتمدة.",14,false));
        col.addView(text("مهم: لا يمكن منع أول اتصال إذا سمح الراوتر به قبل الفحص؛ ولا يمكن كشف انتحال MAC معتمد. لا تشغّل الحارس قبل نجاح اختبار القراءة فقط.",13,true));
        col.addView(text("عنوان الراوتر المحلي",16,true));
        hostField=field("192.168.0.1",InputType.TYPE_CLASS_PHONE);hostField.setText(store.getHost());col.addView(hostField);
        col.addView(text("كلمة مرور إدارة الراوتر (لن تُحفظ)",16,true));
        passwordField=field("كلمة مرور الإدارة",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD); col.addView(passwordField);
        col.addView(button("فحص بروتوكول الراوتر دون تسجيل الدخول",this::probeCompatibility));
        col.addView(button("1. اختبار الاتصال (قراءة فقط)",this::testConnection));
        col.addView(text("حالة المصادقة: المهلة المعروضة للتطبيق فقط، ولا تؤكد زمن الحظر في الراوتر.",13,true));
        authStateView=text("",13,false);col.addView(authStateView);
        col.addView(button("عرض وقت آخر رفض ومهلة التطبيق دون اتصال بالراوتر",this::showAuthStatus));
        col.addView(text("أضف عناوين MAC المعتمدة يدوياً",16,true));
        macField=field("AA:BB:CC:DD:EE:FF",InputType.TYPE_CLASS_TEXT);col.addView(macField);
        col.addView(button("إضافة MAC للقائمة المعتمدة محلياً",this::addMac));
        col.addView(button("حذف MAC من القائمة المعتمدة محلياً",this::removeMac));
        col.addView(button("نسخ قائمة الثقة للاحتفاظ بها قبل إعادة تثبيت APK",this::copyTrustedList));
        col.addView(button("استيراد قائمة MAC محفوظة من نص",this::importTrustedList));
        col.addView(button("إرسال MAC معتمد إلى الراوتر (بموافقة صريحة)",this::confirmAllow));
        trustedView=text("",15,false);col.addView(trustedView);
        col.addView(text("مقارنة قائمة السماح بالأجهزة المتصلة",19,true));
        col.addView(text("قراءة فقط: جميع عناوين السماح في الراوتر، والأجهزة التي رصدها الراوتر متصلة، ومدى اعتمادها في قائمتك المحلية. لا تعني قائمة السماح أن الجهاز متصل. التحديث يدوي ولا يحذف أو يضيف أجهزة. لا يعمل نظام السماح فعلياً إلا عندما يكون Access Control مفعلاً وفي وضع White List.",13,false));
        col.addView(button("تحديث مقارنة الأجهزة (قراءة فقط)",this::refreshInventory));
        inventoryMetaView=text("لم يتم جلب بيانات الراوتر بعد. نفّذ اختبار الاتصال أولاً ثم اضغط تحديث المقارنة.",13,false);
        col.addView(inventoryMetaView);
        inventorySummaryView=text("",15,true);col.addView(inventorySummaryView);
        inventoryRows=new LinearLayout(this);inventoryRows.setOrientation(LinearLayout.VERTICAL);
        col.addView(inventoryRows);
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
        if (authStateView != null) authStateView.setText(AuthAttemptGate.status(this));
    }
    private void copyTrustedList() {
        Set<String> macs = store.get();
        if (macs.isEmpty()) {error("القائمة المحلية فارغة، فلا توجد عناوين للنسخ.");return;}
        StringBuilder lines = new StringBuilder();
        for (String mac : macs) lines.append(mac).append('\n');
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {error("الحافظة غير متاحة");return;}
        clipboard.setPrimaryClip(ClipData.newPlainText("AX12 trusted MACs", lines.toString()));
        error("تم نسخ عناوين MAC فقط. الصقها في ملاحظة أو ملف نصي قبل حذف أي نسخة من التطبيق. لم تُنسخ كلمة المرور.");
    }

    private void importTrustedList() {
        EditText input = new EditText(this);
        input.setHint("الصق عنوان MAC واحداً في كل سطر");
        input.setSingleLine(false);
        input.setMinLines(5);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        new AlertDialog.Builder(this).setTitle("استيراد MAC محلي فقط")
            .setMessage("ستُضاف هذه العناوين إلى قائمة الثقة المحلية بعد التحقق من صيغتها. لن يتم إرسال أي شيء إلى الراوتر.")
            .setView(input).setPositiveButton("تحقق واستورد", (d, w) -> {
                try {
                    String all = input.getText().toString().trim();
                    if (all.isEmpty()) throw new IllegalArgumentException("لم تُدخل أي عنوان MAC");
                    String[] tokens = all.split("[,;\\s]+");
                    if (tokens.length > 128) throw new IllegalArgumentException("عدد العناوين كبير جداً");
                    List<String> list = new ArrayList<>();
                    for (String part : tokens) list.add(MacPolicy.normalize(part));
                    store.addAll(list);
                    log.add("استيراد يدوي محلي: " + list.size() + " عنوان MAC؛ دون اتصال بالراوتر");
                    updateLists();renderInventory();
                } catch (Exception e) {error("تعذر الاستيراد: " + e.getMessage());}
            }).setNegativeButton("إلغاء", null).show();
    }

    private void showAuthStatus() {
        String status = AuthAttemptGate.status(this);
        statusView.setText(status);
        updateLists();
    }
    private String selectedMac(){return MacPolicy.normalize(macField.getText().toString());}
    private void addMac(){try{
        String m=selectedMac();
        if(MacPolicy.locallyAdministered(m))new AlertDialog.Builder(this).setTitle("تنبيه MAC محلي")
           .setMessage("هذا العنوان محلي الإدارة وقد يكون عشوائياً. السماح به لا يثبت هوية الجهاز. هل اعتمدته يدوياً؟")
           .setPositiveButton("أعتمده",(d,w)->{store.add(m);log.add("اعتماد يدوي: "+m);updateLists();renderInventory();})
           .setNegativeButton("إلغاء",null).show();
        else {store.add(m);log.add("اعتماد يدوي: "+m);updateLists();renderInventory();}
    }catch(Exception e){error(e.getMessage());}}
    private void removeMac(){try{
        String m=selectedMac();new AlertDialog.Builder(this).setTitle("إزالة اعتماد MAC")
            .setMessage("إزالة "+m+" من القائمة المحلية تعني أن الحارس سيحذفه من قائمة الراوتر في الدورة التالية. تابع؟")
            .setPositiveButton("إزالة",(d,w)->{store.remove(m);log.add("سُحب اعتماد: "+m);updateLists();renderInventory();})
            .setNegativeButton("إلغاء",null).show();
    }catch(Exception e){error(e.getMessage());}}
    private String routerHost(){String ip=hostField.getText().toString().trim();new RouterClient(ip);store.setHost(ip);return ip;}
    private void requirePassword(){if(passwordField.getText().length()==0)throw new IllegalArgumentException("أدخل كلمة مرور إدارة الراوتر");}
    private void error(String s){new AlertDialog.Builder(this).setTitle("تنبيه").setMessage(s).setPositiveButton("موافق",null).show();}
    private void probeCompatibility() {
        try {
            if (GuardService.isRunning() || testingConnection || probingCompatibility || loadingInventory || modifyingRouter)
                throw new IllegalStateException("أوقف المراقبة أو أي فحص جارٍ أولاً");
            String host=routerHost();
            probingCompatibility = true;
            statusView.setText("جارٍ فحص التهيئة دون إرسال كلمة مرور...");
            new Thread(() -> {
                try {
                    String result=new RouterClient(host).probeCompatibility();
                    ui.post(() -> {statusView.setText(result);log.add("تشخيص بلا كلمة مرور: " + result);updateLists();});
                } catch(Exception e) {
                    String message=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
                    ui.post(() -> {statusView.setText("فشل فحص البروتوكول دون تسجيل دخول");log.add("تشخيص بلا كلمة مرور: " + message);updateLists();});
                } finally {
                    ui.post(() -> probingCompatibility = false);
                }
            },"AX12-NoLoginProbe").start();
        } catch(Exception e) {error(e.getMessage());}
    }
    private void testConnection(){
        try{
            if (testingConnection || probingCompatibility || loadingInventory || modifyingRouter) throw new IllegalStateException("اختبار أو تعديل آخر جارٍ بالفعل؛ لا تكرر الضغط");
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
            if(GuardService.isRunning() || testingConnection || probingCompatibility || loadingInventory || modifyingRouter)
                throw new IllegalStateException("أوقف المراقبة والفحوص الجارية قبل إرسال MAC إلى الراوتر");
            requirePassword();String mac=selectedMac();
            if(!store.get().contains(mac))throw new IllegalArgumentException("اعتمد MAC محلياً قبل إرساله");
            String host=routerHost(),pass=passwordField.getText().toString();
            requireSuccessfulReadTest(host,pass);
            new AlertDialog.Builder(this).setTitle("موافقة صريحة لإضافة MAC")
              .setMessage("إضافة "+mac+" إلى القائمة البيضاء للراوتر؟ هذا إجراء إضافي اختياري.")
              .setPositiveButton("أضف الآن",(d,w)->{
                  modifyingRouter=true;
                  new Thread(()->{
                  RouterClient client=null;
                  try{
                      client=new RouterClient(this,host);client.login(pass);client.allowObserved(mac);
                      ui.post(()->{log.add("أُرسل MAC المعتمد إلى الراوتر: "+mac);updateLists();});
                  }catch(Exception e){
                      String message=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
                      ui.post(()->error("تعذر إضافة MAC إلى الراوتر: "+message));
                  }finally{if(client!=null)client.close();ui.post(()->modifyingRouter=false);}
              },"AX12-Approve").start();
              })
              .setNegativeButton("إلغاء",null).show();
        }catch(Exception e){error(e.getMessage());}
    }
    /** Manually invoked: no timer, no automatic login retries, no router writes. */
    private void refreshInventory() {
        try {
            if (GuardService.isRunning() || testingConnection || probingCompatibility || loadingInventory || modifyingRouter)
                throw new IllegalStateException("أوقف المراقبة والفحوص أو التعديلات الجارية قبل تحديث المقارنة؛ الراوتر يسمح بجلسة إدارة واحدة");
            requirePassword();
            String host=routerHost(), password=passwordField.getText().toString();
            requireSuccessfulReadTest(host,password);
            loadingInventory=true;
            inventoryMetaView.setText("جارٍ قراءة قائمة السماح والأجهزة المتصلة من الراوتر (دون أي تعديل)...");
            new Thread(()->{
                RouterClient client=null;
                try {
                    client=new RouterClient(this,host);
                    client.login(password);
                    Map<String,String> allowed=client.readAllowedWithNames();
                    RouterClient.ConnectedSnapshot connected=client.readConnectedDevices();
                    // Validate all MACs and the comparison before publishing data.
                    DeviceInventory.compare(allowed, connected.devices, store.get());
                    ui.post(()->{
                        lastRouterAllowed=new LinkedHashMap<>(allowed);
                        lastConnected=new ArrayList<>(connected.devices);
                        lastSnapshotComplete=connected.completeSections;
                        lastSnapshotHost=host;
                        lastSnapshotTime=System.currentTimeMillis();
                        renderInventory();
                        log.add("تحديث يدوي للعرض فقط: قائمة السماح="+allowed.size()+"؛ الأجهزة المرصودة="+connected.devices.size());
                        updateLists();
                    });
                } catch (Exception e) {
                    // Do not display raw responses or decrypted/session data from the router.
                    ui.post(()->{
                        inventoryMetaView.setText("فشل تحديث المقارنة؛ البيانات السابقة، إن ظهرت، ليست حالة حية. راجع اختبار الاتصال وسجل الأحداث.");
                        log.add("تعذرت قراءة مقارنة الأجهزة؛ نوع الخطأ: "+e.getClass().getSimpleName());
                        updateLists();
                    });
                } finally {
                    if(client!=null)client.close();
                    ui.post(()->loadingInventory=false);
                }
            },"AX12-InventoryReadOnly").start();
        } catch (Exception e) {error(e.getMessage());}
    }

    private void renderInventory() {
        if (inventoryRows==null || inventorySummaryView==null || inventoryMetaView==null) return;
        inventoryRows.removeAllViews();
        if (lastRouterAllowed==null || lastConnected==null || lastSnapshotHost==null) {
            inventorySummaryView.setText("");
            return;
        }
        try {
            DeviceInventory.Report report=DeviceInventory.compare(lastRouterAllowed,lastConnected,store.get());
            String time=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT,Locale.getDefault())
                    .format(new Date(lastSnapshotTime));
            boolean sameHost=lastSnapshotHost.equals(hostField.getText().toString().trim());
            inventoryMetaView.setText("آخر قراءة: "+time+" | الراوتر: "+lastSnapshotHost
                    +"\nهذه لقطة وقت القراءة فقط وليست مراقبة آنية."
                    +(lastSnapshotComplete ? "" : "\nتنبيه: إحدى فئتي الاتصال (السلكية أو اللاسلكية) لم تظهر في الاستجابة؛ المقارنة قد تكون غير مكتملة.")
                    +(sameHost ? "" : "\nتنبيه: عنوان الراوتر في الحقل تغير منذ هذه القراءة؛ النتائج قد تخص راوترك السابق."));
            inventorySummaryView.setText("في قائمة السماح: "+report.allowedCount
                    +" | ظهر متصلاً في آخر قراءة: "+report.connectedCount
                    +"\nمسموح ومتصل: "+report.connectedAllowedCount
                    +" | متصل خارج السماح: "+report.connectedOutsideAllowCount
                    +"\nمسموح ولم يظهر متصلاً: "+report.allowedNotObservedCount
                    +" | مسموح وغير معتمد محلياً: "+report.allowedNotTrustedCount
                    +"\nمعتمد محلياً وغير موجود في سماح الراوتر: "+report.trustedNotAllowedCount);
            if(report.entries.isEmpty()) {
                inventoryRows.addView(text("لم يرجع الراوتر أي MAC في السماح أو قائمة الاتصال، والقائمة المحلية فارغة.",14,false));
            }
            for (DeviceInventory.Entry entry: report.entries) showInventoryEntry(entry);
        } catch (Exception ignored) {
            inventorySummaryView.setText("تعذر مقارنة البيانات بعد تغيير القائمة المحلية.");
        }
    }

    private void showInventoryEntry(DeviceInventory.Entry d) {
        boolean warning=(d.connected && !d.allowedByRouter) || (d.allowedByRouter && !d.trustedLocally);
        int background=warning ? Color.rgb(255,241,238)
                : (d.connected && d.allowedByRouter && d.trustedLocally ? Color.rgb(237,249,242) : Color.rgb(245,247,250));
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(background);
        shape.setCornerRadius(14);
        shape.setStroke(1, Color.rgb(219,227,231));
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        card.setPadding(15,11,15,11);
        card.setBackground(shape);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0,6,0,8);inventoryRows.addView(card,params);
        TextView heading=text(d.name.isEmpty() ? d.mac : d.name+"  |  "+d.mac,15,true);
        card.addView(heading);
        String state="السماح: "+(d.allowedByRouter ? "نعم" : "لا")
                +"  |  الاتصال: "+(d.connected ? "ظهر في آخر قراءة" : "لم يظهر في القراءة")
                +"  |  الاعتماد المحلي: "+(d.trustedLocally ? "نعم" : "لا");
        card.addView(text(state,13,false));
        if (d.connected) card.addView(text("IP: "+(d.ip.isEmpty() ? "غير متاح" : d.ip)
                +"  |  النوع: "+(d.medium.isEmpty() ? "غير محدد" : d.medium),12,false));
        if (d.connected && !d.allowedByRouter)
            card.addView(text("تنبيه: الجهاز ظهر متصلاً رغم عدم وجوده في قائمة السماح؛ راجع وضع Access Control والشبكات الأخرى.",12,true));
        else if (d.allowedByRouter && !d.trustedLocally)
            card.addView(text("تنبيه: هذا MAC مسموح في الراوتر لكنه غير معتمد في قائمة المشتركين المحلية.",12,true));
        else if (d.trustedLocally && !d.allowedByRouter)
            card.addView(text("هذا MAC معتمد محلياً لكنه غير مدرج في قائمة سماح الراوتر.",12,false));
        if (d.locallyAdministered)
            card.addView(text("ملاحظة: MAC محلي الإدارة وقد يكون عشوائياً؛ لا يمكن إثبات أصل MAC من العنوان وحده.",12,true));
    }

    private void requireSuccessfulReadTest(String host,String password){
        if(!routerReadTestPassed || !host.equals(testedHost) || !passwordFingerprint(password).equals(testedPasswordFingerprint))
            throw new IllegalStateException("يجب نجاح اختبار الاتصال والقراءة فقط بنفس عنوان الراوتر وكلمة المرور قبل تحديث المقارنة أو أي تعديل أو تشغيل للمراقبة");
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
            if(GuardService.isRunning() || testingConnection || probingCompatibility || loadingInventory || modifyingRouter)
                throw new IllegalStateException("المراقبة أو اختبار اتصال/تعديل آخر قيد التنفيذ");
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
