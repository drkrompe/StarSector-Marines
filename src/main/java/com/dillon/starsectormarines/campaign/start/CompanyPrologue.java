package com.dillon.starsectormarines.campaign.start;

import java.util.Map;

/** Primitive save receipt: only newly selected starts opt in; old campaigns stay untouched. */
public final class CompanyPrologue {
    private static final String PAGE = "sm_company_prologue_page";
    private static final String COMPLETE = "sm_company_prologue_complete";
    public static final int PAGE_COUNT = 6;

    private CompanyPrologue() { }

    public static void begin(Map<String, Object> saved) {
        if (!saved.containsKey(COMPLETE)) saved.putIfAbsent(PAGE, 0);
    }

    public static boolean pending(Map<String, Object> saved) {
        return saved.containsKey(CerberusCompanyStart.SHIP_KEY)
                && saved.containsKey(PAGE) && !Boolean.TRUE.equals(saved.get(COMPLETE));
    }

    public static int page(Map<String, Object> saved) {
        Object value = saved.get(PAGE);
        return value instanceof Number n ? clamp(n.intValue()) : 0;
    }

    public static void moveTo(Map<String, Object> saved, int page) {
        if (pending(saved)) saved.put(PAGE, clamp(page));
    }

    public static void finish(Map<String, Object> saved) {
        if (pending(saved)) saved.put(COMPLETE, true);
    }

    private static int clamp(int page) { return Math.max(0, Math.min(PAGE_COUNT - 1, page)); }
}
