AX12 Strict Guard — تعليمات البناء السحابي

1) ارفع محتويات هذا المجلد مباشرة إلى جذر مستودع GitHub.
   يجب أن يظهر app/build.gradle في جذر المستودع تحت مجلد app.
   لا ترفع مجلد AX12StrictGuard كطبقة إضافية داخل المستودع.

2) احتفظ بمجلد .github/workflows كما هو.
   يستخدم workflow إصدار android-actions/setup-android@v4.
   لا تستبدله بـ android-actions/setup-android@v3؛ سجل البناء السابق
   أظهر أن الإصدار v3 حاول تثبيت حزمة SDK القديمة "tools" غير المتاحة.

3) بعد رفع الملفات، افتح GitHub ثم Actions ثم Build Android APK
   وشغّل workflow أو ادفع commit جديداً.

4) عند نجاح البناء، افتح صفحة تشغيل workflow ونزّل Artifact باسم:
   AX12StrictGuard-debug-apk

5) أمر البناء الصحيح هو:
   gradle --no-daemon :app:assembleDebug

ملاحظة:
تحتوي هذه النسخة أيضاً على تصحيح مسار تسجيل الدخول إلى LuCI، والاحتفاظ
بـ Cookies، ورسائل تشخيص HTTP 403، واختبارات تمنع تكرار /login/login.
نجاح تجميع APK لا يثبت توافق واجهة LuCI مع Firmware الراوتر.
اختبر الاتصال والقراءة فقط أولاً؛ إذا ظهر HTTP 403 فلا تشغّل الحارس.
