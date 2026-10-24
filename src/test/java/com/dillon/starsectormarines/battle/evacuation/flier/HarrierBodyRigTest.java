package com.dillon.starsectormarines.battle.evacuation.flier;

import com.dillon.starsectormarines.battle.air.AirBody;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HarrierBodyRigTest {

    private static final float DT = 1f / 30f;

    private static HarrierFlight flight() {
        AirBody body = new AirBody();
        body.teleport(0f, 0f, 0f);
        return new HarrierFlight(7L, body, HarrierLook.DEFAULT);
    }

    @Test
    void aTurnBendsTheWaistAndTheTailTrailsItThenStraightensOut() {
        HarrierFlight m = flight();
        HarrierBodyRig rig = m.rig;
        for (int t = 0; t < 20; t++) {
            m.body.facingDegrees += 120f * DT;   // a steady left turn
            rig.advance(m, -1f, Float.NaN, Float.NaN, true, DT);
        }
        assertTrue(rig.waist < -1f, "the abdomen swings out of a left turn: " + rig.waist);
        for (float a : rig.tail) assertTrue(a < 0f, "the tail trails the turn: " + a);
        for (int t = 0; t < 60; t++) rig.advance(m, -1f, Float.NaN, Float.NaN, true, DT);
        assertEquals(0f, rig.waist, 0.1f);
        for (float a : rig.tail) assertEquals(0f, a, 0.1f);
    }

    @Test
    void theTailSwaysWithTheBeat() {
        HarrierFlight m = flight();
        float most = 0f;
        for (int t = 0; t < 60; t++) {
            m.rig.advance(m, (t * DT / HarrierWingbeat.BEAT_SECONDS) % 1f, Float.NaN, Float.NaN, true, DT);
            most = Math.max(most, Math.abs(m.rig.tail[HarrierBodyRig.TAIL_SEGMENTS - 1]));
        }
        assertTrue(most > 0.5f, "the tip swung " + most);
    }

    @Test
    void theBodyGrowsOnTheDownstrokeAndShrinksOnTheUpstroke() {
        HarrierFlight m = flight();
        m.rig.advance(m, 0.75f, Float.NaN, Float.NaN, true, DT);
        assertEquals(1f + HarrierBodyRig.PUMP, m.rig.pump, 1e-4f);
        m.rig.advance(m, 0.25f, Float.NaN, Float.NaN, true, DT);
        assertEquals(1f - HarrierBodyRig.PUMP, m.rig.pump, 1e-4f);
        m.rig.advance(m, -1f, Float.NaN, Float.NaN, true, DT);
        assertEquals(1f, m.rig.pump, 0f);
    }

    @Test
    void aDiverLocksItsHeadOnItsPreyAndACirclerScansAboutIt() {
        HarrierFlight m = flight();
        for (int t = 0; t < 60; t++) m.rig.advance(m, -1f, -10f, 10f, true, DT);   // prey 45 degrees to the left
        assertEquals(45f, m.rig.head, 0.5f);
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (int t = 0; t < 120; t++) {
            m.wingClock += DT;
            m.rig.advance(m, -1f, -10f, 10f, false, DT);
            lo = Math.min(lo, m.rig.head);
            hi = Math.max(hi, m.rig.head);
        }
        assertTrue(hi - lo > 20f && lo < 45f && hi <= HarrierBodyRig.HEAD_MAX + 1e-3f, "scanned " + lo + ".." + hi);
    }
}
