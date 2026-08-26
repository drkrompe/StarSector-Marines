package com.dillon.starsectormarines.battle.command;

import java.util.Objects;

/**
 * Immutable spawn-time ownership metadata carried by a delivery mission until
 * the squad it transports is minted.
 */
public record SquadCommandClaim(CommandAuthority authority, String issuer, String reason) {

    private static final String REINFORCEMENT_ISSUER = "reinforcement";

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
