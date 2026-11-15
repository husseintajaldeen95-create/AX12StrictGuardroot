public class GuardRecoveryPolicyTest {
    private static int passed;
    private static void check(String name, long actual, long expected) {
        if (actual != expected) throw new AssertionError(name + ": expected=" + expected + " actual=" + actual);
        passed++;
    }
    public static void main(String[] args) {
        check("network timeout reconnects after 30 seconds",
                com.ax12.strictguard.GuardRecoveryPolicy.reconnectDelayMs("Read timed out", 0L), 30_000L);
        check("invalid authenticated router response waits two minutes",
                com.ax12.strictguard.GuardRecoveryPolicy.reconnectDelayMs("رفض الراوتر الطلب أثناء admin/access_control", 0L), 120_000L);
        check("login local cooldown is respected",
                com.ax12.strictguard.GuardRecoveryPolicy.reconnectDelayMs("network", 120_000L), 120_000L);
        check("authentication denial waits at least 10 minutes",
                com.ax12.strictguard.GuardRecoveryPolicy.reconnectDelayMs("HTTP 403", 0L), 600_000L);
        check("server Retry-After longer than local wait is respected",
                com.ax12.strictguard.GuardRecoveryPolicy.reconnectDelayMs("HTTP 403 [router-retry-seconds:3600]", 600_000L), 3_600_000L);
        check("general repeated failures reconnect after two minutes",
                com.ax12.strictguard.GuardRecoveryPolicy.generalFailureDelayMs(0L), 120_000L);
        check("longer gate cooldown is respected for general failures",
                com.ax12.strictguard.GuardRecoveryPolicy.generalFailureDelayMs(900_000L), 900_000L);
        System.out.println("GuardRecoveryPolicyTest: " + passed + " checks passed");
    }
}
