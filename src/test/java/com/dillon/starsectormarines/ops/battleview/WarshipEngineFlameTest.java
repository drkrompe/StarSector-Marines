package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.warship.WarshipScale;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarshipEngineFlameTest {

    @Test
    void theHullsPresenceGrowsTheBodyNotTheBurn() {
        // An Onslaught's big nozzle: 100 px of authored flame, read at the hull's drawn size.
        float slot = WarshipScale.hullCells(100f);
        float atSharedDensity = WarshipScale.cells(100f);
        assertTrue(WarshipRenderSystem.engineFlameCells(slot, 1f) <= atSharedDensity * 1.3f + 1e-4f);
    }

    @Test
    void aWorkingNozzleFlaresLongerThanAHoveringOne() {
        float idle = WarshipRenderSystem.engineFlameCells(9f, 0.15f);
        float working = WarshipRenderSystem.engineFlameCells(9f, 1f);
        assertTrue(working > 2f * idle);
        assertEquals(working, WarshipRenderSystem.engineFlameCells(9f, 4f), 1e-6f);
    }

    @Test
    void theExhaustIsWarmedTowardHeatWhite() {
        float[] c = WarshipRenderSystem.engineColour("HIGH_TECH");
        for (float v : c) assertTrue(v >= 0f && v <= 1f);
    }
}
