package com.dillon.starsectormarines.battle.creep;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.evacuation.SwarmStrains;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.DeadBodySystem;
import com.dillon.starsectormarines.battle.unit.DeathEvent;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreepDigestionTest {

    @Test
    void aStrainBodyOnLivingCreepSinksIntoItAndOneOffItWaits() {
        NavigationGrid grid = new NavigationGrid(40, 12);
        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 40; x++) grid.setWalkableFloor(x, y);
        }
        try (BattleSimulation sim = new BattleSimulation(grid, new CellTopology(40, 12))) {
            CreepField creep = sim.creepField();
            creep.putSource(1L, 5, 6, new CreepGrowth(6f, 12f));
            for (int t = 0; t < 240; t++) creep.tick(t);
            assertTrue(creep.isCreeped(5, 6) && !creep.isCreeped(35, 6), "creep around the source only");

            long onCreep = corpse(sim, 5, 6);
            long offCreep = corpse(sim, 35, 6);
            CreepDigestion digestion = new CreepDigestion();
            int ticks = Math.round(CreepDigestion.DIGEST_SECONDS / BattleSimulation.TICK_DT);
            for (int t = 0; t < ticks / 2; t++) digestion.tick(sim);
            assertEquals(0.5f, digestion.digested(onCreep), 0.02f, "half digested halfway");
            assertEquals(0f, digestion.digested(offCreep), "nothing off the creep");
            for (int t = 0; t < ticks; t++) digestion.tick(sim);
            assertEquals(-1, sprite(sim, onCreep), "the creep has it: no longer drawn");
            assertTrue(sprite(sim, offCreep) >= 0, "the body off the creep still lies there");
        }
    }

    private static long corpse(BattleSimulation sim, int x, int y) {
        long runner = SwarmStrains.spawn(sim, "runner", UnitType.SWARM_RUNNER, x, y);
        sim.applyDamage(runner, 1000f, 1000f);
        new DeadBodySystem(sim.getEntityWorld(), sim.getBattleComponents(), () -> 0)
                .onDeath(new DeathEvent(runner, x + 0.5f, y + 0.5f, 0));
        return runner;
    }

    private static int sprite(BattleSimulation sim, long body) {
        return sim.getEntityWorld().getInt(body, sim.getBattleComponents().SPRITE, BattleComponents.SPRITE_INDEX);
    }
}
