package com.dillon.starsectormarines.battle.evacuation;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PickupCordonWorksTest {

    private static final int SIZE = 64, LIFT = 32;
    private static final PickupCordonWorks.Open BOARD = (x, y) -> x >= 0 && y >= 0 && x < SIZE && y < SIZE;

    @Test
    void everyRingIsAClosedLoopOfEdgeJoinedCells() {
        for (int r : new int[]{PickupCordonWorks.TRENCH, PickupCordonWorks.BAGS, PickupCordonWorks.WALL}) {
            Set<Long> ring = new HashSet<>();
            for (int[] c : PickupCordonWorks.loop(0, 0, r)) ring.add(PickupCordonWorks.key(c[0], c[1]));
            for (int[] c : PickupCordonWorks.loop(0, 0, r)) {
                int joined = 0;
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    if (ring.contains(PickupCordonWorks.key(c[0] + d[0], c[1] + d[1]))) joined++;
                }
                // Two neighbours each, all the way round: the trench art joins and the wall leaves no corner to slip.
                assertEquals(2, joined, "ring " + r + " at " + c[0] + "," + c[1]);
            }
        }
    }

    @Test
    void theWallShutsTheLiftInExceptThroughTheGates() {
        PickupCordonWorks.Layout shut = PickupCordonWorks.layout(LIFT, LIFT, (x, y) -> true, BOARD, List.of(), formation(), 1L);
        assertFalse(reachesLift(shut), "with no lane the wall closes all the way round");
        assertEquals(0, shut.leaks());

        // The survivors' walk comes in from the south edge straight up to the lift.
        List<int[]> lane = new ArrayList<>();
        for (int y = LIFT; y < SIZE; y++) lane.add(new int[]{LIFT, y});
        PickupCordonWorks.Layout gated = PickupCordonWorks.layout(LIFT, LIFT, (x, y) -> true, BOARD, List.of(lane), formation(), 1L);
        assertTrue(reachesLift(gated), "the gate lets the company in");
        Set<Long> wall = wallCells(gated);
        for (int dx = -PickupCordonWorks.GATE_HALF; dx <= PickupCordonWorks.GATE_HALF; dx++) {
            assertFalse(wall.contains(PickupCordonWorks.key(LIFT + dx, LIFT + PickupCordonWorks.WALL)),
                    "the gate is five cells wide");
        }
        assertTrue(wall.contains(PickupCordonWorks.key(LIFT + PickupCordonWorks.GATE_HALF + 1,
                LIFT + PickupCordonWorks.WALL)), "and no wider");
    }

    @Test
    void aLaneIsGatedWhereItCrossesNotWhereItRunsBesideTheWall() {
        // Out south past the wall, then east along the wall's outside.
        List<int[]> lane = new ArrayList<>();
        for (int y = LIFT; y <= LIFT + PickupCordonWorks.WALL + 1; y++) lane.add(new int[]{LIFT, y});
        for (int x = LIFT + 1; x < SIZE; x++) lane.add(new int[]{x, LIFT + PickupCordonWorks.WALL + 1});
        PickupCordonWorks.Layout layout = PickupCordonWorks.layout(LIFT, LIFT, (x, y) -> true, BOARD, List.of(lane),
                formation(), 1L);
        assertTrue(wallCells(layout).contains(PickupCordonWorks.key(LIFT + 8, LIFT + PickupCordonWorks.WALL)),
                "the wall stands where the lane only runs beside it");
        assertFalse(wallCells(layout).contains(PickupCordonWorks.key(LIFT, LIFT + PickupCordonWorks.WALL)));
    }

    @Test
    void noTrenchFacesTheMapEdge() {
        // The lift sixteen cells in from the west edge: the west wall would stand off the map.
        int lift = 16;
        PickupCordonWorks.Layout layout = PickupCordonWorks.layout(lift, LIFT, (x, y) -> BOARD.at(x, y),
                BOARD, List.of(), formation(), 1L);
        for (int[] c : layout.trench()) assertTrue(c[0] > lift - PickupCordonWorks.TRENCH, "trench at " + c[0]);
        for (RedoubtWorks.Piece p : layout.wall()) assertTrue(p.x() >= 0);
    }

    @Test
    void groundThatTakesNoPieceIsLeftOpenAndCounted() {
        // A building's frontage across the east side of the wall ring.
        PickupCordonWorks.Open open = (x, y) -> x != LIFT + PickupCordonWorks.WALL;
        PickupCordonWorks.Layout layout = PickupCordonWorks.layout(LIFT, LIFT, open, BOARD, List.of(), formation(), 1L);
        assertEquals(2 * (PickupCordonWorks.WALL - PickupCordonWorks.CHAMFER) + 1, layout.leaks());
    }

    @Test
    void eachPostIsTheTrenchCellNearestItsFormationPoint() {
        int[] formation = formation();
        PickupCordonWorks.Layout layout = PickupCordonWorks.layout(LIFT, LIFT, (x, y) -> true, BOARD, List.of(), formation, 1L);
        // The northern point stands thirteen up from the lift: its post is the trench straight out beyond it.
        assertEquals(LIFT, layout.posts()[0]);
        assertEquals(LIFT - PickupCordonWorks.TRENCH, layout.posts()[1]);
        for (int p = 0; p < formation.length; p += 2) {
            assertEquals(PickupCordonWorks.TRENCH,
                    PickupCordonWorks.ring(layout.posts()[p] - LIFT, layout.posts()[p + 1] - LIFT));
        }
    }

    /** The five pickup points thirteen out, the first due north, as the placement lays them. */
    private static int[] formation() {
        int[] cells = new int[10];
        for (int p = 0; p < 5; p++) {
            double angle = -Math.PI / 2 + p * Math.PI * 2 / 5;
            cells[p * 2] = (int) Math.round(LIFT + Math.cos(angle) * 13);
            cells[p * 2 + 1] = (int) Math.round(LIFT + Math.sin(angle) * 13);
        }
        return cells;
    }

    private static Set<Long> wallCells(PickupCordonWorks.Layout layout) {
        Set<Long> wall = new HashSet<>();
        for (RedoubtWorks.Piece p : layout.wall()) wall.add(PickupCordonWorks.key(p.x(), p.y()));
        return wall;
    }

    /** Whether the board's corner walks to the lift round the wall, edge to edge. */
    private static boolean reachesLift(PickupCordonWorks.Layout layout) {
        Set<Long> wall = wallCells(layout);
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(new int[]{0, 0});
        seen.add(PickupCordonWorks.key(0, 0));
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            if (c[0] == LIFT && c[1] == LIFT) return true;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = c[0] + d[0], y = c[1] + d[1];
                long k = PickupCordonWorks.key(x, y);
                if (x < 0 || y < 0 || x >= SIZE || y >= SIZE || wall.contains(k) || !seen.add(k)) continue;
                queue.add(new int[]{x, y});
            }
        }
        return false;
    }
}
