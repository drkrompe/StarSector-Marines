package com.dillon.starsectormarines.ui.retained.style;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CornerCutTest {

    @Test
    void oneLengthCutsEveryCornerAndFourGoClockwiseFromTopLeft() {
        assertEquals(new CornerCut(3f, 3f, 3f, 3f), CornerCut.parse("3px"));
        assertEquals(new CornerCut(0f, 7f, 0f, 7f), CornerCut.parse("0 7px 0px 7px"));
        assertTrue(CornerCut.parse("0").none());
    }

    @Test
    void refusesAnythingButOneOrFourPixelLengths() {
        assertThrows(UiStyleException.class, () -> CornerCut.parse("1px 2px"));
        assertThrows(UiStyleException.class, () -> CornerCut.parse("-1px"));
        assertThrows(UiStyleException.class, () -> CornerCut.parse("2em"));
    }

    @Test
    void anUncutCornerIsOnePointAndACutCornerTwo() {
        assertArrayEquals(new float[] {0f, 0f, 93f, 0f, 100f, 7f, 100f, 20f, 7f, 20f, 0f, 13f},
                new CornerCut(0f, 7f, 0f, 7f).outline(100f, 20f));
    }

    @Test
    void aCutIsHeldToHalfTheShorterSide() {
        assertArrayEquals(new float[] {0f, 5f, 5f, 0f, 5f, 0f, 10f, 5f, 10f, 5f, 5f, 10f, 5f, 10f, 0f, 5f},
                new CornerCut(40f, 40f, 40f, 40f).outline(10f, 10f));
    }
}
