**الحالة الحالية:** 0.1.2-auth-safety. اكتشاف توافق المصادقة غير مؤكد على الراوتر الحقيقي؛ المراقبة الصارمة يجب أن تبقى مغلقة حتى نجاح القراءة دون أخطاء.

# Status / حالة التنفيذ

- Date: 2026-11-14
- Android Studio source project: PRESENT
- Repository layout: root-level `app/`, `settings.gradle`, `build.gradle`, and `.github/workflows/main.yml`
- GitHub Actions workflow: uses `android-actions/setup-android@v4` and runs `:app:assembleDebug`
- MAC policy tests: 9 PASSED
- LuCI URL regression tests: 5 PASSED
- All application Java files: syntax compilation PASSED against lightweight Android/JSON stubs (not a substitute for Android Gradle build)
- Router login URL: corrected to `;stok=/login?form=keys` (prevents duplicated `/login/login`)
- Authentication profile: read-only detection of `EU CE RED` / `SG CLS L1 STAGE2`; enhanced SHA-256 + AES-CBC + RSA-OAEP/HMAC path implemented, with legacy RSA-PKCS1 fallback
- Router session cookies: captured and sent on subsequent requests; best-effort logout on client close
- HTTP error diagnostics: includes request path, status, redirect target when present, and a short response excerpt
- Automatic retry: raw HTTP 403 is not retried by silently starting another login session
- Guard safety: start/write actions require a successful read-only test with the same host and in-memory password fingerprint; requires a parseable router-protected host MAC that is explicitly trusted; at most one untrusted entry is removed per polling cycle
- Router compatibility: NOT VERIFIED on the user's live Archer AX12 EU V1.0 / Firmware 1.5.3 Build 20261008
- Android APK: NOT BUILT in this environment (Android SDK and Gradle are unavailable)
- Live router test: NOT PERFORMED; no router password or network credentials were supplied

## Important

The enhanced protocol selection is based on the certification value returned by a read-only router endpoint. The implementation has not been tested against the user's live firmware, so it must be treated as experimental. First run the read-only connection test. If any HTTP 403, decryption, or schema error remains, do not start the strict guard. The app must not be treated as a security boundary until a live read-only test succeeds and its returned settings/list are checked against the router's web interface.
