AX12 Strict Guard — تعليمات البناء السحابي

1) ارفع محتويات هذا المجلد مباشرة إلى جذر مستودع GitHub.
   يجب أن يظهر app/build.gradle في جذر المستودع تحت مجلد app.
   لا ترفع مجلد AX12StrictGuard كطبقة إضافية داخل المستودع.

2) احتفظ بمجلد .github/workflows كما هو.
   يستخدم workflow إصدار android-actions/setup-android@v4.
   لا تستخدم android-actions/setup-android@v3 في هذا المشروع؛
   سجل البناء المرسل أظهر أنه حاول تثبيت حزمة SDK القديمة "tools"
   التي لم تعد Google توفرها.

3) بعد رفع الملفات، افتح GitHub ثم Actions ثم Build Android APK
   وشغّل workflow أو ادفع commit جديداً.

4) عند نجاح البناء، افتح صفحة تشغيل workflow ونزّل Artifact باسم:
   AX12StrictGuard-debug-apk

5) أمر البناء الصحيح هو:
   gradle --no-daemon :app:assembleDebug

ملاحظة:
هذا التعديل يعالج بنية المشروع وإعداد البناء فقط. نجاح تجميع APK
لا يثبت توافق واجهة LuCI مع إصدار Firmware الموجود على الراوتر.
يجب اختبار الاتصال بوضع القراءة فقط قبل تشغيل الحارس.
