package com.dillon.starsectormarines.battle.scene;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.OrderTrace.Sample;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the labels the trace reads off a real squad are the ones the debug
 * readouts show. The arithmetic is pinned on synthetic streams elsewhere; this
 * only closes the seam between {@link Squad}'s fields and the sample, which no
 * synthetic stream can.
 */
class OrderTraceSimTest {

    private static final int W = 24;
    private static final int H = 12;

    @Test
    void readsTheOrderStackOffALiveSquad() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        sim.setMissionCompletionEnabled(false);

        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        for (int i = 0; i < 2; i++) {
            sim.spawn(new EntitySpec("m" + i, Faction.MARINE, UnitType.MARINE, 2 + i, 6)
                    .squad(squadId));
        }
        Squad squad = sim.getSquad(squadId);
        assertNotNull(squad);
        squad.originalSize = 2;
        squad.aliveMembers = 2;
        squad.assignedObjective = ObjectiveAssignment.attackMove(squadId, 20, 6);

        OrderTrace trace = new OrderTrace().track("assault", squadId);
        trace.observe(sim, 0);

        Sample s = trace.at(squadId, 0);
        assertNotNull(s);
        assertEquals("ATTACK_MOVE cell:20,6", s.mission());
        assertEquals(s.mission(), s.executing(), "no player order stands, so the mission is what it runs");
        assertFalse(s.playerOrdered());
        assertEquals("", s.suspension(), "a minted squad is not forming up");
        assertEquals(2, s.alive());
        assertTrue(s.planless(), "no replan has run before the first tick advances");
        assertEquals(1, trace.planlessTicks(squadId));
    }
}
