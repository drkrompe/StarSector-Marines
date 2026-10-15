package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.weapon.WeaponDef.Guidance;
import com.dillon.starsectormarines.battle.weapon.AirMissileSpec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuidedMissileTest {
    @Test void guidanceTurnsAfterTheTargetMovesAndContactUsesTheTravelledSegment() {
        var missile = new GuidedMissile(0, 0, -90f, 25f, new Guidance(140, 20, 8));
        for (int i = 0; i < 30; i++) missile.advance(1f / 30, true, 50, 0, 3);
        float initial = missile.heading;
        missile.advance(1f / 30, true, 40, 20, 3);
        assertTrue(missile.heading > initial);
        assertTrue(missile.heading - initial <= Math.toRadians(140f / 30) + .0001);
        assertTrue(missile.contacts((missile.previousX + missile.x) * .5f,
                (missile.previousY + missile.y) * .5f, (missile.previousZ + missile.z) * .5f, .01f));
        assertFalse(missile.contacts(100, 100, 3, 1));
    }

    @Test void targetLossDoesNotTeleportOrKeepAMissileAliveForever() {
        var missile = new GuidedMissile(0, 0, -90f, 25f, new Guidance(140, 20, 2));
        float heading = missile.heading;
        for (int i = 0; i < 90; i++) missile.advance(1f / 30, false, -100, -100, -100);
        assertEquals(heading, missile.heading);
        assertEquals(0f, missile.remaining);
        assertTrue(missile.x > 0);
    }
    @Test void predictiveLeadSolvesTransverseInterceptAndRejectsUncatchableTargets() {
        float time = GuidedMissile.leadSeconds(30, 0, 0, 0, 9, 15, 5);
        assertEquals(2.5f, time, .0001f);
        assertEquals(0f, GuidedMissile.leadSeconds(30, 0, 0, 20, 0, 15, 5));
        assertEquals(1f, GuidedMissile.leadSeconds(30, 0, 0, 0, 9, 15, 1));
    }

    @Test void proximityChargeCoastsWithoutHomingOrPoweredExhaustAndStopsAtFuelLimit() {
        var spec = new AirMissileSpec(false, false, 20, 5, 2, 2, 0, 4, 20, 2, 7.5f, 3.75f, true);
        var missile = new GuidedMissile(0, 0, -90, 50, 0, 0, spec);
        missile.advance(1, true, -100, -100, 30);
        assertEquals(0f, missile.heading);
        assertEquals(18f, missile.speed);
        assertEquals(19f, missile.x);
        assertFalse(missile.powered());
        missile.advance(10, false, 0, 0, 0);
        assertEquals(0f, missile.remaining);
        float terminal = missile.x;
        missile.advance(1, false, 0, 0, 0);
        assertEquals(terminal, missile.x);
        assertEquals(1f, spec.damageFraction(3.75f));
        assertEquals(.5f, spec.damageFraction(5.625f));
        assertEquals(0f, spec.damageFraction(7.5f));
        assertEquals(-10f, spec.launchAngleOffset(0));
        assertEquals(10f, spec.launchAngleOffset(1));
        assertEquals(0f, spec.damageFraction(AirMissileSpec.groundSurfaceDistance(0, 10, .5f, .5f)));
        assertEquals(1f, spec.damageFraction(AirMissileSpec.groundSurfaceDistance(0, 2, .5f, .5f)));
    }

    @Test void sweptContactFindsHullEntryAndMotorStopsOnExpiry() {
        var spec = new AirMissileSpec(true, true, 5, 15, 125, 40, 240, 5, 0, 0, 0, 0, false);
        var missile = new GuidedMissile(0, 0, -90, 50, 0, 0, spec);
        assertTrue(missile.powered());
        missile.advance(1, false, 0, 0, 0);
        assertEquals(.4f, missile.contactFraction(5, 0, 0, 1), .0001f);
        assertEquals(Float.POSITIVE_INFINITY, missile.contactFraction(5, 2, 0, 1));
        missile.advance(10, false, 0, 0, 0);
        assertFalse(missile.powered());
    }

    @Test void contactSweepsBothBodiesAndRejectsPathsCrossedAtDifferentTimes() {
        var missile = new GuidedMissile(0, 0, -90, 15, new Guidance(240, 125, 5));
        missile.previousX = 0; missile.previousY = 0; missile.previousZ = 0;
        missile.x = 10; missile.y = 0; missile.z = 0;
        assertEquals(Float.POSITIVE_INFINITY, missile.contactFraction(5, 5, 0, .1f));
        assertTrue(Float.isFinite(missile.contactFractionMoving(5, -5, 0, 5, 5, 0, .1f)),
                "aircraft crosses the moving missile between observations");
        assertEquals(Float.POSITIVE_INFINITY, missile.contactFractionMoving(5, -10, 0, 5, 0, 0, .1f),
                "paths cross at different times, so there is no contact");
    }

    @Test void movingTargetCanBeContactedBeforeItsFinalStepLeavesTheMap() {
        var missile = new GuidedMissile(98, 0, -90, 15, new Guidance(240, 125, 5));
        missile.previousX = 98; missile.previousY = 0; missile.previousZ = 0;
        missile.x = 100; missile.y = 0; missile.z = 0;
        float fraction = missile.contactFractionMoving(97, -2, 0, 101, 2, 0, .1f);
        assertTrue(Float.isFinite(fraction));
        assertTrue(98 + 2 * fraction < 100, "contact precedes the boundary despite the target ending outside");
    }

}
