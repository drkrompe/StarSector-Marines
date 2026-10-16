package com.dillon.starsectormarines.battle.combat.fx;

import org.junit.jupiter.api.Test;

import java.util.Random;
import com.dillon.starsectormarines.battle.world.model.PropDamage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectsServiceTest {

    @Test void pickupBurnIsShorterAndSmallerThanFreightBurnAndBothFollowTheHull() {
        var pickup = new EffectsService(new Random(17));
        var freight = new EffectsService(new Random(17));
        pickup.spawnTechnicalWreck(10, 10, 90);
        freight.spawnHaulerWreck(10, 10, 90);
        assertEquals(1, pickup.getHeavyImpactsThisFrame().size());
        assertEquals(1, freight.getHeavyImpactsThisFrame().size());
        assertTrue(pickup.getHeavyImpactsThisFrame().get(0)[2] < freight.getHeavyImpactsThisFrame().get(0)[2]);
        assertEquals(9.05f, pickup.getSmokingWrecks().get(0).x, .001);
        assertEquals(8.45f, freight.getSmokingWrecks().get(0).x, .001);
        assertEquals(10.75f, freight.getSmokingWrecks().get(1).x, .001);
        pickup.beginFrame(); freight.beginFrame();
        pickup.tickWrecks(8); freight.tickWrecks(8);
        assertTrue(pickup.getFireBurstsThisFrame().isEmpty());
        assertFalse(pickup.getSmokePuffsThisFrame().isEmpty());
        pickup.beginFrame(); freight.beginFrame();
        pickup.tickWrecks(28); freight.tickWrecks(28);
        assertTrue(pickup.getSmokingWrecks().isEmpty());
        assertFalse(freight.getSmokePuffsThisFrame().isEmpty());
        assertTrue(freight.getFireBurstsThisFrame().isEmpty());
        freight.beginFrame(); freight.tickWrecks(55);
        assertTrue(freight.getSmokingWrecks().isEmpty());
        assertTrue(freight.getSmokePuffsThisFrame().isEmpty());
    }

    @Test void apcEffectsFollowHullSizeAndExpireSoonerThanTankEffects() {
        var tank = new EffectsService(new Random(17));
        tank.spawnTankWreck(10, 10, 90);
        float previousRadius = 0;
        for (float hullSize : new float[]{2.4f, 3.2f}) {
            var effects = new EffectsService(new Random(17));
            effects.spawnApcWreck(10, 10, 90, hullSize);
            float radius = effects.getHeavyImpactsThisFrame().get(0)[2];
            assertTrue(radius > previousRadius);
            assertTrue(radius < tank.getHeavyImpactsThisFrame().get(0)[2]);
            previousRadius = radius;
            assertEquals(2, effects.getSmokingWrecks().size());
            assertEquals(10 + .55f * hullSize / 2.4f, effects.getSmokingWrecks().get(1).x, .001);
            assertEquals(10, effects.getSmokingWrecks().get(1).y, .001);
            assertTrue(effects.getSmokingWrecks().get(1).effectScale
                    < tank.getSmokingWrecks().get(1).effectScale);
            effects.beginFrame();
            effects.tickWrecks(11);
            assertTrue(effects.getFireBurstsThisFrame().isEmpty());
            assertFalse(effects.getSmokePuffsThisFrame().isEmpty());
            effects.beginFrame();
            effects.tickWrecks(45);
            assertTrue(effects.getSmokingWrecks().isEmpty());
            assertTrue(effects.getSmokePuffsThisFrame().isEmpty());
        }
    }

    @Test void tankFireCoolsIntoSmokeAndBothEmittersExpire() {
        var effects = new EffectsService(new Random(17));
        effects.spawnTankWreck(10, 10, 0);
        assertEquals(1, effects.getHeavyImpactsThisFrame().size());
        assertEquals(8.9f, effects.getSmokingWrecks().get(1).y, .001f);
        effects.beginFrame();
        effects.tickWrecks(20);
        assertTrue(effects.getFireBurstsThisFrame().isEmpty());
        assertFalse(effects.getSmokePuffsThisFrame().isEmpty());
        effects.beginFrame();
        effects.tickWrecks(71);
        assertTrue(effects.getSmokingWrecks().isEmpty());
        assertTrue(effects.getSmokePuffsThisFrame().isEmpty());
    }

    @Test void crashFanFollowsTheHullAndFillsItsCentreWithJitteredMovingBlasts() {
        var effects = new EffectsService(new Random(17));
        var replay = new EffectsService(new Random(17));
        effects.spawnCrashBlastFan(10f, 20f, 0f, 12f, 3f);
        replay.spawnCrashBlastFan(10f, 20f, 0f, 12f, 3f);
        var blasts = effects.getRollingBlastsThisFrame();
        assertEquals(blasts, replay.getRollingBlastsThisFrame());
        assertEquals(5, blasts.size());
        assertEquals(10f, blasts.get(0).x());
        assertEquals(20f, blasts.get(0).y());
        assertTrue(blasts.stream().allMatch(b -> b.vy() > 0f));
        assertTrue(blasts.stream().anyMatch(b -> b.vx() < -2f));
        assertTrue(blasts.stream().anyMatch(b -> b.vx() > 2f));
        assertTrue(blasts.stream().anyMatch(b -> Math.abs(b.vx()) < 2f));
        assertTrue(blasts.stream().map(RollingBlast::x).distinct().count() > 3);
        assertTrue(blasts.stream().map(RollingBlast::radius).distinct().count() > 3);
        effects.beginFrame();
        assertTrue(effects.getRollingBlastsThisFrame().isEmpty());
        effects.spawnCrashBlastFan(13f, 24f, 0f, 8f, 3f);
        assertEquals(13f, effects.getRollingBlastsThisFrame().get(0).x());
        assertEquals(24f, effects.getRollingBlastsThisFrame().get(0).y());
    }

    @Test void aStoppedWreckDoesNotKeepEmittingRollingBlasts() {
        var effects = new EffectsService(new Random(17));
        effects.spawnCrashBlastFan(10f, 20f, 0f, 0f, 3f);
        assertTrue(effects.getRollingBlastsThisFrame().isEmpty());
    }
    @Test void vehicleCookOffIsOnceQueuedAndLeavesLongerFiniteSmoke() {
        var effects = new EffectsService(new Random(17));
        effects.spawnVehicleWreck(4.5f, 6.5f, 4.8f, 1.65f);
        assertEquals(1, effects.getHeavyImpactsThisFrame().size());
        assertEquals(1, effects.getSmokingWrecks().size());
        effects.tickWrecks(.1f);
        assertFalse(effects.getFireBurstsThisFrame().isEmpty());
        assertTrue(effects.getSmokePuffsThisFrame().get(0)[2] > .85f);
        effects.beginFrame();
        effects.tickWrecks(31f);
        assertTrue(effects.getHeavyImpactsThisFrame().isEmpty());
        assertTrue(effects.getFireBurstsThisFrame().isEmpty());
        assertFalse(effects.getSmokePuffsThisFrame().isEmpty());
        effects.beginFrame();
        effects.tickWrecks(150f);
        assertTrue(effects.getSmokingWrecks().isEmpty());
        assertTrue(effects.getSmokePuffsThisFrame().isEmpty());
    }

    @Test void propCuesUseOrdinaryDrainsAndExpire() {
        for (PropDamage.Effect cue : PropDamage.Effect.values()) {
            EffectsService effects = new EffectsService(new Random(17L));
            effects.spawnPropDamage(4.5f, 6.5f, cue, 1f);
            assertEquals(cue == PropDamage.Effect.FIRE ? 1 : 0, effects.getFireBurstsThisFrame().size());
            assertEquals(cue == PropDamage.Effect.DUST ? 1 : 0, effects.getWallDustsThisFrame().size());
            effects.tickPlumes(.05f);
            assertEquals(cue == PropDamage.Effect.SMOKE || cue == PropDamage.Effect.FIRE ? 1 : 0,
                    effects.getSmokePuffsThisFrame().size());
            for (float[] puff : effects.getSmokePuffsThisFrame()) assertTrue(puff[2] <= .18f);
            for (float[] fire : effects.getFireBurstsThisFrame()) assertTrue(fire[2] <= .22f);
            for (float[] dust : effects.getWallDustsThisFrame()) assertEquals(.25f, dust[2]);
            effects.beginFrame(); effects.tickPlumes(6f);
            assertTrue(effects.getSmokePuffsThisFrame().isEmpty());
            assertTrue(effects.getFireBurstsThisFrame().isEmpty());
            assertTrue(effects.getWallDustsThisFrame().isEmpty());
        }
    }

    @Test void equipmentCueRadiusTracksFootprintAndSmokeNeverEmitsFire() {
        EffectsService small = new EffectsService(new Random(17L));
        EffectsService large = new EffectsService(new Random(17L));
        small.spawnPropDamage(0, 0, PropDamage.Effect.SMOKE, 1f);
        large.spawnPropDamage(0, 0, PropDamage.Effect.SMOKE, 2f);
        for (int tick = 0; tick < 120; tick++) {
            small.beginFrame(); large.beginFrame();
            small.tickPlumes(1f / 30f); large.tickPlumes(1f / 30f);
            assertTrue(small.getFireBurstsThisFrame().isEmpty());
            assertTrue(large.getFireBurstsThisFrame().isEmpty());
            assertEquals(small.getSmokePuffsThisFrame().size(), large.getSmokePuffsThisFrame().size());
            if (!small.getSmokePuffsThisFrame().isEmpty())
                assertEquals(small.getSmokePuffsThisFrame().get(0)[2] * 2,
                        large.getSmokePuffsThisFrame().get(0)[2], .00001f);
        }
        assertTrue(small.getSmokePuffsThisFrame().isEmpty());
    }

    @Test
    void ordinaryImpactPlumeRemainsSmokeOnly() {
        EffectsService effects = new EffectsService(new Random(17L));
        effects.spawnSmokePlume(4f, 6f);

        effects.tickPlumes(0.05f);

        assertEquals(1, effects.getSmokePuffsThisFrame().size());
        assertTrue(effects.getFireBurstsThisFrame().isEmpty());
    }

    @Test
    void cannonImpactPlumeBurnsBrieflyThenKeepsSmoking() {
        EffectsService effects = new EffectsService(new Random(17L));
        effects.spawnBurningSmokePlume(4f, 6f);

        effects.tickPlumes(0.05f);

        assertEquals(1, effects.getSmokePuffsThisFrame().size());
        assertEquals(1, effects.getFireBurstsThisFrame().size());

        effects.beginFrame();
        effects.tickPlumes(1.30f);

        assertFalse(effects.getSmokePuffsThisFrame().isEmpty(),
                "the plume should retain its smoke tail after the flame phase");
        assertTrue(effects.getFireBurstsThisFrame().isEmpty(),
                "cannon plume fire should not persist through the full smoke lifetime");
    }
}
