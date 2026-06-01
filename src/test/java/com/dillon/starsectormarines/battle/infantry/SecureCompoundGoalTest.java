package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.decision.goap.action.ClearZone;
import com.dillon.starsectormarines.battle.decision.goap.action.EnterZone;
import com.dillon.starsectormarines.battle.decision.goap.action.HoldZone;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Synthetic-grid coverage for {@link SecureCompoundGoal}'s AABB-gated clear
 * scoping (story 17). The bug it pins: a {@code SECURE_COMPOUND} plan used to
 * emit an {@code EnterZone}+{@code ClearZone} pair for <em>every</em> zone on
 * the BFS route to the compound — including the unbounded outdoor flood-fill,
 * which always holds a stray defender somewhere and so never reads clear. A
 * garrison squad would park on that {@code ClearZone[outdoor]} step and chase
 * defenders across the whole map instead of holding the compound.
 *
 * <p>Layout is a 3-zone corridor — a small start room, a large outdoor zone in
 * the middle, and a small compound room on the far side — so the outdoor zone
 * sits on the route as an <em>intermediate</em> zone, exactly where the bug
 * bit. The compound's bbox covers only the far room.
 */
public class SecureCompoundGoalTest {

    private static final int W = 30;
    private static final int H = 10;
    /** Wall column between the start room and the outdoor zone, doorway at row 5. */
    private static final int WALL_A = 4;
    /** Wall column between the outdoor zone and the compound room, doorway at row 5. */
    private static final int WALL_B = 24;

    /**
     * start room (x 0..3) | wall | outdoor (x 5..23) | wall | compound room (x 25..29).
     * Outdoor is ~190 cells; each room is ~40-50 — so the size gate alone
     * separates "room to clear" from "ground to cross."
     */
    private static BattleSimulation threeZoneSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (x == WALL_A || x == WALL_B) continue;
                grid.setWalkableFloor(x, y);
            }
        }
        grid.setWalkableFloor(WALL_A, 5);
        grid.setDoorway(WALL_A, 5, true);
        grid.setWalkableFloor(WALL_B, 5);
        grid.setDoorway(WALL_B, 5, true);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /** Compound node whose bbox covers only the far room (x 25..29, full height). */
    private static TacticalNode compoundNode() {
        return new TacticalNode(TacticalNode.Kind.ARMORY, 27, 5,
                25, 0, 29, 9, Faction.DEFENDER, 80, 4);
    }

    private static Squad squadAt(int id, float centroidX, float centroidY, int aliveMembers) {
        Squad squad = new Squad(id, Faction.MARINE);
        squad.aliveMembers = aliveMembers;
        squad.centroidX = centroidX;
        squad.centroidY = centroidY;
        return squad;
    }

    @Test
    public void planTransitsOutdoorZoneWithoutClearingIt() {
        BattleSimulation sim = threeZoneSim();
        Squad squad = squadAt(1, 2f, 5f, 1); // in the small start room
        TacticalNode node = compoundNode();
        int outdoorZone = sim.getZoneGraph().zoneIdAt(12, 5);
        int compoundZone = sim.getZoneGraph().zoneIdAt(27, 5);
        squad.assignedObjective = ObjectiveAssignment.secureCompound(squad.id, compoundZone, node);

        SquadPlan plan = SecureCompoundGoal.INSTANCE.customPlan(squad, sim);
        assertNotNull(plan, "reachable compound assignment should produce a plan");
        List<SquadPlan.Step> steps = plan.steps();

        // The outdoor flood is huge → fails the size gate → gets a bare transit
        // EnterZone but NEVER a ClearZone. This is the whole point of story 17.
        boolean clearsOutdoor = steps.stream().anyMatch(s ->
                s.action instanceof ClearZone cz && cz.targetZoneId() == outdoorZone);
        assertFalse(clearsOutdoor, "must not emit ClearZone against the unbounded outdoor zone");

        boolean transitsOutdoor = steps.stream().anyMatch(s ->
                s.action instanceof EnterZone ez && ez.targetZoneId() == outdoorZone);
        assertTrue(transitsOutdoor, "must still walk through the outdoor zone via a transit EnterZone");
    }

    @Test
    public void planClearsTheCompoundRoomThenHolds() {
        BattleSimulation sim = threeZoneSim();
        Squad squad = squadAt(1, 2f, 5f, 1);
        TacticalNode node = compoundNode();
        int compoundZone = sim.getZoneGraph().zoneIdAt(27, 5);
        squad.assignedObjective = ObjectiveAssignment.secureCompound(squad.id, compoundZone, node);

        SquadPlan plan = SecureCompoundGoal.INSTANCE.customPlan(squad, sim);
        assertNotNull(plan);
        List<SquadPlan.Step> steps = plan.steps();

        // The compound room is small + inside the bbox → it earns a ClearZone.
        boolean clearsCompound = steps.stream().anyMatch(s ->
                s.action instanceof ClearZone cz && cz.targetZoneId() == compoundZone);
        assertTrue(clearsCompound, "the in-box compound room is small enough to clear");

        // The plan always terminates on HoldZone(compound) — the step the old
        // plan could never reach because it parked on ClearZone[outdoor].
        var terminal = steps.get(steps.size() - 1).action;
        assertTrue(terminal instanceof HoldZone, "plan must terminate on a HoldZone");
        assertEquals(compoundZone, ((HoldZone) terminal).targetZoneId(),
                "the hold target is the compound zone");
    }

    @Test
    public void inCompoundZonePlanIsClearThenHold() {
        // Squad already standing in the compound room: short single-zone plan,
        // no transit hops. (Mirrors the from == to branch.)
        BattleSimulation sim = threeZoneSim();
        Squad squad = squadAt(1, 27f, 5f, 1);
        TacticalNode node = compoundNode();
        int compoundZone = sim.getZoneGraph().zoneIdAt(27, 5);
        squad.assignedObjective = ObjectiveAssignment.secureCompound(squad.id, compoundZone, node);

        SquadPlan plan = SecureCompoundGoal.INSTANCE.customPlan(squad, sim);
        assertNotNull(plan);
        List<SquadPlan.Step> steps = plan.steps();
        var terminal = steps.get(steps.size() - 1).action;
        assertTrue(terminal instanceof HoldZone, "in-zone plan still ends on HoldZone");
        assertEquals(compoundZone, ((HoldZone) terminal).targetZoneId());
        // No EnterZone against a transit zone — squad is already there.
        boolean anyEnter = steps.stream().anyMatch(s -> s.action instanceof EnterZone);
        assertFalse(anyEnter, "already in the compound zone → no transit EnterZone steps");
    }
}
