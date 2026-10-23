package com.dillon.starsectormarines.battle.world.gen.layout;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineCourseTest {

    private static final int[] NORTH = {0, 1};

    @Test
    void rasterIsFourConnectedAndEndsOnBothVertices() {
        List<int[]> cells = LineCourse.raster(List.of(new int[]{0, 0}, new int[]{13, 5}, new int[]{20, -3}));
        assertArrayEquals(new int[]{0, 0}, cells.get(0));
        assertArrayEquals(new int[]{20, -3}, cells.get(cells.size() - 1));
        for (int i = 1; i < cells.size(); i++) {
            int step = Math.abs(cells.get(i)[0] - cells.get(i - 1)[0]) + Math.abs(cells.get(i)[1] - cells.get(i - 1)[1]);
            assertEquals(1, step, "cells " + (i - 1) + " and " + i + " share an edge");
        }
    }

    @Test
    void eachRoadCrossingIsOneGapOfTheRoadAndItsMargins() {
        // Two roads across a straight east-west course: x 20..22 and x 40.
        LineCourse.Course course = LineCourse.plan(List.of(new int[]{0, 10}, new int[]{60, 10}),
                (x, y) -> (x >= 20 && x <= 22) || x == 40, 2, NORTH, 0);
        assertEquals(2, course.gaps().size());
        assertEquals(3 + 2 * 2, course.gaps().get(0).size());
        assertEquals(1 + 2 * 2, course.gaps().get(1).size());
        assertEquals(18, course.gaps().get(0).get(0)[0]);
        assertEquals(3, course.runs().size());
        for (List<int[]> run : course.runs()) {
            for (int[] c : run) assertFalse(c[0] >= 18 && c[0] <= 24 || c[0] >= 38 && c[0] <= 42, "no works in a gap");
        }
    }

    @Test
    void nestsStandAtTheEndsOfEveryLongEnoughRun() {
        LineCourse.Course course = LineCourse.plan(List.of(new int[]{0, 10}, new int[]{60, 10}),
                (x, y) -> x == 30, 2, NORTH, 0);
        assertEquals(4, course.nests().size());
        assertArrayEquals(new int[]{0, 10}, course.nests().get(0));
        assertArrayEquals(new int[]{27, 10}, course.nests().get(1));
        assertArrayEquals(new int[]{33, 10}, course.nests().get(2));
        assertArrayEquals(new int[]{60, 10}, course.nests().get(3));
        for (List<int[]> run : course.runs()) {
            for (int[] c : run) for (int[] n : course.nests()) assertFalse(c[0] == n[0] && c[1] == n[1]);
        }
    }

    @Test
    void wireStandsInFrontWithAFunnelAtEachPitch() {
        LineCourse.Course course = LineCourse.plan(List.of(new int[]{0, 10}, new int[]{41, 10}),
                (x, y) -> false, 2, NORTH, 14);
        for (int[] w : course.wire()) assertEquals(10 + LineCourse.WIRE_OFFSET, w[1]);
        // 42 cells at a pitch of 14: three funnels, each two cells across.
        assertEquals(3 * LineCourse.FUNNEL_WIDTH, course.funnels().size());
        assertEquals(42, course.wire().size() + course.funnels().size());
        List<Integer> open = new ArrayList<>();
        for (int[] f : course.funnels()) open.add(f[0]);
        assertTrue(open.contains(7) && open.contains(8) && open.contains(21) && open.contains(35));
    }

    @Test
    void theSameInputsPlanTheSameCourse() {
        List<int[]> path = List.of(new int[]{3, 4}, new int[]{70, 19});
        LineCourse.CellTest road = (x, y) -> x % 23 == 0;
        LineCourse.Course a = LineCourse.plan(path, road, 1, new int[]{0, -1}, 9);
        LineCourse.Course b = LineCourse.plan(path, road, 1, new int[]{0, -1}, 9);
        assertEquals(flat(a), flat(b));
    }

    private static String flat(LineCourse.Course c) {
        StringBuilder out = new StringBuilder();
        for (List<int[]> run : c.runs()) for (int[] x : run) out.append(x[0]).append(',').append(x[1]).append(';');
        out.append('|');
        for (int[] x : c.wire()) out.append(x[0]).append(',').append(x[1]).append(';');
        out.append('|');
        for (int[] x : c.funnels()) out.append(x[0]).append(',').append(x[1]).append(';');
        return out.toString();
    }
}
