import com.ax12.strictguard.AuthAttemptPolicy;

public final class AuthAttemptPolicyTest {
    private static int count;
    private static void ok(boolean yes, String message) { count++; if (!yes) throw new AssertionError(message); }
    public static void main(String[] args) {
        ok(AuthAttemptPolicy.isDenied("HTTP 403 from router"), "403 triggers protection");
        ok(AuthAttemptPolicy.isDenied("exceeded max attempts"), "max attempts triggers protection");
        ok(!AuthAttemptPolicy.isDenied("connection timeout"), "timeout is not known lockout");
        ok(AuthAttemptPolicy.cooldownMs("exceeded max attempts") == 1800000L, "30-minute cooldown");
        ok(AuthAttemptPolicy.cooldownMs("bad format") == 120000L, "2-minute cooldown");
        ok(AuthAttemptPolicy.remainingMs(1000L, 5000L) == 4000L, "remaining correctly computed");
        ok(AuthAttemptPolicy.remainingMs(6000L, 5000L) == 0L, "expired is zero");
        System.out.println("PASS: " + count + " AuthAttemptPolicy tests");
    }
}
