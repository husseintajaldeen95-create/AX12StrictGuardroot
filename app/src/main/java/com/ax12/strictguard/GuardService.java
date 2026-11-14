package com.ax12.strictguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Reconcile only the allow-list, never change parental control, Wi-Fi passwords, or blacklist. */
public final class GuardService extends Service {
    public static final String START="com.ax12.strictguard.START";
    public static final String STOP="com.ax12.strictguard.STOP";
    public static final String EXTRA_HOST="host";
    public static final String EXTRA_PASS="password";
    private static final String CHANNEL="strict_guard_v1";
    private static final int MAX_REMOVALS_PER_CYCLE = 1; // fail-safe: never bulk-delete on one poll
    private volatile boolean active=false;
    private static volatile boolean running=false;
    public static boolean isRunning(){return running;}
    private Thread worker;
    private GuardLog log;
    private NotificationManager notifier;
    private int consecutiveFailures=0;

    @Override public void onCreate() {
        super.onCreate();log=new GuardLog(this);
        notifier=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        notifier.createNotificationChannel(new NotificationChannel(CHANNEL,"مراقبة قائمة MAC",NotificationManager.IMPORTANCE_LOW));
    }
    private Notification notifyText(String message) {
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("AX12 Strict Guard").setContentText(message).setContentIntent(pi)
            .setOngoing(active).build();
    }
    private void update(String message) {notifier.notify(42,notifyText(message));}
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent==null)return START_NOT_STICKY;
        if(STOP.equals(intent.getAction())) {halt();stopSelf();return START_NOT_STICKY;}
        if(!START.equals(intent.getAction()))return START_NOT_STICKY;
        if(active) {log.add("الحارس يعمل بالفعل؛ تجاهل التشغيل المكرر");return START_NOT_STICKY;}
        // Enter foreground immediately; invalid start data must not trigger the Android
        // foreground-service startup timeout.
        startForeground(42,notifyText("جارٍ التحقق من إعدادات بدء المراقبة"));
        String host=intent.getStringExtra(EXTRA_HOST);
        String pass=intent.getStringExtra(EXTRA_PASS);
        if(host==null || pass==null || pass.isEmpty()) {log.add("تعذر تشغيل الحارس: البيانات ناقصة");stopForeground(true);stopSelf();return START_NOT_STICKY;}
        try {new RouterClient(host);if(new TrustedStore(this).get().isEmpty())throw new IllegalStateException("القائمة اليدوية فارغة");}
        catch(Exception e){log.add("تعذر البدء: "+e.getMessage());stopForeground(true);stopSelf();return START_NOT_STICKY;}
        active=true;running=true;startForeground(42,notifyText("مراقبة القائمة البيضاء قيد التشغيل"));
        worker=new Thread(()->monitor(host,pass),"AX12-StrictGuard");worker.start();
        return START_NOT_STICKY; // if killed, do not restart without an explicitly supplied password
    }
    private void monitor(String host,String pass) {
        RouterClient client=null;
        String lastResult="";
        try {
            client=new RouterClient(host);
            client.login(pass);
            log.add("اتصال إدارة ناجح عبر "+client.getProtocolName()+". بدء التحقق من القائمة البيضاء.");
            while(active && !Thread.currentThread().isInterrupted()) {
                try {
                    RouterClient.Settings settings=client.readSettings();
                    if(!settings.enabled) {
                        log.add("إنذار: Access Control معطّل على الراوتر؛ أوقفت الحارس لتفادي إعادة تفعيل غير آمنة");
                        update("حماية الراوتر معطّلة. راجع إعدادات Access Control");break;
                    }
                    if(!"white".equalsIgnoreCase(settings.mode)) {
                        log.add("إنذار: وضع الراوتر ليس White List. لن أغيّره تلقائياً حتى لا أسمح بإضافات ضمنية.");
                        update("الراوتر ليس على وضع White List");break;
                    }
                    Set<String> trusted=new TrustedStore(this).get();
                    if(trusted.isEmpty()) {log.add("القائمة اليدوية فارغة، توقفت المراقبة");break;}
                    String protectedHost=settings.protectedHost;
                    final String normalizedHost;
                    try {
                        normalizedHost=MacPolicy.normalize(protectedHost);
                    } catch(IllegalArgumentException invalidHost) {
                        // A missing/unparseable protected host makes it unsafe to decide
                        // which entries may be removed. Stop instead of guessing.
                        log.add("إنذار: لم يعلن الراوتر MAC جهاز الإدارة بصيغة صحيحة؛ أوقفت الحارس دون حذف أي عنوان");
                        update("تعذر تحديد جهاز الإدارة؛ لم يُحذف أي MAC");break;
                    }
                    if(!trusted.contains(normalizedHost)) {
                        log.add("إنذار: عنوان جهاز الإدارة المحمي "+normalizedHost+" غير موجود في قائمة الثقة. أضفه يدوياً قبل البدء.");
                        update("أضف MAC جهاز الإدارة إلى القائمة المعتمدة");break;
                    }
                    Set<String> actual=client.readAllowed();
                    List<String> unauthorized=MacPolicy.unauthorized(actual,trusted,protectedHost);
                    if(unauthorized.size()>0) {
                        log.add("اكتُشف "+unauthorized.size()+" MAC غير مصرح به في Allow List");
                        int removed=0;
                        for(String m:unauthorized) {
                            if(!active || removed>=MAX_REMOVALS_PER_CYCLE) break;
                            client.removeAllowed(m);
                            log.add("أزيل من القائمة البيضاء: "+m);
                            removed++;
                        }
                        update("أُزيل "+removed+" عنوان غير معتمد من Allow List؛ الحد عنوان واحد في كل دورة");
                        lastResult="";
                    } else if(!"healthy".equals(lastResult)) {
                        log.add("مراجعة ناجحة: القائمة البيضاء لا تحتوي على MAC غير معتمد");
                        update("الحماية فعالة؛ القائمة البيضاء مطابقة");lastResult="healthy";
                    }
                    consecutiveFailures=0;
                    pause(15000);
                } catch(Exception e) {
                    consecutiveFailures++;
                    String msg=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
                    log.add("تعذر فحص الحماية: "+msg);
                    update("تعذر التحقق من الراوتر: راجع سجل الأحداث");
                    if(consecutiveFailures>=5) {log.add("إيقاف المراقبة بعد 5 أخطاء متتالية لتجنب الإضرار بالراوتر");break;}
                    pause(Math.min(120000,15000L*consecutiveFailures));
                }
            }
        } catch (Exception e) {
            log.add("فشل الاتصال أو تسجيل الدخول: "+e.getMessage());
        } finally {
            if(client!=null)client.close();active=false;running=false;
            log.add("المراقبة متوقفة؛ الراوتر يدير الشبكة حسب إعداداته الحالية");
            stopForeground(true);stopSelf();
        }
    }
    private void pause(long millis) {try {Thread.sleep(millis);}catch(InterruptedException ex){Thread.currentThread().interrupt();}}
    private void halt(){active=false;if(worker!=null)worker.interrupt();log.add("أُرسل أمر إيقاف المراقبة");}
    @Override public void onDestroy(){halt();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
