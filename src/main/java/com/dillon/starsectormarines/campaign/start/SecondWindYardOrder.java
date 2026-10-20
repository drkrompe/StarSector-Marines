package com.dillon.starsectormarines.campaign.start;

import com.fs.starfarer.api.fleet.FleetMemberAPI;

/**
 * The Second Wind in a yard after a loss: which yard, when she went in, and what the yard will charge.
 * Saved in sector persistent data; the member is the same fleet member she was, so her id, name, refits and
 * the company's saved interior state all come back with her.
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
