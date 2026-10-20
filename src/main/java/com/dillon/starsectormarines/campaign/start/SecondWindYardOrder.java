package com.dillon.starsectormarines.campaign.start;

import com.fs.starfarer.api.fleet.FleetMemberAPI;

/**
 * Legacy: the 2026-10-20 build sent a lost Second Wind to a yard for three weeks. Kept only so a save holding one
 * still loads; {@link SecondWindKeeper} turns it into a {@link SecondWindLoss} on load.
 */
public final class SecondWindYardOrder {

    private final FleetMemberAPI ship;
    private String marketId;
    private final long since;
    private final int bill;
    private final boolean dModAdded;

    public SecondWindYardOrder(FleetMemberAPI ship, String marketId, long since, int bill, boolean dModAdded) {
        this.ship = ship;
        this.marketId = marketId;
        this.since = since;
        this.bill = bill;
        this.dModAdded = dModAdded;
    }

    public FleetMemberAPI ship() { return ship; }
    public String marketId() { return marketId; }
    /** Sector clock timestamp of the loss; the yard days count from here. */
    public long since() { return since; }
    public int bill() { return bill; }
    public boolean dModAdded() { return dModAdded; }

    /** Her yard was lost to the company; another one finishes the work on the same schedule. */
    void relocate(String marketId) { this.marketId = marketId; }
}
