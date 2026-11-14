import com.ax12.strictguard.MacPolicy;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class MacPolicyTest {
    private static int tests=0;
    private static void check(boolean ok,String why){tests++;if(!ok)throw new AssertionError(why);}
    private static void invalid(String mac){try{MacPolicy.normalize(mac);throw new AssertionError("Accepted invalid "+mac);}catch(IllegalArgumentException expected){tests++;}}
    private static void rejectsTrusted(String mac){try{MacPolicy.normalizeTrusted(mac);throw new AssertionError("Accepted local-administered trusted MAC "+mac);}catch(IllegalArgumentException expected){tests++;}}
    public static void main(String[] args){
        check("AA-BB-CC-DD-EE-F0".equals(MacPolicy.normalize("aa:bb:cc:dd:ee:f0")),"normalize :");
        check("AA-BB-CC-DD-EE-F0".equals(MacPolicy.normalize("aa-bb-cc-dd-ee-f0")),"normalize -");
        invalid("01:11:22:33:44:55");invalid("00:00:00:00:00:00");invalid("ABCD");
        check(MacPolicy.locallyAdministered("1A:E5:F2:25:1C:5B"),"LAA detection");
        check(MacPolicy.locallyAdministered("0A:11:22:33:44:66"),"0A is locally administered");
        check(!MacPolicy.locallyAdministered("08:E5:F2:25:1C:5B"),"UAA detection");
        check("08-11-22-33-44-55".equals(MacPolicy.normalizeTrusted("08:11:22:33:44:55")),"globally administered trust accepted");
        rejectsTrusted("1A:E5:F2:25:1C:5B");
        rejectsTrusted("0A:11:22:33:44:66");
        rejectsTrusted("02:11:22:33:44:66");
        try { MacPolicy.canonicalTrusted(Arrays.asList("08:11:22:33:44:55", "1A:E5:F2:25:1C:5B")); throw new AssertionError("canonicalTrusted accepted local MAC"); }
        catch (IllegalArgumentException expected) { tests++; }
        Set<String> allowed=MacPolicy.canonicalTrusted(Arrays.asList("08:11:22:33:44:55","08:11:22:33:44:66"));
        List<String> rogue=MacPolicy.unauthorized(Arrays.asList("08-11-22-33-44-55","1A-E5-F2-25-1C-5B","00-11-22-33-44-77"),allowed,"00-11-22-33-44-77");
        check(rogue.size()==1 && "1A-E5-F2-25-1C-5B".equals(rogue.get(0)),"local-administered MAC is unauthorized");
        check(allowed.size()==2,"trusted remains unchanged");
        try { MacPolicy.unauthorized(Arrays.asList("1A:E5:F2:25:1C:5B"),Arrays.asList("1A:E5:F2:25:1C:5B"),null); throw new AssertionError("randomized MAC became trusted"); }
        catch (IllegalArgumentException expected) { tests++; }
        System.out.println("PASS: "+tests+" MacPolicy tests");
    }
}
