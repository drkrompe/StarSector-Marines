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
import com.dillon.starsectormarines.battle.decision.TacticalMap;

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
 * <p>The marine holder garrisons whatever area it has — even a single-room
 * captured compound (one garrison zone) runs {@link GarrisonPatrol}, which
 * degenerates to holding/patrolling that one room. Marines never run
 * {@link GuardPost} (it is gated on the defender {@code holdsFireUntilKillZone}
 * flag), so this goal is the only marine garrison behavior.
 *
 * <p>Also serves the <b>defender</b> base garrison: a squad with the
 * {@code holdsFireUntilKillZone} flag whose {@link Squad#assignedNode} sits in
 * a multi-building compound (≥2 garrison rooms). Because every building's
 * defender squad shares the same compound footprint, only the compound's
 * <em>primary</em> node (highest {@link TacticalNode#priorityScore}, anchor
 * tie-break) runs the area patrol; the others keep holding their own building
 * via {@link GuardPost} (which yields to this goal only for the primary). A
 * single-building / standalone <em>defender</em> post (one garrison zone) stays
 * on {@link GuardPost} — only the defender path has the ≥2-zone gate.
 *
 * <p>The plan is a single perpetual {@link GarrisonPatrol} parameterised by the
 * compound's garrison zones (see {@link GarrisonArea#garrisonZones}).
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
        // Only relevant when there's a real area to garrison; otherwise fall
        // through (a null customPlan would drop us into the backward-chaining
        // planner, which we don't want here).
        return garrisonNode(squad, sim) != null ? 0.95f : 0f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleView sim) {
        return WorldState.EMPTY;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleView sim) {
        TacticalNode node = garrisonNode(squad, sim);
        if (node == null) return null;
        List<Integer> zones = GarrisonArea.garrisonZones(node, GARRISON_MARGIN, sim);
        if (zones.isEmpty()) return null;
        return new SquadPlan(List.of(new SquadPlan.Step(new GarrisonPatrol(zones))));
    }

    /**
     * The compound node this squad should garrison, or null if it isn't a
     * garrison squad for this goal. Marine: the {@code HOLD_NODE} assignment's
     * target node, when it has any garrison area. Defender: its assigned post,
     * when the post is the primary node of a multi-building compound (see
     * {@link #defenderAreaPatrol}).
     */
    private static TacticalNode garrisonNode(Squad squad, BattleView sim) {
        ObjectiveAssignment a = squad.assignedObjective;
        if (a != null && a.kind() == AssignmentKind.HOLD_NODE) {
            TacticalNode node = a.targetNode();
            if (node != null && !GarrisonArea.garrisonZones(node, GARRISON_MARGIN, sim).isEmpty()) {
                return node;
            }
            return null;
        }
        return defenderAreaPatrol(squad, sim) ? squad.assignedNode : null;
    }

    /**
     * True iff this defender garrison squad should run the multi-building area
     * patrol — it carries the garrison flag, its post sits in a compound with
     * ≥2 garrison rooms, and its post is that compound's primary node. The
     * non-primary squads of the same compound stay on {@link GuardPost} holding
     * their own building. Package-visible so {@link GuardPost} can yield to it.
     */
    static boolean defenderAreaPatrol(Squad squad, BattleView sim) {
        if (!squad.holdsFireUntilKillZone) return false;
        TacticalNode node = squad.assignedNode;
        if (node == null) return false;
        if (GarrisonArea.garrisonZones(node, GARRISON_MARGIN, sim).size() < 2) return false;
        return isPrimaryGarrisonNode(node, sim);
    }

    /**
     * Whether {@code node} is the primary garrison node of its compound — the
     * highest-priority node among all same-faction nodes sharing its compound
     * footprint (ties broken by anchor for determinism). Nodes of one compound
     * all carry the identical persisted union bbox, so footprint equality is
     * the compound key; a standalone post matches only itself and is trivially
     * primary.
     */
    private static boolean isPrimaryGarrisonNode(TacticalNode node, BattleView sim) {
        TacticalMap map = sim.getTacticalMap();
        if (map == null) return true;
        TacticalNode best = node;
        for (TacticalNode n : map.forFaction(node.defaultGuard)) {
            if (!sameCompound(n, node)) continue;
            if (higherPriority(n, best)) best = n;
        }
        return best == node;
    }

    private static boolean sameCompound(TacticalNode a, TacticalNode b) {
        return a.compoundLeft() == b.compoundLeft() && a.compoundTop() == b.compoundTop()
                && a.compoundRight() == b.compoundRight() && a.compoundBottom() == b.compoundBottom();
    }

    private static boolean higherPriority(TacticalNode n, TacticalNode best) {
        if (n.priorityScore != best.priorityScore) return n.priorityScore > best.priorityScore;
        if (n.anchorX != best.anchorX) return n.anchorX < best.anchorX;
        return n.anchorY < best.anchorY;
    }
}
