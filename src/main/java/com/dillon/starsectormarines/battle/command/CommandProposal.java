package com.dillon.starsectormarines.battle.command;

import java.util.Objects;

/** One frame-only strategy request, validated later by {@link AssignmentArbiter}. */
public record CommandProposal(
        int squadId,
        Action action,
        ObjectiveAssignment assignment,
        CommandAuthority authority,
        String reason,
        int leaseUntilTick) {

    public enum Action { ASSIGN, RETAIN, RELEASE }

    public CommandProposal {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(reason, "reason");
        if (action == Action.ASSIGN && assignment == null) {
            throw new IllegalArgumentException("ASSIGN requires an assignment");
        }
        if (assignment != null && assignment.squadId() != squadId) {
            throw new IllegalArgumentException("assignment squad does not match proposal");
        }
    }

    public static CommandProposal assign(ObjectiveAssignment assignment,
                                         CommandAuthority authority,
                                         String reason) {
        return new CommandProposal(assignment.squadId(), Action.ASSIGN,
                assignment, authority, reason, -1);
    }

    public static CommandProposal retain(int squadId, CommandAuthority authority,
                                         String reason) {
        return new CommandProposal(squadId, Action.RETAIN, null,
                authority, reason, -1);
    }

    public static CommandProposal release(int squadId, CommandAuthority authority,
                                          String reason) {
        return new CommandProposal(squadId, Action.RELEASE, null,
                authority, reason, -1);
    }
}
