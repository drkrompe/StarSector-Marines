package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;

import java.util.EnumSet;
import java.util.List;

/**
 * Resolves a {@link ReinforcementRequest}'s objective coordinates to the
 * {@link TacticalNode} the deboarded squad should be assigned to — the
 * "assign at deboard, not on arrival" contract from
 * {@code roadmap/conquest/stories/progressive-reinforcement.md}. Means call
 * this at dispatch time and stamp the result on the mission
 * ({@code ShuttleMission#assignNode} / {@code VehicleMission#assignNode}) so
 * the deboard path can assign the squad the moment it lands, rather than
 * only once it physically arrives at the position.
 *
 * <p>The front-line trigger's objective is always an exact node anchor (see
 * {@link RecaptureTarget#objectiveX()}/{@link RecaptureTarget#objectiveY()}),
 * so the tolerance here only absorbs coordinate rounding — it is not a loose
 * "nearest node" search.
 */
public final class ObjectiveNodes {

    /**
     * Manhattan distance from the request's objective coordinates a
     * candidate node's anchor must fall within to be accepted as a match.
     */
    private static final int OBJECTIVE_TOLERANCE = 2;

    private ObjectiveNodes() {}

    /**
     * The tactical node at {@code req}'s objective coordinates, or
     * {@code null} when the request has no objective, {@code map} is null,
     * or no node's anchor sits within {@link #OBJECTIVE_TOLERANCE} of the
     * objective.
     */
    public static TacticalNode resolve(TacticalMap map, ReinforcementRequest req) {
        if (!req.hasObjective() || map == null) return null;
        List<TacticalNode> near = map.nearest(
                req.objectiveX, req.objectiveY, 1, EnumSet.noneOf(TacticalNode.Kind.class));
        if (near.isEmpty()) return null;
        TacticalNode candidate = near.get(0);
        int d = Math.abs(candidate.anchorX - req.objectiveX) + Math.abs(candidate.anchorY - req.objectiveY);
        return d <= OBJECTIVE_TOLERANCE ? candidate : null;
    }
}
