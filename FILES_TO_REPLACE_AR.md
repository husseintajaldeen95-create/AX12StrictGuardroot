# ما الذي يُرفع؟

هذا تحديث جزئي للمستودع **AX12StrictGuardroot**. كل ملفات الحزمة تحت المسارات نفسها الموجودة في المستودع، لذا يمكن رفعها فوق الملفات القديمة مباشرةً؛ لا تحتاج إلى حذف المستودع أو المجلدات.

الملفات المعدلة:
- `app/src/main/java/com/ax12/strictguard/RouterClient.java`
- `app/src/main/java/com/ax12/strictguard/AuthAttemptGate.java`
- `app/src/main/java/com/ax12/strictguard/GuardService.java`
- `app/src/main/java/com/ax12/strictguard/MainActivity.java`
- `app/build.gradle`
- `PROJECT_STATUS.md`, `CHANGELOG_AR.md`, `README_AR.md` (تحديث التوثيق)

لا تعديل على `.github/workflows/main.yml`، ولا على قائمة الثقة المحلية.

**تنبيه:** هذا إصدار احترازي للتعامل مع تداخل الجلسات والفشل؛ لا يثبت حل رفض المصادقة مع Firmware قبل إجراء اختبار على الراوتر.
