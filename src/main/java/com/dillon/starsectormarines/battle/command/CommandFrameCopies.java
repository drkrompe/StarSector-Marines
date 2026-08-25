package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.decision.TacticalNode;

/** Deep-copy helpers for mutable objects admitted into frozen command values. */
final class CommandFrameCopies {

    private CommandFrameCopies() { }

    static ObjectiveAssignment assignment(ObjectiveAssignment source) {
        if (source == null) return null;
        return new ObjectiveAssignment(source.squadId(), source.kind(),
                source.targetZoneId(), node(source.targetNode()),
                source.objectiveId(), source.targetCellX(), source.targetCellY());
    }

    static CommandDirective directive(CommandDirective source) {
        if (source == null) return null;
        return new CommandDirective(source.squadId(), source.perspective(),
                source.issuer(), source.authority(), source.reason(),
                assignment(source.assignment()), source.issuedTick(),
                source.stableUntilTick(), source.leaseUntilTick(), source.status(),
                source.dispositionReason());
    }

    static TacticalNode node(TacticalNode source) {
        if (source == null) return null;
        TacticalNode copy = new TacticalNode(source.kind, source.anchorX,
                source.anchorY, source.left, source.top, source.right,
                source.bottom, source.defaultGuard, source.priorityScore,
                source.garrisonSize, source.mustHold, source.standPositions());
        copy.setCompoundBounds(source.compoundLeft(), source.compoundTop(),
                source.compoundRight(), source.compoundBottom());
        return copy;
    }
}
