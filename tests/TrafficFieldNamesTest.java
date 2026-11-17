import com.ax12.strictguard.TrafficFieldNames;

public final class TrafficFieldNamesTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(TrafficFieldNames.matches("downloadSpeed", "download_speed"), "camelCase download field");
        check(TrafficFieldNames.matches("uploadSpeed", "upload_speed"), "camelCase upload field");
        check(TrafficFieldNames.matches("trafficUsage", "traffic_usage"), "camelCase usage field");
        check(TrafficFieldNames.matches("Download-Speed", "download_speed"), "separator normalization");
        check(!TrafficFieldNames.matches("downloadSpeed", "upload_speed"), "different fields remain distinct");
        System.out.println("PASS: " + checks + " traffic field-name checks");
    }
}
