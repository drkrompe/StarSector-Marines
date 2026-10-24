package com.dillon.starsectormarines.battle.world.gen.layout;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadCourseTest {

    private static final int[] BOUNDS = {10, 20, 30, 40};

    @Test
    void anEndpointWithNoGateIsJustOutsideTheMiddleOfItsSide() {
        assertArrayEquals(new int[]{20, 41}, RoadCourse.point(BOUNDS, Side.NORTH, List.of(), 0, 0, (x, y) -> false));
        assertArrayEquals(new int[]{9, 30}, RoadCourse.point(BOUNDS, Side.WEST, List.of(), 0, 0, (x, y) -> false));
        assertArrayEquals(new int[]{34, 25}, RoadCourse.point(BOUNDS, Side.EAST, List.of(), 3, -5, (x, y) -> false));
    }

    @Test
    void aGateEndpointStepsOutOfThePlaceFromTheGateNearestItsSide() {
        List<int[]> northGate = List.of(new int[]{25, 38}, new int[]{26, 38}, new int[]{27, 38});
        List<int[]> southGate = List.of(new int[]{15, 21}, new int[]{16, 21}, new int[]{17, 21});
        LineCourse.CellTest inside = (x, y) -> x >= 10 && x <= 30 && y >= 20 && y <= 38;
        assertArrayEquals(new int[]{26, 39},
                RoadCourse.point(BOUNDS, Side.NORTH, List.of(southGate, northGate), 0, 0, inside));
        assertArrayEquals(new int[]{16, 19},
                RoadCourse.point(BOUNDS, Side.SOUTH, List.of(southGate, northGate), 0, 0, inside));
    }

    @Test
    void theCourseLeavesAlongItsSideCrossesAtHalfwayAndArrives() {
        List<int[]> path = RoadCourse.path(new int[]{10, 0}, Side.NORTH, new int[]{40, 20});
        assertEquals("[10, 0][10, 10][40, 10][40, 20]", flat(path));
        path = RoadCourse.path(new int[]{0, 5}, Side.EAST, new int[]{20, 15});
        assertEquals("[0, 5][10, 5][10, 15][20, 15]", flat(path));
    }

    @Test
    void theCourseIsBrushedToItsWidthEachCellOnce() {
        List<int[]> cells = RoadCourse.cells(List.of(new int[]{0, 0}, new int[]{9, 0}), 3);
        // Ten cells along, a cell of brush past each end, three across.
        assertEquals(12 * 3, cells.size());
        for (int[] c : cells) assertEquals(true, c[1] >= -1 && c[1] <= 1);
    }

    @Test
    void aCarvedStretchCutOffFromTheNetworkIsGivenBack() {
        int[][] owner = new int[12][3];
        for (int[] column : owner) Arrays.fill(column, GrownTrunkPlan.UNOWNED);
        owner[0][1] = 0;
        // Carved: 1..4 joined to the road at 0, and 7..9 beyond a gap.
        List<int[]> carved = List.of(new int[]{1, 1}, new int[]{2, 1}, new int[]{3, 1}, new int[]{4, 1},
                new int[]{7, 1}, new int[]{8, 1}, new int[]{9, 1});
        for (int[] c : carved) owner[c[0]][c[1]] = 0;
        List<int[]> kept = LayoutRoads.connected(owner, carved, List.of(), 12, 3);
        assertEquals(4, kept.size());
        assertEquals(GrownTrunkPlan.UNOWNED, owner[8][1]);
        assertEquals(0, owner[3][1]);
    }

    @Test
    void aStretchThatReachesAWalledPlacesGateIsKeptWithNoOtherRoadNear() {
        int[][] owner = new int[12][3];
        for (int[] column : owner) Arrays.fill(column, GrownTrunkPlan.UNOWNED);
        // Open country between two walled places, no road anywhere: the stretch's ends are on their outlines.
        List<int[]> carved = List.of(new int[]{1, 1}, new int[]{2, 1}, new int[]{3, 1}, new int[]{4, 1},
                new int[]{5, 1});
        for (int[] c : carved) owner[c[0]][c[1]] = 0;
        List<int[]> kept = LayoutRoads.connected(owner, carved, List.of(carved.get(4)), 12, 3);
        assertEquals(5, kept.size(), "the gate the road makes is where it joins");
        assertEquals(0, owner[1][1]);
    }

    private static String flat(List<int[]> path) {
        StringBuilder out = new StringBuilder();
        for (int[] v : path) out.append(Arrays.toString(v));
        return out.toString();
    }
}
