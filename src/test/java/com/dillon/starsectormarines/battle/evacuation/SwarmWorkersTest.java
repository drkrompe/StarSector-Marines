package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The skitterer's own rules (`colony-breach-nouns.md`, Skitterer); the jobs themselves are the `swarm-workers` scene's. */
class SwarmWorkersTest {

    @Test
    void aBoreBroodsMoreAsItGrowsAndAMotherMoreThanASatellite() {
        int last = 0;
        for (BoreTier tier : BoreTier.values()) {
            int brood = SwarmWorkers.broodFor(tier, false);
            assertTrue(brood > last, tier + " broods more than the tier below");
            assertTrue(SwarmWorkers.broodFor(tier, true) > brood, "a mother at " + tier);
            last = brood;
        }
    }

    @Test
    void itRunsFromPeopleNearAndFromAFreshHurtOnly() {
        int hurtTicks = Math.round(SwarmWorkers.HURT_SECONDS / BattleSimulation.TICK_DT);
        assertTrue(SwarmWorkers.flees(SwarmWorkers.THREAT_RANGE, Integer.MAX_VALUE));
        assertFalse(SwarmWorkers.flees(SwarmWorkers.THREAT_RANGE + 0.1f, Integer.MAX_VALUE));
        assertFalse(SwarmWorkers.flees(-1f, Integer.MAX_VALUE), "nobody near, never hurt");
        assertTrue(SwarmWorkers.flees(-1f, 0));
        assertFalse(SwarmWorkers.flees(-1f, hurtTicks), "an old hurt is forgotten");
    }

    @Test
    void mendingAddsUpPerWorkerAndNeverPassesFull() {
        float one = SwarmWorkers.mended(10f, 1000f, 1, 1f) - 10f;
        assertTrue(one > 0f);
        assertEquals(2f * one, SwarmWorkers.mended(10f, 1000f, 2, 1f) - 10f, 1e-3f);
        assertEquals(1000f, SwarmWorkers.mended(999f, 1000f, 2, 1f), 1e-4f);
    }

    @Test
    void aTendedSpireWaitsLessButNeverUnderTheFloor() {
        int full = BroodSpires.gapTicks(0);
        int fed = Math.round(SwarmWorkers.SPIRE_FEED_SECONDS / BattleSimulation.TICK_DT);
        assertEquals(full - fed, BroodSpires.gapTicks(fed));
        int floor = Math.round(BroodSpires.TENDED_GAP_FLOOR_SECONDS / BattleSimulation.TICK_DT);
        assertEquals(floor, BroodSpires.gapTicks(full * 4));
        assertFalse(BroodSpires.releases(true, false, full - fed - 1, fed, true));
        assertTrue(BroodSpires.releases(true, false, full - fed, fed, true));
    }
}
