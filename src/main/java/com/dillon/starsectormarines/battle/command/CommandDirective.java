package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.Objects;

/** Immutable arbiter result and assignment-provenance record. */
public record CommandDirective(
        int squadId,
        Faction perspective,
        String issuer,
        CommandAuthority authority,
        String reason,
        ObjectiveAssignment assignment,
        int issuedTick,
        int leaseUntilTick,
        Status status,
        String dispositionReason) {

    public enum Status { ACTIVE, RETAINED, RELEASED, UNASSIGNED, REJECTED }

    public CommandDirective {
        Objects.requireNonNull(perspective, "perspective");
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(status, "status");
        dispositionReason = dispositionReason == null ? "" : dispositionReason;
    }

    public boolean ownsAssignment() {
        return assignment != null
                && (status == Status.ACTIVE || status == Status.RETAINED);
    }
}
