package com.dillon.starsectormarines.ops.detachment;

/**
 * What one squad has managed to collect — the quality axis of a
 * {@link DebugCompanyStage}, separated from force size.
 *
 * <p><b>Quality is armour now.</b> Experience is issued with the armour pattern
 * ({@code progression-nouns.md}), so a plan expresses a company's standing as
 * the best suits it owns rather than as authored XP. {@link #maxArmorTier} is
 * that statement: a company on its first contract has collected nothing above a
 * scratch security kit, and one at the top of the ladder fields battlesuits.
 * {@link DebugCompany} draws each squad's armour doctrine from the faction
 * catalog <em>within</em> that ceiling, so flavour stays randomized while the
 * stage keeps meaning what it says.
 *
 * <p>Split out when the stage ladder grew past three points: five stages each
 * carrying their own copy of a billet plan was five copies of the same three
 * patterns. A stage now names a plan and a size, and the briefing's squad
 * dial overrides the size without touching the quality.
 *
 * <p>Weapons are deliberately absent from this ladder. A plan sets the armour
 * ceiling and nothing else; {@link DebugCompany} rolls each squad's weapon
 * doctrine separately from the authored faction-flavored catalog, so two
 * companies at the same stage still turn up armed differently.
 *
 * <p>See `c12-the-debug-company.md`.
 */
public enum DebugBilletPlan {

    /**
     * A new game: nothing worth having has been collected yet, beyond whatever
     * low-tier protection happened to be on a market shelf.
     */
    STARTER_ISSUE {
        @Override public int maxArmorTier() { return 2; }
    },

    /** A company a few contracts in: line armour is available, battlesuits are not. */
    SEASONED {
        @Override public int maxArmorTier() { return 3; }
    },

    /** A company at the top of the ladder, fielding recovered battlesuits. */
    HARDENED {
        @Override public int maxArmorTier() { return 4; }
    };

    /** The best armour tier this company has collected; the ceiling on its band. */
    public abstract int maxArmorTier();
}
