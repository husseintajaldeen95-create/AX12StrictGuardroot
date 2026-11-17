import com.ax12.strictguard.LiveTrafficStatus;

public final class LiveTrafficStatusTest {
    private static int passed;
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        passed++;
    }
    public static void main(String[] args) {
        check(LiveTrafficStatus.classify(false, true, false, 0, 0)
                == LiveTrafficStatus.State.UNKNOWN, "stale online snapshot with no activity evidence must be unknown");
        check(LiveTrafficStatus.classify(false, false, true, 1, 0)
                == LiveTrafficStatus.State.ONLINE_ACTIVE, "fresh non-zero rate is direct activity evidence");
        check(LiveTrafficStatus.classify(true, false, false, 0, 0)
                == LiveTrafficStatus.State.OFFLINE, "absent from fresh complete snapshot is offline");
        check(LiveTrafficStatus.classify(true, true, true, 0, 0)
                == LiveTrafficStatus.State.ONLINE_IDLE, "connected zero rates is idle");
        check(LiveTrafficStatus.classify(true, true, true, 15, 0)
                == LiveTrafficStatus.State.ONLINE_ACTIVE, "download activity is active");
        check(LiveTrafficStatus.classify(true, true, true, 0, 7)
                == LiveTrafficStatus.State.ONLINE_ACTIVE, "upload activity is active");
        check(LiveTrafficStatus.classify(true, true, false, Double.NaN, Double.NaN)
                == LiveTrafficStatus.State.ONLINE_RATE_UNKNOWN, "missing rate remains unknown");
        check(LiveTrafficStatus.sortRank(LiveTrafficStatus.State.ONLINE_ACTIVE)
                < LiveTrafficStatus.sortRank(LiveTrafficStatus.State.OFFLINE), "active devices sort before offline");
        System.out.println("LiveTrafficStatusTest: " + passed + " checks passed");
    }
}
