# تحديث AX12 Strict Guard إلى الإصدار 0.2.1-session-recovery

هذه حزمة مصدر كاملة. ارفع محتويات المجلد `AX12StrictGuardroot-main` إلى جذر مستودع GitHub الحالي، مع استبدال الملفات ذات المسارات نفسها. لا تنشئ مستودعاً جديداً ولا تضع المجلد الأب داخل المستودع كطبقة إضافية.

## أهم الملفات التي تغيرت

- `app/src/main/java/com/ax12/strictguard/GuardService.java`: إبقاء خدمة Foreground فعالة بعد انتهاء جلسة الراوتر وإعادة الاتصال بأمان.
- `app/src/main/java/com/ax12/strictguard/GuardRecoveryPolicy.java`: ملف جديد يحدد مهلة الاستعادة.
- `app/src/main/java/com/ax12/strictguard/AuthAttemptGate.java`: إضافة قراءة مدة المهلة المتبقية دون اتصال بالراوتر.
- `app/src/main/java/com/ax12/strictguard/MainActivity.java`: تحديث شرح الحفظ الآمن وإضافة اختصار إعدادات بطارية التطبيق.
- `app/build.gradle`: `versionCode 12` و`versionName 0.2.1-session-recovery`.
- `tests/GuardRecoveryPolicyTest.java`: اختبارات جديدة لسياسة الاستعادة.
- `tests/run_mac_tests.sh`: إدراج اختبارات الاستعادة.
- ملفات التوثيق: `CHANGELOG_AR.md`, `PROJECT_STATUS.md`, `README.md`, `README_AR.md`, `SESSION_RECOVERY_AR.md`.

## ملف Workflow

ملف `.github/workflows/main.yml` لم يتغير. يمكنك إبقاء ملفك الحالي كما هو، بشرط أن يكون في المسار نفسه:

` .github/workflows/main.yml `

لا تنشئ ملف `main.yml` آخر بالاسم نفسه.

## طريقة الرفع

1. احتفظ بنسخة خارجية من قائمة MAC المحلية، ولا تحذف التطبيق من الهاتف الآن.
2. فك ضغط الحزمة على الحاسوب.
3. افتح مجلد `AX12StrictGuardroot-main` وتأكد أن `app` و`tests` و`settings.gradle` ظاهرة مباشرة داخله.
4. ارفع محتويات المجلد إلى جذر المستودع الحالي، مع استبدال الملفات المتطابقة بالاسم والمسار. تأكد من تضمين الملف الجديد `GuardRecoveryPolicy.java` والاختبار الجديد `GuardRecoveryPolicyTest.java`.
5. إذا لم ترد استبدال Workflow، اترك `.github/workflows/main.yml` الموجود في GitHub كما هو.
6. نفّذ Commit changes ثم افتح Actions وانتظر نجاح Build Android APK.
7. نزّل APK من Artifacts. لا تثبته قبل التأكد من نجاح Workflow.

## قبل اعتماد الإصدار على الهاتف

- فعّل خيار حفظ كلمة المرور المشفرة في Android Keystore عند تشغيل الحارس إذا أردت استعادة الخدمة بعد إعادة إنشاء Android لها.
- من زر «فتح إعدادات التطبيق والبطارية»، اجعل استخدام البطارية غير مقيّد واسمح بالتشغيل التلقائي إن توفر.
- لا تستخدم Force Stop؛ Android يمنع إعادة تشغيل التطبيق قسراً حتى يفتحه المستخدم مجدداً.
- اختبر أولاً استمرار الحارس والشاشة مطفأة، ثم اختبر فتح إعدادات الراوتر من Chrome. إذا كان Firmware لا يسمح إلا بجلسة إدارة واحدة فقد تتعارض جلسة Chrome مع جلسة التطبيق؛ لا يمكن ضمان التزامن دون دعم الراوتر له.

## الاختبارات

نجحت 108 اختبارات Java منطقية عند تنفيذ:

`sh tests/run_mac_tests.sh`

لم يُبنَ APK محلياً لعدم توفر Android SDK وGradle في بيئة التحرير؛ يجب أن يؤكد GitHub Actions بناء Android.
