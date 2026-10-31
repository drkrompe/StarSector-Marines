package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionMarksTest {

    @Test
    void aDottedLineStartsOnItsFirstEndAndCutsItsLastDotShortAtTheOther() {
        List<float[]> dots = SelectionMarks.dots(0f, 0f, 20f, 0f, 4f, 5f);

        // Dots at 0, 9 and 18: the last runs 18..20, two pixels, not four.
        assertEquals(3, dots.size());
        assertArrayEquals(new float[]{0f, 0f, 4f, 0f}, dots.get(0), 1e-4f);
        assertArrayEquals(new float[]{9f, 0f, 13f, 0f}, dots.get(1), 1e-4f);
        assertArrayEquals(new float[]{18f, 0f, 20f, 0f}, dots.get(2), 1e-4f);
    }

    @Test
    void aDottedLineFollowsItsDirectionAndALineOfNoLengthHasNoDots() {
        float[] first = SelectionMarks.dots(10f, 10f, 10f, 0f, 4f, 5f).get(0);
        assertArrayEquals(new float[]{10f, 10f, 10f, 6f}, first, 1e-4f);
        assertTrue(SelectionMarks.dots(3f, 3f, 3f, 3f, 4f, 5f).isEmpty());
    }
}
