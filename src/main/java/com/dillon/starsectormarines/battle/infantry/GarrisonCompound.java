package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.world.GarrisonArea;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalNode;

import java.util.List;

/**
 * Garrison a captured/held multi-building compound: patrol its rooms and
 * re-clear them on counter-attack. The consumer of the marine
 * {@link AssignmentKind#HOLD_NODE} assignment (written by
 * {@link com.dillon.starsectormarines.battle.command.ConquestCommand} Pass 0
 * for a squad sitting in a {@code MARINE_HELD} compound) — without it a
 * captured compound is left ungarrisoned, since
 * {@link SecureCompoundGoal} goes inactive the moment the compound flips
 * {@code MARINE_HELD}.
 *
 * <p>{@link Priority#MISSION}. Relevance 0.95 — below {@link GarrisonAmbush}'s
 * 1.0 so a live chokepoint ambush still preempts, above the ambient
 * engagement default so the garrison doesn't abandon the compound to chase.
 * Yields (0) on morale break so {@code SurviveContact} (SURVIVAL) takes over.
 *
 * <p>The plan is a single perpetual {@link GarrisonPatrol} parameterised by the
 * compound's garrison zones (see
 * {@link GarrisonArea#garrisonZones}). The defender base-garrison trigger
 * (the {@code holdsFireUntilKillZone} flag) is wired in a later slice.
 */
public final class GarrisonCompound implements Goal {

    public static final GarrisonCompound INSTANCE = new GarrisonCompound();

    /**
     * Cells the garrison footprint is grown by before gathering rooms — absorbs
     * the perimeter wall ring / parade-ground rim so edge rooms still qualify.
     * Small so it never drags the open exterior across the size gate.
     */
    public static final int GARRISON_MARGIN = 2;

    private GarrisonCompound() {}

    @Override public String name() { return "GarrisonCompound"; }

    @Override
    public Priority priority() {
        return Priority.MISSION;
    }

    @Override
    public float relevance(WorldState state, Squad squad, BattleView sim) {
        if (state.get(Predicate.MORALE_BROKEN)) return 0f;
        TacticalNode node = garrisonNode(squad);
        if (node == null) return 0f;
        // Only relevant when there's an actual area to garrison; otherwise fall
        // through (a non-null customPlan that returns null would drop us into
        // the backward-chaining planner, which we don't want here).
        if (GarrisonArea.garrisonZones(node, GARRISON_MARGIN, sim).isEmpty()) return 0f;
        return 0.95f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleView sim) {
        return WorldState.EMPTY;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleView sim) {
        TacticalNode node = garrisonNode(squad);
        if (node == null) return null;
        List<Integer> zones = GarrisonArea.garrisonZones(node, GARRISON_MARGIN, sim);
        if (zones.isEmpty()) return null;
        return new SquadPlan(List.of(new SquadPlan.Step(new GarrisonPatrol(zones))));
    }

    /**
     * The compound node this squad is garrisoning, or null if it isn't a
     * garrison squad. Marine: the {@code HOLD_NODE} assignment's target node.
     * (Defender flag trigger added in a later slice.)
     */
    private static TacticalNode garrisonNode(Squad squad) {
        ObjectiveAssignment a = squad.assignedObjective;
        if (a != null && a.kind() == AssignmentKind.HOLD_NODE) {
            return a.targetNode();
        }
        return null;
    }
}
