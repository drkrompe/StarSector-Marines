package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.campaign.systems.RaidStatus;

/**
 * Decides whether one vanilla raid is a fight the company may still take on the ground.
 *
 * <p>Pure, so the rule can be tested without a sector: the adapter that reads live raid
 * state supplies the three facts and this owns the judgement between them.
 */
public final class PolityThreatFilter {

    private PolityThreatFilter() {}

    /**
     * A raid is fightable only on the player's own colony, only while it is still
     * {@link RaidStatus#LIVE}, only once, and only where no posting already answers it.
     *
     * <p>A {@link RaidStatus#LANDED} raid is over — vanilla already resolved the ground
     * half — and a {@link RaidStatus#REPELLED} one is gone. {@code alreadySettled} is the
     * caller's record of raids this market has already been defended against, keyed by
     * event key, so a battle fought and resolved does not offer itself again.
     *
     * <p>A standing posting makes the raid the posting's to answer: the trigger arms the
     * posting row and the ordinary stationing response fights it with the posted
     * detachment, so offering the same raid a second way would be two defences of one
     * landing. See {@code polity-defence-raid-hook.md}.
     *
     * @param playerOwned    whether the threatened market belongs to the player
     * @param status         what vanilla has done with this raid so far
     * @param alreadySettled whether this raid has already been fought at this market
     * @param postingStands  whether an active posting is bound to this market
     */
    public static boolean fightable(boolean playerOwned, RaidStatus status,
                                    boolean alreadySettled, boolean postingStands) {
        return playerOwned && status == RaidStatus.LIVE && !alreadySettled && !postingStands;
    }
}
