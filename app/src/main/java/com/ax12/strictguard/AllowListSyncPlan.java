package com.ax12.strictguard;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Pure policy for an explicit user-requested reconciliation.
 * The local trust list is the desired state; the router allow-list is the
 * executable state. This class performs no network or storage operations.
 */
public final class AllowListSyncPlan {
    public final Set<String> desired;
    public final Set<String> toAdd;
    public final Set<String> toRemove;
    public final String protectedHost;

    private AllowListSyncPlan(Set<String> desired, Set<String> toAdd,
                              Set<String> toRemove, String protectedHost) {
        this.desired = immutable(desired);
        this.toAdd = immutable(toAdd);
        this.toRemove = immutable(toRemove);
        this.protectedHost = protectedHost;
    }

    public static AllowListSyncPlan create(Collection<String> desiredAddresses,
                                           Collection<String> routerAllowedAddresses,
                                           String protectedHostAddress) {
        if (desiredAddresses == null || routerAllowedAddresses == null)
            throw new IllegalArgumentException("بيانات المزامنة غير مكتملة");

        Set<String> desired = MacPolicy.canonicalTrusted(desiredAddresses);
        if (desired.isEmpty())
            throw new IllegalArgumentException("لا يمكن تطبيق قائمة سماح فارغة");

        String protectedHost = MacPolicy.normalizeTrusted(protectedHostAddress);
        if (!desired.contains(protectedHost))
            throw new IllegalArgumentException("يجب إبقاء MAC جهاز إدارة الراوتر محدداً ضمن قائمة السماح لتجنب فقدان الوصول إلى الراوتر");

        Set<String> current = MacPolicy.canonical(routerAllowedAddresses);
        Set<String> add = new LinkedHashSet<>(desired);
        add.removeAll(current);

        Set<String> remove = new LinkedHashSet<>(current);
        remove.removeAll(desired);
        if (remove.contains(protectedHost))
            throw new IllegalStateException("رفضت خطة المزامنة إزالة جهاز إدارة الراوتر");

        return new AllowListSyncPlan(desired, add, remove, protectedHost);
    }

    private static Set<String> immutable(Collection<String> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}
