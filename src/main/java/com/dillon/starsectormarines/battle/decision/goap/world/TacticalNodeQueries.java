package com.dillon.starsectormarines.battle.decision.goap.world;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.squad.Squad;

/** Shared resolution rules for the tactical node a squad is actively holding. */
public final class TacticalNodeQueries {

    private TacticalNodeQueries() {}

    /**
     * Commander-issued hold orders override a defender's spawn-time post.
     * Other assignment kinds do not replace {@link Squad#assignedNode}.
     */
    public static TacticalNode assignedNode(Squad squad) {
        ObjectiveAssignment assignment = squad.assignedObjective;
        if (assignment != null && assignment.kind() == AssignmentKind.HOLD_NODE
                && assignment.targetNode() != null) {
            return assignment.targetNode();
        }
        return squad.assignedNode;
    }

    public static boolean isMustHold(Squad squad) {
        TacticalNode node = assignedNode(squad);
        return node != null && node.mustHold;
    }
}
