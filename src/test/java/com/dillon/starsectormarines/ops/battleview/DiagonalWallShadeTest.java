package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiagonalWallShadeTest {

    /**
     * A diagonal's cells drop out of the height field's blocks and its slanted footprint stands in their place, so
     * its shadow runs along it; the cell where it runs into a straight wall stays that wall's block.
     */
    @Test
    void aDiagonalCastsAlongItsStrokeNotItsCells() {
        int w = 12;
        int[] cells = {0, 1, 2, 3 + w, 4 + 2 * w, 5 + 3 * w};
        Arrays.sort(cells);
        Set<Integer> in = new HashSet<>();
        for (int c : cells) in.add(c);
        float[] strokes = FreestandingWalls.strokes(cells, w, w, in::contains);
        List<Integer> lowered = new ArrayList<>();
        List<float[]> raised = new ArrayList<>();
        DiagonalWallShade.shade(strokes, w, lowered::add, (corners, cell) -> raised.add(corners.clone()));
        assertEquals(List.of(3 + w, 4 + 2 * w, 5 + 3 * w), lowered.stream().sorted().toList(),
                "the diagonal's own cells, not the straight wall's end");
        assertEquals(1, raised.size(), "one slanted footprint");
        float[] expected = new float[8];
        FreestandingWalls.corners(2.5f, .5f, 5.5f, 3.5f, false, true, FreestandingWalls.HALF, expected);
        for (int k = 0; k < 8; k++) assertEquals(expected[k], raised.get(0)[k], 1e-5f);
    }
}
