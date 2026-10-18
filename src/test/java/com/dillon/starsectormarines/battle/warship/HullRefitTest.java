package com.dillon.starsectormarines.battle.warship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A refit moves the pool's capacity and dissipation and the shield's arc, upkeep and efficiency, and nothing else. */
class HullRefitTest {

    @Test
    void scalesCapacityAndDissipationFittingsIncluded() {
        WarshipFlux flux = new WarshipFlux(2000f, 100f);
        flux.fittings(400f, 50f);
        flux.applyFittings(true);
        flux.refit(1.25f, 2f);
        assertEquals(3000f, flux.capacity(), 1e-3f);
        assertEquals(300f, flux.dissipation(), 1e-3f);
        flux.refit(1f, 1f);
        assertEquals(2400f, flux.capacity(), 1e-3f);
    }

    @Test
    void absorbedDamageCostsLessFluxAndTheArcWidens() {
        WarshipShield shield = new WarshipShield(WarshipShield.Type.FRONT, 150f, 5f, 0f, 0f, 0.4f, 0.7f);
        WarshipFlux flux = new WarshipFlux(10000f, 100f);
        flux.enabled(true);
        assertFalse(shield.covers(0f, 80f), "a 150-degree front shield does not cover 80 degrees off the bow");
        shield.refit(20f, 0.75f, 0.8f);
        assertEquals(170f, shield.arcDegrees(), 1e-3f);
        assertTrue(shield.covers(0f, 80f));
        assertEquals(0.3f, shield.upkeep(), 1e-4f);
        assertEquals(0.56f, shield.efficiency(), 1e-4f);
        shield.absorb(100f, flux);
        assertEquals(56f, flux.flux(), 1e-3f);
        assertEquals(100f, shield.absorbedDamage(), 1e-3f, "the evidence counts the damage, not the flux");
    }

    @Test
    void anArcNeverPassesAllRoundAndNoShieldGainsNone() {
        WarshipShield front = new WarshipShield(WarshipShield.Type.FRONT, 300f, 5f, 0f, 0f, 0.4f, 1f);
        front.refit(120f, 1f, 1f);
        assertEquals(360f, front.arcDegrees(), 1e-3f);
        WarshipShield none = new WarshipShield(WarshipShield.Type.NONE, 0f, 0f, 0f, 0f, 0f, 1f);
        none.refit(60f, 1f, 1f);
        assertEquals(0f, none.arcDegrees(), 1e-3f);
    }

    @Test
    void refusesFiguresThatAreNotARefit() {
        assertThrows(IllegalArgumentException.class, () -> new HullRefit("x", 0f, 1f, 0f, 1f, 1f));
        assertThrows(IllegalArgumentException.class, () -> new HullRefit("x", 1f, 1f, -5f, 1f, 1f));
        assertThrows(IllegalArgumentException.class, () -> new HullRefit(" ", 1f, 1f, 0f, 1f, 1f));
    }
}
