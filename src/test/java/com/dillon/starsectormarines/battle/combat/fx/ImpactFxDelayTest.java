package com.dillon.starsectormarines.battle.combat.fx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImpactFxDelayTest {

    @Test
    void exactDelayBoundaryDoesNotAgeParticleEarly() {
        Particle particle = new Particle();
        particle.delayRemaining = 0.25f;
        particle.lifetimeRemaining = 1f;
        particle.vx = 4f;

        assertTrue(ImpactFx.advanceParticle(particle, 0.25f));
        assertEquals(0f, particle.delayRemaining, 0f);
        assertEquals(1f, particle.lifetimeRemaining, 0f);
        assertEquals(0f, particle.x, 0f);

        assertTrue(ImpactFx.advanceParticle(particle, 0.25f));
        assertEquals(0.75f, particle.lifetimeRemaining, 0f);
        assertEquals(1f, particle.x, 0f);
    }
}
