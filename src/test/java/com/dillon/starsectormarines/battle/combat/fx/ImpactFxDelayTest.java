package com.dillon.starsectormarines.battle.combat.fx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImpactFxDelayTest {

    @Test void rollingExplosionTravelsAcrossGroundAndSlowsIndependentlyOfStepSize() {
        Particle whole = new Particle(), stepped = new Particle();
        for (Particle p : new Particle[]{whole, stepped}) {
            p.vx = 10f; p.vy = -4f; p.dragPerSecond = .8f;
            p.lifetimeRemaining = 2f;
        }
        ImpactFx.advanceParticle(whole, 1f);
        for (int i = 0; i < 30; i++) ImpactFx.advanceParticle(stepped, 1f / 30f);
        assertTrue(whole.x > 6f && whole.y < -2f);
        assertTrue(whole.vx < 5f && whole.vx > 0f);
        assertEquals(whole.x, stepped.x, .001f);
        assertEquals(whole.y, stepped.y, .001f);
    }

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
