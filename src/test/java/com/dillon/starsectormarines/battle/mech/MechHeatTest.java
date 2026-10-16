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
}
