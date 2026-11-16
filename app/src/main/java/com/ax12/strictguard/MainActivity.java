package com.ax12.strictguard;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.content.res.ColorStateList;
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
import android.widget.FrameLayout;
import android.widget.ProgressBar;
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
    private TextView trustedView,eventsView,statusView,authStateView,inventorySummaryView,inventoryMetaView,parentalProbeView,deviceControlMetaView;
    private String lastParentalProbeReport = "";
    private LinearLayout inventoryRows;
    private TextView selectionMetaView;
    private FrameLayout pageHost;
    private View[] pages;
    private TextView[] navItems;
    private TextView dashboardGuardView, dashboardRouterView, dashboardSummaryView;
    private LinearLayout dashboardChart;
    private int activeTab = 0;
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
    private Map<String, DeviceControlStatus> lastDeviceControlStatuses = new LinkedHashMap<>();
    private long lastDeviceControlReadTime;
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(Color.rgb(245, 248, 252));

        pageHost = new FrameLayout(this);
        LinearLayout.LayoutParams hostParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(pageHost, hostParams);

        ScrollView dashboard = createScrollPage();
        ScrollView devices = createScrollPage();
        ScrollView protection = createScrollPage();
        ScrollView settings = createScrollPage();
        pages = new View[]{dashboard, devices, protection, settings};
        buildDashboardPage(pageContent(dashboard));
        buildDevicesPage(pageContent(devices));
        buildProtectionPage(pageContent(protection));
        buildSettingsPage(pageContent(settings));
        for (View page : pages) {
            pageHost.addView(page, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            page.setVisibility(View.GONE);
        }

        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        navigation.setGravity(Gravity.CENTER);
        navigation.setPadding(5, 5, 5, 7);
        navigation.setBackgroundColor(Color.WHITE);
        GradientDrawable navBg = new GradientDrawable();
        navBg.setColor(Color.WHITE);
        navBg.setStroke(1, Color.rgb(225, 232, 239));
        navigation.setBackground(navBg);
        String[] labels = {"⌂\nالرئيسية", "▦\nالأجهزة", "◈\nالحماية", "⚙\nالإعدادات"};
        navItems = new TextView[4];
        for (int i = 0; i < labels.length; i++) {
            final int tab = i;
            TextView item = new TextView(this);
            item.setText(labels[i]);
            item.setGravity(Gravity.CENTER);
            item.setTextSize(12);
            item.setTypeface(null, Typeface.BOLD);
            item.setPadding(2, 7, 2, 7);
            item.setClickable(true);
            item.setFocusable(true);
            item.setOnClickListener(v -> selectTab(tab));
            navItems[i] = item;
            navigation.addView(item, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        root.addView(navigation, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(root);
        selectTab(0);
        updateLists();
        renderInventory();
        updateDashboard();
    }

    private ScrollView createScrollPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        content.setPadding(18, 17, 18, 24);
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private LinearLayout pageContent(ScrollView page) {
        return (LinearLayout) page.getChildAt(0);
    }

    private void selectTab(int tab) {
        if (pages == null || navItems == null) return;
        activeTab = tab;
        for (int i = 0; i < pages.length; i++) {
            pages[i].setVisibility(i == tab ? View.VISIBLE : View.GONE);
            navItems[i].setTextColor(i == tab ? Color.rgb(15, 112, 151) : Color.rgb(105, 119, 133));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(i == tab ? Color.rgb(231, 244, 250) : Color.TRANSPARENT);
            bg.setCornerRadius(18);
            navItems[i].setBackground(bg);
        }
    }

    private void pageHeader(LinearLayout parent, String title, String subtitle) {
        TextView heading = text(title, 23, true);
        heading.setTextColor(Color.rgb(20, 91, 121));
        parent.addView(heading);
        TextView sub = text(subtitle, 13, false);
        sub.setTextColor(Color.rgb(105, 119, 133));
        parent.addView(sub);
        addGap(parent, 8);
    }

    private LinearLayout addCard(LinearLayout parent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        card.setPadding(15, 12, 15, 12);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Color.WHITE);
        shape.setCornerRadius(18);
        shape.setStroke(1, Color.rgb(226, 233, 239));
        card.setBackground(shape);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 5, 0, 10);
        parent.addView(card, params);
        return card;
    }

    private void addGap(LinearLayout parent, int height) {
        View gap = new View(this);
        parent.addView(gap, new LinearLayout.LayoutParams(1, height));
    }

    private void buildDashboardPage(LinearLayout col) {
        pageHeader(col, "AX12 STRICT GUARD", "مركز إدارة الشبكة والحماية");
        LinearLayout hero = addCard(col);
        TextView heroTitle = text("مركز مراقبة الشبكة", 18, true);
        heroTitle.setTextColor(Color.rgb(20, 91, 121));
        hero.addView(heroTitle);
        dashboardRouterView = text("حالة الراوتر: لم يُجرَ اختبار القراءة بعد", 13, true);
        hero.addView(dashboardRouterView);
        dashboardGuardView = text("الحارس الصارم: غير نشط", 13, true);
        hero.addView(dashboardGuardView);
        hero.addView(text("تعرض المؤشرات آخر بيانات قرأها التطبيق. لا تُعد هذه الأرقام قياساً آنياً ما لم يُجرَ تحديث ناجح.", 12, false));

        LinearLayout stats = addCard(col);
        stats.addView(text("ملخص الأجهزة", 17, true));
        dashboardSummaryView = text("لم تُقرأ بيانات الأجهزة بعد.", 13, false);
        stats.addView(dashboardSummaryView);

        LinearLayout chartCard = addCard(col);
        chartCard.addView(text("مؤشرات الشبكة", 17, true));
        dashboardChart = new LinearLayout(this);
        dashboardChart.setOrientation(LinearLayout.VERTICAL);
        chartCard.addView(dashboardChart);

        LinearLayout quick = addCard(col);
        quick.addView(text("الوصول السريع", 17, true));
        quick.addView(button("إدارة الأجهزة وقائمة الثقة", () -> selectTab(1)));
        quick.addView(button("الحماية والرقابة الأبوية", () -> selectTab(2)));
        quick.addView(button("إعداد الراوتر واختبار الاتصال", () -> selectTab(3)));
        quick.addView(button("تحديث مقارنة الأجهزة", this::refreshInventory));
        quick.addView(button("تشخيص الإنترنت والرقابة لكل جهاز", this::refreshDeviceControlDiagnostics));
    }

    private void buildDevicesPage(LinearLayout col) {
        pageHeader(col, "إدارة الأجهزة", "قائمة الثقة والأجهزة المرصودة ومزامنة السماح");
        LinearLayout trusted = addCard(col);
        trusted.addView(text("قائمة الثقة المحلية", 17, true));
        trusted.addView(text("لا يُضاف أي MAC تلقائياً. تُقبل عناوين MAC العالمية الإدارة فقط، وتُرفض العناوين المحلية الإدارة (Private/Randomized).", 12, false));
        macField=field("AA:BB:CC:DD:EE:FF",InputType.TYPE_CLASS_TEXT);trusted.addView(macField);
        trusted.addView(button("إضافة MAC للقائمة المعتمدة محلياً",this::addMac));
        trusted.addView(button("حذف MAC من القائمة المعتمدة محلياً",this::removeMac));
        trusted.addView(button("نسخ قائمة الثقة للاحتفاظ بها",this::copyTrustedList));
        trusted.addView(button("استيراد قائمة MAC محفوظة من نص",this::importTrustedList));
        trusted.addView(button("إرسال MAC معتمد إلى الراوتر (بموافقة صريحة)",this::confirmAllow));
        trustedView=text("",14,false);trusted.addView(trustedView);

        LinearLayout inventory = addCard(col);
        inventory.addView(text("مقارنة قائمة السماح بالأجهزة المتصلة",18,true));
        inventory.addView(text("تُعرض حالة قائمة السماح في الراوتر، والظهور في آخر قراءة، والاعتماد المحلي. هذه لقطة وقت القراءة وليست مراقبة آنية.",12,false));
        inventory.addView(button("تحديث مقارنة الأجهزة (قراءة فقط)",this::refreshInventory));
        inventoryMetaView=text("لم يتم جلب بيانات الراوتر بعد. نفّذ اختبار الاتصال أولاً.",12,false);
        inventory.addView(inventoryMetaView);
        inventorySummaryView=text("",13,true);inventory.addView(inventorySummaryView);
        inventoryRows=new LinearLayout(this);inventoryRows.setOrientation(LinearLayout.VERTICAL);
        inventory.addView(inventoryRows);
        selectionMetaView=text("بعد تحديث المقارنة، حدد الأجهزة المسموح لها ثم طبّق الاختيارات.",12,false);
        inventory.addView(selectionMetaView);
        applySelectionButton=button("تطبيق التحديدات على قائمة سماح الراوتر",this::applySelectionToRouter);
        applySelectionButton.setEnabled(false);inventory.addView(applySelectionButton);
        inventory.addView(button("التراجع عن التحديدات غير المحفوظة",this::discardSelectionChanges));
        inventory.addView(text("لا تتغير إعدادات Wi-Fi أو DHCP أو Blacklist أو الرقابة الأبوية عند مزامنة Allow List.",11,false));
    }

    private void buildProtectionPage(LinearLayout col) {
        pageHeader(col, "مركز الحماية", "المراقبة الصارمة وتشخيص الرقابة الأبوية وسجل الأحداث");
        LinearLayout guard = addCard(col);
        guard.addView(text("الحارس الصارم",18,true));
        guard.addView(text("يراجع الحارس قائمة السماح دورياً ويرصد ظهور الأجهزة واختفاءها. لا يمكن منع أول اتصال إذا سمح الراوتر به قبل الفحص، ولا يثبت MAC وحده هوية الجهاز.",12,false));
        guard.addView(button("تشغيل المراقبة الصارمة",this::startGuard));
        guard.addView(button("إيقاف المراقبة",this::stopGuard));
        statusView=text("الحالة: غير متصلة",14,true);guard.addView(statusView);
        authStateView=text("",12,false);guard.addView(authStateView);
        guard.addView(button("عرض وقت آخر رفض ومهلة التطبيق",this::showAuthStatus));

        LinearLayout parental = addCard(col);
        parental.addView(text("تشخيص الرقابة الأبوية",18,true));
        parental.addView(text("قراءة ملف Profile والجدول وحقول حجب الإنترنت التي يعيدها Firmware. البيانات المفقودة تُعرض كغير معروفة، ولا يُستنتج منها أن الإنترنت يعمل أو محجوب.",12,false));
        parental.addView(button("فحص دعم الرقابة الأبوية والجداول (قراءة فقط)",this::probeParentalControls));
        parentalProbeView=text("لم يُجرَ فحص واجهات الرقابة الأبوية بعد. أوقف المراقبة واختبر الاتصال أولاً.",12,false);
        parental.addView(parentalProbeView);
        parental.addView(button("نسخ تقرير فحص الرقابة الأبوية",this::copyParentalProbeReport));
        parental.addView(button("تشخيص الإنترنت والرقابة الأبوية لكل جهاز",this::refreshDeviceControlDiagnostics));
        deviceControlMetaView=text("لم يُجرَ تشخيص ملفات الرقابة لكل جهاز بعد. يتطلب نجاح اختبار الاتصال أولاً.",12,false);
        parental.addView(deviceControlMetaView);

        LinearLayout events = addCard(col);
        events.addView(text("سجل أحداث الحماية والاتصالات",17,true));
        eventsView=text("",12,false);events.addView(eventsView);
    }

    private void buildSettingsPage(LinearLayout col) {
        pageHeader(col, "إعدادات التطبيق والراوتر", "إعداد الاتصال والمصادقة والاستمرارية");
        LinearLayout router = addCard(col);
        router.addView(text("الاتصال بالراوتر",18,true));
        router.addView(text("عنوان الراوتر المحلي",14,true));
        hostField=field("192.168.0.1",InputType.TYPE_CLASS_PHONE);hostField.setText(store.getHost());router.addView(hostField);
        router.addView(text("كلمة مرور إدارة الراوتر",14,true));
        passwordField=field("كلمة مرور الإدارة",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);router.addView(passwordField);
        router.addView(button("فحص بروتوكول الراوتر دون تسجيل الدخول",this::probeCompatibility));
        router.addView(button("اختبار الاتصال (قراءة فقط)",this::testConnection));
        router.addView(text("يجب نجاح اختبار القراءة فقط بنفس عنوان الراوتر وكلمة المرور قبل تحديث المقارنة أو أي تعديل أو تشغيل للمراقبة.",12,false));

        LinearLayout persistence = addCard(col);
        persistence.addView(text("استمرارية الحارس",18,true));
        persistCredentialsCheck = new CheckBox(this);
        persistCredentialsCheck.setText("استعادة الحارس تلقائياً بعد إنهاء Android لخدمة التطبيق. تُحفظ كلمة المرور مشفرة في Android Keystore، وتُحذف عند إيقاف الحارس صراحة أو حذفها يدوياً.");
        persistCredentialsCheck.setChecked(CredentialVault.hasSavedCredential(this));
        persistence.addView(persistCredentialsCheck);
        persistence.addView(text("لأفضل استمرارية: اجعل استخدام البطارية غير مقيّد، واسمح بالتشغيل التلقائي إن توفر، ولا تستخدم Force Stop. قد يؤدي تسجيل الدخول من Chrome إلى إبطال جلسة التطبيق إذا كان Firmware يسمح بجلسة إدارة واحدة.",12,false));
        persistence.addView(button("فتح إعدادات التطبيق والبطارية", this::openAppSettings));
        persistence.addView(button("حذف كلمة المرور المشفرة المحفوظة", this::clearSavedCredential));
    }

    private void addDashboardBar(String label, int value, int max, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 6, 0, 6);
        TextView name = text(label, 12, false);
        name.setWidth(dp(112));
        row.addView(name);
        ProgressBar track = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        track.setMax(Math.max(1, max));
        track.setProgress(Math.max(0, Math.min(value, Math.max(1, max))));
        track.setProgressTintList(ColorStateList.valueOf(color));
        track.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(235, 240, 245)));
        LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(0, dp(9), 1f);
        trackParams.setMargins(5, 0, 5, 0);
        row.addView(track, trackParams);
        TextView number = text(String.valueOf(value), 12, true);
        number.setGravity(Gravity.CENTER);
        number.setWidth(dp(34));
        row.addView(number);
        dashboardChart.addView(row);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void updateDashboard() {
        if (dashboardGuardView == null || dashboardRouterView == null || dashboardSummaryView == null
                || dashboardChart == null || store == null) return;
        boolean running = GuardService.isRunning();
        dashboardGuardView.setText("الحارس الصارم: " + (running ? "يعمل" : "متوقف"));
        dashboardGuardView.setTextColor(running ? Color.rgb(34, 132, 83) : Color.rgb(105, 119, 133));
        dashboardRouterView.setText(routerReadTestPassed
                ? "حالة الراوتر: نجح آخر اختبار قراءة فقط"
                : "حالة الراوتر: لم يتم تأكيد الاتصال باختبار قراءة ناجح");
        int trusted = store.getValid().size();
        if (lastConnected == null || lastRouterAllowed == null) {
            dashboardSummaryView.setText("الأجهزة المتصلة: غير متاح | قائمة السماح: غير متاحة\nالأجهزة المعتمدة محلياً: " + trusted
                    + "\nنفّذ اختبار الاتصال ثم حدّث مقارنة الأجهزة للحصول على إحصائيات فعلية.");
            dashboardChart.removeAllViews();
            dashboardChart.addView(text("لا توجد لقطة أجهزة بعد. ستظهر الرسوم بعد قراءة ناجحة من الراوتر.", 12, false));
            return;
        }
        DeviceInventory.Report report = DeviceInventory.compare(lastRouterAllowed, lastConnected, store.getValid());
        int connected = report.connectedCount;
        int allowed = report.allowedCount;
        int outside = report.connectedOutsideAllowCount;
        int blocked = 0;
        int knownInternet = 0;
        for (DeviceControlStatus status : lastDeviceControlStatuses.values()) {
            if (status.internetBlocked != null) {
                knownInternet++;
                if (status.internetBlocked) blocked++;
            }
        }
        dashboardSummaryView.setText("الأجهزة التي ظهرت في آخر قراءة: " + connected
                + " | قائمة سماح الراوتر: " + allowed
                + "\nالاعتماد المحلي: " + trusted
                + " | متصل خارج السماح: " + outside
                + "\nسجلات الرقابة ذات حالة إنترنت معروفة: " + knownInternet
                + (lastDeviceControlReadTime > 0 ? " | محجوب حسب سجل الراوتر: " + blocked : " | تشخيص الإنترنت: لم يُنفذ بعد"));
        dashboardChart.removeAllViews();
        int max = Math.max(1, Math.max(Math.max(connected, allowed), Math.max(trusted, outside)));
        addDashboardBar("ظهر متصلاً", connected, max, Color.rgb(35, 139, 177));
        addDashboardBar("سماح الراوتر", allowed, max, Color.rgb(64, 155, 112));
        addDashboardBar("ثقة التطبيق", trusted, max, Color.rgb(111, 117, 194));
        addDashboardBar("خارج السماح", outside, max, Color.rgb(213, 125, 78));
        if (lastDeviceControlReadTime > 0) {
            int internetMax = Math.max(1, lastDeviceControlStatuses.size());
            addDashboardBar("محجوب حسب السجل", blocked, internetMax, Color.rgb(191, 91, 91));
        }
        dashboardChart.addView(text("ملاحظة: (محجوب حسب السجل) يعكس حقلاً أعاده الراوتر فقط، وليس اختباراً مستقلاً لوصول الإنترنت.", 11, false));
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
        updateDashboard();
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
    /** Runs a bounded, read-only probe of candidate Smart Network/Patrol forms. */
    private void probeParentalControls() {
        try {
            if (GuardService.isRunning() || testingConnection || probingCompatibility
                    || loadingInventory || modifyingRouter || selectionBusy)
                throw new IllegalStateException("أوقف المراقبة وأي اختبار أو تعديل آخر قبل فحص الرقابة الأبوية؛ الراوتر قد يسمح بجلسة إدارة واحدة فقط");
            requirePassword();
            String host = routerHost();
            String password = passwordField.getText().toString();
            requireSuccessfulReadTest(host, password);
            loadingInventory = true;
            parentalProbeView.setText("جارٍ فحص أربع واجهات مرشحة بطلبات read/load فقط؛ لن تُغيّر أي إعدادات...");
            new Thread(() -> {
                RouterClient client = null;
                try {
                    client = new RouterClient(this, host);
                    client.login(password);
                    RouterClient.ParentalControlReport report = client.probeParentalControlsReadOnly();
                    String formatted = report.toArabicText();
                    boolean sessionInvalid = report.sessionInvalid;
                    ui.post(() -> {
                        lastParentalProbeReport = formatted;
                        parentalProbeView.setText(formatted);
                        log.add(report.toLogSummary());
                        if (sessionInvalid) {
                            routerReadTestPassed = false;
                            testedHost = null;
                            testedPasswordFingerprint = null;
                        }
                        updateLists();
                    });
                } catch (Exception error) {
                    String message = error.getMessage() == null
                            ? error.getClass().getSimpleName() : AuthErrorLabel.safeValue(error.getMessage());
                    ui.post(() -> {
                        lastParentalProbeReport = "تعذر إكمال فحص الرقابة الأبوية: " + message
                                + "\nلم تُرسل أي طلبات كتابة أو تعديل. أعد اختبار الاتصال قبل محاولة جديدة.";
                        parentalProbeView.setText(lastParentalProbeReport);
                        log.add("تعذر فحص واجهات الرقابة الأبوية: " + message);
                        updateLists();
                    });
                } finally {
                    if (client != null) client.close();
                    ui.post(() -> { loadingInventory = false; updateSelectionControls(); });
                }
            }, "AX12-ParentalReadOnlyProbe").start();
        } catch (Exception error) {
            error(error.getMessage());
        }
    }

    private void copyParentalProbeReport() {
        if (lastParentalProbeReport == null || lastParentalProbeReport.trim().isEmpty()) {
            error("لا يوجد تقرير لنسخه؛ نفّذ الفحص أولاً.");
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("AX12 Parental Control Probe", lastParentalProbeReport));
        error("نُسخ التقرير إلى الحافظة. يمكنك إرساله هنا لتحليل نتيجة الراوتر.");
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

    /** Reads router-recorded per-device parental-control evidence; no router writes. */
    private void refreshDeviceControlDiagnostics() {
        try {
            if (GuardService.isRunning() || testingConnection || probingCompatibility || loadingInventory || modifyingRouter || selectionBusy)
                throw new IllegalStateException("أوقف المراقبة والفحوص أو التعديلات الجارية قبل التشخيص؛ الراوتر قد يسمح بجلسة إدارة واحدة فقط");
            requirePassword();
            String host = routerHost(), password = passwordField.getText().toString();
            requireSuccessfulReadTest(host, password);
            loadingInventory = true;
            deviceControlMetaView.setText("جارٍ قراءة بيانات Patrol للأجهزة والملفات — قراءة فقط، دون تغيير أي إعداد...");
            new Thread(() -> {
                RouterClient client = null;
                try {
                    client = new RouterClient(this, host);
                    client.login(password);
                    Map<String, DeviceControlStatus> statuses = client.readDeviceControlStatusesReadOnly();
                    ui.post(() -> {
                        lastDeviceControlStatuses = new LinkedHashMap<>(statuses);
                        lastDeviceControlReadTime = System.currentTimeMillis();
                        renderInventory();
                        String time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
                                .format(new Date(lastDeviceControlReadTime));
                        deviceControlMetaView.setText("آخر تشخيص: " + time + " | سجلات أجهزة مرتبطة بـ MAC: " + statuses.size()
                                + "\nالحالة المعروضة هي ما سجله الراوتر؛ عدم وجود حقل لا يعني أن الإنترنت يعمل أو أنه محجوب.");
                        log.add("اكتمل تشخيص Patrol للقراءة فقط؛ سجلات الأجهزة المرتبطة بـ MAC=" + statuses.size());
                    });
                } catch (Exception error) {
                    String type = error.getClass().getSimpleName();
                    ui.post(() -> {
                        deviceControlMetaView.setText("تعذر جلب تشخيص الرقابة لكل جهاز (" + type + "). لم تتغير إعدادات الراوتر؛ راجع توافق Firmware وسجل الأحداث.");
                        log.add("تعذر تشخيص حالة الرقابة لكل جهاز؛ نوع الخطأ: " + type);
                    });
                } finally {
                    if (client != null) client.close();
                    ui.post(() -> { loadingInventory = false; updateSelectionControls(); });
                }
            }, "AX12-DeviceControlReadOnly").start();
        } catch (Exception error) { error(error.getMessage()); }
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
            java.util.Set<String> shownMacs = new java.util.LinkedHashSet<>();
            for (DeviceInventory.Entry entry: report.entries) {
                showInventoryEntry(entry);
                shownMacs.add(entry.mac);
                if (isProtectedHost(entry.mac)) protectedHostShown = true;
            }
            for (DeviceControlStatus control : lastDeviceControlStatuses.values()) {
                if (!shownMacs.contains(control.mac)) showControlOnlyEntry(control);
            }
            if (!protectedHostShown) showProtectedHostEntry();
            updateSelectionControls();
            updateDashboard();
        } catch (Exception ignored) {
            inventorySummaryView.setText("تعذر مقارنة البيانات بعد تغيير القائمة المحلية.");
        }
    }

    private void showControlOnlyEntry(DeviceControlStatus control) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        card.setPadding(15, 11, 15, 11);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Color.rgb(255, 248, 225));
        shape.setCornerRadius(14);
        shape.setStroke(1, Color.rgb(219, 227, 231));
        card.setBackground(shape);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 6, 0, 8);
        inventoryRows.addView(card, params);
        card.addView(text("سجل رقابة غير ظاهر في لقطة العملاء | " + control.mac, 14, true));
        card.addView(text("ملف الرقابة: " + (control.profileName.isEmpty() ? "غير محدد" : control.profileName)
                + " | Profile ID: " + (control.ownerId.isEmpty() ? "غير متاح" : control.ownerId), 12, false));
        card.addView(text("حالة الإنترنت وفق سجل الراوتر: " + control.internetStateArabic(), 12, true));
        if (!control.scheduleSummary.isEmpty())
            card.addView(text("بيانات الجدول: " + control.scheduleSummary, 11, false));
        card.addView(text("هذا السجل لم يرتبط بقائمة العملاء الحالية؛ لا نستنتج منه أن الجهاز متصل الآن.", 11, false));
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
        DeviceControlStatus control = lastDeviceControlStatuses.get(d.mac);
        if (lastDeviceControlReadTime == 0) {
            card.addView(text("الرقابة الأبوية: لم يُجرَ فحص بيانات Patrol بعد.",12,false));
        } else if (control == null) {
            card.addView(text("الرقابة الأبوية: لم يُرجع الراوتر سجلاً مرتبطاً بهذا MAC؛ حالة الحجب غير معروفة.",12,false));
        } else {
            card.addView(text("ملف الرقابة: " + (control.profileName.isEmpty() ? "غير محدد" : control.profileName)
                    + " | Profile ID: " + (control.ownerId.isEmpty() ? "غير متاح" : control.ownerId),12,false));
            card.addView(text("حالة الإنترنت وفق سجل الراوتر: " + control.internetStateArabic(),12,true));
            if (!control.scheduleSummary.isEmpty())
                card.addView(text("بيانات الجدول التي أعادها الراوتر: " + control.scheduleSummary,11,false));
        }
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
                    ? "\n\nوافقت على حفظ كلمة مرور الإدارة مشفرة في Android Keystore لاستعادة الخدمة إذا أعاد Android إنشاءها، ولإعادة إنشاء جلسة الراوتر عند انتهاء الجلسة. تبقى مشفرة حتى توقف الحارس صراحة أو تحذفها يدوياً؛ لا تُحفظ كنص صريح."
                    : "\n\nلم تختر حفظ كلمة المرور. ستواصل خدمة Foreground العمل عند إغلاق واجهة التطبيق ما دام Android يبقي الخدمة حية، وستتعافى من انقطاع الجلسة ما دامت الخدمة حية؛ لكن لن يمكن استعادتها تلقائياً إذا أنهى النظام عملية الخدمة.";
            String notificationNote = Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ? "\n\nتنبيه: إذن إشعارات Android غير ممنوح حالياً؛ سيُسجل التطبيق أحداث الأجهزة داخلياً، لكن لن تظهر إشعارات الدخول والخروج حتى تسمح بالإشعارات من إعدادات الهاتف."
                    : "\n\nعند ظهور جهاز أو اختفائه في لقطتين كاملتين متتاليتين، سيُسجل الحدث ويُرسل إشعاراً. تأكد من عدم تعطيل قناة إشعارات الأجهزة من إعدادات Android.";
            new AlertDialog.Builder(this).setTitle("تشغيل الحارس")
              .setMessage("يتطلب نجاح اختبار الاتصال والقراءة أولاً، وتفعيل Allow List مسبقاً في الراوتر. سيحذف الحارس عنواناً واحداً غير معتمد في كل دورة، وقد ينقطع اتصال الأجهزة غير المدرجة. تأكد أن MAC جهاز الإدارة ضمن قائمتك. إذا ظهر HTTP 403 فلا تشغّل الحارس.\n\nلا يمكن منع انتحال MAC معتمد اعتماداً على عنوان MAC وحده." + persistenceNote + notificationNote)
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

    private void openAppSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            error("تعذر فتح إعدادات التطبيق: " + AuthErrorLabel.safeValue(e.getMessage()));
        }
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
