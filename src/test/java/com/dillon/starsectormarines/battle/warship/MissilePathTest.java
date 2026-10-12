package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.warship.MissileFlightSpec.Guidance;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The closed-form missile path, asked of the function directly with hand-built specs. */
class MissilePathTest {

    private static MissileFlightSpec spec(Guidance guidance, float launchPx, float maxPx, float accPx,
                                          float decPx, float turnDeg, float burnSeconds, float armingSeconds) {
        return new MissileFlightSpec(guidance, launchPx, maxPx, accPx, decPx, turnDeg, burnSeconds,
                armingSeconds, 100f, Color.ORANGE, Color.GRAY, 1f, 1f, false, 20f, 4f, null);
    }

    /** A Locust: fast, quick to turn. */
    private static final MissileFlightSpec HOMING = spec(Guidance.HOMING, 100f, 400f, 2500f, 2500f, 240f, 6f, 0f);
    /** An Annihilator rocket: no turn at all. */
    private static final MissileFlightSpec ROCKET = spec(Guidance.UNGUIDED, 50f, 400f, 400f, 0f, 0f, 3.75f, 0f);

    @Test
    void endsExactlyWhereItWasAimed() {
        MissilePath p = MissilePath.plan(HOMING, 10f, 20f, 70f, 50f, 7L);
        assertEquals(10f, p.x(0f), 1e-4);
        assertEquals(20f, p.y(0f), 1e-4);
        assertEquals(70f, p.landX(), 1e-3);
        assertEquals(50f, p.landY(), 1e-3);
        assertEquals(70f, p.x(p.totalSeconds()), 1e-3);
        assertEquals(50f, p.y(p.totalSeconds()), 1e-3);
        assertFalse(p.fallsShort());
    }

    @Test
    void theSameSeedFliesTheSamePathAndAnotherDoesNot() {
        MissilePath a = MissilePath.plan(ROCKET, 0f, 0f, 60f, 0f, 42L);
        MissilePath b = MissilePath.plan(ROCKET, 0f, 0f, 60f, 0f, 42L);
        MissilePath c = MissilePath.plan(ROCKET, 0f, 0f, 60f, 0f, 43L);
        float mid = a.totalSeconds() * 0.5f;
        assertEquals(a.x(mid), b.x(mid), 0f);
        assertEquals(a.y(mid), b.y(mid), 0f);
        assertNotEquals(a.y(mid), c.y(mid), 1e-3f);
    }

    @Test
    void speedIsContinuousAcrossBoostCruiseAndCoast() {
        MissileFlightSpec coasting = spec(Guidance.UNGUIDED, 50f, 400f, 400f, 300f, 0f, 2f, 0f);
        MissilePath p = MissilePath.plan(coasting, 0f, 0f, 200f, 0f, 3L);
        float step = 1e-3f;
        float maxJump = WarshipScale.cells(400f) * 0.01f;
        for (float t = 0f; t + step < p.totalSeconds(); t += step) {
            assertTrue(Math.abs(p.speed(t + step) - p.speed(t)) <= Math.max(maxJump, WarshipScale.cells(400f) * step * 1.1f),
                    "speed jumps at t=" + t);
        }
    }

    @Test
    void boostReachesCruisingSpeedAndCruiseHoldsIt() {
        MissilePath p = MissilePath.plan(HOMING, 0f, 0f, 90f, 0f, 1L);
        assertEquals(WarshipScale.cells(100f), p.speed(0f), 1e-3);
        assertEquals(WarshipScale.cells(400f), p.speed(p.totalSeconds() * 0.5f), 1e-3);
    }

    @Test
    void aGuidedWeaveNeverAsksMoreThanTheTurnRateAllows() {
        for (long seed = 0; seed < 40; seed++) {
            MissilePath p = MissilePath.plan(HOMING, 0f, 0f, 60f, 20f, seed);
            float limit = WarshipScale.cells(400f) * (float) Math.toRadians(240.0);
            float h = 0.02f;
            float worst = 0f;
            for (float t = h; t < p.totalSeconds() - h; t += h) {
                float a = (p.lateral(t + h) - 2f * p.lateral(t) + p.lateral(t - h)) / (h * h);
                worst = Math.max(worst, Math.abs(a));
            }
            assertTrue(worst <= limit, "seed " + seed + " lateral acceleration " + worst + " over " + limit);
        }
    }

    @Test
    void aStiffMissileWeavesLessThanANimbleOne() {
        float stiff = MissilePath.weaveBound(20f, 10f, 0.5f);
        float nimble = MissilePath.weaveBound(20f, 240f, 0.5f);
        assertTrue(nimble > stiff * 10f);
        assertEquals(0f, MissilePath.weaveBound(20f, 0f, 0.5f), 0f);
    }

    @Test
    void anUnguidedRocketBulgesToOneSideWhereAHomingMissileWeavesAcross() {
        int rocketCrossings = 0;
        int homingCrossings = 0;
        for (long seed = 0; seed < 30; seed++) {
            rocketCrossings += signChanges(MissilePath.plan(ROCKET, 0f, 0f, 60f, 0f, seed));
            homingCrossings += signChanges(MissilePath.plan(HOMING, 0f, 0f, 60f, 0f, seed));
        }
        assertTrue(rocketCrossings <= 30, "rockets cross the line " + rocketCrossings + " times in 30 rounds");
        assertTrue(homingCrossings >= 60, "homing missiles cross it only " + homingCrossings + " times in 30 rounds");
    }

    private static int signChanges(MissilePath p) {
        int changes = 0;
        float last = 0f;
        for (float t = 0.05f; t < p.totalSeconds() - 0.05f; t += 0.02f) {
            float l = p.lateral(t);
            if (Math.abs(l) < 1e-3f) continue;
            if (last != 0f && Math.signum(l) != Math.signum(last)) changes++;
            last = l;
        }
        return changes;
    }

    @Test
    void aRocketThatOutrunsItsMotorLandsShortByTheCoast() {
        MissileFlightSpec shortMotor = spec(Guidance.UNGUIDED, 50f, 400f, 400f, 0f, 0f, 1f, 0f);
        MissilePath p = MissilePath.plan(shortMotor, 0f, 0f, 80f, 0f, 5L);
        assertTrue(p.fallsShort());
        assertTrue(p.length() < p.aimedLength());
        float v0 = WarshipScale.cells(50f);
        float vMax = WarshipScale.cells(400f);
        float acc = WarshipScale.cells(400f);
        float boost = (vMax - v0) / acc;
        float sBurn = v0 * boost + 0.5f * acc * boost * boost + vMax * (1f - boost);
        float expected = sBurn + vMax * MissilePath.DEFAULT_COAST_TAU * (1f - (float) Math.exp(-MissilePath.COAST_TAUS));
        assertEquals(expected, p.landX(), 1e-2);
        assertTrue(p.x(p.totalSeconds()) < 80f);
        assertTrue(p.totalSeconds() > 1f);
    }

    @Test
    void aRoundIsHeldToItsArmingTime() {
        MissileFlightSpec arming = spec(Guidance.UNGUIDED, 100f, 400f, 2000f, 0f, 0f, 3f, 5f);
        MissilePath p = MissilePath.plan(arming, 0f, 0f, 10f, 0f, 9L);
        assertTrue(p.totalSeconds() >= 5f);
        assertEquals(p.landX(), p.x(p.totalSeconds()), 1e-4);
    }

    @Test
    void aPlainShellFliesALineAtOneSpeed() {
        MissilePath p = MissilePath.straight(0f, 0f, 30f, 40f, 10f);
        assertEquals(5f, p.totalSeconds(), 1e-4);
        assertEquals(15f, p.x(2.5f), 1e-4);
        assertEquals(20f, p.y(2.5f), 1e-4);
        assertEquals(10f, p.speed(1f), 1e-4);
        assertEquals(0f, p.lateral(2.5f), 0f);
    }

    @Test
    void theHeadingFollowsThePathNotTheChord() {
        MissilePath p = MissilePath.plan(HOMING, 0f, 0f, 60f, 0f, 11L);
        float chord = p.headingDegrees(0.01f);
        float spread = 0f;
        for (float t = 0.1f; t < p.totalSeconds(); t += 0.1f) {
            spread = Math.max(spread, Math.abs(WarshipFacing.wrap(p.headingDegrees(t) - chord)));
        }
        assertTrue(spread > 1f, "a weaving missile points off the chord at times");
    }

    @Test
    void theHashIsStatelessAndInTheUnitInterval() {
        for (int i = 0; i < 50; i++) {
            float u = MissilePath.unit(99L, i);
            assertTrue(u >= 0f && u < 1f);
            assertEquals(u, MissilePath.unit(99L, i), 0f);
        }
    }
}
