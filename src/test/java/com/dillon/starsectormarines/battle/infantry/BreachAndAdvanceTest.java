package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase-transition + slot-binding coverage for {@link BreachAndAdvance}.
 * Pins the stack-up/advance state machine to its observable contract:
 * before the squad is stacked, members head for their stack-up cell; once
 * stacked (or timed out), they head for the forward cover cell.
 */
public class BreachAndAdvanceTest {

    private static BattleSimulation openArena(int w, int h) {
        NavigationGrid grid = new NavigationGrid(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(w, h));
    }

    private static Squad squad(BattleSimulation sim, int memberCount, int startX, int startY) {
        Entity first = new Entity("m0", Faction.MARINE, UnitType.MARINE, startX, startY);
        int sid = sim.mintSquad(Faction.MARINE, first);
        first.squadId = sid;
        sim.addUnit(first);
        for (int i = 1; i < memberCount; i++) {
            Entity u = new Entity("m" + i, Faction.MARINE, UnitType.MARINE, startX + i, startY);
            u.squadId = sid;
            sim.addUnit(u);
        }
        Squad sq = sim.getSquad(sid);
        sq.aliveMembers = memberCount;
        sq.centroidX = startX;
        sq.centroidY = startY;
        return sq;
    }

    /** Builds a plan-with-one-step around the action and binds each alive member to slot N. */
    private static SquadPlan attach(BattleSimulation sim, Squad squad, BreachAndAdvance action) {
        SquadPlan.Step step = new SquadPlan.Step(action);
        int slotIdx = 0;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            Entity u = sim.liveUnitAt(i);
            if (u.squadId != squad.id) continue;
            step.assignments.put("breacher:" + slotIdx, new java.util.ArrayList<>(List.of(u)));
            slotIdx++;
        }
        SquadPlan plan = new SquadPlan(List.of(step));
        squad.currentPlan = plan;
        return plan;
    }

    @Test
    public void stackUpPhaseRoutesToStackUpCell() {
        BattleSimulation sim = openArena(20, 20);
        Squad sq = squad(sim, 2, 2, 5);
        // Stack-up cells far from start, forward cells even further.
        int[] stackX = {8, 8};
        int[] stackY = {5, 6};
        int[] forwardX = {14, 14};
        int[] forwardY = {5, 6};
        BreachAndAdvance action = new BreachAndAdvance(99, stackX, stackY, forwardX, forwardY);
        attach(sim, sq, action);

        // No members are near the stack-up cells yet → first execute should
        // path members toward the stack-up cell, not the forward cell.
        Entity m0 = sim.liveUnitAt(0);
        action.execute(m0, sq, sim);
        assertNotEquals(0, m0.pathCellCount(), "stack-up phase queues a path");
        // Path destination is the stack-up cell, not the forward cell.
        int destX = m0.pathCellX(m0.pathCellCount() - 1);
        int destY = m0.pathCellY(m0.pathCellCount() - 1);
        assertEquals(8, destX, "stack-up dest x");
        assertEquals(5, destY, "stack-up dest y");
    }

    @Test
    public void advancePhaseRoutesToForwardCell() {
        BattleSimulation sim = openArena(20, 20);
        // Place both members AT their stack-up cells already.
        Entity m0 = new Entity("m0", Faction.MARINE, UnitType.MARINE, 8, 5);
        Entity m1 = new Entity("m1", Faction.MARINE, UnitType.MARINE, 8, 6);
        int sid = sim.mintSquad(Faction.MARINE, m0);
        m0.squadId = sid;
        m1.squadId = sid;
        sim.addUnit(m0);
        sim.addUnit(m1);
        Squad sq = sim.getSquad(sid);
        sq.aliveMembers = 2;
        sq.centroidX = 8f;
        sq.centroidY = 5.5f;

        int[] stackX = {8, 8};
        int[] stackY = {5, 6};
        int[] forwardX = {14, 14};
        int[] forwardY = {5, 6};
        BreachAndAdvance action = new BreachAndAdvance(99, stackX, stackY, forwardX, forwardY);
        attach(sim, sq, action);

        action.execute(m0, sq, sim);
        // Squad is stacked → advance phase → path to forward cell.
        int destX = m0.pathCellX(m0.pathCellCount() - 1);
        int destY = m0.pathCellY(m0.pathCellCount() - 1);
        assertEquals(14, destX, "advance phase dest x");
        assertEquals(5, destY, "advance phase dest y");
    }

    @Test
    public void timeoutForcesAdvancePhase() {
        BattleSimulation sim = openArena(20, 20);
        Squad sq = squad(sim, 2, 2, 5);
        int[] stackX = {8, 8};
        int[] stackY = {5, 6};
        int[] forwardX = {14, 14};
        int[] forwardY = {5, 6};
        BreachAndAdvance action = new BreachAndAdvance(99, stackX, stackY, forwardX, forwardY);
        attach(sim, sq, action);

        // Force the timeout: marines are nowhere near stack-up but the squad
        // timer says we've waited long enough.
        sq.breachStackupTimer = BreachAndAdvance.STACKUP_TIMEOUT_SECONDS + 0.1f;

        Entity m0 = sim.liveUnitAt(0);
        action.execute(m0, sq, sim);
        int destX = m0.pathCellX(m0.pathCellCount() - 1);
        assertEquals(14, destX, "timeout commits the breach — path heads for forward cell");
    }

    @Test
    public void arrivalAtForwardCellClearsPathAndPinsRender() {
        BattleSimulation sim = openArena(20, 20);
        Entity m0 = new Entity("m0", Faction.MARINE, UnitType.MARINE, 14, 5);
        Entity m1 = new Entity("m1", Faction.MARINE, UnitType.MARINE, 14, 6);
        int sid = sim.mintSquad(Faction.MARINE, m0);
        m0.squadId = sid;
        m1.squadId = sid;
        sim.addUnit(m0);
        sim.addUnit(m1);
        Squad sq = sim.getSquad(sid);
        sq.aliveMembers = 2;
        sq.centroidX = 14f;
        sq.centroidY = 5.5f;
        // Force advance phase via timeout — both members are at their forward
        // cells, so execute should pin them in place.
        sq.breachStackupTimer = BreachAndAdvance.STACKUP_TIMEOUT_SECONDS + 1f;

        int[] stackX = {8, 8};
        int[] stackY = {5, 6};
        int[] forwardX = {14, 14};
        int[] forwardY = {5, 6};
        BreachAndAdvance action = new BreachAndAdvance(99, stackX, stackY, forwardX, forwardY);
        attach(sim, sq, action);

        action.execute(m0, sq, sim);
        assertTrue(m0.pathEmpty(), "arrived members clear their path");
        assertEquals(0f, sim.world().moveProgress(m0.entityId), 1e-6f);
        assertEquals(14f, m0.getRenderX(), 1e-6f, "render pinned at the destination cell");
    }

    @Test
    public void successWhenAllMembersAtForward() {
        BattleSimulation sim = openArena(20, 20);
        Entity m0 = new Entity("m0", Faction.MARINE, UnitType.MARINE, 14, 5);
        Entity m1 = new Entity("m1", Faction.MARINE, UnitType.MARINE, 14, 6);
        int sid = sim.mintSquad(Faction.MARINE, m0);
        m0.squadId = sid;
        m1.squadId = sid;
        sim.addUnit(m0);
        sim.addUnit(m1);
        Squad sq = sim.getSquad(sid);
        sq.aliveMembers = 2;
        sq.centroidX = 14f;
        sq.centroidY = 5.5f;
        sq.breachStackupTimer = BreachAndAdvance.STACKUP_TIMEOUT_SECONDS + 1f;

        int[] stackX = {8, 8};
        int[] stackY = {5, 6};
        int[] forwardX = {14, 14};
        int[] forwardY = {5, 6};
        BreachAndAdvance action = new BreachAndAdvance(99, stackX, stackY, forwardX, forwardY);
        attach(sim, sq, action);

        // Both members at their forward cells; first execute should detect
        // squad-wide success and return SUCCESS for the plan to advance.
        ActionStatus status = action.execute(m0, sq, sim);
        assertSame(ActionStatus.SUCCESS, status,
                "all alive members at their forward cells → plan step SUCCESS");
    }

    @Test
    public void slotCountIsArrayLength() {
        int[] sx = {1, 2, 3};
        int[] sy = {1, 1, 1};
        int[] fx = {10, 11, 12};
        int[] fy = {1, 1, 1};
        BreachAndAdvance action = new BreachAndAdvance(7, sx, sy, fx, fy);
        assertEquals(3, action.slotCount());
        assertEquals(7, action.portalId());
    }

    @Test
    public void rolesReturnsOneSlotPerCell() {
        int[] sx = {1, 2};
        int[] sy = {1, 1};
        int[] fx = {10, 11};
        int[] fy = {1, 1};
        BreachAndAdvance action = new BreachAndAdvance(0, sx, sy, fx, fy);
        BattleSimulation sim = openArena(15, 15);
        Squad sq = new Squad(0, Faction.MARINE);
        sq.aliveMembers = 2;
        var slots = action.roles(sq, sim);
        assertEquals(2, slots.size(), "one slot per breach position");
        assertNotNull(slots.get(0).name());
        assertTrue(slots.get(0).name().startsWith("breacher:"));
    }
}
