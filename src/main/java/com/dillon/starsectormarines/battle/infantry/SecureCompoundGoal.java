package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.action.ClearZone;
import com.dillon.starsectormarines.battle.decision.goap.action.EnterZone;
import com.dillon.starsectormarines.battle.decision.goap.action.HoldZone;
import com.dillon.starsectormarines.battle.decision.goap.world.ZoneQueries;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;

import java.util.ArrayList;
import java.util.List;

/**
 * Commander-driven compound capture goal. Fires for squads whose
 * {@link Squad#assignedObjective} has {@link AssignmentKind#SECURE_COMPOUND}
 * — written by {@link com.dillon.starsectormarines.battle.command.ConquestCommand}
 * when an uncaptured compound is at or behind the squad's forward position.
 *
 * <p>Three-phase plan: push to the compound's zone (zone-graph BFS),
 * clear enemies ({@link ClearZone}), then hold until the capture timer
 * completes ({@link HoldZone}). The hold phase keeps marines present in the
 * zone so {@link com.dillon.starsectormarines.battle.command.compound.CompoundCaptureSystem}
 * accumulates toward {@link CompoundService.CompoundState#MARINE_HELD}.
 *
 * <p>Relevance 0.9 — above {@link ClearAssignedZoneGoal}'s 0.8 so a squad
 * assigned both a generic zone clear and a compound capture prefers the
 * compound (in practice the commander issues one or the other, not both).
 * Below {@link SecureObjectiveZone}'s 1.0 so a planter squad still follows
 * its unit-level mission.
 */
public final class SecureCompoundGoal implements Goal {

    public static final SecureCompoundGoal INSTANCE = new SecureCompoundGoal();

    private static final WorldState DESIRED = WorldState.EMPTY
            .with(Predicate.ZONE_CLEAR, true);

    private SecureCompoundGoal() {}

    @Override public String name() { return "SecureCompound"; }

    @Override
    public Priority priority() {
        return Priority.MISSION;
    }

    @Override
    public float relevance(WorldState state, Squad squad, BattleView sim) {
        ObjectiveAssignment assignment = squad.assignedObjective;
        if (assignment == null) return 0f;
        if (assignment.kind() != AssignmentKind.SECURE_COMPOUND) return 0f;
        int targetZone = assignment.targetZoneId();
        if (targetZone < 0) return 0f;
        TacticalNode node = assignment.targetNode();
        if (node == null) return 0f;
        if (state.get(Predicate.MORALE_BROKEN)) return 0f;
        CompoundService.Record record = sim.getCompoundService().getRecord(node);
        if (record == null) return 0f;
        if (record.state == CompoundService.CompoundState.MARINE_HELD) return 0f;
        int currentZone = ZoneQueries.squadCurrentZone(squad, sim);
        if (currentZone < 0) return 0f;
        if (currentZone != targetZone
                && ZoneQueries.zonePathBfs(currentZone, targetZone, sim).size() < 2) return 0f;
        return 0.9f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleView sim) {
        return DESIRED;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleView sim) {
        ObjectiveAssignment assignment = squad.assignedObjective;
        if (assignment == null || assignment.kind() != AssignmentKind.SECURE_COMPOUND) return null;
        int to = assignment.targetZoneId();
        TacticalNode node = assignment.targetNode();
        if (node == null) return null;

        if (planEndsWithHoldZone(squad.currentPlan, to)) {
            return squad.currentPlan;
        }

        int from = ZoneQueries.squadCurrentZone(squad, sim);
        if (from == to) {
            Faction enemy = squad.faction == Faction.MARINE ? Faction.DEFENDER : Faction.MARINE;
            List<SquadPlan.Step> steps = new ArrayList<>(2);
            if (!ZoneQueries.zoneClear(to, enemy, sim)) {
                steps.add(new SquadPlan.Step(new ClearZone(to)));
            }
            steps.add(new SquadPlan.Step(new HoldZone(to, node)));
            return new SquadPlan(steps);
        }

        return synthesizeSecurePlan(from, to, node, sim);
    }

    /**
     * Multiplier on the compound's bbox area above which a zone is treated as
     * open ground to transit, not a room to clear. The outdoor flood-fill (one
     * zone spanning the whole exterior) dwarfs any building box and is rejected
     * by this gate alone; the slack absorbs door/edge cells that spill just
     * outside the box.
     */
    private static final float MAX_GARRISON_AREA_RATIO = 1.25f;

    /**
     * Minimum fraction of a zone's cells that must fall inside the compound's
     * bbox for the zone to count as part of the garrison (and so earn a
     * {@link ClearZone} step rather than a bare transit {@link EnterZone}).
     */
    private static final float MIN_INSIDE_FRACTION = 0.5f;

    private static SquadPlan synthesizeSecurePlan(int fromZone, int toZone, TacticalNode node,
                                                   BattleView sim) {
        List<Integer> path = ZoneQueries.zonePathBfs(fromZone, toZone, sim);
        if (path.size() < 2) return null;

        var graph = sim.getZoneGraph();
        var grid = sim.getGrid();
        List<SquadPlan.Step> steps = new ArrayList<>(2 * path.size());
        for (int i = 1; i < path.size(); i++) {
            int zoneId = path.get(i);
            var zone = graph.zoneById(zoneId);
            if (zone == null) return null;
            if (i < path.size() - 1
                    && zone.getCellCount() == 1
                    && grid.isDoorwayAt(zone.getCellIndices()[0])) continue;
            // Every zone on the route gets a transit EnterZone to move through;
            // only the compound's constituent rooms also earn a ClearZone. This
            // keeps the squad from parking on a ClearZone against the unbounded
            // outdoor zone (it always holds a stray defender) — see story 17.
            steps.add(new SquadPlan.Step(EnterZone.forZone(zone, grid)));
            if (isGarrisonZone(zone, node, grid)) {
                steps.add(new SquadPlan.Step(new ClearZone(zoneId)));
            }
        }
        steps.add(new SquadPlan.Step(new HoldZone(toZone, node)));
        return new SquadPlan(steps);
    }

    /**
     * True iff {@code zone} is small enough and sits mostly inside the
     * compound's bounding box — i.e. it's a room the garrison should clear,
     * not open ground it merely crosses. Two gates, cheap one first:
     *
     * <ol>
     *   <li><b>Size (O(1)):</b> a zone meaningfully larger than the compound's
     *       footprint is transit ground. {@link NavigationZone#getCellCount()}
     *       is a field read, so the outdoor flood bails here without its
     *       thousands of cells ever being iterated.</li>
     *   <li><b>Containment (O(cells), only on size-gate survivors):</b> a
     *       point-in-rect test per cell; the zone qualifies when at least
     *       {@link #MIN_INSIDE_FRACTION} of its cells fall inside the box.</li>
     * </ol>
     *
     * <p>A multi-room compound clears each interior room (each small + inside
     * the box); the open exterior fails the size gate and is transited only.
     * Uses the raw building bbox — if the courtyard/parade ground should be
     * held too, expand the box by a margin here (story 17's tuning knob).
     */
    private static boolean isGarrisonZone(NavigationZone zone, TacticalNode node, NavigationGrid grid) {
        long compoundArea = (long) (node.right - node.left + 1) * (node.bottom - node.top + 1);
        if (zone.getCellCount() > MAX_GARRISON_AREA_RATIO * compoundArea) return false;

        int width = grid.getWidth();
        int[] cells = zone.getCellIndices();
        int inside = 0;
        for (int idx : cells) {
            int x = idx % width;
            int y = idx / width;
            if (x >= node.left && x <= node.right && y >= node.top && y <= node.bottom) inside++;
        }
        return inside >= MIN_INSIDE_FRACTION * cells.length;
    }

    private static boolean planEndsWithHoldZone(SquadPlan plan, int targetZoneId) {
        if (plan == null || plan.isComplete()) return false;
        List<SquadPlan.Step> steps = plan.steps();
        if (steps.isEmpty()) return false;
        var terminal = steps.get(steps.size() - 1).action;
        return terminal instanceof HoldZone hz && hz.targetZoneId() == targetZoneId;
    }
}
