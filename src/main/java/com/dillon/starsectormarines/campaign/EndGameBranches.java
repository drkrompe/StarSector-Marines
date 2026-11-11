package com.dillon.starsectormarines.campaign;

/**
 * When the alpha campaign's end-game branches open (owner, 2026-11-11): the Khettara Bloom on Asharu
 * ({@code wellhollow-bloom-arc.md}) and the Long Survey on Garnir ({@code long-survey-arc.md}) open together when
 * Morrow departs at the end of A Working Ship. The player takes up either first; neither arc's progress opens,
 * closes or gates the other, so the rule reads only the departure.
 */
public final class EndGameBranches {
    private EndGameBranches() { }

    /** Whether the end-game branches are open in this campaign. */
    public static boolean open(CampaignState state) {
        return state != null && state.shepherdRecovery != null && state.shepherdRecovery.departed();
    }
}
