import com.ax12.strictguard.AuthErrorLabel;
public final class AuthErrorLabelTest {
    public static void main(String[] args) {
        check("17", "17");
        check("timeout", "timeout");
        check("bad password", "bad password");
        check("exceeded max attempts", "exceeded max attempts");
        check("too many attempts", "too many attempts");
        check("{\"stok\":\"secret\"}", "قيمة محجوبة حفاظاً على بيانات الجلسة");
        check("abc.def.ghi", "قيمة محجوبة حفاظاً على بيانات الجلسة");
        check("session secret", "قيمة محجوبة حفاظاً على بيانات الجلسة");
        check(null, "غير معلن");
        System.out.println("AuthErrorLabel: 9 checks passed");
    }
    private static void check(String value, String expected) {
        if (!expected.equals(AuthErrorLabel.safeValue(value)))
            throw new AssertionError(value + " unexpectedly returned " + AuthErrorLabel.safeValue(value));
    }
}
