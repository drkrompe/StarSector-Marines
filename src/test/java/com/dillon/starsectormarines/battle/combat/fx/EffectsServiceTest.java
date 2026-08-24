package com.dillon.starsectormarines.battle.combat.fx;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectsServiceTest {

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
