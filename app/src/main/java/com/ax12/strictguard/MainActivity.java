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
import android.widget.CheckBox;
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
    private CheckBox persistCredentialsCheck;
    private TextView trustedView,eventsView,statusView,authStateView,inventorySummaryView,inventoryMetaView;
    private LinearLayout inventoryRows;
    private TextView selectionMetaView;
    private Button applySelectionButton;
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
    private String lastProtectedHost;
    private boolean lastAccessControlEnabled;
    private String lastAccessMode;
    private Set<String> stagedTrustedMacs;
    private boolean selectionEdited = false;
    private boolean selectionBusy = false;
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
        col.addView(text("إدارة قائمة السماح بالتحديد والمزامنة الصريحة – 0.1.9",14,false));
        col.addView(text("لا يُضاف أي MAC تلقائياً إلى قائمة الثقة. عند تشغيل الحارس تُراجع قائمة الراوتر كل 15 ثانية وتُزال الإدخالات غير المعتمدة.",14,false));
        col.addView(text("تُرفض عناوين MAC المحلية الإدارة (Private/Randomized) من قائمة الثقة. قبول MAC عالمي الإدارة لا يثبت أنه أصلي ولا يمنع انتحاله. لا يمكن منع أول اتصال إذا سمح الراوتر به قبل الفحص. لا تشغّل الحارس قبل نجاح اختبار القراءة فقط.",13,true));
        col.addView(text("عنوان الراوتر المحلي",16,true));
        hostField=field("192.168.0.1",InputType.TYPE_CLASS_PHONE);hostField.setText(store.getHost());col.addView(hostField);
        col.addView(text("كلمة مرور إدارة الراوتر",16,true));
        passwordField=field("كلمة مرور الإدارة",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD); col.addView(passwordField);
        persistCredentialsCheck = new CheckBox(this);
        persistCredentialsCheck.setText("السماح باستعادة المراقبة إذا أوقف Android عملية التطبيق. تُحفظ كلمة المرور مشفرة بمفتاح Android Keystore غير قابل للتصدير؛ لا تُحفظ كنص صريح.");
        persistCredentialsCheck.setChecked(CredentialVault.hasSavedCredential(this));
        col.addView(persistCredentialsCheck);
        col.addView(button("حذف كلمة المرور المشفرة المحفوظة", this::clearSavedCredential));
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
        col.addView(text("تعرض هذه القائمة عناوين السماح في الراوتر والأجهزة التي رصدها متصلة واعتمادها في التطبيق. حدد الأجهزة المسموح لها، ثم اضغط زر تطبيق التحديدات بعد مراجعة الملخص. لا تعني قائمة السماح أن الجهاز متصل، ويجب أن يكون Access Control مفعلاً في وضع White List.",13,false));
        col.addView(button("تحديث مقارنة الأجهزة (قراءة فقط)",this::refreshInventory));
        inventoryMetaView=text("لم يتم جلب بيانات الراوتر بعد. نفّذ اختبار الاتصال أولاً ثم اضغط تحديث المقارنة.",13,false);
        col.addView(inventoryMetaView);
        inventorySummaryView=text("",15,true);col.addView(inventorySummaryView);
        inventoryRows=new LinearLayout(this);inventoryRows.setOrientation(LinearLayout.VERTICAL);
        col.addView(inventoryRows);
        selectionMetaView=text("بعد تحديث المقارنة، حدد الأجهزة المسموح لها ثم طبّق الاختيارات على الراوتر.",13,false);
        col.addView(selectionMetaView);
        applySelectionButton=button("تطبيق التحديدات على قائمة سماح الراوتر",this::applySelectionToRouter);
        applySelectionButton.setEnabled(false);
        col.addView(applySelectionButton);
        col.addView(button("التراجع عن التحديدات غير المحفوظة",this::discardSelectionChanges));
        col.addView(text("التحديد لا يغيّر الراوتر فوراً. زر التطبيق ينفذ المزامنة بعد تأكيدك، ويضيف الأجهزة المحددة أولاً ثم يزيل غير المحددة، مع حماية MAC جهاز الإدارة.",12,false));
        col.addView(button("2. تشغيل المراقبة الصارمة",this::startGuard));
        col.addView(button("إيقاف المراقبة",this::stopGuard));
        statusView=text("الحالة: غير متصلة",14,true);col.addView(statusView);
        col.addView(text("سجل الأحداث",17,true));
        eventsView=text("",13,false);col.addView(eventsView);
        setContentView(scroll);updateLists();
    }
    private void updateLists(){
        try {
            List<String> stored = store.getStoredForReview();
            int rejected = 0;
            StringBuilder b = new StringBuilder("عناوين MAC المحفوظة (" + stored.size() + "):\n");
            for (String mac : stored) {
                boolean local = MacPolicy.locallyAdministered(mac);
                if (local) rejected++;
                b.append("• ").append(mac)
                        .append(local ? " — مرفوض: MAC محلي الإدارة؛ احذفه من القائمة" : " — صالح للاعتماد")
                        .append('\n');
            }
            if (rejected > 0) b.append("\nتنبيه: توجد ").append(rejected)
                    .append(" عناوين Private/Randomized قديمة. لن يبدأ الحارس حتى حذفها.");
            trustedView.setText(b.toString());
        } catch(Exception e){trustedView.setText("خطأ في قائمة الثقة: "+e.getMessage());}
        eventsView.setText(log.text());
        if (authStateView != null) authStateView.setText(AuthAttemptGate.status(this));
        updateSelectionControls();
    }
    private void copyTrustedList() {
        Set<String> macs = new java.util.LinkedHashSet<>(store.getStoredForReview());
        if (macs.isEmpty()) {error("القائمة المحلية فارغة، فلا توجد عناوين للنسخ.");return;}
        StringBuilder lines = new StringBuilder();
        for (String mac : macs) lines.append(mac).append('\n');
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {error("الحافظة غير متاحة");return;}
        clipboard.setPrimaryClip(ClipData.newPlainText("AX12 trusted MACs", lines.toString()));
        error("تم نسخ عناوين MAC فقط. الصقها في ملاحظة أو ملف نصي قبل حذف أي نسخة من التطبيق. لم تُنسخ كلمة المرور.");
    }

    private void importTrustedList() {
        if (selectionEdited) { error("طبّق تحديدات الأجهزة أو تراجع عنها أولاً قبل الاستيراد."); return; }
        EditText input = new EditText(this);
        input.setHint("الصق عنوان MAC واحداً في كل سطر");
        input.setSingleLine(false);
        input.setMinLines(5);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        new AlertDialog.Builder(this).setTitle("استيراد MAC محلي فقط")
            .setMessage("ستُضاف فقط عناوين MAC العالمية الإدارة (UAA). ستُرفض عناوين Private/Randomized المحلية الإدارة. لن يتم إرسال أي شيء إلى الراوتر.")
            .setView(input).setPositiveButton("تحقق واستورد", (d, w) -> {
                try {
                    String all = input.getText().toString().trim();
                    if (all.isEmpty()) throw new IllegalArgumentException("لم تُدخل أي عنوان MAC");
                    String[] tokens = all.split("[,;\\s]+");
                    if (tokens.length > 128) throw new IllegalArgumentException("عدد العناوين كبير جداً");
                    List<String> list = new ArrayList<>();
                    for (String part : tokens) list.add(MacPolicy.normalizeTrusted(part));
                    store.addAll(list);
                    stagedTrustedMacs = null;
                    selectionEdited = false;
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
    private String selectedTrustedMac(){return MacPolicy.normalizeTrusted(macField.getText().toString());}
    private void addMac(){try{
        if (selectionEdited) throw new IllegalStateException("طبّق تحديدات الأجهزة أو تراجع عنها أولاً قبل تعديل القائمة يدوياً.");
        String mac=selectedTrustedMac();
        store.add(mac);
        stagedTrustedMacs = null;
        selectionEdited = false;
        log.add("اعتماد MAC عالمي الإدارة يدوياً: "+mac);
        updateLists();renderInventory();
    }catch(Exception e){error(e.getMessage());}}
    private void removeMac(){try{
        if (selectionEdited) throw new IllegalStateException("طبّق تحديدات الأجهزة أو تراجع عنها أولاً قبل تعديل القائمة يدوياً.");
        String m=selectedMac();
        if (isProtectedHost(m)) throw new IllegalArgumentException("لا يمكن سحب اعتماد MAC جهاز إدارة الراوتر.");
        new AlertDialog.Builder(this).setTitle("إزالة اعتماد MAC")
            .setMessage("إزالة "+m+" من القائمة المحلية تعني أن الحارس سيحذفه من قائمة الراوتر في الدورة التالية. تابع؟")
            .setPositiveButton("إزالة",(d,w)->{store.remove(m);stagedTrustedMacs=null;selectionEdited=false;log.add("سُحب اعتماد: "+m);updateLists();renderInventory();})
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
            if (selectionEdited) throw new IllegalStateException("طبّق تحديدات الأجهزة أو تراجع عنها أولاً.");
            if(GuardService.isRunning() || testingConnection || probingCompatibility || loadingInventory || modifyingRouter)
                throw new IllegalStateException("أوقف المراقبة والفحوص الجارية قبل إرسال MAC إلى الراوتر");
            requirePassword();String mac=selectedTrustedMac();
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
            if (GuardService.isRunning() || testingConnection || probingCompatibility || loadingInventory || modifyingRouter || selectionBusy)
                throw new IllegalStateException("أوقف المراقبة والفحوص أو التعديلات الجارية قبل تحديث المقارنة؛ الراوتر يسمح بجلسة إدارة واحدة");
            if (selectionEdited)
                throw new IllegalStateException("لديك تحديدات غير محفوظة. طبّقها أو تراجع عنها قبل تحديث المقارنة حتى لا تضيع اختياراتك.");
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
                    RouterClient.Settings settings=client.readSettings();
                    Map<String,String> allowed=client.readAllowedWithNames();
                    RouterClient.ConnectedSnapshot connected=client.readConnectedDevices();
                    // Validate all MACs and the comparison before publishing data.
                    DeviceInventory.compare(allowed, connected.devices, store.getValid());
                    ui.post(()->{
                        lastRouterAllowed=new LinkedHashMap<>(allowed);
                        lastConnected=new ArrayList<>(connected.devices);
                        lastSnapshotComplete=connected.completeSections;
                        lastSnapshotHost=host;
                        lastSnapshotTime=System.currentTimeMillis();
                        lastProtectedHost=settings.protectedHost;
                        lastAccessControlEnabled=settings.enabled;
                        lastAccessMode=settings.mode;
                        stagedTrustedMacs=new java.util.LinkedHashSet<>(store.getValid());
                        selectionEdited=false;
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
                    ui.post(()->{loadingInventory=false;updateSelectionControls();});
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
            if (stagedTrustedMacs == null)
                stagedTrustedMacs = new java.util.LinkedHashSet<>(store.getValid());
            DeviceInventory.Report report=DeviceInventory.compare(lastRouterAllowed,lastConnected,store.getValid());
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
            boolean protectedHostShown = false;
            for (DeviceInventory.Entry entry: report.entries) {
                showInventoryEntry(entry);
                if (isProtectedHost(entry.mac)) protectedHostShown = true;
            }
            if (!protectedHostShown) showProtectedHostEntry();
            updateSelectionControls();
        } catch (Exception ignored) {
            inventorySummaryView.setText("تعذر مقارنة البيانات بعد تغيير القائمة المحلية.");
        }
    }

    private void showInventoryEntry(DeviceInventory.Entry d) {
        boolean selected = stagedTrustedMacs != null && stagedTrustedMacs.contains(d.mac);
        boolean protectedDevice = isProtectedHost(d.mac);
        boolean warning=(d.connected && !d.allowedByRouter) || (d.allowedByRouter && !d.trustedLocally);
        int background=selected ? Color.rgb(237,249,242)
                : (warning ? Color.rgb(255,241,238) : Color.rgb(245,247,250));
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
        String state="السماح في الراوتر: "+(d.allowedByRouter ? "نعم" : "لا")
                +"  |  الاتصال: "+(d.connected ? "ظهر في آخر قراءة" : "لم يظهر في القراءة")
                +"  |  قائمة التطبيق: "+(selected ? "محدد" : "غير محدد");
        card.addView(text(state,13,false));
        if (d.connected) card.addView(text("IP: "+(d.ip.isEmpty() ? "غير متاح" : d.ip)
                +"  |  النوع: "+(d.medium.isEmpty() ? "غير محدد" : d.medium),12,false));
        if (d.connected && !d.allowedByRouter)
            card.addView(text("تنبيه: الجهاز ظهر متصلاً رغم عدم وجوده في قائمة السماح؛ راجع وضع Access Control والشبكات الأخرى.",12,true));
        else if (d.allowedByRouter && !selected)
            card.addView(text("تنبيه: هذا MAC مسموح في الراوتر لكنه غير محدد في قائمة التطبيق؛ سيُزال عند تطبيق الاختيارات.",12,true));
        else if (selected && !d.allowedByRouter)
            card.addView(text("هذا MAC محدد في التطبيق لكنه غير مدرج حالياً في قائمة سماح الراوتر؛ سيُضاف إذا أتاحه Firmware.",12,false));
        if (d.locallyAdministered)
            card.addView(text("مرفوض للاعتماد: هذا MAC محلي الإدارة (Private/Randomized محتمل). لا يمكن إثبات أصالة MAC العالمي من العنوان وحده.",12,true));

        CheckBox choice = new CheckBox(this);
        choice.setText(d.locallyAdministered
                ? "غير قابل للاعتماد — MAC محلي الإدارة"
                : (protectedDevice ? "جهاز إدارة الراوتر — يجب إبقاؤه مسموحاً" : "السماح لهذا الجهاز بالاتصال"));
        choice.setChecked(selected);
        boolean canToggle = !d.locallyAdministered && (!protectedDevice || !selected);
        choice.setEnabled(canToggle && !selectionBusy);
        choice.setOnCheckedChangeListener((buttonView, checked) ->
                handleSelectionChange(d.mac, checked, choice, protectedDevice));
        card.addView(choice);
    }

    private void showProtectedHostEntry() {
        String host = normalizedProtectedHost();
        if (host == null || inventoryRows == null) return;
        boolean selected = stagedTrustedMacs != null && stagedTrustedMacs.contains(host);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        card.setPadding(15, 11, 15, 11);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(selected ? Color.rgb(237,249,242) : Color.rgb(255,248,225));
        shape.setCornerRadius(14);
        shape.setStroke(1, Color.rgb(219,227,231));
        card.setBackground(shape);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0,6,0,8);
        inventoryRows.addView(card, params);
        card.addView(text("جهاز إدارة الراوتر | " + host, 15, true));
        card.addView(text("لم يظهر هذا العنوان ضمن لقطة الأجهزة أو قائمة السماح، لكنه معلن من إعدادات الراوتر بوصفه جهاز الإدارة المحمي.", 12, false));
        CheckBox choice = new CheckBox(this);
        choice.setText("إبقاء جهاز إدارة الراوتر ضمن قائمة السماح");
        choice.setChecked(selected);
        choice.setEnabled(!selected && !selectionBusy);
        choice.setOnCheckedChangeListener((buttonView, checked) ->
                handleSelectionChange(host, checked, choice, true));
        card.addView(choice);
    }

    private String normalizedProtectedHost() {
        try { return MacPolicy.normalizeTrusted(lastProtectedHost); }
        catch (Exception ignored) { return null; }
    }

    private boolean isProtectedHost(String mac) {
        String protectedMac = normalizedProtectedHost();
        if (protectedMac == null) return false;
        try { return protectedMac.equals(MacPolicy.normalize(mac)); }
        catch (Exception ignored) { return false; }
    }

    private void handleSelectionChange(String mac, boolean checked, CheckBox checkbox,
                                       boolean protectedDevice) {
        if (stagedTrustedMacs == null)
            stagedTrustedMacs = new java.util.LinkedHashSet<>(store.getValid());
        if (selectionBusy) {
            checkbox.setOnCheckedChangeListener(null);
            checkbox.setChecked(stagedTrustedMacs.contains(mac));
            checkbox.setOnCheckedChangeListener((buttonView, value) ->
                    handleSelectionChange(mac, value, checkbox, protectedDevice));
            return;
        }
        try {
            String normalized = checked ? MacPolicy.normalizeTrusted(mac) : MacPolicy.normalize(mac);
            if (checked) {
                stagedTrustedMacs.add(normalized);
                if (protectedDevice) checkbox.setEnabled(false);
            } else {
                if (protectedDevice) {
                    checkbox.setOnCheckedChangeListener(null);
                    checkbox.setChecked(true);
                    checkbox.setEnabled(false);
                    checkbox.setOnCheckedChangeListener((buttonView, value) ->
                            handleSelectionChange(mac, value, checkbox, protectedDevice));
                    error("لا يمكن إلغاء تحديد MAC جهاز إدارة الراوتر؛ يجب أن يبقى مسموحاً حتى لا تفقد الوصول إلى الإدارة.");
                    return;
                }
                stagedTrustedMacs.remove(normalized);
            }
            selectionEdited = !stagedTrustedMacs.equals(store.getValid());
            updateSelectionControls();
        } catch (Exception e) {
            checkbox.setOnCheckedChangeListener(null);
            checkbox.setChecked(false);
            checkbox.setEnabled(false);
            checkbox.setOnCheckedChangeListener((buttonView, value) ->
                    handleSelectionChange(mac, value, checkbox, protectedDevice));
            error(e.getMessage());
        }
    }

    private void updateSelectionControls() {
        if (selectionMetaView == null || applySelectionButton == null) return;
        try {
            if (stagedTrustedMacs == null)
                stagedTrustedMacs = new java.util.LinkedHashSet<>(store.getValid());
            Set<String> saved = store.getValid();
            Set<String> selected = new java.util.LinkedHashSet<>(stagedTrustedMacs);
            Set<String> router = lastRouterAllowed == null
                    ? new java.util.LinkedHashSet<>()
                    : new java.util.LinkedHashSet<>(lastRouterAllowed.keySet());

            Set<String> addedLocal = new java.util.LinkedHashSet<>(selected);
            addedLocal.removeAll(saved);
            Set<String> removedLocal = new java.util.LinkedHashSet<>(saved);
            removedLocal.removeAll(selected);
            Set<String> addRouter = new java.util.LinkedHashSet<>(selected);
            addRouter.removeAll(router);
            Set<String> removeRouter = new java.util.LinkedHashSet<>(router);
            removeRouter.removeAll(selected);

            boolean sameHost = lastSnapshotHost != null
                    && lastSnapshotHost.equals(hostField.getText().toString().trim());
            boolean modeReady = lastAccessControlEnabled && "white".equalsIgnoreCase(lastAccessMode);
            boolean snapshotReady = lastRouterAllowed != null && sameHost && lastSnapshotComplete
                    && normalizedProtectedHost() != null && modeReady;
            boolean pending = !selected.equals(saved) || !selected.equals(router);

            String status = "المحدد في التطبيق: " + selected.size()
                    + " | إضافة إلى الراوتر: " + addRouter.size()
                    + " | إزالة من الراوتر: " + removeRouter.size()
                    + "\nتغييرات محلية غير محفوظة: +" + addedLocal.size()
                    + " / -" + removedLocal.size();
            if (!sameHost) status += "\nحدّث المقارنة بعد التأكد من عنوان الراوتر الحالي.";
            else if (!lastSnapshotComplete) status += "\nالمقارنة غير مكتملة؛ لن نسمح بالمزامنة حتى تظهر الأقسام السلكية واللاسلكية.";
            else if (!lastAccessControlEnabled) status += "\nAccess Control غير مفعّل في الراوتر؛ فعّله يدوياً.";
            else if (!"white".equalsIgnoreCase(lastAccessMode)) status += "\nالراوتر ليس في وضع White List؛ غيّر الوضع يدوياً.";
            else if (normalizedProtectedHost() == null) status += "\nتعذر التحقق من MAC جهاز إدارة الراوتر.";
            else if (!selected.contains(normalizedProtectedHost())) status += "\nيجب تحديد MAC جهاز إدارة الراوتر.";
            else if (!routerReadTestPassed) status += "\nأجرِ اختبار الاتصال الناجح قبل تطبيق التغييرات.";
            else if (GuardService.isRunning()) status += "\nأوقف الحارس مؤقتاً قبل تنفيذ مزامنة يدوية.";
            else if (selectionBusy) status += "\nالمزامنة قيد التنفيذ.";

            selectionMetaView.setText(status);
            boolean ready = pending && snapshotReady && selected.contains(normalizedProtectedHost())
                    && routerReadTestPassed && !GuardService.isRunning()
                    && !testingConnection && !probingCompatibility && !loadingInventory
                    && !modifyingRouter && !selectionBusy;
            applySelectionButton.setEnabled(ready);
        } catch (Exception e) {
            selectionMetaView.setText("تعذر إعداد المزامنة: " + e.getMessage());
            applySelectionButton.setEnabled(false);
        }
    }

    private void discardSelectionChanges() {
        if (selectionBusy || modifyingRouter) {
            error("انتظر انتهاء عملية المزامنة أولاً.");
            return;
        }
        stagedTrustedMacs = new java.util.LinkedHashSet<>(store.getValid());
        selectionEdited = false;
        renderInventory();
        updateSelectionControls();
    }

    private void applySelectionToRouter() {
        try {
            if (GuardService.isRunning() || testingConnection || probingCompatibility
                    || loadingInventory || modifyingRouter || selectionBusy)
                throw new IllegalStateException("أوقف الحارس وأي فحص أو تعديل آخر قبل المزامنة.");
            if (lastRouterAllowed == null || lastSnapshotHost == null
                    || !lastSnapshotHost.equals(hostField.getText().toString().trim()))
                throw new IllegalStateException("حدّث مقارنة الأجهزة للراوتر الحالي أولاً.");
            if (!lastSnapshotComplete)
                throw new IllegalStateException("لقطة الأجهزة غير مكتملة؛ أعد تحديث المقارنة قبل المزامنة.");
            if (!lastAccessControlEnabled || !"white".equalsIgnoreCase(lastAccessMode))
                throw new IllegalStateException("يجب تفعيل Access Control واختيار White List يدوياً في الراوتر. التطبيق لن يغير هذين الإعدادين.");
            requirePassword();
            String host = routerHost();
            String password = passwordField.getText().toString();
            requireSuccessfulReadTest(host, password);

            Set<String> desired = MacPolicy.canonicalTrusted(stagedTrustedMacs);
            AllowListSyncPlan plan = AllowListSyncPlan.create(desired,
                    lastRouterAllowed.keySet(), lastProtectedHost);
            String summary = "سيتم تنفيذ التغييرات التالية على Allow List فقط:\n"
                    + "إضافة أجهزة محددة: " + plan.toAdd.size() + "\n"
                    + "إزالة أجهزة غير محددة: " + plan.toRemove.size() + "\n\n"
                    + "لن تتغير إعدادات Wi-Fi أو DHCP أو Blacklist أو الرقابة الأبوية.\n"
                    + "يجب إبقاء MAC جهاز الإدارة محدداً. الإضافة تتطلب أن يكون الجهاز ظاهراً ضمن قائمة الأجهزة التي يتيحها Firmware.\n\n"
                    + "هل تريد المتابعة؟";
            new AlertDialog.Builder(this).setTitle("تأكيد مزامنة قائمة السماح")
                    .setMessage(summary)
                    .setPositiveButton("تطبيق التغييرات", (dialog, which) -> {
                        selectionBusy = true;
                        modifyingRouter = true;
                        updateSelectionControls();
                        new Thread(() -> {
                            RouterClient client = null;
                            try {
                                client = new RouterClient(this, host);
                                client.login(password);
                                Map<String, String> updated = client.synchronizeAllowList(desired, lastProtectedHost);
                                store.replaceAll(desired);
                                ui.post(() -> {
                                    lastRouterAllowed = new LinkedHashMap<>(updated);
                                    stagedTrustedMacs = new java.util.LinkedHashSet<>(desired);
                                    selectionEdited = false;
                                    selectionBusy = false;
                                    modifyingRouter = false;
                                    renderInventory();
                                    updateLists();
                                    log.add("اكتملت مزامنة Allow List: أضيف " + plan.toAdd.size()
                                            + " وأزيل " + plan.toRemove.size() + "؛ القائمة المحلية والراوتر متطابقتان.");
                                    error("نجحت المزامنة وتأكد التطبيق من تطابق قائمة السماح في الراوتر مع الأجهزة المحددة.");
                                });
                            } catch (Exception e) {
                                String message = e.getMessage() == null
                                        ? e.getClass().getSimpleName() : AuthErrorLabel.safeValue(e.getMessage());
                                ui.post(() -> {
                                    routerReadTestPassed = false;
                                    testedHost = null;
                                    testedPasswordFingerprint = null;
                                    selectionBusy = false;
                                    modifyingRouter = false;
                                    inventoryMetaView.setText("لم تكتمل المزامنة أو تعذر تأكيدها. قد تكون بعض التغييرات وصلت إلى الراوتر؛ لا تشغّل الحارس قبل إعادة اختبار الاتصال وتحديث المقارنة.");
                                    log.add("فشلت/لم تكتمل مزامنة قائمة السماح: " + message);
                                    renderInventory();
                                    updateLists();
                                    error("لم يعلن التطبيق نجاح المزامنة:\n" + message
                                            + "\nأعد اختبار الاتصال ثم حدّث المقارنة قبل أي محاولة أخرى.");
                                });
                            } finally {
                                if (client != null) client.close();
                            }
                        }, "AX12-AllowListSync").start();
                    })
                    .setNegativeButton("إلغاء", null).show();
        } catch (Exception e) {
            error(e.getMessage());
        }
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
            if(GuardService.isRunning() || testingConnection || probingCompatibility || loadingInventory || modifyingRouter || selectionBusy)
                throw new IllegalStateException("المراقبة أو اختبار اتصال/تعديل آخر قيد التنفيذ");
            if (selectionEdited)
                throw new IllegalStateException("لديك تحديدات لم تُطبّق بعد. طبّقها على الراوتر أو تراجع عنها قبل تشغيل الحارس.");
            if (lastRouterAllowed != null && lastSnapshotHost != null
                    && lastSnapshotHost.equals(hostField.getText().toString().trim())
                    && !new java.util.LinkedHashSet<>(store.getValid()).equals(new java.util.LinkedHashSet<>(lastRouterAllowed.keySet())))
                throw new IllegalStateException("قائمة الثقة وقائمة سماح الراوتر غير متطابقتين حسب آخر قراءة. حدّث المقارنة وطبّق المزامنة قبل تشغيل الحارس.");
            requirePassword();String host=routerHost();if(store.get().isEmpty())throw new IllegalStateException("أدخل عنوان MAC معتمداً واحداً على الأقل بما فيه جهاز الإدارة");
            String password=passwordField.getText().toString();
            requireSuccessfulReadTest(host,password);
            boolean persistent = persistCredentialsCheck != null && persistCredentialsCheck.isChecked();
            String persistenceNote = persistent
                    ? "\n\nوافقت على حفظ كلمة مرور الإدارة مشفرة في Android Keystore لاستعادة الخدمة إذا أوقف Android عملية التطبيق. لن تُحفظ كنص صريح، وستُمسح عند إيقاف الحارس أو انتهاء جلسة الحماية."
                    : "\n\nلم تختر حفظ كلمة المرور. ستواصل خدمة Foreground العمل عند إغلاق واجهة التطبيق ما دام Android يبقي الخدمة حية، لكن لن يمكن استعادتها تلقائياً إذا أنهى النظام عملية الخدمة.";
            new AlertDialog.Builder(this).setTitle("تشغيل الحارس")
              .setMessage("يتطلب نجاح اختبار الاتصال والقراءة أولاً، وتفعيل Allow List مسبقاً في الراوتر. سيحذف الحارس عنواناً واحداً غير معتمد في كل دورة، وقد ينقطع اتصال الأجهزة غير المدرجة. تأكد أن MAC جهاز الإدارة ضمن قائمتك. إذا ظهر HTTP 403 فلا تشغّل الحارس.\n\nلا يمكن منع انتحال MAC معتمد اعتماداً على عنوان MAC وحده." + persistenceNote)
              .setPositiveButton("تشغيل",(d,w)->{
                  Intent i=new Intent(this,GuardService.class);i.setAction(GuardService.START);
                  i.putExtra(GuardService.EXTRA_HOST,host);i.putExtra(GuardService.EXTRA_PASS,password);
                  i.putExtra(GuardService.EXTRA_PERSISTENT,persistent);
                  startForegroundService(i);passwordField.setText("");statusView.setText("طلب تشغيل الحارس أُرسل؛ تحقق من السجل");
              }).setNegativeButton("إلغاء",null).show();
        }catch(Exception e){error(e.getMessage());}
    }
    private void stopGuard(){
        CredentialVault.clear(this);
        Intent i=new Intent(this,GuardService.class);i.setAction(GuardService.STOP);startService(i);
        if (persistCredentialsCheck != null) persistCredentialsCheck.setChecked(false);
        statusView.setText("طُلب إيقاف المراقبة وحذف بيانات الاستعادة المشفرة");
    }

    private void clearSavedCredential() {
        if (GuardService.isRunning()) {
            error("أوقف المراقبة أولاً؛ لا يمكن حذف بيانات الاستعادة بينما الخدمة تستخدم جلسة الإدارة.");
            return;
        }
        new AlertDialog.Builder(this).setTitle("حذف كلمة المرور المحفوظة")
                .setMessage("سيتم حذف النص المشفر ومفتاحه من Android Keystore. لن يستطيع التطبيق استعادة المراقبة تلقائياً بعد ذلك.")
                .setPositiveButton("حذف", (dialog, which) -> {
                    CredentialVault.clear(this);
                    if (persistCredentialsCheck != null) persistCredentialsCheck.setChecked(false);
                    log.add("حُذفت كلمة مرور الاستعادة المشفرة ومفتاح Android Keystore");
                    updateLists();
                })
                .setNegativeButton("إلغاء", null).show();
    }
    @Override protected void onResume(){super.onResume();ui.removeCallbacks(refresh);ui.post(refresh);}
    @Override protected void onPause(){ui.removeCallbacks(refresh);super.onPause();}
}
