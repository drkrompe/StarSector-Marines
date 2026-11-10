package com.dillon.starsectormarines.battle.world.gen.raid;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pipe rack's laws ({@link PipeRack}): beside roads not on them, square turns, gantries where they may stand. */
class PipeRackTest {

    private static final int W = 40, H = 20;

    /** A road along row 10; the rack from its west end to its east end keeps beside it rather than on it. */
    @Test
    void aRackRunsBesideARoadRatherThanAlongIt() {
        List<int[]> path = PipeRack.route(W, H, 0, 9, i -> i / W == 10 ? 6 : 0, i -> i % W == W - 1);
        assertFalse(path.isEmpty());
        for (int[] c : path) assertTrue(c[1] != 10, "the rack ran along the road");
    }

    /** A road across the course: the rack bridges it square, crossing in as few cells as the road is wide. */
    @Test
    void aRackBridgesARoadSquare() {
        // Road columns 18..20; the goal is beyond it.
        List<int[]> path = PipeRack.route(W, H, 2, 5, i -> i % W >= 18 && i % W <= 20 ? 6 : 0,
                i -> i % W == 35 && i / W == 12);
        int onRoad = 0;
        for (int[] c : path) if (c[0] >= 18 && c[0] <= 20) onRoad++;
        assertEquals(3, onRoad);
    }

    @Test
    void aRackTurnsOnlySquare() {
        List<int[]> path = PipeRack.route(W, H, 1, 1, i -> 0, i -> i % W == 30 && i / W == 15);
        for (int k = 1; k < path.size(); k++) {
            int dx = Math.abs(path.get(k)[0] - path.get(k - 1)[0]), dy = Math.abs(path.get(k)[1] - path.get(k - 1)[1]);
            assertEquals(1, dx + dy, "every step is to a side neighbour");
        }
        int turns = 0;
        for (int k = 1; k + 1 < path.size(); k++) {
            int[] a = path.get(k - 1), c = path.get(k + 1);
            if (a[0] != c[0] && a[1] != c[1]) turns++;
        }
        assertEquals(1, turns, "a long straight run and one turn");
    }

    @Test
    void anImpassableCellIsNeverCrossed() {
        List<int[]> path = PipeRack.route(W, H, 1, 5, i -> i % W == 20 && i / W != 0 ? -1 : 0, i -> i % W == 30);
        for (int[] c : path) assertFalse(c[0] == 20 && c[1] != 0);
    }

    @Test
    void gantriesStandAtTheEndsTheTurnsAndEveryBayWhereTheyMay() {
        List<int[]> path = new ArrayList<>();
        for (int x = 0; x <= 20; x++) path.add(new int[]{x, 0});
        for (int y = 1; y <= 4; y++) path.add(new int[]{20, y});
        // No column may stand on cells 5..7.
        List<PipeRack.Piece> pieces = PipeRack.pieces(path, i -> i % W < 5 || i % W > 7 || i / W > 0, W);
        assertEquals(PipeRack.Kind.GANTRY, pieces.get(0).kind());
        assertEquals(PipeRack.Kind.GANTRY, pieces.get(pieces.size() - 1).kind());
        assertEquals(PipeRack.Kind.GANTRY, pieces.get(20).kind(), "the turn is a gantry");
        for (PipeRack.Piece p : pieces) {
            if (p.y() == 0 && p.x() >= 5 && p.x() <= 7) assertEquals(PipeRack.Kind.RUN, p.kind());
        }
        int last = 0;
        for (int k = 1; k <= 20; k++) {
            if (pieces.get(k).kind() != PipeRack.Kind.GANTRY) continue;
            assertTrue(k - last <= PipeRack.BAY + 2, "a bay too long between gantries");
            last = k;
        }
        assertTrue(pieces.get(3).alongX() && !pieces.get(22).alongX(), "pipes lie along their run");
    }

    @Test
    void aTwinRunsBesideItsCourse() {
        List<int[]> path = new ArrayList<>();
        for (int x = 0; x <= 10; x++) path.add(new int[]{x, 5});
        for (int y = 6; y <= 9; y++) path.add(new int[]{10, y});
        List<int[]> twin = PipeRack.twin(path);
        for (int k = 1; k < twin.size(); k++) {
            int d = Math.abs(twin.get(k)[0] - twin.get(k - 1)[0]) + Math.abs(twin.get(k)[1] - twin.get(k - 1)[1]);
            assertTrue(d <= 2, "the twin keeps together");
        }
        for (int[] t : twin) {
            boolean beside = false;
            for (int[] p : path) {
                assertFalse(p[0] == t[0] && p[1] == t[1], "the twin never takes its course's cell");
                if (Math.abs(p[0] - t[0]) + Math.abs(p[1] - t[1]) <= 2) beside = true;
            }
            assertTrue(beside);
        }
    }
}
