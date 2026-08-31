package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.decision.TacticalNode;

/** Deep-copy helpers for mutable objects admitted into frozen command values. */
final class CommandFrameCopies {

    private CommandFrameCopies() { }

    static ObjectiveAssignment assignment(ObjectiveAssignment source) {
        if (source == null) return null;
        return new ObjectiveAssignment(source.squadId(), source.kind(),
                source.targetZoneId(), node(source.targetNode()),
                source.objectiveId(), source.targetCellX(), source.targetCellY(),
                source.targetRadiusCells());
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

    /**
     * Logical identity retained by a node copied into a frozen command frame.
     * Tactical-node reference identity remains meaningful inside the live map,
     * but a command assignment and the next frame necessarily hold different
     * defensive copies of that same authored place.
     */
    static boolean sameNodeIdentity(TacticalNode left, TacticalNode right) {
        if (left == right) return true;
        if (left == null || right == null) return false;
        return left.kind == right.kind
                && left.anchorX == right.anchorX
                && left.anchorY == right.anchorY
                && left.left == right.left
                && left.top == right.top
                && left.right == right.right
                && left.bottom == right.bottom
                && left.compoundLeft() == right.compoundLeft()
                && left.compoundTop() == right.compoundTop()
                && left.compoundRight() == right.compoundRight()
                && left.compoundBottom() == right.compoundBottom();
    }
}
