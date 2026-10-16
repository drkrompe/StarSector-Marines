package com.dillon.starsectormarines.battle.mech;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MechHeatTest {
    @Test void autonomousFireReservesHeadroomAndResumesOnlyAfterCooling() {
        MechHeat heat = new MechHeat();
        heat.commit(70f);
        assertFalse(heat.permits(28f, true), "AI leaves space below the venting interlock");
        heat.commit(10f);
        assertFalse(heat.permits(18f, true));
        assertTrue(heat.permits(18f, false), "player can spend the remaining burst reserve");
        heat.advance(39f / MechHeat.COOLING_PER_SECOND);
        assertFalse(heat.permits(18f, true), "hysteresis prevents single-tick AI trigger chatter");
        heat.advance(1f / MechHeat.COOLING_PER_SECOND);
        assertTrue(heat.permits(18f, true));
        assertEquals(40f, heat.value(), .001f);
    }

    @Test void thermalInterlockRecoversWithoutDamageOrNegativeHeat() {
        MechHeat heat = new MechHeat();
        heat.commit(98f);
        assertTrue(heat.venting());
        assertFalse(heat.permits(1f, false));
        assertThrows(IllegalStateException.class, () -> heat.commit(18f));
        heat.advance(42f / MechHeat.COOLING_PER_SECOND);
        assertTrue(heat.venting());
        heat.advance(1.5f / MechHeat.COOLING_PER_SECOND);
        assertFalse(heat.venting());
        assertTrue(heat.permits(28f, false));
        heat.advance(100f);
        assertEquals(0f, heat.value());
    }

    @Test void coolingIsSimulationTimeAndInvalidStepsCannotPoisonResources() {
        MechHeat whole = new MechHeat(), split = new MechHeat();
        whole.commit(60f); split.commit(60f);
        whole.advance(0f); assertEquals(60f, whole.value());
        whole.advance(1f);
        for (int i = 0; i < 30; i++) split.advance(1f / 30f);
        assertEquals(whole.value(), split.value(), .001f);
        assertFalse(whole.permits(Float.NaN, false));
        assertThrows(IllegalArgumentException.class, () -> whole.advance(Float.NaN));
    }

    @Test void thresholdsScaleWithCapacityAsFractionsOfStock() {
        MechHeat heat = new MechHeat(140f, 8f);
        assertEquals(140f, heat.capacity());
        assertEquals(8f, heat.coolingPerSecond());
        heat.commit(100f);
        assertFalse(heat.aiCooling(), "100 is below the 105 latch");
        assertFalse(heat.permits(26.1f, true), "AI refuses above 126");
        assertTrue(heat.permits(26f, true));
        heat.commit(6f);
        assertTrue(heat.aiCooling(), "latch at 0.75 of capacity (105)");
        assertFalse(heat.permits(1f, true), "latched");
        assertTrue(heat.permits(26f, false));
        heat.commit(27f);
        assertTrue(heat.venting(), "venting at 133");
        assertFalse(heat.permits(0f, false));
        heat.advance((133f - 77f) / 8f);
        assertFalse(heat.venting(), "releases at 0.55 of capacity (77)");
        heat.advance((77f - 56f) / 8f);
        assertFalse(heat.aiCooling(), "releases at 0.40 of capacity (56)");
        assertFalse(heat.permits(141f, false), "ceiling is capacity");
    }

    @Test void stockCapacityKeepsStockThresholds() {
        MechHeat heat = new MechHeat();
        assertEquals(MechHeat.CAPACITY, heat.capacity());
        assertEquals(MechHeat.COOLING_PER_SECOND, heat.coolingPerSecond());
        heat.commit(10f);
        assertFalse(heat.permits(0f, false) && heat.permits(95f, false));
        assertThrows(IllegalStateException.class, () -> heat.configure(120f, 6f));
    }
}
