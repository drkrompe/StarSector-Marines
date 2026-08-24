package com.dillon.starsectormarines.ui.retained;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CanvasSpriteRegionTest {

    @Test
    void resolvesRowMajorFramesInNormalizedImageCoordinates() {
        CanvasSpriteRegion frame = CanvasSpriteRegion.frame(4, 4, 9);

        assertEquals(0.25f, frame.x());
        assertEquals(0.5f, frame.y());
        assertEquals(0.25f, frame.width());
        assertEquals(0.25f, frame.height());
    }

    @Test
    void rejectsEmptyEscapingAndOutOfRangeRegions() {
        assertThrows(IllegalArgumentException.class,
                () -> new CanvasSpriteRegion(0f, 0f, 0f, 1f));
        assertThrows(IllegalArgumentException.class,
                () -> new CanvasSpriteRegion(0.75f, 0f, 0.5f, 1f));
        assertThrows(IllegalArgumentException.class,
                () -> CanvasSpriteRegion.frame(4, 4, 16));
    }
}
