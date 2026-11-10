package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Crossing;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.CrossingKind;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Drop;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Pt;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Rise;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which cells a drop, its crossing and a rise claim. */
class SketchRasterTest {

    @Test
    void aDropClaimsItsWidthAndItsCrossingIsLeftOut() {
        Drop drop = new Drop("d", List.of(new Pt(0, 5), new Pt(20, 5)), 4, List.of(20f, 20f), 2,
                List.of(new Crossing(.5f, 4, CrossingKind.BRIDGE)));
        MapSketch sketch = new MapSketch("t", List.of(drop), List.of(), List.of(), List.of(), List.of());
        SketchRaster.Cut cut = SketchRaster.cut(20, 10, sketch);
        int across = 0;
        for (int y = 0; y < 10; y++) if (cut.drop()[y * 20 + 2]) across++;
        assertEquals(4, across);
        assertFalse(cut.drop()[5 * 20 + 10]);
        assertTrue(cut.bridged(5 * 20 + 10));
        assertFalse(cut.bridged(5 * 20 + 2));
    }

    @Test
    void aDropOverARiseStaysADrop() {
        Drop drop = new Drop("d", List.of(new Pt(0, 5), new Pt(20, 5)), 2, List.of(20f, 20f), 2, List.of());
        Rise rise = new Rise("r", List.of(new Pt(10, 0), new Pt(10, 10)), 4, List.of(10f, 10f), 3);
        SketchRaster.Cut cut = SketchRaster.cut(20, 10, new MapSketch("t", List.of(drop), List.of(rise), List.of(), List.of(), List.of()));
        assertTrue(cut.drop()[5 * 20 + 10]);
        assertFalse(cut.rise()[5 * 20 + 10]);
        assertTrue(cut.rise()[1 * 20 + 10]);
    }

    @Test
    void theNearestPointAlongAPathIsItsShareOfTheLength() {
        float[] n = SketchRaster.nearest(List.of(new Pt(0, 0), new Pt(10, 0), new Pt(10, 10)), 10.5f, 5f);
        assertEquals(.75f, n[0], 1e-4f);
        assertEquals(.5f, n[1], 1e-4f);
    }
}
