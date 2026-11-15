import com.ax12.strictguard.AllowListSyncPlan;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

public final class AllowListSyncPlanTest {
    private static int checks = 0;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void rejects(Runnable action, String message) {
        try { action.run(); throw new AssertionError(message); }
        catch (IllegalArgumentException | IllegalStateException expected) { checks++; }
    }
    public static void main(String[] args) {
        String admin = "08-11-22-33-44-01";
        String old = "08-11-22-33-44-02";
        String add = "08-11-22-33-44-03";
        String random = "1A-11-22-33-44-04";

        AllowListSyncPlan plan = AllowListSyncPlan.create(
                Arrays.asList(admin, add),
                Arrays.asList(admin, old),
                admin);
        check(plan.desired.size() == 2, "desired list size");
        check(plan.toAdd.equals(Collections.singleton(add)), "only selected missing MAC is added");
        check(plan.toRemove.equals(Collections.singleton(old)), "only unchecked router MAC is removed");
        check(!plan.toRemove.contains(admin), "protected router host never removed");

        AllowListSyncPlan unchanged = AllowListSyncPlan.create(
                Arrays.asList(admin, old), Arrays.asList(old, admin), admin);
        check(unchanged.toAdd.isEmpty() && unchanged.toRemove.isEmpty(), "identical lists produce no operations");

        rejects(() -> AllowListSyncPlan.create(
                Arrays.asList(add), Arrays.asList(admin, old), admin),
                "must reject removing protected host from desired list");
        rejects(() -> AllowListSyncPlan.create(
                Arrays.asList(admin, random), Arrays.asList(admin), admin),
                "must reject locally administered randomized MAC");
        rejects(() -> AllowListSyncPlan.create(
                Collections.emptyList(), Arrays.asList(admin), admin),
                "must reject empty allow list");
        rejects(() -> AllowListSyncPlan.create(
                Arrays.asList(admin), Arrays.asList(admin), random),
                "must reject randomized protected host");
        rejects(() -> AllowListSyncPlan.create(
                null, Arrays.asList(admin), admin),
                "must reject null desired list");

        try {
            plan.toAdd.add(old);
            throw new AssertionError("plan sets must be immutable");
        } catch (UnsupportedOperationException expected) {
            checks++;
        }

        System.out.println("PASS: " + checks + " AllowListSyncPlan tests");
    }
}
