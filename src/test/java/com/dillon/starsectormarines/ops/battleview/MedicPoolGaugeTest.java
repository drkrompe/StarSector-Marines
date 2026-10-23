package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedicPoolGaugeTest {

    @Test
    void fillIsTheShareOfTheCapClamped() {
        assertEquals(0.5f, MedicPoolGauge.fill(15f, 30f), 1e-6f);
        assertEquals(1f, MedicPoolGauge.fill(31f, 30f), 1e-6f);
        assertEquals(0f, MedicPoolGauge.fill(-1f, 30f), 1e-6f);
        assertEquals(0f, MedicPoolGauge.fill(5f, 0f), 1e-6f);
    }

    @Test
    void readyAtTheStartGateAndNotBelowIt() {
        assertTrue(MedicPoolGauge.ready(7.5f, 7.5f));
        assertTrue(MedicPoolGauge.ready(7.5f - 5e-5f, 7.5f));
        assertFalse(MedicPoolGauge.ready(7.4f, 7.5f));
    }
}
