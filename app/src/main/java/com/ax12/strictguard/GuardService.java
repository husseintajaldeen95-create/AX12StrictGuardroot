package com.ax12.strictguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.Manifest;
import android.os.Build;
import android.os.IBinder;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;

/** Guard the allow-list and observe connected-device presence; never change parental controls, Wi-Fi, or blacklist. */
public final class GuardService extends Service {
    public static final String START = "com.ax12.strictguard.START";
    public static final String STOP = "com.ax12.strictguard.STOP";
    public static final String EXTRA_HOST = "host";
    public static final String EXTRA_PASS = "password";
    public static final String EXTRA_PERSISTENT = "persistent_restart";

    private static final String CHANNEL = "strict_guard_v1";
    private static final String DEVICE_EVENT_CHANNEL = "device_events_v1";
    private static final String DEVICE_ALERT_CHANNEL = "device_alerts_v1";
    private static final String EVENT_PREFS = "device_event_notifications_v1";
    private static final int MAX_REMOVALS_PER_CYCLE = 1;
    private volatile boolean active = false;
    private static volatile boolean running = false;
    private volatile boolean explicitStopRequested = false;
    private boolean persistentRestartEnabled = false;
    private Thread worker;
    private GuardLog log;
    private NotificationManager notifier;
    private boolean deviceSnapshotWarningLogged = false;
    private boolean notificationPermissionWarningLogged = false;

    public static boolean isRunning() { return running; }

    @Override public void onCreate() {
        super.onCreate();
        log = new GuardLog(this);
        notifier = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notifier.createNotificationChannel(new NotificationChannel(
                CHANNEL, "حالة الحارس", NotificationManager.IMPORTANCE_LOW));
        NotificationChannel events = new NotificationChannel(
                DEVICE_EVENT_CHANNEL, "اتصال الأجهزة وانقطاعها", NotificationManager.IMPORTANCE_DEFAULT);
        events.setDescription("إشعارات عند ظهور جهاز في قائمة المتصلين أو اختفائه منها بعد التحقق من لقطتين متتاليتين");
        notifier.createNotificationChannel(events);
        NotificationChannel alerts = new NotificationChannel(
                DEVICE_ALERT_CHANNEL, "تنبيهات الأجهزة غير المتوقعة", NotificationManager.IMPORTANCE_HIGH);
        alerts.setDescription("تنبيهات للأجهزة التي تظهر متصلة ولا تتوافق مع قائمة الثقة وقائمة سماح الراوتر");
        notifier.createNotificationChannel(alerts);
    }

    private Notification notifyText(String message) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("AX12 Strict Guard")
                .setContentText(message)
                .setContentIntent(pi)
                .setOngoing(active)
                .build();
    }

    private void update(String message) { notifier.notify(42, notifyText(message)); }

    private boolean canPostNotifications(String channelId) {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return false;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = notifier.getNotificationChannel(channelId);
            if (channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE)
                return false;
        }
        return true;
    }

    private synchronized int nextDeviceNotificationId() {
        SharedPreferences prefs = getSharedPreferences(EVENT_PREFS, MODE_PRIVATE);
        int id = prefs.getInt("next_id", 1000);
        int next = id >= 2000000000 ? 1000 : id + 1;
        prefs.edit().putInt("next_id", next).apply();
        return id;
    }

    private String deviceClassification(String mac, Set<String> trusted, Set<String> allowed) {
        boolean isTrusted = trusted.contains(mac);
        boolean isAllowed = allowed.contains(mac);
        if (isTrusted && isAllowed) return "موثوق في التطبيق وموجود في قائمة سماح الراوتر";
        if (isTrusted) return "موثوق في التطبيق لكنه غير موجود في قائمة سماح الراوتر";
        if (isAllowed) return "موجود في قائمة سماح الراوتر لكنه غير معتمد في قائمة التطبيق";
        return "غير معتمد في التطبيق وغير موجود في قائمة سماح الراوتر";
    }

    private void publishDeviceEvents(List<DevicePresenceTracker.Event> events,
                                     Set<String> trusted, Set<String> allowed) {
        for (DevicePresenceTracker.Event event : events) {
            DeviceInventory.ConnectedDevice device = event.device;
            boolean arrived = event.type == DevicePresenceTracker.Type.ARRIVED;
            String name = device.name.isEmpty() ? "اسم غير متاح" : device.name;
            String classification = deviceClassification(device.mac, trusted, allowed);
            String time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
                    .format(new Date());
            StringBuilder details = new StringBuilder()
                    .append("الاسم: ").append(name)
                    .append("\nMAC: ").append(device.mac);
            if (!device.ip.isEmpty()) details.append(arrived ? "\nIP الحالي: " : "\nآخر IP مرصود: ").append(device.ip);
            if (!device.medium.isEmpty()) details.append("\nنوع الاتصال: ").append(device.medium);
            details.append("\nالتصنيف: ").append(classification)
                    .append("\nوقت الرصد: ").append(time);

            log.add((arrived ? "اتصال مرصود: " : "اختفاء من قائمة المتصلين: ")
                    + name + " | " + device.mac + " | " + classification);

            boolean unexpectedArrival = arrived && !(trusted.contains(device.mac) && allowed.contains(device.mac));
            String channel = unexpectedArrival ? DEVICE_ALERT_CHANNEL : DEVICE_EVENT_CHANNEL;
            if (!canPostNotifications(channel)) {
                if (!notificationPermissionWarningLogged) {
                    log.add("إذن إشعارات Android أو قناة إشعارات الأجهزة معطّلة؛ سُجلت الأحداث داخل التطبيق لكن لن تظهر تنبيهات خارجية حتى تفعيل الإشعارات");
                    notificationPermissionWarningLogged = true;
                }
                continue;
            }

            String title = arrived ? "اتصال جهاز بالشبكة" : "جهاز لم يعد ظاهراً على الشبكة";
            String shortText = name + " • " + device.mac;
            Notification.Builder builder = new Notification.Builder(this, channel)
                    .setSmallIcon(android.R.drawable.ic_lock_lock)
                    .setContentTitle(title)
                    .setContentText(shortText)
                    .setStyle(new Notification.BigTextStyle().bigText(details.toString()))
                    .setWhen(System.currentTimeMillis())
                    .setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_STATUS)
                    .setContentIntent(openAppPendingIntent());
            try {
                notifier.notify(nextDeviceNotificationId(), builder.build());
            } catch (SecurityException denied) {
                if (!notificationPermissionWarningLogged) {
                    log.add("تعذر عرض إشعار جهاز بسبب صلاحية الإشعارات؛ راجع إعدادات Android");
                    notificationPermissionWarningLogged = true;
                }
            }
        }
    }

    private PendingIntent openAppPendingIntent() {
        Intent open = new Intent(this, MainActivity.class);
        return PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Device-list errors are isolated from allow-list protection. Invalid admin sessions
     *  are discarded and recreated by the outer monitor loop; the foreground service stays alive. */
    private void pollDevicePresence(RouterClient client, DevicePresenceTracker tracker,
                                    Set<String> trusted, Set<String> allowed) throws Exception {
        try {
            RouterClient.ConnectedSnapshot snapshot = client.readConnectedDevices();
            if (!snapshot.completeSections) {
                if (!deviceSnapshotWarningLogged) {
                    log.add("رصد الأجهزة غير مكتمل: Firmware لم يُرجع قسمي الأجهزة السلكية واللاسلكية معاً؛ لن أستنتج دخولاً أو خروجاً من لقطة جزئية");
                    deviceSnapshotWarningLogged = true;
                }
                return;
            }
            if (deviceSnapshotWarningLogged) {
                log.add("استعاد الراوتر لقطة كاملة للأجهزة المتصلة؛ استؤنف رصد الدخول والخروج");
                deviceSnapshotWarningLogged = false;
            }
            boolean baseline = !tracker.hasBaseline();
            List<DevicePresenceTracker.Event> events = tracker.observe(snapshot.devices, true);
            if (baseline) {
                log.add("بدأ رصد حركة الأجهزة من لقطة أساس تضم " + tracker.confirmedOnlineCount()
                        + " جهازاً؛ لن تُرسل إشعارات للأجهزة الموجودة قبل بدء الرصد");
            } else if (!events.isEmpty()) {
                publishDeviceEvents(events, trusted, allowed);
            }
        } catch (Exception e) {
            if (client.isSessionInvalid() || AuthAttemptPolicy.isDenied(e.getMessage())) throw e;
            if (!deviceSnapshotWarningLogged) {
                log.add("تعذر قراءة قائمة الأجهزة المتصلة؛ استمر فحص قائمة السماح دون إصدار استنتاجات دخول/خروج: " + safeMessage(e));
                deviceSnapshotWarningLogged = true;
            }
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            explicitStopRequested = true;
            persistentRestartEnabled = false;
            CredentialVault.clear(this);
            halt(true);
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null && !START.equals(intent.getAction())) return START_NOT_STICKY;
        if (active) {
            log.add("الحارس يعمل بالفعل؛ تجاهل التشغيل المكرر");
            return persistentRestartEnabled ? START_STICKY : START_NOT_STICKY;
        }

        // Enter foreground immediately, including the Android sticky-service restart path.
        startForeground(42, notifyText(intent == null
                ? "محاولة استعادة المراقبة المحفوظة بأمان"
                : "جارٍ التحقق من إعدادات بدء المراقبة"));

        try {
            if (intent == null) {
                CredentialVault.SavedCredentials saved = CredentialVault.loadForRestart(this);
                if (saved == null) {
                    log.add("لم تُفعّل استعادة المراقبة أو لم تُحفظ بيانات آمنة؛ لن تتم إعادة التشغيل تلقائياً");
                    stopForeground(true);
                    stopSelf();
                    return START_NOT_STICKY;
                }
                beginMonitoring(saved.host, saved.password, true, false);
                log.add("استُعيدت المراقبة بعد إعادة إنشاء خدمة Android باستخدام كلمة مرور مشفرة في Android Keystore");
                return START_STICKY;
            }

            String host = intent.getStringExtra(EXTRA_HOST);
            String password = intent.getStringExtra(EXTRA_PASS);
            boolean persistent = intent.getBooleanExtra(EXTRA_PERSISTENT, false);
            beginMonitoring(host, password, persistent, true);
            return persistent ? START_STICKY : START_NOT_STICKY;
        } catch (Exception e) {
            persistentRestartEnabled = false;
            log.add("تعذر تشغيل/استعادة الحارس: " + safeMessage(e));
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
    }

    private void beginMonitoring(String host, String password, boolean persistent, boolean saveCredential) {
        if (host == null || host.trim().isEmpty() || password == null || password.isEmpty())
            throw new IllegalStateException("بيانات بدء المراقبة ناقصة");
        new RouterClient(host); // Validate the router address before storing credentials.
        Set<String> trusted = new TrustedStore(this).get();
        if (trusted.isEmpty()) throw new IllegalStateException("القائمة اليدوية فارغة");

        if (persistent) {
            if (saveCredential) CredentialVault.saveForRestart(this, host, password);
            else if (!CredentialVault.hasSavedCredentialForHost(this, host))
                throw new IllegalStateException("بيانات الاستعادة المحفوظة لا تطابق عنوان الراوتر");
        } else {
            CredentialVault.clear(this);
        }

        persistentRestartEnabled = persistent;
        active = true;
        running = true;
        startForeground(42, notifyText("مراقبة القائمة البيضاء قيد التشغيل"));
        worker = new Thread(() -> monitor(host, password), "AX12-StrictGuard");
        worker.start();
    }

    private void monitor(String host, String password) {
        DevicePresenceTracker presenceTracker = new DevicePresenceTracker();
        String lastResult = "";
        String safetyPauseReason = "";
        int consecutiveFailures = 0;

        // Keep the foreground service alive while router sessions are renewed. A lost
        // session is not the same as a user-requested stop.
        while (active && !Thread.currentThread().isInterrupted()) {
            RouterClient client = null;
            long reconnectWait = GuardRecoveryPolicy.SESSION_TIMEOUT_RECONNECT_MS;
            try {
                client = new RouterClient(this, host);
                client.login(password);
                consecutiveFailures = 0;
                log.add("اتصال إدارة ناجح عبر " + client.getProtocolName()
                        + ". استؤنف فحص القائمة البيضاء ورصد الأجهزة.");
                update("الحارس متصل بالراوتر ويراقب القائمة البيضاء");

                while (active && !Thread.currentThread().isInterrupted()) {
                    try {
                        RouterClient.Settings settings = client.readSettings();
                        if (!settings.enabled) {
                            if (!"access-control-disabled".equals(safetyPauseReason)) {
                                log.add("تنبيه: Access Control معطّل من إعدادات الراوتر؛ بقيت خدمة الحارس قيد التشغيل وستعيد الفحص دورياً، لكنها لا تستطيع فرض القائمة البيضاء الآن");
                                update("الحارس يعمل؛ Access Control معطّل في الراوتر");
                                safetyPauseReason = "access-control-disabled";
                            }
                            pause(60000L);
                            continue;
                        }
                        if (!"white".equalsIgnoreCase(settings.mode)) {
                            if (!"not-white-list".equals(safetyPauseReason)) {
                                log.add("تنبيه: وضع الراوتر ليس White List؛ بقيت الخدمة قيد التشغيل دون تغيير إعداد الراوتر، وستعيد الفحص دورياً");
                                update("الحارس يعمل؛ الراوتر ليس على وضع White List");
                                safetyPauseReason = "not-white-list";
                            }
                            pause(60000L);
                            continue;
                        }

                        Set<String> trusted = new TrustedStore(this).get();
                        if (trusted.isEmpty()) {
                            if (!"empty-trust-list".equals(safetyPauseReason)) {
                                log.add("قائمة الثقة المحلية فارغة؛ بقيت الخدمة قيد التشغيل دون إجراء تغييرات على الراوتر");
                                update("الحارس ينتظر قائمة ثقة غير فارغة");
                                safetyPauseReason = "empty-trust-list";
                            }
                            pause(60000L);
                            continue;
                        }

                        final String normalizedHost;
                        try {
                            normalizedHost = MacPolicy.normalizeTrusted(settings.protectedHost);
                        } catch (IllegalArgumentException invalidHost) {
                            if (!"invalid-protected-host".equals(safetyPauseReason)) {
                                log.add("تعذر التحقق من MAC جهاز الإدارة؛ بقيت الخدمة قيد التشغيل ولم تُحذف أي عناوين");
                                update("الحارس ينتظر التحقق من MAC جهاز الإدارة");
                                safetyPauseReason = "invalid-protected-host";
                            }
                            pause(60000L);
                            continue;
                        }
                        if (!trusted.contains(normalizedHost)) {
                            if (!"untrusted-protected-host".equals(safetyPauseReason)) {
                                log.add("MAC جهاز الإدارة المحمي " + normalizedHost
                                        + " غير موجود في قائمة الثقة؛ بقيت الخدمة قيد التشغيل ولم تُحذف أي عناوين");
                                update("أضف MAC جهاز الإدارة إلى قائمة الثقة");
                                safetyPauseReason = "untrusted-protected-host";
                            }
                            pause(60000L);
                            continue;
                        }
                        if (!safetyPauseReason.isEmpty()) {
                            log.add("عادت شروط الحماية المطلوبة؛ استؤنف فحص القائمة البيضاء");
                            safetyPauseReason = "";
                        }

                        Set<String> actual = client.readAllowed();
                        // Presence monitoring is read-only and isolated: an unsupported status endpoint
                        // does not disable the existing allow-list guard.
                        pollDevicePresence(client, presenceTracker, trusted, actual);
                        List<String> unauthorized = MacPolicy.unauthorized(actual, trusted, settings.protectedHost);
                        if (!unauthorized.isEmpty()) {
                            log.add("اكتُشف " + unauthorized.size() + " MAC غير مصرح به في Allow List");
                            int removed = 0;
                            for (String mac : unauthorized) {
                                if (!active || removed >= MAX_REMOVALS_PER_CYCLE) break;
                                client.removeAllowed(mac);
                                log.add("أزيل من القائمة البيضاء: " + mac);
                                removed++;
                            }
                            update("أُزيل " + removed + " عنوان غير معتمد من Allow List؛ الحد عنوان واحد في كل دورة");
                            lastResult = "";
                        } else if (!"healthy".equals(lastResult)) {
                            log.add("مراجعة ناجحة: القائمة البيضاء لا تحتوي على MAC غير معتمد");
                            update("الحماية فعالة؛ القائمة البيضاء مطابقة");
                            lastResult = "healthy";
                        }
                        consecutiveFailures = 0;
                        pause(15000L);
                    } catch (Exception e) {
                        String rawMessage = e.getMessage();
                        String message = safeMessage(e);
                        log.add("تعذر فحص الحماية: " + message);
                        update("الحارس مستمر؛ تعذر فحص الراوتر مؤقتاً");

                        if (client.isSessionInvalid() || AuthAttemptPolicy.isDenied(rawMessage)) {
                            long gateRemaining = AuthAttemptGate.remainingCooldownMs(this);
                            reconnectWait = GuardRecoveryPolicy.reconnectDelayMs(rawMessage, gateRemaining);
                            if (AuthAttemptPolicy.isDenied(rawMessage)) {
                                log.add("يبدو أن الراوتر رفض الجلسة أو استُبدلت بجلسة إدارة أخرى؛ أبقيت خدمة الحارس والإشعار الأمامي فعالين، وأجّلت محاولة الدخول لتجنب تكرار المصادقة. قد لا يسمح Firmware بجلسة متصفح وتطبيق متزامنتين.");
                                update("الحارس يعمل وينتظر استعادة جلسة الراوتر؛ قد تكون جلسة الإدارة مستخدمة من المتصفح");
                            } else {
                                log.add("انتهت جلسة الإدارة أو أصبحت قراءة التوقيع غير موثوقة؛ لم أستخدم الجلسة القديمة مجدداً. ستبقى الخدمة فعالة وستنشئ جلسة جديدة بعد مهلة قصيرة.");
                                update("الحارس مستمر؛ يعيد إنشاء جلسة الراوتر بعد انقطاع مؤقت");
                            }
                            break;
                        }

                        consecutiveFailures++;
                        if (consecutiveFailures >= 5) {
                            reconnectWait = GuardRecoveryPolicy.generalFailureDelayMs(
                                    AuthAttemptGate.remainingCooldownMs(this));
                            log.add("تكررت أخطاء الاتصال؛ أبقيت الخدمة فعالة وسأعيد إنشاء جلسة الراوتر بعد مهلة تهدئة");
                            update("الحارس مستمر؛ ينتظر إعادة الاتصال بالراوتر");
                            break;
                        }
                        pause(Math.min(120000L, 15000L * consecutiveFailures));
                    }
                }
            } catch (Exception e) {
                String rawMessage = e.getMessage();
                String message = safeMessage(e);
                long gateRemaining = AuthAttemptGate.remainingCooldownMs(this);
                reconnectWait = GuardRecoveryPolicy.reconnectDelayMs(rawMessage, gateRemaining);
                log.add("تعذر إنشاء جلسة الراوتر: " + message
                        + ". خدمة الحارس لم تتوقف؛ ستُعاد المحاولة بعد مهلة أمان.");
                update("الحارس يعمل؛ ينتظر إعادة الاتصال الإداري بالراوتر");
            } finally {
                if (client != null) client.close();
            }

            if (!active || Thread.currentThread().isInterrupted()) break;
            log.add("خدمة الحارس ما زالت فعالة في الخلفية؛ إعادة محاولة الاتصال بعد نحو "
                    + Math.max(1L, (reconnectWait + 59999L) / 60000L) + " دقيقة");
            pause(reconnectWait);
        }

        active = false;
        running = false;
        if (explicitStopRequested) {
            log.add("أُوقفت خدمة الحارس بناءً على طلب المستخدم؛ بقيت إعدادات الراوتر كما هي");
        } else {
            log.add("انتهى عامل المراقبة بسبب إنهاء خدمة Android؛ ستُستعاد الخدمة إذا أعاد Android إنشاءها وكان خيار الاستعادة الآمنة مفعلاً");
        }
        stopForeground(true);
        stopSelf();
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) return error.getClass().getSimpleName();
        // Never surface raw HTTP bodies, encrypted payloads, cookies, or credentials in the event log.
        return AuthErrorLabel.safeValue(message);
    }

    private void pause(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
    }

    private void halt(boolean userRequested) {
        active = false;
        if (worker != null) worker.interrupt();
        if (userRequested) log.add("أُرسل أمر إيقاف المراقبة من المستخدم");
    }

    @Override public void onDestroy() {
        if (!explicitStopRequested && active) {
            log.add("أنهى Android خدمة الحارس؛ ستُحاول الاستعادة التلقائية إذا كان الحفظ الآمن مفعلاً ولم يكن التطبيق في حالة إيقاف إجباري");
        }
        halt(false);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
