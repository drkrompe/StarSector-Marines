package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.evacuation.SwarmStrains;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BodyPileCapTest {

    @Test
    void anAreaOverItsLimitLosesItsOldestAndNoOtherArea() {
        BodyPileCap cap = new BodyPileCap(true, 3, 100);
        // area 0: five bodies, deaths out of id order; area 1: three, at its limit
        int[][] area0 = {{10, 50}, {11, 20}, {12, 40}, {13, 20}, {14, 90}};
        int i = 0;
        for (int[] body : area0) cap.gather(i++, body[0], body[1], 0);
        for (int k = 0; k < 3; k++) cap.gather(i++, 20 + k, 0, 1);
        cap.retireOldest(i);
        // the two oldest in area 0, the tie broken by id
        assertTrue(cap.isFading(11) && cap.isFading(13));
        assertFalse(cap.isFading(10) || cap.isFading(12) || cap.isFading(14));
        assertFalse(cap.isFading(20) || cap.isFading(21) || cap.isFading(22), "an area at its limit is left alone");
    }

    @Test
    void pastTheGlobalLimitTheOldestAnywhereGo() {
        BodyPileCap cap = new BodyPileCap(true, 100, 4);
        for (int k = 0; k < 6; k++) cap.gather(k, 100 + k, 60 - k, k);
        cap.retireOldest(6);
        assertTrue(cap.isFading(105) && cap.isFading(104));
        assertFalse(cap.isFading(103));
    }

    @Test
    void aRetiredBodyFadesAndIsNoLongerDrawnAndTheControlTouchesNothing() {
        NavigationGrid grid = new NavigationGrid(16, 8);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 16; x++) grid.setWalkableFloor(x, y);
        }
        try (BattleSimulation sim = new BattleSimulation(grid, new CellTopology(16, 8))) {
            long first = corpse(sim, 2, 2, 0);
            long second = corpse(sim, 3, 2, 1);
            long apart = corpse(sim, 12, 2, 0);
            BodyPileCap cap = new BodyPileCap(true, 1, 100);
            BodyPileCap control = new BodyPileCap(false, 1, 100);
            int fade = Math.round(BodyPileCap.FADE_SECONDS / BattleSimulation.TICK_DT);
            for (int t = 0; t <= fade / 2; t++) {
                cap.tick(sim);
                control.tick(sim);
            }
            assertTrue(cap.faded(first) > 0.4f && cap.faded(first) < 0.6f, "half faded halfway");
            assertEquals(0f, cap.faded(second));
            assertEquals(0f, control.faded(first), "the control retires nothing");
            for (int t = 0; t < fade; t++) cap.tick(sim);
            assertEquals(-1, sprite(sim, first), "the oldest of the crowded area is gone");
            assertTrue(sprite(sim, second) >= 0 && sprite(sim, apart) >= 0);
            assertEquals(1, cap.retired());
        }
    }

    private static long corpse(BattleSimulation sim, int x, int y, int tick) {
        long runner = SwarmStrains.spawn(sim, "runner", UnitType.SWARM_RUNNER, x, y);
        sim.applyDamage(runner, 1000f, 1000f);
        new DeadBodySystem(sim.getEntityWorld(), sim.getBattleComponents(), () -> tick)
                .onDeath(new DeathEvent(runner, x + 0.5f, y + 0.5f, 0));
        return runner;
    }

    private static int sprite(BattleSimulation sim, long body) {
        return sim.getEntityWorld().getInt(body, sim.getBattleComponents().SPRITE, BattleComponents.SPRITE_INDEX);
    }
}
