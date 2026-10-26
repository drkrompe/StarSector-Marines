package com.dillon.starsectormarines.campaign.refit;

import com.dillon.starsectormarines.battle.world.gen.ship.ReservedFacility;
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
    RANGE(ReservedFacility.FIRING_RANGE),
    GYM(ReservedFacility.GYMNASIUM),
    SICK_BAY(ReservedFacility.SICK_BAY),
    REC_LOUNGE(ReservedFacility.REC_LOUNGE),
    BRIG(ReservedFacility.BRIG),
    // Mech Lab refits: passives on the company's own mechs, frozen into each deployment at launch.
    MECH_CALIBRATION(Group.FACILITY, null),
    MECH_COOLANT(Group.FACILITY, null),
    MECH_ARMOR(Group.FACILITY, null),
    /** Campaign-side: repair clocks and the spares rack, read when an order is quoted. */
    MECH_REPAIR(Group.FACILITY, null),
    /** A placeholder with no orders until the company's vehicles can deploy. */
    VEHICLE_BAY(Group.FACILITY, null),
    // Facilities fitted into rooms the deck already has: no reserve to open.
    /** Grow racks in the provisions store. */
    HYDROPONICS(Group.FACILITY, null),
    /** The officers' end of the mess, then a staff college. */
    WARDROOM(Group.FACILITY, null),
    /** A plot table, then a holo-tank, at the other end of the mess. */
    BRIEFING(Group.FACILITY, null),
    /** The cargo holds and, at the top, bonded stores. */
    HOLDS(Group.FACILITY, null),
    /** A sealed corner of the hangar where whole machines are set aside; capacity, no passive. */
    RESERVE_HANGAR(Group.FACILITY, null),
    HULL(Group.COMBAT, null),
    FLUX(Group.COMBAT, null),
    SENSORS(Group.COMBAT, null),
    DRIVE(Group.COMBAT, null);

    public enum Group { CORE, FACILITY, COMBAT }

    public final Group group;
    /** The core this track is, or null for a facility or combat track. */
    public final ShipCore core;

    /** The sealed compartment this facility track opens, or null for a track that is not a reserve. */
    public final ReservedFacility reserve;

    RefitTrack(Group group, ShipCore core) { this.group = group; this.core = core; this.reserve = null; }

    /** A facility whose Restored tier unseals one of the hull's reserved compartments. */
    RefitTrack(ReservedFacility reserve) { this.group = Group.FACILITY; this.core = null; this.reserve = reserve; }

    /** The track that opens {@code reserve}. */
    public static RefitTrack forReserve(ReservedFacility reserve) {
        for (RefitTrack track : values()) if (track.reserve == reserve) return track;
        throw new IllegalArgumentException("No refit track opens " + reserve);
    }

    /** Every track of the battered start begins at a deficit; nothing starts improved. */
    public RefitBand start() { return RefitBand.DEFICIT; }
}
