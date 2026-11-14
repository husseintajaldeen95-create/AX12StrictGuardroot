import com.ax12.strictguard.RouterUrl;

public final class RouterUrlTest {
    private static int tests = 0;
    private static void check(boolean condition, String message) {
        tests++;
        if (!condition) throw new AssertionError(message);
    }
    private static void fails(Runnable action, String message) {
        try { action.run(); throw new AssertionError(message); }
        catch (IllegalStateException expected) { tests++; }
    }
    public static void main(String[] args) {
        String host = "192.168.0.1";
        check(("http://192.168.0.1/cgi-bin/luci/;stok=/login?form=keys")
                .equals(RouterUrl.build(host, "login?form=keys", null, true)),
                "login keys route must not duplicate /login");
        check(("http://192.168.0.1/cgi-bin/luci/;stok=/login?form=auth")
                .equals(RouterUrl.build(host, "login?form=auth", null, true)),
                "login auth route");
        check(("http://192.168.0.1/cgi-bin/luci/;stok=/login?form=login")
                .equals(RouterUrl.build(host, "login?form=login", null, true)),
                "login submit route");
        check(("http://192.168.0.1/cgi-bin/luci/;stok=abc123/admin/access_control?form=enable")
                .equals(RouterUrl.build(host, "admin/access_control?form=enable", "abc123", false)),
                "authenticated route includes returned token");
        check(("http://192.168.0.1/cgi-bin/luci/;stok=/login?form=keys&operation=read")
                .equals(RouterUrl.build(host, "login?form=keys&operation=read", null, true)),
                "SG keys query-form route");
        check(("http://192.168.0.1/cgi-bin/luci/;stok=/login?form=auth&operation=read")
                .equals(RouterUrl.build(host, "login?form=auth&operation=read", null, true)),
                "SG auth query-form route");
        fails(() -> RouterUrl.build(host, "admin/access_control?form=enable", null, false),
                "authenticated route must require token");
        System.out.println("PASS: " + tests + " RouterUrl tests");
    }
}
