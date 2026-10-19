package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which known threat a hull gives its front to, and what turning under a turret does to the gun. */
class ThreatFrontTest {

    private static final float TANK_PLATE = 18f;

    @Test
    void aCannonThatGetsThroughThePlateOutweighsANearerMachineGunThatMostlyDoesNot() {
        List<WeaponDef> cannon = List.of(WeaponRegistry.require("weapon.tank-twin-cannon"));
        List<WeaponDef> mg = List.of(WeaponRegistry.require("weapon.turret-heavy-mg"));
        float cannonHarm = ThreatFront.harm(cannon, 30f, TANK_PLATE);
        float mgHarm = ThreatFront.harm(mg, 12f, TANK_PLATE);
        assertTrue(cannonHarm > mgHarm * ThreatFront.SWITCH_MARGIN, cannonHarm + " vs " + mgHarm);
        // Unplated, the same machine gun is the worse of the two.
        assertTrue(ThreatFront.harm(mg, 12f, 0f) > ThreatFront.harm(cannon, 30f, 0f));
        long face = ThreatFront.choose(List.of(new ThreatFront.Threat(1L, mgHarm),
                new ThreatFront.Threat(2L, cannonHarm)), 1L);
        assertEquals(2L, face);
    }

    @Test
    void aGunOutOfItsOwnReachDoesNoHarm() {
        WeaponDef mg = WeaponRegistry.require("weapon.turret-heavy-mg");
        assertEquals(0f, ThreatFront.harm(List.of(mg), mg.range + 1f, TANK_PLATE));
        assertEquals(0f, ThreatFront.harm(List.of(mg), mg.minRange - 0.5f, TANK_PLATE));
        assertEquals(0L, ThreatFront.choose(List.of(new ThreatFront.Threat(1L, 0f)), 0L));
    }

    @Test
    void theFacedThreatKeepsTheFrontUntilAnotherIsClearlyWorse() {
        List<ThreatFront.Threat> close = List.of(new ThreatFront.Threat(1L, 10f), new ThreatFront.Threat(2L, 12f));
        assertEquals(1L, ThreatFront.choose(close, 1L));
        assertEquals(2L, ThreatFront.choose(close, 0L));
        List<ThreatFront.Threat> clear = List.of(new ThreatFront.Threat(1L, 10f), new ThreatFront.Threat(2L, 13f));
        assertEquals(2L, ThreatFront.choose(clear, 1L));
        // A faced threat that can no longer reach is given up whatever the margin.
        assertEquals(2L, ThreatFront.choose(List.of(new ThreatFront.Threat(1L, 0f), new ThreatFront.Threat(2L, 1f)), 1L));
    }

    @Test
    void aHullTurningAsFastAsItsTurretTraversesShootsLikeAMovingShooter() {
        assertEquals(1f, HullPivot.accuracyMultiplier(0f, 40f), 1e-6f);
        assertEquals(0.75f, HullPivot.accuracyMultiplier(-20f, 40f), 1e-6f);
        assertEquals(0.5f, HullPivot.accuracyMultiplier(90f, 40f), 1e-6f);
    }

    @Test
    void aBicycleHullsYawRateIsTheTurnItIntegrates() {
        BicycleBody body = (BicycleBody) VehicleType.BREAKTHROUGH_TANK.createBody();
        body.teleport(0f, 0f, 0f);
        float dt = 1f / 30f;
        for (int i = 0; i < 90; i++) body.tickManual(-1f, 1f, dt);
        float before = body.facingDegrees;
        float rate = body.yawRateDegPerSec();
        body.tickManual(-1f, 1f, dt);
        assertEquals(rate * dt, body.facingDegrees - before, Math.abs(rate * dt) * 0.05f + 1e-4f);
        assertTrue(rate < 0f, "backing with left lock swings the nose right: " + rate);
    }
}
