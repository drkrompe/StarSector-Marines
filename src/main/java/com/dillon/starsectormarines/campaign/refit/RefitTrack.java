package com.dillon.starsectormarines.campaign.refit;

import com.dillon.starsectormarines.battle.world.gen.ship.ShipCore;

/**
 * One line of the Second Wind's refit tree. Cores are her plant and gate the
 * rest; facilities are rooms whose passives reach the company; combat tracks
 * are the hull's own fighting figures, applied through the conversion hull mod
 * so the base game shows and uses them.
 */
public enum RefitTrack {
    POWER(Group.CORE, ShipCore.POWER),
    LIFE_SUPPORT(Group.CORE, ShipCore.LIFE_SUPPORT),
    STRUCTURE(Group.CORE, ShipCore.STRUCTURE),
    COMPUTE(Group.CORE, ShipCore.COMPUTE),
    /** Read from the workshop chain, which keeps its own orders. */
    FABRICATION(Group.CORE, ShipCore.FABRICATION),
    MESS(Group.FACILITY, null),
    HULL(Group.COMBAT, null),
    FLUX(Group.COMBAT, null),
    SENSORS(Group.COMBAT, null),
    DRIVE(Group.COMBAT, null);

    public enum Group { CORE, FACILITY, COMBAT }

    public final Group group;
    /** The core this track is, or null for a facility or combat track. */
    public final ShipCore core;

    RefitTrack(Group group, ShipCore core) { this.group = group; this.core = core; }

    /** Every track of the battered start begins at a deficit; nothing starts improved. */
    public RefitBand start() { return RefitBand.DEFICIT; }
}
