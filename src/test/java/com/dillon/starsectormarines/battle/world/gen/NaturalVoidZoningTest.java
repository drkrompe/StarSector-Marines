package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.model.NaturalVoid;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NaturalVoidZoningTest {

    /** A straight cut {@code len} cells along x, rows 10 to 15 of a 40-row map. */
    private static boolean[] strip(int len, boolean alongX) {
        int w = alongX ? len : 40, h = alongX ? 40 : len;
        boolean[] cells = new boolean[w * h];
        for (int a = 0; a < len; a++) {
            for (int c = 10; c < 16; c++) cells[alongX ? c * w + a : a * w + c] = true;
        }
        return cells;
    }

    @Test
    void marksRunAlongTheCutsMiddleAndSpreadOverTheBand() {
        List<NaturalVoid.DepthMark> marks = NaturalVoidZoning.marks(strip(181, true), 181, 40, 28f, 55f, 7);
        assertEquals(7, marks.size(), "one every 30 cells over 180, both ends included");
        float lo = Float.MAX_VALUE, hi = 0f;
        for (NaturalVoid.DepthMark m : marks) {
            assertEquals(13f, m.y(), 1e-4f, "at the cut's middle");
            assertTrue(m.floorMeters() >= 28f && m.floorMeters() <= 55f);
            lo = Math.min(lo, m.floorMeters());
            hi = Math.max(hi, m.floorMeters());
        }
        assertTrue(lo < 28f + 27f / 7f && hi > 55f - 27f / 7f, "one mark in the band's shallowest and deepest slice");
        assertEquals(0.5f, marks.get(0).x(), 1e-4f);
        assertEquals(180.5f, marks.get(6).x(), 1e-4f);
    }

    @Test
    void aCutDownTheMapIsMarkedDownIt() {
        List<NaturalVoid.DepthMark> marks = NaturalVoidZoning.marks(strip(61, false), 40, 61, 28f, 55f, 3);
        assertEquals(3, marks.size());
        for (NaturalVoid.DepthMark m : marks) assertEquals(13f, m.x(), 1e-4f);
    }

    @Test
    void onlyABandedRowVaries() {
        boolean[] cells = strip(61, true);
        assertTrue(NaturalVoidZoning.DRY_GORGE.varies());
        assertFalse(NaturalVoidZoning.GORGE.varies());
        assertTrue(NaturalVoidZoning.GORGE.look(1, cells, 61, 40).marks().isEmpty());
        assertFalse(NaturalVoidZoning.DRY_GORGE.look(1, cells, 61, 40).marks().isEmpty());
        assertTrue(NaturalVoidZoning.marks(new boolean[40 * 40], 40, 40, 28f, 55f, 1).isEmpty());
    }
}
