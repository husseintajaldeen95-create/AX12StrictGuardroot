package com.ax12.strictguard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Pure Java: deterministic trust policy. The router list NEVER becomes the trust source. */
public final class MacPolicy {
    private MacPolicy() {}

    public static String normalize(String input) {
        if (input == null) throw new IllegalArgumentException("MAC مفقود");
        String s = input.trim().replace(":", "").replace("-", "").toUpperCase(Locale.ROOT);
        if (!s.matches("[0-9A-F]{12}")) throw new IllegalArgumentException("عنوان MAC غير صحيح");
        int first = Integer.parseInt(s.substring(0, 2), 16);
        if ((first & 0x01) != 0) throw new IllegalArgumentException("عنوان بث/متعدد الإرسال غير صالح لجهاز واحد");
        if (s.equals("000000000000")) throw new IllegalArgumentException("عنوان MAC صفري غير صالح");
        StringBuilder out = new StringBuilder(17);
        for (int i=0; i<12; i+=2) {
            if (i > 0) out.append('-');
            out.append(s, i, i+2);
        }
        return out.toString();
    }

    public static boolean locallyAdministered(String mac) {
        return (Integer.parseInt(normalize(mac).substring(0,2),16) & 0x02) != 0;
    }

    public static Set<String> canonical(Collection<String> addresses) {
        Set<String> out = new LinkedHashSet<>();
        for (String m: addresses) out.add(normalize(m));
        return Collections.unmodifiableSet(out);
    }

    public static List<String> unauthorized(Collection<String> actual, Collection<String> trusted, String protectedHost) {
        Set<String> wanted = canonical(trusted);
        String protect = null;
        try { protect = normalize(protectedHost); } catch (Exception ignored) {}
        LinkedHashSet<String> unauthorized = new LinkedHashSet<>();
        for (String m: actual) {
            String n = normalize(m);
            if (!wanted.contains(n) && !n.equals(protect)) unauthorized.add(n);
        }
        return new ArrayList<>(unauthorized);
    }
}
