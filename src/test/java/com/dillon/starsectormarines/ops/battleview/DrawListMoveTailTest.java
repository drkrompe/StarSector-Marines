package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins {@link DrawList#moveTail}: the units pass sends its dead under the fog with it. */
class DrawListMoveTailTest {

    @Test
    void movesOnlyTheTailInOrderAfterWhatTheTargetHolds() {
        DrawList out = new DrawList();
        out.addSolidRect(RenderLayer.UNITS, 0, 0, 1, 1, 1f, 1f, 1f, 1f);
        out.addSolidRect(RenderLayer.CORPSES, 9, 0, 1, 1, 1f, 1f, 1f, 1f);
        int keep = out.count(RenderLayer.UNITS);
        // Past the initial capacity, so the target grows mid-move.
        for (int i = 1; i <= 300; i++) out.addSolidRect(RenderLayer.UNITS, i, 0, 1, 1, 1f, 1f, 1f, 1f);

        out.moveTail(RenderLayer.UNITS, keep, RenderLayer.CORPSES);

        assertEquals(1, out.count(RenderLayer.UNITS));
        assertEquals(0f, out.buffer(RenderLayer.UNITS)[0].centerX());
        assertEquals(301, out.count(RenderLayer.CORPSES));
        assertEquals(9f, out.buffer(RenderLayer.CORPSES)[0].centerX());
        for (int i = 1; i <= 300; i++) assertEquals(i, out.buffer(RenderLayer.CORPSES)[i].centerX());
    }

    @Test
    void corpsesAreDrawnWhereverUnitsAre() {
        assertTrue(RenderLayer.CORPSES.drawnFor(EnumSet.of(RenderLayer.UNITS)));
        assertFalse(RenderLayer.CORPSES.drawnFor(EnumSet.of(RenderLayer.GROUND)));
        assertTrue(RenderLayer.CORPSES.ordinal() < RenderLayer.FOG.ordinal(), "the dead lie under the fog");
    }
}
