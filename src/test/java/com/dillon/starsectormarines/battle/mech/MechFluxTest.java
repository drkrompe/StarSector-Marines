package com.dillon.starsectormarines.battle.mech;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MechFluxTest {
    @Test void autonomousFireReservesHeadroomAndResumesOnlyAfterDissipation() {
        MechFlux flux = new MechFlux();
        flux.commit(70f);
        assertFalse(flux.permits(28f, true), "AI leaves space below the venting interlock");
        flux.commit(10f);
        assertFalse(flux.permits(18f, true));
        assertTrue(flux.permits(18f, false), "player can spend the remaining burst reserve");
        flux.advance(39f / MechFlux.DISSIPATION_PER_SECOND);
        assertFalse(flux.permits(18f, true), "hysteresis prevents single-tick AI trigger chatter");
        flux.advance(1f / MechFlux.DISSIPATION_PER_SECOND);
        assertTrue(flux.permits(18f, true));
        assertEquals(40f, flux.value(), .001f);
    }

    @Test void fluxInterlockRecoversWithoutDamageOrNegativeFlux() {
        MechFlux flux = new MechFlux();
        flux.commit(98f);
        assertTrue(flux.venting());
        assertFalse(flux.permits(1f, false));
        assertThrows(IllegalStateException.class, () -> flux.commit(18f));
        flux.advance(42f / MechFlux.DISSIPATION_PER_SECOND);
        assertTrue(flux.venting());
        flux.advance(1.5f / MechFlux.DISSIPATION_PER_SECOND);
        assertFalse(flux.venting());
        assertTrue(flux.permits(28f, false));
        flux.advance(100f);
        assertEquals(0f, flux.value());
    }

    @Test void dissipationIsSimulationTimeAndInvalidStepsCannotPoisonResources() {
        MechFlux whole = new MechFlux(), split = new MechFlux();
        whole.commit(60f); split.commit(60f);
        whole.advance(0f); assertEquals(60f, whole.value());
        whole.advance(1f);
        for (int i = 0; i < 30; i++) split.advance(1f / 30f);
        assertEquals(whole.value(), split.value(), .001f);
        assertFalse(whole.permits(Float.NaN, false));
        assertThrows(IllegalArgumentException.class, () -> whole.advance(Float.NaN));
    }

    @Test void thresholdsScaleWithCapacityAsFractionsOfStock() {
        MechFlux flux = new MechFlux(140f, 8f);
        assertEquals(140f, flux.capacity());
        assertEquals(8f, flux.dissipationPerSecond());
        flux.commit(100f);
        assertFalse(flux.aiDissipating(), "100 is below the 105 latch");
        assertFalse(flux.permits(26.1f, true), "AI refuses above 126");
        assertTrue(flux.permits(26f, true));
        flux.commit(6f);
        assertTrue(flux.aiDissipating(), "latch at 0.75 of capacity (105)");
        assertFalse(flux.permits(1f, true), "latched");
        assertTrue(flux.permits(26f, false));
        flux.commit(27f);
        assertTrue(flux.venting(), "venting at 133");
        assertFalse(flux.permits(0f, false));
        flux.advance((133f - 77f) / 8f);
        assertFalse(flux.venting(), "releases at 0.55 of capacity (77)");
        flux.advance((77f - 56f) / 8f);
        assertFalse(flux.aiDissipating(), "releases at 0.40 of capacity (56)");
        assertFalse(flux.permits(141f, false), "ceiling is capacity");
    }

    @Test void stockCapacityKeepsStockThresholds() {
        MechFlux flux = new MechFlux();
        assertEquals(MechFlux.CAPACITY, flux.capacity());
        assertEquals(MechFlux.DISSIPATION_PER_SECOND, flux.dissipationPerSecond());
        flux.commit(10f);
        assertFalse(flux.permits(0f, false) && flux.permits(95f, false));
        assertThrows(IllegalStateException.class, () -> flux.configure(120f, 6f));
    }
}
