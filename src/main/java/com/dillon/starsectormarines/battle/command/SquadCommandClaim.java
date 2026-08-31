package com.dillon.starsectormarines.battle.command;

import java.util.Objects;

/**
 * Immutable spawn-time ownership metadata carried by a delivery mission until
 * the squad it transports is minted.
 */
public record SquadCommandClaim(CommandAuthority authority, String issuer, String reason) {

    /** Issuer every reinforcement claim carries, and the name a handoff must give back. */
    public static final String REINFORCEMENT_ISSUER = "reinforcement";

    public SquadCommandClaim {
        Objects.requireNonNull(authority, "authority");
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("issuer must not be blank");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }

    public static SquadCommandClaim reinforcement(String reason) {
        return new SquadCommandClaim(CommandAuthority.REINFORCEMENT,
                REINFORCEMENT_ISSUER, reason);
    }

    /** Issuer every works-crew claim carries. */
    public static final String WORKS_ISSUER = "works";

    /**
     * A crew posted to a structure, which nobody commands away from it.
     *
     * <p>{@link CommandAuthority#SCRIPTED} because a works crew is the map's
     * rather than the commander's: they were put in a building to work it, and
     * that is their orders for the battle. Left unclaimed they are an ordinary
     * unowned squad, which mission command may take and send somewhere — and a
     * shed is not producing anything once its technicians have been ordered to
     * hold a road.
     */
    public static SquadCommandClaim works(String reason) {
        return new SquadCommandClaim(CommandAuthority.SCRIPTED, WORKS_ISSUER, reason);
    }

    public static SquadCommandClaim mission(String issuer, String reason) {
        return new SquadCommandClaim(CommandAuthority.MISSION_COMMAND,
                issuer, reason);
    }

    /** Claims command of {@code squadId} without inventing a tactical assignment. */
    public void apply(SquadDirectiveControl control, int squadId) {
        control.claimSquadCommand(squadId, authority, issuer, reason);
    }

    /** Claims the squad and installs its first assignment atomically by issuer. */
    public void apply(SquadDirectiveControl control,
                      ObjectiveAssignment assignment) {
        if (assignment == null) {
            throw new IllegalArgumentException("assignment is required");
        }
        control.assignSquadCommand(assignment, authority, issuer, reason);
    }
}
