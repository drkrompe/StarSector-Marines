package com.dillon.starsectormarines.battle.evacuation.flier;

import com.dillon.starsectormarines.battle.air.AirBody;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HarrierFallTest {

    private static final float DT = 1f / 30f;

    private static HarrierFall fall(float altitude) {
        AirBody body = new AirBody();
        body.teleport(10f, 10f, 0f);
        body.vx = 6f;
        return new HarrierFall(42L, HarrierLook.DEFAULT, body, altitude);
    }

    @Test
    void dropsFasterAndFasterAndLandsOnTheGroundAheadOfWhereItWasHit() {
        HarrierFall f = fall(1f);
        float lastAltitude = f.altitude(), lastDrop = 0f, lastX = f.x();
        int ticks = 0;
        while (!f.landed()) {
            f.advance(DT);
            ticks++;
            float drop = lastAltitude - f.altitude();
            assertTrue(drop >= lastDrop - 1e-6f, "the fall accelerates");
            assertTrue(f.x() >= lastX, "it carries on the way it flew");
            lastDrop = drop;
            lastAltitude = f.altitude();
            lastX = f.x();
        }
        assertEquals(0f, f.altitude(), 1e-6f);
        assertTrue(f.x() > 10f, "its momentum carried it on");
        assertEquals(Math.round(HarrierRules.FALL_SECONDS / DT), ticks, 1);
    }

    @Test
    void aFlierHitLowFallsForLessAndItsMomentumStopsAtTheGround() {
        HarrierFall high = fall(1f), low = fall(0.1f);
        int lowTicks = 0;
        while (!low.landed()) { low.advance(DT); lowTicks++; }
        float landedX = low.x();
        low.advance(DT);
        assertEquals(landedX, low.x(), 1e-6f, "a body on the ground stays put");
        for (int i = 0; i < lowTicks; i++) high.advance(DT);
        assertFalse(high.landed(), "one hit at height is still falling");
    }

    @Test
    void itsCorpseDrawingIsSeededOffTheId() {
        assertEquals(fall(1f).corpseVariant(), fall(0.3f).corpseVariant());
        int v = fall(1f).corpseVariant();
        assertTrue(v >= 0 && v < HarrierLook.CORPSE_VARIANTS);
    }
}
