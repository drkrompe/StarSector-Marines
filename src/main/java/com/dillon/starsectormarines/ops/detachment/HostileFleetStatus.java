package com.dillon.starsectormarines.ops.detachment;

import com.fs.starfarer.api.campaign.FleetAssignment;

/**
 * Whether a defending-faction fleet's ships are free to answer a battle. Only {@link #AVAILABLE}
 * fleets can lend their capital to the defender; every other state is a way the player (or the
 * campaign) has kept it bogged down, and the briefing says which.
 */
public enum HostileFleetStatus {
    /** On station, patrolling or defending: it will answer. */
    AVAILABLE("hostileShipStatusAvailable"),
    /** In a battle already, with anyone (including the player). */
    IN_BATTLE("hostileShipStatusInBattle"),
    /** Despawning, or returning to a base to despawn. */
    DESPAWNING("hostileShipStatusDespawning"),
    /** The fleet's own AI is fleeing. */
    FLEEING("hostileShipStatusFleeing"),
    /**
     * Committed elsewhere: flagged busy, intercepting, following, raiding, delivering, resupplying,
     * or travelling or attacking somewhere other than the target world.
     */
    BUSY("hostileShipStatusBusy");

    /** Strings key of the short phrase that names this state. */
    public final String stringsKey;

    HostileFleetStatus(String stringsKey) { this.stringsKey = stringsKey; }

    public boolean free() { return this == AVAILABLE; }

    /**
     * The status the fleet's observable state implies, in this precedence: battle, despawn, flee,
     * busy flag, then the standing assignment.
     *
     * @param assignmentAtTarget the assignment's destination is the world being fought over
     */
    public static HostileFleetStatus classify(boolean inBattle, boolean despawning, boolean fleeing,
                                              boolean flaggedBusy, FleetAssignment assignment,
                                              boolean assignmentAtTarget) {
        if (inBattle) return IN_BATTLE;
        if (despawning || assignment == FleetAssignment.GO_TO_LOCATION_AND_DESPAWN) return DESPAWNING;
        if (fleeing) return FLEEING;
        if (flaggedBusy) return BUSY;
        if (assignment == null) return AVAILABLE;
        return switch (assignment) {
            case INTERCEPT, FOLLOW, RAID_SYSTEM, RESUPPLY,
                 DELIVER_RESOURCES, DELIVER_SUPPLIES, DELIVER_FUEL, DELIVER_PERSONNEL, DELIVER_CREW,
                 DELIVER_MARINES -> BUSY;
            case GO_TO_LOCATION, ATTACK_LOCATION -> assignmentAtTarget ? AVAILABLE : BUSY;
            default -> AVAILABLE;
        };
    }
}
