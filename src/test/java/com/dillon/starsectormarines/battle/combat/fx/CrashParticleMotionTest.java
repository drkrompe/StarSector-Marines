package com.dillon.starsectormarines.battle.combat.fx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CrashParticleMotionTest {
    @Test
    void neutralShapeDefaultsPreserveExistingParticles() {
        Particle particle = new Particle();
        particle.angleDeg = 17;
        particle.lifetimeRemaining = 2;
        assertEquals(1, particle.aspectRatio);
        ImpactFx.advanceParticle(particle, .25f);
        assertEquals(17, particle.angleDeg);
    }

    @Test
    void tumblingShardSpinAndInheritedTravelAreStepIndependent() {
        Particle whole = new Particle();
        Particle stepped = new Particle();
        for (Particle p : new Particle[]{whole, stepped}) {
            p.vx = 10;
            p.vy = 2;
            p.dragPerSecond = 3;
            p.angularVelocityDegPerSec = 120;
            p.aspectRatio = .7f;
            p.lifetimeRemaining = 2;
        }
        ImpactFx.advanceParticle(whole, .5f);
        for (int i = 0; i < 15; i++) ImpactFx.advanceParticle(stepped, 1f / 30);
        assertEquals(60, whole.angleDeg, .001f);
        assertEquals(whole.angleDeg, stepped.angleDeg, .001f);
        assertEquals(whole.x, stepped.x, .001f);
        assertEquals(.7f, whole.aspectRatio);
        assertTrue(whole.vx > 0 && whole.vx < 3);
    }
}
