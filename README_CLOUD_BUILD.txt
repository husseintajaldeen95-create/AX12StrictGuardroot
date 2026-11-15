AX12 Strict Guard 0.2.1 — تعليمات البناء السحابي

1) ارفع محتويات هذا المجلد مباشرة إلى جذر مستودع GitHub.
   يجب أن يظهر app/build.gradle في جذر المستودع تحت مجلد app.
   لا ترفع مجلد AX12StrictGuardroot-main كطبقة إضافية داخل المستودع.

2) احتفظ بمجلد .github/workflows كما هو.
   ملف .github/workflows/main.yml الحالي لم يتغير في هذا الإصدار.
   يستخدم workflow إصدار android-actions/setup-android@v4.

3) بعد رفع الملفات، افتح GitHub ثم Actions ثم Build Android APK
   وشغّل workflow أو ادفع commit جديداً.

4) عند نجاح البناء، افتح صفحة تشغيل workflow ونزّل Artifact باسم:
   AX12StrictGuardroot-debug-apk

5) أمر البناء الصحيح هو:
   gradle --no-daemon :app:assembleDebug

ملاحظة:
يحتوي الإصدار 0.2.1 على إعادة إنشاء جلسة الراوتر بعد timeout أو انتهاء الجلسة،
مع إبقاء خدمة Foreground فعالة وعدم حذف كلمة المرور المشفرة بسبب انقطاع الجلسة.
نجاح تجميع APK لا يثبت توافق واجهة LuCI مع Firmware الراوتر أو دعم جلستي إدارة
متزامنتين من التطبيق والمتصفح. اختبر الاتصال والقراءة فقط أولاً.
