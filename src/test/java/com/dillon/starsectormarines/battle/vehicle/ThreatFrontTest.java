package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.ArmorArcs;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which way a hull gives its front to the threats it knows of, and what turning under a turret does to the gun. */
class ThreatFrontTest {

    private static final float TANK_PLATE = 18f;
    private static final ArmorArcs.Profile TANK_ARCS = VehicleType.BREAKTHROUGH_TANK.armorArcs();
    /** A whole tank: the full plate on the nose, under half on each side and less behind, each times its arc. */
    private static final ThreatFront.Hold WHOLE = new ThreatFront.Hold(550f * 1.15f, 250f * .95f, 250f * .95f,
            165f * .6f, 750f);

    @Test
    void aCannonThatGetsThroughThePlateOutweighsANearerMachineGunThatMostlyDoesNot() {
        List<WeaponDef> cannon = List.of(WeaponRegistry.require("weapon.tank-twin-cannon"));
        List<WeaponDef> mg = List.of(WeaponRegistry.require("weapon.turret-heavy-mg"));
        float cannonHarm = ThreatFront.harm(cannon, 30f, TANK_PLATE);
        float mgHarm = ThreatFront.harm(mg, 12f, TANK_PLATE);
        assertTrue(cannonHarm > mgHarm * ThreatFront.SWITCH_MARGIN, cannonHarm + " vs " + mgHarm);
        // Unplated, the same machine gun is the worse of the two.
        assertTrue(ThreatFront.harm(mg, 12f, 0f) > ThreatFront.harm(cannon, 30f, 0f));
    }

    @Test
    void aGunOutOfItsOwnReachDoesNoHarm() {
        WeaponDef mg = WeaponRegistry.require("weapon.turret-heavy-mg");
        assertEquals(0f, ThreatFront.harm(List.of(mg), mg.range + 1f, TANK_PLATE));
        assertEquals(0f, ThreatFront.harm(List.of(mg), mg.minRange - 0.5f, TANK_PLATE));
        assertEquals(0L, ThreatFront.choose(List.of(
                ThreatFront.threat(1L, 0f, mg.range + 1f, List.of(mg), TANK_PLATE, TANK_ARCS)), WHOLE, 0L));
    }

    @Test
    void twoGunsOnOneSideGetTheFrontAndTheLoneOneTheTail() {
        List<WeaponDef> mg = List.of(WeaponRegistry.require("weapon.turret-heavy-mg"));
        var lone = ThreatFront.threat(1L, 0f, -15f, mg, TANK_PLATE, TANK_ARCS);
        var left = ThreatFront.threat(2L, -2f, 15f, mg, TANK_PLATE, TANK_ARCS);
        var right = ThreatFront.threat(3L, 2f, 15f, mg, TANK_PLATE, TANK_ARCS);
        long face = ThreatFront.choose(List.of(lone, left, right), WHOLE, 1L);
        assertTrue(face == 2L || face == 3L, "faced " + face);
    }

    @Test
    void aSideItsAttackersHaveStrippedIsTurnedAwayFromThem() {
        // Two equal guns, north and east. Facing north gives the east gun the right side; facing east gives the
        // north gun the left.
        var north = new ThreatFront.Threat(1L, 0f, 10f, 1f, 4f, 8f, 16f);
        var east = new ThreatFront.Threat(2L, 10f, 0f, 1f, 4f, 8f, 16f);
        var bareRight = new ThreatFront.Hold(WHOLE.front(), WHOLE.left(), 0f, WHOLE.rear(), 100f);
        var bareLeft = new ThreatFront.Hold(WHOLE.front(), 0f, WHOLE.right(), WHOLE.rear(), 100f);
        assertEquals(2L, ThreatFront.choose(List.of(north, east), bareRight, 1L));
        assertEquals(1L, ThreatFront.choose(List.of(north, east), bareLeft, 2L));
    }

    @Test
    void theFacedThreatKeepsTheFrontUntilAnotherIsClearlyBetter() {
        // Two threats north and south, each worse on the tail than on the nose.
        var north = new ThreatFront.Threat(1L, 0f, 10f, 1f, 2f, 10f, 16f);
        var south = new ThreatFront.Threat(2L, 0f, -10f, 1f, 2f, 12f, 16f);
        var even = new ThreatFront.Hold(100f, 100f, 100f, 100f, 100f);
        assertEquals(1L, ThreatFront.choose(List.of(north, south), even, 1L));
        assertEquals(2L, ThreatFront.choose(List.of(north, south), even, 0L));
        var worse = new ThreatFront.Threat(2L, 0f, -10f, 1f, 2f, 30f, 16f);
        assertEquals(2L, ThreatFront.choose(List.of(north, worse), even, 1L));
        // A faced threat that can no longer reach is given up whatever the margin.
        var gone = new ThreatFront.Threat(1L, 0f, 10f, 0f, 0f, 0f, 0f);
        assertEquals(2L, ThreatFront.choose(List.of(gone, new ThreatFront.Threat(2L, 0f, -10f, 0f, 1f, 1f, 16f)), even, 1L));
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
