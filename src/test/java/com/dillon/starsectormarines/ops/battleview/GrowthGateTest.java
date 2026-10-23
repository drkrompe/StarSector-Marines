package com.dillon.starsectormarines.ops.battleview;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GrowthGateTest {
    @Test
    void firesOnFirstAskAndOnGrowthOnly() {
        GrowthGate gate = new GrowthGate();
        assertTrue(gate.changed(0), "first ask registers what attach already loaded");
        assertFalse(gate.changed(0));
        assertTrue(gate.changed(1), "a sheet loaded since the last ask");
        assertFalse(gate.changed(1));
    }
}
