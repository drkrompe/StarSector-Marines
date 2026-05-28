package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.decision.TacticalNode;

/**
 * One strategic task assigned to one squad by its faction's
 * {@link MissionCommand}. Read by MISSION-priority GOAP goals
 * ({@code ClearAssignedZoneGoal}, {@code HoldAssignedNodeGoal},
 * {@code RushAssignedObjectiveGoal}) to decide whether they're relevant
 * this tick.
 *
 * <p>Most fields are slot-typed: not every {@link AssignmentKind} uses every
 * field. {@code CLEAR_ZONE} populates {@link #targetZoneId} (the zone to
 * push into) and leaves {@link #targetNode} null; {@code HOLD_NODE} is the
 * reverse. Consumers read the field appropriate to {@link #kind} — assigning
 * a node to a {@code CLEAR_ZONE} task has no defined meaning and is ignored.
 *
 * <p>Mutable on {@code Squad} but immutable as a record — a re-assignment
 * replaces the whole reference rather than mutating fields, so a goal that
 * snapshot-reads the assignment at relevance-eval time can't see a half-
 * updated state.
 *
 * @param squadId     the squad this assignment is for; matches {@code Squad.id}
 * @param kind        the strategic task category
 * @param targetZoneId zone id from {@code ZoneGraph}; {@code -1} when not
 *                    zone-scoped
 * @param targetNode  tactical node for node-scoped kinds; {@code null} otherwise
 * @param objectiveId backref to a mission {@code Objective} for kinds that
 *                    serve one; {@code -1} for pure tactical assignments
 */
public record ObjectiveAssignment(
        int squadId,
        AssignmentKind kind,
        int targetZoneId,
        TacticalNode targetNode,
        int objectiveId) {

    /** Sentinel returned by zone-id / objective-id slots when the assignment isn't scoped to them. */
    public static final int UNSCOPED = -1;

    /** Convenience: zone-scoped clear with no node + no objective backref. */
    public static ObjectiveAssignment clearZone(int squadId, int zoneId) {
        return new ObjectiveAssignment(squadId, AssignmentKind.CLEAR_ZONE, zoneId, null, UNSCOPED);
    }

    /** Convenience: zone-scoped compound capture — push, clear, hold until captured. */
    public static ObjectiveAssignment secureCompound(int squadId, int zoneId, TacticalNode compoundNode) {
        return new ObjectiveAssignment(squadId, AssignmentKind.SECURE_COMPOUND, zoneId, compoundNode, UNSCOPED);
    }

    /** Convenience: node-scoped hold with no zone + no objective backref. */
    public static ObjectiveAssignment holdNode(int squadId, TacticalNode node) {
        return new ObjectiveAssignment(squadId, AssignmentKind.HOLD_NODE, UNSCOPED, node, UNSCOPED);
    }

    /** Convenience: objective-scoped rush, optionally with a zone hint for the planner. */
    public static ObjectiveAssignment rushObjective(int squadId, int objectiveId, int zoneId) {
        return new ObjectiveAssignment(squadId, AssignmentKind.RUSH_OBJECTIVE, zoneId, null, objectiveId);
    }

    /** Convenience: catch-all support assignment with no concrete scope. */
    public static ObjectiveAssignment support(int squadId) {
        return new ObjectiveAssignment(squadId, AssignmentKind.SUPPORT, UNSCOPED, null, UNSCOPED);
    }
}
