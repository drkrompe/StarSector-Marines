package com.dillon.starsectormarines.campaign.ruins;

/**
 * Who holds a ruins site ({@code ruins-expedition.md}, Occupants): a seeded fact of the site, drawn by
 * {@link RuinsOccupants#draw}, never stored. Derelict security comes in its two lineages, Domain security and
 * Tri-Tachyon contingency security ({@code derelict-lore-guide.md}), because the briefing and the network read them
 * apart.
 */
public enum RuinsOccupant {
    /** An empty site: its works are the expedition. Only a scattered site holds nobody. */
    NOBODY(null),
    /** A pirate salvage crew already working the ruins. */
    SCAVENGERS("pirates"),
    /** An old seeding site's sleepers, on a reservoir world only. */
    RAV(null),
    /** The site's own Domain-era automated security. */
    DOMAIN_SECURITY("derelict"),
    /** Tri-Tachyon contingency security, in a Remnant system. */
    CONTINGENCY_SECURITY("remnant");

    /**
     * The campaign faction the occupant's side answers to, for its roster, liveries and loot; null where no
     * campaign faction stands for it (nobody, the Rav).
     */
    public final String factionId;

    RuinsOccupant(String factionId) {
        this.factionId = factionId;
    }

    /** Derelict security of either lineage. */
    public boolean isDerelict() {
        return this == DOMAIN_SECURITY || this == CONTINGENCY_SECURITY;
    }
}
