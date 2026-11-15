import com.ax12.strictguard.ParentalControlProbe;

public final class ParentalControlProbeTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(ParentalControlProbe.candidates().size() == 4, "four read-only candidates");
        check("read".equals(ParentalControlProbe.candidates().get(0).operation), "enable is read only");
        check("load".equals(ParentalControlProbe.candidates().get(2).operation), "device list is load only");
        check(ParentalControlProbe.classify(true, null, true)
                == ParentalControlProbe.Outcome.READABLE_WITH_DATA, "success with data");
        check(ParentalControlProbe.classify(true, null, false)
                == ParentalControlProbe.Outcome.READABLE_EMPTY, "success empty");
        check(ParentalControlProbe.classify(false, "no such callback", false)
                == ParentalControlProbe.Outcome.UNSUPPORTED, "unsupported endpoint");
        check(ParentalControlProbe.classify(false, "invalid args", false)
                == ParentalControlProbe.Outcome.NEEDS_ARGUMENT, "requires arguments");
        check(ParentalControlProbe.classify(false, "permission denied", false)
                == ParentalControlProbe.Outcome.REFUSED, "permission denied");
        check(ParentalControlProbe.classify(false, "mystery", false)
                == ParentalControlProbe.Outcome.INCONCLUSIVE, "unknown refusal");
        check(ParentalControlProbe.isSessionFailure("timeout"), "timeout invalidates session");
        check(ParentalControlProbe.isSessionFailure("permission denied"), "permission denied invalidates session");
        check(!ParentalControlProbe.isSessionFailure("no such callback"), "missing form is not session failure");
        check(ParentalControlProbe.isScheduleFieldName("bedtime_start_time"), "bedtime field");
        check(ParentalControlProbe.isScheduleFieldName("weekend_limit"), "weekend field");
        check(ParentalControlProbe.isScheduleFieldName("effective_time"), "effective time field");
        check(ParentalControlProbe.isScheduleFieldName("startTime"), "camel case start time field");
        check(ParentalControlProbe.isScheduleFieldName("days"), "days field");
        check(!ParentalControlProbe.isScheduleFieldName("owner_name"), "owner name is not a schedule field");
        check(!ParentalControlProbe.isScheduleFieldName("time_password"), "sensitive field values are never reported");
        System.out.println("ParentalControlProbeTest: " + checks + " checks passed");
    }
}
