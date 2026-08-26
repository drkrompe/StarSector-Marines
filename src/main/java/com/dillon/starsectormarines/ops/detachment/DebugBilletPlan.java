package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.marine.MarineSquad;

/**
 * How one squad's twelve billets are experienced — the quality axis of a
 * {@link DebugCompanyStage}, separated from both force size and equipment.
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

    /** A green squad on its first operation. */
    STARTER_ISSUE {
        @Override public int experienceXp(int billet) { return 0; }
    },

    /**
     * A veteran sergeant over regulars, with a green tail. The tail is
     * deliberate: a company that has been growing carries recent hires, and a
     * squad of uniformly-experienced marines is not a shape the campaign
     * actually produces.
     */
    SEASONED {
        @Override public int experienceXp(int billet) {
            if (billet == 0) return 400;                 // Sergeant — squad leader
            if (billet % TEAM == 0) return 200;          // the other two team leads
            if (billet >= REPLACEMENTS) return 40;       // recent replacements
            return 130;
        }
    },

    /** An elite sergeant over veterans and seasoned team leaders. */
    HARDENED {
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
    /** Experience for one billet, keyed on position within the squad (0-based). */
    public abstract int experienceXp(int billet);
}
