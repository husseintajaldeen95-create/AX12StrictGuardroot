package com.ax12.strictguard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Pure Java MAC validation. Router observations may be local; trusted identities may not. */
public final class MacPolicy {
    private MacPolicy() {}

    /** Normalize a syntactically valid unicast MAC. Local-administered addresses remain observable. */
    public static String normalize(String input) {
        if (input == null) throw new IllegalArgumentException("MAC مفقود");
        String s = input.trim().replace(":", "").replace("-", "").toUpperCase(Locale.ROOT);
        if (!s.matches("[0-9A-F]{12}")) throw new IllegalArgumentException("عنوان MAC غير صحيح");
        int first = Integer.parseInt(s.substring(0, 2), 16);
        if ((first & 0x01) != 0) throw new IllegalArgumentException("عنوان بث/متعدد الإرسال غير صالح لجهاز واحد");
        if (s.equals("000000000000")) throw new IllegalArgumentException("عنوان MAC صفري غير صالح");
        StringBuilder out = new StringBuilder(17);
        for (int i = 0; i < 12; i += 2) {
            if (i > 0) out.append('-');
            out.append(s, i, i + 2);
        }
        return out.toString();
    }

    /**
     * Validate an address for the local trust list or router allow-list writes.
     * The U/L bit must be zero, rejecting private/randomized (locally administered) MACs.
     * This does NOT prove that a globally administered MAC is genuine or cannot be spoofed.
     */
    public static String normalizeTrusted(String input) {
        String normalized = normalize(input);
        if (locallyAdministered(normalized)) {
            throw new IllegalArgumentException("مرفوض: عنوان MAC محلي الإدارة (Private/Randomized MAC). أوقف العنوان العشوائي في الجهاز واستخدم عنوان MAC العالمي؛ لا يمكن إثبات أصالة العنوان بمجرد فحصه.");
        }
        return normalized;
    }

    public static boolean locallyAdministered(String mac) {
        return (Integer.parseInt(normalize(mac).substring(0, 2), 16) & 0x02) != 0;
    }

    /** Canonicalize MACs that may come from router observations, including randomized MACs. */
    public static Set<String> canonical(Collection<String> addresses) {
        Set<String> out = new LinkedHashSet<>();
        for (String m : addresses) out.add(normalize(m));
        return Collections.unmodifiableSet(out);
    }

    /** Canonicalize only globally administered unicast MACs for the trust list. */
    public static Set<String> canonicalTrusted(Collection<String> addresses) {
        Set<String> out = new LinkedHashSet<>();
        for (String m : addresses) out.add(normalizeTrusted(m));
        return Collections.unmodifiableSet(out);
    }

    public static List<String> unauthorized(Collection<String> actual, Collection<String> trusted, String protectedHost) {
        Set<String> wanted = canonicalTrusted(trusted);
        String protect = null;
        try { protect = normalizeTrusted(protectedHost); } catch (Exception ignored) {}
        LinkedHashSet<String> unauthorized = new LinkedHashSet<>();
        for (String m : actual) {
            String n = normalize(m);
            if (!wanted.contains(n) && !n.equals(protect)) unauthorized.add(n);
        }
        return new ArrayList<>(unauthorized);
    }
}
