package com.dillon.starsectormarines.battle.combat.fx;

import org.junit.jupiter.api.Test;

import java.util.Random;
import com.dillon.starsectormarines.battle.world.model.PropDamage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectsServiceTest {

    @Test void crashFireFansOutAlongTravelOverTimeAndFinishes() {
        var effects = new EffectsService(new Random(17));
        effects.spawnCrashBlastFan(10f, 20f, 0f, 12f, 3f);
        effects.tickPlumes(.05f);
        assertTrue(effects.getHeavyImpactsThisFrame().isEmpty());
        effects.tickPlumes(.05f);
        var first = effects.getHeavyImpactsThisFrame();
        assertEquals(2, first.size());
        assertEquals(20f, first.get(0)[0] + first.get(1)[0], .001f);
        assertEquals(first.get(0)[1], first.get(1)[1], .001f);
        float firstReach = first.get(0)[1] - 20f;
        float firstWidth = Math.abs(first.get(0)[0] - 10f);
        assertTrue(firstReach > 0f);
        int bursts = first.size();
        float finalReach = firstReach, finalWidth = firstWidth;
        for (int step = 0; step < 12; step++) {
            effects.beginFrame();
            effects.tickPlumes(.1f);
            for (float[] burst : effects.getHeavyImpactsThisFrame()) {
                bursts++;
                assertTrue(burst[1] - 20f > firstReach);
                finalReach = Math.max(finalReach, burst[1] - 20f);
                finalWidth = Math.max(finalWidth, Math.abs(burst[0] - 10f));
            }
        }
        assertEquals(14, bursts);
        assertTrue(finalReach > firstReach * 6f);
        assertTrue(finalWidth > firstWidth * 6f);
        effects.beginFrame();
        effects.tickPlumes(10f);
        assertTrue(effects.getHeavyImpactsThisFrame().isEmpty());
        assertTrue(effects.getFireBurstsThisFrame().isEmpty());
    }

    @Test void aVerticalCrashDoesNotInventADirectionalFuelFan() {
        var effects = new EffectsService(new Random(17));
        effects.spawnCrashBlastFan(10f, 20f, 0f, 0f, 3f);
        effects.tickPlumes(1f);
        assertTrue(effects.getHeavyImpactsThisFrame().isEmpty());
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
