package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.fixture.BattleFixtureTestSupport;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LosCacheLifecycleTest {

    @Test
    void releaseDeregistersCurrentWorkerCache() {
        LosCache.releaseCurrentThread();
        int before = LosCache.trackedWorkerCount();
        LosCache.enable();
        try {
            LosCache.current();
            assertEquals(before + 1, LosCache.trackedWorkerCount());

            LosCache.releaseCurrentThread();

            assertEquals(before, LosCache.trackedWorkerCount());
        } finally {
            LosCache.releaseCurrentThread();
            LosCache.disable();
        }
    }

    @Test
    void closingFixtureSimulationDeregistersWorkerCaches() throws Exception {
        LosCache.releaseCurrentThread();
        int before = LosCache.trackedWorkerCount();
        BattleSimulation sim = BattleFixtureTestSupport.loadDefaultFixture().build();
        for (int tick = 0; tick < 5; tick++) {
            sim.advance(BattleSimulation.TICK_DT);
        }
        assertTrue(LosCache.trackedWorkerCount() > before,
                "real battle ticks should create worker-local LoS caches");

        sim.close();

        assertEquals(before, LosCache.trackedWorkerCount());
    }
}
