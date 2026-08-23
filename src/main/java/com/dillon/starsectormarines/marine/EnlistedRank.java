package com.dillon.starsectormarines.marine;

/**
 * A rank-and-file marine's rank. Distinct from {@link Rank}, which is the
 * officer ladder above it: officers command companies, NCOs lead squads.
 *
 * <p>Rank here follows the billet rather than being awarded on its own — see
 * {@code MarineRoster.refreshLeadership}. The ordinal order is the seniority
 * order, which is what promotion-on-loss sorts by.
 */
public enum EnlistedRank {
    /** Rank and file. */
    MARINE("Marine", "Mar."),
    /** Leads a four-marine fire team. */
    LANCE_CORPORAL("Lance Corporal", "LCpl."),
    /** Leads a squad, and its first fire team with it. */
    CORPORAL("Corporal", "Cpl."),
    /** A squad leader who has become a veteran; the officer's right hand. */
    SERGEANT("Sergeant", "Sgt.");

    private final String displayName;
    private final String abbreviation;

    EnlistedRank(String displayName, String abbreviation) {
        this.displayName = displayName;
        this.abbreviation = abbreviation;
    }

    public String displayName() {
        return displayName;
    }

    /** Short form for a roster row, where the name is what the player is scanning for. */
    public String abbreviation() {
        return abbreviation;
    }

    /** True for the billets that lead something — a fire team or a whole squad. */
    public boolean leads() {
        return this != MARINE;
    }
}
