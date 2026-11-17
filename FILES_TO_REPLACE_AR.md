# تحديث AX12 Strict Guard إلى 0.2.7.1-live-network-status-fix

## سبب خطأ البناء السابق

ظهر في GitHub Actions خطأ داخل:

`app/src/main/java/com/ax12/strictguard/TrafficUsageStore.java`

هذا الملف لم يعد جزءاً من مصدر 0.2.7؛ فقد أزيلت ميزة الاستهلاك التاريخي واستُبدلت ببطاقات الحالة اللحظية. لكن رفع ملفات ZIP عبر واجهة GitHub يستبدل الملفات ذات المسارات المتطابقة ولا يحذف الملفات القديمة التي لا توجد في الحزمة. لذلك بقي ملف `TrafficUsageStore.java` القديم في المستودع، وكان يستدعي الحقول القديمة `hasRates` و`downloadBytesPerSecond` و`uploadBytesPerSecond`، بينما أصبحت حقول العينة الحالية `hasRawRates` و`rawDownload` و`rawUpload`.

## الملفات القديمة التي يجب حذفها من المستودع

احذف هذه الملفات الثلاثة إن كانت موجودة:

1. `app/src/main/java/com/ax12/strictguard/TrafficUsageStore.java`
2. `app/src/main/java/com/ax12/strictguard/TrafficRateMath.java`
3. `tests/TrafficRateMathTest.java`

من صفحة GitHub افتح الملف، ثم اختر Edit (رمز القلم)، وبعدها Delete this file، ثم Commit changes. كرر ذلك لكل ملف. لا تحذف `TrafficMonitorProbe.java` أو `TrafficFieldNames.java` أو `LiveTrafficStore.java` أو `LiveTrafficStatus.java`؛ هذه ملفات مستخدمة في الإصدار الحالي.

## ما الذي تغير في 0.2.7.1؟

- لم نعدّل منطق الحارس الصارم أو قواعد MAC أو آلية المصادقة.
- أضفنا خطوة وقائية إلى `.github/workflows/main.yml` تحذف ملفات الاستهلاك التاريخي القديمة من بيئة البناء قبل ترجمة Android؛ لذلك لن يتكرر الخطأ إذا بقيت هذه الملفات في المستودع سهواً.
- بقيت مراقبة النشاط اللحظي فقط، دون سجل يومي أو أسبوعي أو شهري ودون إجماليات KB/MB/GB.
- تحديث `versionCode` إلى 20 و`versionName` إلى `0.2.7.1-live-network-status-fix`.

## طريقة التحديث

1. احتفظ بنسخة خارجية من قائمة MAC المحلية قبل تحديث APK.
2. فك ضغط الحزمة وافتح مجلد `AX12StrictGuardroot-main`.
3. ارفع محتويات هذا المجلد إلى جذر مستودع `AX12StrictGuardroot`، وليس المجلد الأب نفسه.
4. استبدل `.github/workflows/main.yml` بالنسخة الموجودة في الحزمة؛ هذا مهم لإضافة خطوة التنظيف الوقائية.
5. احذف الملفات القديمة الثلاثة المذكورة أعلاه من المستودع، حتى يبقى المصدر نظيفاً خارج بيئة GitHub Actions أيضاً.
6. اضغط Commit changes، ثم افتح Actions وشغّل Build Android APK.
7. إذا نجح البناء، نزّل `AX12StrictGuardroot-debug-apk` من Artifacts.

## التحقق والسلامة

- اختبارات Java المنطقية وفحوص بنية الواجهة تُشغّل بواسطة `sh tests/run_mac_tests.sh`.
- خطوة التنظيف في GitHub Actions لا تتصل بالراوتر ولا تغيّر إعداداته؛ إنها تزيل ملفات Java قديمة من مساحة البناء فقط.
- لم يتغير أي طلب كتابة إلى الراوتر. مراقبة النشاط اختيارية وقراءة فقط، وفشلها لا ينبغي أن يعطل دورة حماية Allow List.
- لا تعتبر النسخة معتمدة قبل نجاح GitHub Actions واختبار التطبيق على الهاتف والراوتر الفعليين.
