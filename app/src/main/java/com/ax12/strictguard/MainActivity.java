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

/** Arabic UI. Manual MAC approvals are independent of whatever the router auto-adds. */
public final class MainActivity extends Activity {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private EditText hostField,passwordField,macField;
    private TextView trustedView,eventsView,statusView;
    private TrustedStore store;
    private GuardLog log;
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
        col.addView(text("مهم: لا يمكن منع أول اتصال إذا سمح الراوتر به قبل الفحص؛ ولا يمكن كشف انتحال MAC معتمد.",13,true));
        col.addView(text("عنوان الراوتر المحلي",16,true));
        hostField=field("192.168.0.1",InputType.TYPE_CLASS_PHONE);hostField.setText(store.getHost());col.addView(hostField);
        col.addView(text("كلمة مرور إدارة الراوتر (لن تُحفظ)",16,true));
        passwordField=field("كلمة مرور الإدارة",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD); col.addView(passwordField);
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
    private void testConnection(){
        try{requirePassword();String host=routerHost(),pwd=passwordField.getText().toString();
            statusView.setText("جاري اختبار الاتصال...");
            new Thread(()->{
                try{RouterClient client=new RouterClient(host);client.login(pwd);
                    RouterClient.Settings s=client.readSettings();Set<String> allow=client.readAllowed();client.close();
                    ui.post(()->{statusView.setText("متصل: enabled="+s.enabled+" / mode="+s.mode+" / Allow List="+allow.size());
                        log.add("نجح اختبار قراءة القائمة البيضاء: "+allow.size()+" عنوان");updateLists();});
                }catch(Exception e){ui.post(()->{statusView.setText("فشل اختبار التوافق/الاتصال");log.add("فشل اختبار الاتصال: "+e.getMessage());updateLists();});}
            },"AX12-ReadTest").start();
        }catch(Exception e){error(e.getMessage());}
    }
    private void confirmAllow(){
        try {requirePassword();String mac=selectedMac();
            if(!store.get().contains(mac))throw new IllegalArgumentException("اعتمد MAC محلياً قبل إرساله");
            String host=routerHost(),pass=passwordField.getText().toString();
            new AlertDialog.Builder(this).setTitle("موافقة صريحة لإضافة MAC")
              .setMessage("إضافة "+mac+" إلى القائمة البيضاء للراوتر؟ هذا إجراء إضافي اختياري.")
              .setPositiveButton("أضف الآن",(d,w)->new Thread(()->{
                  try{RouterClient client=new RouterClient(host);client.login(pass);client.allowObserved(mac);client.close();
                      ui.post(()->{log.add("أُرسل MAC المعتمد إلى الراوتر: "+mac);updateLists();});}
                  catch(Exception e){ui.post(()->error("تعذر إضافة MAC إلى الراوتر: "+e.getMessage()));}
              },"AX12-Approve").start())
              .setNegativeButton("إلغاء",null).show();
        }catch(Exception e){error(e.getMessage());}
    }
    private void startGuard(){
        try {requirePassword();String host=routerHost();if(store.get().isEmpty())throw new IllegalStateException("أدخل عنوان MAC معتمداً واحداً على الأقل بما فيه جهاز الإدارة");
            String password=passwordField.getText().toString();
            new AlertDialog.Builder(this).setTitle("تشغيل الحارس")
              .setMessage("يتطلب تفعيل Allow List مسبقاً في الراوتر. سيحذف الحارس أي عنوان غير معتمد يكتشفه؛ وقد ينقطع اتصال الأجهزة غير المدرجة. تأكد أن MAC جهاز الإدارة ضمن قائمتك.")
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
