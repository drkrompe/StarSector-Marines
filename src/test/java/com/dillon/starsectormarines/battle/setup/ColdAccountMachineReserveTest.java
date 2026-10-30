package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The outpost's machines stand down in their shed until the company is inside the walls, then come out on a clock. */
class ColdAccountMachineReserveTest {
    private static final int W = 60, H = 20;
    private static final int[] COMPOUND = {5, 2, 30, 17};
    private static final int[] SHED = {10, 10};

    @Test
    void machinesWaitForTheCompanyInsideThenComeOutTogether() {
        try (BattleSimulation sim = arena()) {
            var reserve = reserve();
            long marine = sim.spawn(new EntitySpec("pointman", Faction.MARINE, UnitType.MARINE, 45, 10));
            for (int t = 0; t < ticks(ColdAccountMachineReserve.WAKE_SECONDS) * 2; t++) reserve.advance(sim);
            assertFalse(reserve.awake(), "a company outside the walls leaves the shed shut");
            assertEquals(2, reserve.pendingMembers());

            sim.world().setPos(marine, 25.5f, 10.5f);
            int wake = ticks(ColdAccountMachineReserve.WAKE_SECONDS);
            for (int t = 0; t < wake; t++) reserve.advance(sim);
            assertFalse(reserve.awake(), "the crews take the clock to mount up");
            for (int t = 0; t < 2; t++) reserve.advance(sim);
            assertTrue(reserve.awake());
            assertEquals(0, reserve.pendingMembers());
            assertEquals(2, sim.squadMemberCount(reserve.squad()));
        }
    }

    private static ColdAccountMachineReserve reserve() {
        return new ColdAccountMachineReserve(List.of(MechVariant.HOUND, MechVariant.WARDEN), COMPOUND, SHED, "pirates");
    }

    private static int ticks(float seconds) {
        return (int) Math.ceil(seconds / BattleSimulation.TICK_DT);
    }

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        sim.setMissionCompletionEnabled(false);
        return sim;
    }
}
