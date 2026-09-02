package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.fixture.BattleFixtureTestSupport;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LosCacheLifecycleTest {

    @Test
    void releaseDeregistersCurrentWorkerCache() {
        LosCaches caches = new LosCaches();
        caches.enable();
        try {
            caches.current();
            assertEquals(1, caches.trackedWorkerCount());

            caches.releaseCurrentThread();

            assertEquals(0, caches.trackedWorkerCount());
        } finally {
            caches.releaseCurrentThread();
            caches.disable();
        }
    }

    /**
     * The reason the caches hang off the grid rather than off the class: two
     * simulations in one JVM used to sweep and disable each other's.
     */
    @Test
    void eachGridKeepsItsOwnCaches() {
        NavigationGrid first = new NavigationGrid(4, 4);
        NavigationGrid second = new NavigationGrid(4, 4);
        try {
            first.losCaches().enable();

            assertFalse(second.losCaches().isEnabled(),
                    "enabling one grid's caches must not enable another's");
            assertNull(second.losCaches().current(),
                    "a grid whose window is shut hands out no cache");

            second.losCaches().enable();
            LosCache firstCache = first.losCaches().current();
            LosCache secondCache = second.losCaches().current();
            assertNotNull(firstCache);
            assertNotNull(secondCache);
            assertNotSame(firstCache, secondCache,
                    "one thread holds a separate cache per grid");

            firstCache.put(0, 0, 3, 3, true);
            secondCache.put(0, 0, 3, 3, true);

            first.losCaches().clearAll();

            assertEquals(-1, firstCache.tryGet(0, 0, 3, 3),
                    "the swept grid loses its entry");
            assertEquals(1, secondCache.tryGet(0, 0, 3, 3),
                    "the other grid keeps its entry");
        } finally {
            first.losCaches().releaseCurrentThread();
            second.losCaches().releaseCurrentThread();
            first.losCaches().disable();
            second.losCaches().disable();
        }
    }

    @Test
    void closingFixtureSimulationDeregistersWorkerCaches() throws Exception {
        BattleSimulation sim = BattleFixtureTestSupport.loadDefaultFixture().build();
        LosCaches caches = sim.getGrid().losCaches();
        for (int tick = 0; tick < 5; tick++) {
            sim.advance(BattleSimulation.TICK_DT);
        }
        assertTrue(caches.trackedWorkerCount() > 0,
                "real battle ticks should create worker-local LoS caches");

        sim.close();

        assertEquals(0, caches.trackedWorkerCount());
    }
}
