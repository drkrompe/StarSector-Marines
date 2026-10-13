package com.dillon.starsectormarines.battle.warship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The low pass's line, speed, time-cost arithmetic and clocks: no battle, no ship. */
class LowPassTest {

    private static final float W = 400f;
    private static final float H = 300f;

    private static LowPass line(float shipX, float shipY, float ax, float ay, float bx, float by) {
        return LowPass.plan(shipX, shipY, ax, ay, bx, by, W, H);
    }

    @Test
    void aLineShorterThanTheMinimumIsRefusedAndALongOneIsCut() {
        assertNull(line(0, 0, 100, 100, 110, 100));
        assertNull(line(0, 0, Float.NaN, 100, 150, 100));
        LowPass cut = line(0, 0, 10, 100, 390, 100);
        assertEquals(LowPass.MAX_LENGTH_CELLS, cut.length(), 1e-3f);
        assertEquals(10f + LowPass.MAX_LENGTH_CELLS, cut.endX(), 1e-3f);
        assertEquals(100f, cut.endY(), 1e-3f);
    }

    @Test
    void bothEndsAreKeptOnTheMap() {
        LowPass p = line(0, 0, -50, 20, 500, 20);
        assertEquals(0f, p.startX(), 1e-4f);
        assertTrue(p.endX() <= W);
    }

    @Test
    void headingFollowsTheWarshipConvention() {
        assertEquals(-90f, line(10, 50, 10, 50, 110, 50).headingDegrees(), 1e-3f);
        assertEquals(0f, line(50, 10, 50, 10, 50, 110).headingDegrees(), 1e-3f);
    }

    @Test
    void passSpeedAndAccelerationAreMultiplesOfTheHullsOwn() {
        assertEquals(1.5f * LowPass.SPEED_FACTOR, LowPass.passSpeed(1.5f), 1e-5f);
        assertEquals(0.4f * LowPass.HANDLING_FACTOR, LowPass.passAcceleration(0.4f), 1e-5f);
        assertTrue(LowPass.passSpeed(1.5f) > 1.5f, "faster than hover station-keeping");
    }

    @Test
    void timeToCoverIsTheRampPlusCruiseFromRest() {
        float v = LowPass.passSpeed(2f);
        float a = LowPass.passAcceleration(1f);
        float ramp = v * v / (2f * a);
        // Past the ramp: accelerate, then cruise.
        assertEquals(v / a + (100f - ramp) / v, LowPass.secondsToCover(100f, 2f, 1f), 1e-4f);
        // Inside the ramp it never reaches speed: s = a t^2 / 2.
        assertEquals((float) Math.sqrt(2f * 2f / a), LowPass.secondsToCover(2f, 2f, 1f), 1e-4f);
        assertEquals(0f, LowPass.secondsToCover(0f, 2f, 1f));
    }

    @Test
    void everySecondFlownCostsTwiceThatInStationTime() {
        assertEquals(2f, LowPass.STATION_COST_FACTOR);
        assertEquals(30f, LowPass.stationCost(15f), 1e-5f);
    }

    @Test
    void theEstimatedCostIncludesTheRunInToA() {
        LowPass onA = line(50, 100, 50, 100, 200, 100);
        LowPass far = line(50, 160, 50, 100, 200, 100);
        float at = onA.estimatedCost(50, 100, 2f, 1f);
        float away = far.estimatedCost(50, 160, 2f, 1f);
        assertTrue(away > at, "running in costs station time too");
        assertEquals(LowPass.stationCost(LowPass.secondsToCover(onA.length(), 2f, 1f)), at, 1e-4f);
    }

    @Test
    void aHullAlreadyOnAStartsOnTheLineAndOneFarOffRunsIn() {
        assertEquals(LowPass.Phase.PASS, line(50, 100, 52, 100, 200, 100).phase());
        assertEquals(LowPass.Phase.RUN_IN, line(50, 160, 50, 100, 200, 100).phase());
    }

    @Test
    void alongAndAcrossAreTheLinesOwnAxes() {
        LowPass p = line(0, 0, 100, 100, 200, 100);
        assertEquals(40f, p.along(140, 120), 1e-4f);
        assertEquals(20f, p.across(140, 120), 1e-4f);
        assertEquals(20f, p.across(140, 80), 1e-4f);
    }

    @Test
    void theCorridorIsTheLineWithRoundedEnds() {
        LowPass p = line(0, 0, 100, 100, 200, 100);
        float w = LowPass.CORRIDOR_HALF_WIDTH_CELLS;
        assertTrue(p.inCorridor(150, 100 + w - 0.5f));
        assertFalse(p.inCorridor(150, 100 + w + 0.5f));
        assertTrue(p.inCorridor(100 - w + 1f, 100), "the cap before A");
        assertFalse(p.inCorridor(100 - w - 1f, 100));
        assertFalse(p.inCorridor(100 - w + 1f, 100 + w - 1f), "a rounded cap, not a box");
    }

    @Test
    void theFiringCircleLeadsTheHullAndIsKeptOnTheLine() {
        LowPass p = line(0, 0, 100, 100, 200, 100);
        float[] c = p.fireCentre(140, 120);
        assertEquals(140f + LowPass.LEAD_CELLS, c[0], 1e-4f);
        assertEquals(100f, c[1], 1e-4f);
        assertEquals(100f, p.fireCentre(-40, 100)[0], 1e-4f, "never before A");
        assertEquals(200f, p.fireCentre(199, 100)[0], 1e-4f, "never past B");
    }

    @Test
    void runInHandsToThePassAtAAndThePassEndsAtB() {
        LowPass p = line(50, 160, 50, 100, 200, 100);
        p.advance(0.1f, 50, 160);
        assertEquals(LowPass.Phase.RUN_IN, p.phase());
        assertTrue(p.active());
        p.advance(0.1f, 50, 103);
        assertEquals(LowPass.Phase.PASS, p.phase());
        assertTrue(p.onLine());
        p.advance(0.1f, 120, 100);
        assertEquals(70f, p.progress(), 1e-4f);
        p.advance(0.1f, 100, 100);
        assertEquals(70f, p.progress(), 1e-4f, "progress never goes back");
        p.advance(0.1f, 199.5f, 100);
        assertEquals(LowPass.Phase.DONE, p.phase());
        assertFalse(p.active());
        assertEquals(0f, p.endedSeconds());
    }

    @Test
    void theStartSoundIsAskedForOnceAndOnlyOnTheLine() {
        LowPass p = line(50, 160, 50, 100, 200, 100);
        assertFalse(p.takeStartSound(), "not while running in");
        p.advance(0.1f, 50, 100);
        assertTrue(p.takeStartSound());
        assertFalse(p.takeStartSound());
    }

    @Test
    void blendBuildsWithTheRunAndFadesAfterIt() {
        LowPass p = line(50, 100, 50, 100, 200, 100);
        assertEquals(0f, p.blend(), 1e-5f);
        p.advance(LowPass.RAMP_SECONDS, 60, 100);
        assertEquals(1f, p.blend(), 1e-5f);
        p.advance(0.1f, 199.5f, 100);
        assertEquals(1f, p.blend(), 1e-5f, "full at the instant it ends");
        p.advance(LowPass.FADE_SECONDS / 2f, 210, 100);
        assertTrue(p.blend() > 0.3f && p.blend() < 0.7f);
        assertFalse(p.spent());
        p.advance(LowPass.FADE_SECONDS, 220, 100);
        assertEquals(0f, p.blend(), 1e-5f);
        assertTrue(p.spent());
    }

    @Test
    void theSteerPointIsAWhileRunningInAndAheadOnTheLineAfter() {
        LowPass p = line(50, 160, 50, 100, 200, 100);
        float[] in = p.steerPoint(50, 160);
        assertEquals(50f, in[0]);
        assertEquals(100f, in[1]);
        p.advance(0.1f, 50, 100);
        float[] on = p.steerPoint(70, 104);
        assertEquals(70f + 12f, on[0], 1e-4f);
        assertEquals(100f, on[1], 1e-4f);
    }

    @Test
    void theShadowAndWakeCurvesSkimTheGroundAtThePeakAndAreOrdinaryAtNone() {
        assertEquals(1f, WarshipAtmosphere.passShadowAltitudeFactor(0f), 1e-5f);
        assertEquals(WarshipAtmosphere.PASS_SHADOW_ALTITUDE_KEEP, WarshipAtmosphere.passShadowAltitudeFactor(1f), 1e-5f);
        assertEquals(1f, WarshipAtmosphere.passShadowDensityFactor(0f), 1e-5f);
        assertTrue(WarshipAtmosphere.passShadowDensityFactor(1f) > 1f);
        assertEquals(0.5f, WarshipAtmosphere.passShadowSoftness(0.5f, 0f), 1e-5f);
        assertEquals(0f, WarshipAtmosphere.passShadowSoftness(0.5f, 1f), 1e-5f);
        assertEquals(0f, WarshipAtmosphere.passWake(0f));
        assertEquals(1f, WarshipAtmosphere.passWake(1f));
        assertEquals(1f, WarshipAtmosphere.passDust(0f), 1e-5f);
        assertEquals(0f, WarshipAtmosphere.passDust(WarshipAtmosphere.PASS_DUST_SECONDS), 1e-5f);
    }
}
