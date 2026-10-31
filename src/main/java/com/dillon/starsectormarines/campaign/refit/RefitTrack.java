package com.dillon.starsectormarines.campaign.refit;

import com.dillon.starsectormarines.battle.world.gen.ship.ReservedFacility;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipCore;

/**
 * One line of the Second Wind's refit tree. Cores are her plant and gate the
 * rest; facilities are rooms whose passives reach the company's marines and
 * machines. Only tracks that make the company stronger are offered: her own
 * fleet figures stand at the stock hull's, and the brig stays sealed.
 */
public enum RefitTrack {
    POWER(Group.CORE, ShipCore.POWER),
    LIFE_SUPPORT(Group.CORE, ShipCore.LIFE_SUPPORT),
    STRUCTURE(Group.CORE, ShipCore.STRUCTURE),
    COMPUTE(Group.CORE, ShipCore.COMPUTE),
    /** Read from the workshop chain, which keeps its own orders. */
    FABRICATION(Group.CORE, ShipCore.FABRICATION),
    MESS(Group.FACILITY, null),
    RANGE(ReservedFacility.FIRING_RANGE),
    GYM(ReservedFacility.GYMNASIUM),
    SICK_BAY(ReservedFacility.SICK_BAY),
    REC_LOUNGE(ReservedFacility.REC_LOUNGE),
    // Mech Lab refits: passives on the company's own mechs, frozen into each deployment at launch.
    MECH_CALIBRATION(Group.FACILITY, null),
    MECH_COOLANT(Group.FACILITY, null),
    MECH_ARMOR(Group.FACILITY, null),
    /** Campaign-side: repair clocks and the spares rack, read when an order is quoted. */
    MECH_REPAIR(Group.FACILITY, null),
    /** A sealed corner of the hangar where whole machines are set aside; capacity, no passive. */
    RESERVE_HANGAR(Group.FACILITY, null);

    public enum Group { CORE, FACILITY }

    public final Group group;
    /** The core this track is, or null for a facility or combat track. */
    public final ShipCore core;

    /** The sealed compartment this facility track opens, or null for a track that is not a reserve. */
    public final ReservedFacility reserve;

    RefitTrack(Group group, ShipCore core) { this.group = group; this.core = core; this.reserve = null; }

    /** A facility whose Restored tier unseals one of the hull's reserved compartments. */
    RefitTrack(ReservedFacility reserve) { this.group = Group.FACILITY; this.core = null; this.reserve = reserve; }

    /** The track that opens {@code reserve}, or null for a reserve no order opens (the brig). */
    public static RefitTrack forReserve(ReservedFacility reserve) {
        for (RefitTrack track : values()) if (track.reserve == reserve) return track;
        return null;
    }

    /** Every track of the battered start begins at a deficit; nothing starts improved. */
    public RefitBand start() { return RefitBand.DEFICIT; }
}
