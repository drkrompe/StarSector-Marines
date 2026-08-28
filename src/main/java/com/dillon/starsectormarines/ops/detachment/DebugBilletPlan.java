package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.marine.MarineSquad;

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
 * <p>Equipment is deliberately absent from this ladder. The debug selector
 * answers an experience question; {@link DebugCompany} rolls each squad's
 * weapon and armor doctrine separately from the authored faction-flavored
 * catalog.
 *
 * <p>See `c12-the-debug-company.md`.
 */
public enum DebugBilletPlan {

    /**
     * A new game: nothing worth having has been collected yet, beyond whatever
     * low-tier protection happened to be on a market shelf.
     */
    STARTER_ISSUE {
        @Override public int experienceXp(int billet) { return 0; }
        @Override public int maxArmorTier() { return 2; }
    },

    /**
     * A veteran sergeant over regulars, with a green tail. The tail is
     * deliberate: a company that has been growing carries recent hires, and a
     * squad of uniformly-experienced marines is not a shape the campaign
     * actually produces.
     */
    SEASONED {
        @Override public int maxArmorTier() { return 3; }
        @Override public int experienceXp(int billet) {
            if (billet == 0) return 400;                 // Sergeant — squad leader
            if (billet % TEAM == 0) return 200;          // the other two team leads
            if (billet >= REPLACEMENTS) return 40;       // recent replacements
            return 130;
        }
    },

    /** An elite sergeant over veterans and seasoned team leaders. */
    HARDENED {
        @Override public int maxArmorTier() { return 4; }
        @Override public int experienceXp(int billet) {
            if (billet == 0) return 900;                 // Elite sergeant
            if (billet % TEAM == 0) return 500;
            if (billet >= REPLACEMENTS) return 200;
            return 400;
        }
    };

    /** Billets from here up are recent replacements — the green tail every squad carries. */
    private static final int REPLACEMENTS = MarineSquad.CAPACITY - 2;
    /** Local alias so the billet plans read in fire teams. */
    private static final int TEAM = MarineSquad.TEAM_SIZE;
    /**
     * Experience for one billet, keyed on position within the squad (0-based).
     *
     * @deprecated Inert for combat since bands became issued with the armour
     *     pattern. Retained only because it still writes the persisted service
     *     number; `xp-authority-cleanup.md` owns its disposal.
     */
    @Deprecated
    public abstract int experienceXp(int billet);

    /** The best armour tier this company has collected; the ceiling on its band. */
    public abstract int maxArmorTier();
}
