package com.dillon.starsectormarines.campaign.start;

import com.dillon.starsectormarines.campaign.start.SecondWindKeep.Cause;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

/**
 * The Second Wind gone from the fleet and waiting to be bought back: how she went, what the buy-back costs and
 * which d-mod the loss left on her. Saved in sector persistent data; the member is the same fleet member she was,
 * so her id, name, refits and the company's saved interior state all come back with her.
 */
public final class SecondWindLoss {

    private final FleetMemberAPI ship;
    private final Cause cause;
    private final int bill;
    /** Display name of the d-mod the loss added, or null when she was already at the cap. */
    private final String dMod;

    public SecondWindLoss(FleetMemberAPI ship, Cause cause, int bill, String dMod) {
        this.ship = ship;
        this.cause = cause;
        this.bill = bill;
        this.dMod = dMod;
    }

    public FleetMemberAPI ship() { return ship; }
    public Cause cause() { return cause == null ? Cause.BATTLE : cause; }
    public int bill() { return bill; }
    public String dMod() { return dMod; }
}
