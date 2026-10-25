package com.dillon.starsectormarines.battle.evacuation.flier;

import com.dillon.starsectormarines.battle.air.AirBody;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A patrol's loop over its nest, and its landing back on the spire. */
class HarrierPatrolTest {

    private static final float BX = 100f, BY = 100f, HX = 120f, HY = 100f;

    @Test
    void itsLoopRingsTheBoreAtItsReachAndEndsOnTheSpire() {
        float[] r = HarrierPatrol.route(BX, BY, HX, HY, 7L, 400, 400);
        assertEquals((HarrierPatrol.LEGS + 1) * 2, r.length);
        for (int i = 0; i < HarrierPatrol.LEGS; i++) {
            float d = (float) Math.hypot(r[i * 2] - BX, r[i * 2 + 1] - BY);
            assertTrue(d >= HarrierPatrol.REACH_MIN - 1e-3f && d <= HarrierPatrol.REACH_MAX + 1e-3f, "out " + d);
        }
        assertEquals(HX, r[r.length - 2]);
        assertEquals(HY, r[r.length - 1]);
    }

    @Test
    void itStaysOnTheMap() {
        float[] r = HarrierPatrol.route(3f, 3f, 10f, 3f, 11L, 40, 40);
        for (float v : r) assertTrue(v >= 2f && v <= 38f, "on the map, " + v);
    }

    @Test
    void theSameSpireFliesTheSameLoopAndAnotherPatrolAnother() {
        assertArrayEquals(HarrierPatrol.route(BX, BY, HX, HY, 7L, 400, 400), HarrierPatrol.route(BX, BY, HX, HY, 7L, 400, 400));
        assertFalse(Arrays.equals(HarrierPatrol.route(BX, BY, HX, HY, 7L, 400, 400),
                HarrierPatrol.route(BX, BY, HX, HY, 8L, 400, 400)));
    }

    @Test
    void itsAnchorWalksTheRouteAtItsSpeedAndComesHome() {
        HarrierPatrol p = new HarrierPatrol(new float[]{130f, 100f, 120f, 100f}, 120f, 100f, 3);
        p.step(1f);
        assertEquals(120f + HarrierPatrol.SPEED, p.x(), 1e-3f);
        assertFalse(p.homing());
        for (int i = 0; i < 20; i++) p.step(1f);
        assertTrue(p.homing());
        assertEquals(120f, p.x(), 1e-3f);
    }

    @Test
    void aHomingPatrolLandsOnItsSpireAndNowhereElse() {
        HarrierPack pack = new HarrierPack(1, HX, HY);
        pack.patrol = new HarrierPatrol(new float[]{HX, HY}, HX, HY, 1);
        pack.patrol.step(1f);
        AirBody body = new AirBody();
        body.teleport(HX + 6f, HY, 0f);
        pack.members.add(new HarrierFlight(5L, body, HarrierLook.DEFAULT));
        for (int t = 0; t < 300 && !pack.members.isEmpty(); t++) {
            pack.advance(1f / 30f, (h, target) -> { });
            if (pack.landedNow.isEmpty()) continue;
            assertTrue(pack.landedNow.get(0).body.distanceTo(HX, HY) <= HarrierPatrol.LAND_REACH);
            pack.landedNow.clear();
        }
        assertTrue(pack.members.isEmpty(), "landed");
        assertEquals(1, pack.patrol.landed());
    }
}
