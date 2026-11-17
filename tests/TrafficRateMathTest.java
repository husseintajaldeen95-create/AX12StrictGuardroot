import com.ax12.strictguard.TrafficRateMath;

public final class TrafficRateMathTest {
    public static void main(String[] args) {
        check(TrafficRateMath.integrateBytes(1000d, 1000d, 15000L, 90000L) == 15000L,
                "constant 1000 B/s for 15 s");
        check(TrafficRateMath.integrateBytes(1000d, 3000d, 15000L, 90000L) == 30000L,
                "trapezoid average rate");
        check(TrafficRateMath.integrateBytes(1000d, 3000d, 91000L, 90000L) == 0L,
                "long gaps are not invented");
        check(TrafficRateMath.integrateBytes(-1d, 3000d, 15000L, 90000L) == 0L,
                "negative rate rejected");
        check(TrafficRateMath.integrateBytes(Double.NaN, 3000d, 15000L, 90000L) == 0L,
                "unknown rate rejected");
        check(TrafficRateMath.integrateBytes(1000d, 1000d, 500L, 90000L) == 0L,
                "too-short interval ignored");
        System.out.println("PASS: 6 traffic-rate integration checks");
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError("FAIL: " + label);
    }
}
