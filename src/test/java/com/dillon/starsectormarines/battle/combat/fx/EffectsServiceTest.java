package com.dillon.starsectormarines.battle.combat.fx;

import org.junit.jupiter.api.Test;

import java.util.Random;
import com.dillon.starsectormarines.battle.world.model.PropDamage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectsServiceTest {

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
