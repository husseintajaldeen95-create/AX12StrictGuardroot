import com.ax12.strictguard.MacPolicy;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class MacPolicyTest {
    private static int tests=0;
    private static void check(boolean ok,String why){tests++;if(!ok)throw new AssertionError(why);}
    private static void invalid(String mac){try{MacPolicy.normalize(mac);throw new AssertionError("Accepted invalid "+mac);}catch(IllegalArgumentException expected){tests++;}}
    public static void main(String[] args){
        check("AA-BB-CC-DD-EE-F0".equals(MacPolicy.normalize("aa:bb:cc:dd:ee:f0")),"normalize :");
        check("AA-BB-CC-DD-EE-F0".equals(MacPolicy.normalize("aa-bb-cc-dd-ee-f0")),"normalize -");
        invalid("01:11:22:33:44:55");invalid("00:00:00:00:00:00");invalid("ABCD");
        check(MacPolicy.locallyAdministered("1A:E5:F2:25:1C:5B"),"LAA detection");
        check(!MacPolicy.locallyAdministered("08:E5:F2:25:1C:5B"),"UAA detection");
        Set<String> allowed=MacPolicy.canonical(Arrays.asList("08:11:22:33:44:55","0A:11:22:33:44:66"));
        List<String> rogue=MacPolicy.unauthorized(Arrays.asList("08-11-22-33-44-55","1A-E5-F2-25-1C-5B","00-11-22-33-44-77"),allowed,"00-11-22-33-44-77");
        check(rogue.size()==1 && "1A-E5-F2-25-1C-5B".equals(rogue.get(0)),"protected host excluded");
        check(allowed.size()==2,"trusted remains unchanged");
        System.out.println("PASS: "+tests+" MacPolicy tests");
    }
}
