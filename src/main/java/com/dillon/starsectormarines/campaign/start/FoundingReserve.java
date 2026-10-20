package com.dillon.starsectormarines.campaign.start;

import java.util.Map;
import java.util.Objects;

/**
 * Earmarked yard payment, never part of the fleet's spendable credit account. Castle's chits come to
 * {@link #CREDITS}, short of the conversion by exactly its pressure test, which the yard waives when
 * the reserve pays ({@link CompanyYard#price}); the protected start therefore converts on an empty account.
 */
public final class FoundingReserve {
    private static final String KEY = "sm_founding_yard_reserve";
    public static final int CREDITS = 4862;
    private FoundingReserve() { }
    public static void issue(Map<String, Object> saved) {
        saved.putIfAbsent(KEY, CREDITS);
    }
    public static boolean protectedStart(Map<String, Object> saved) { return saved.containsKey(KEY); }
    public static int available(Map<String, Object> saved, String shipId) {
        if (shipId == null || !Objects.equals(shipId, saved.get(CerberusCompanyStart.SHIP_KEY))) return 0;
        Object value = saved.get(KEY);
        return value instanceof Number number
                ? Math.max(0, Math.min(CREDITS, number.intValue())) : 0;
    }
    public static void consume(Map<String, Object> saved, String shipId, int amount) {
        int available = available(saved, shipId);
        if (amount < 0 || amount > available) throw new IllegalArgumentException("Invalid founding reserve draw");
        if (amount > 0) saved.put(KEY, available - amount);
    }
}
