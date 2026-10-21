package com.dillon.starsectormarines.battle.sally;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where {@link SallyDoorSurvey} puts a door in a walled yard, on a hand-built grid. */
class SallyDoorSurveyTest {

    private static final int W = 60;
    private static final int H = 60;
    /** The yard's wall ring, inclusive. */
    private static final int L = 20, T = 10, R = 39, B = 49;

    private static NavigationGrid grid;
    private static CellTopology topology;

    /** A one-cell wall ring round open ground, twice as deep as it is wide, with a gate two wide in the middle of the south face. */
    private static void yard(boolean gatesEveryFace) {
        grid = new NavigationGrid(W, H);
        topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        for (int x = L; x <= R; x++) {
            wall(x, T);
            wall(x, B);
        }
        for (int y = T; y <= B; y++) {
            wall(L, y);
            wall(R, y);
        }
        int mid = (L + R) / 2;
        open(mid, B);
        open(mid + 1, B);
        if (gatesEveryFace) {
            open(mid, T);
            open(mid + 1, T);
            open(L, mid);
            open(L, mid + 1);
            open(R, mid);
            open(R, mid + 1);
        }
    }

    private static void wall(int x, int y) {
        grid.setWalkable(x, y, false);
        topology.setWall(x, y, true);
    }

    private static void open(int x, int y) {
        grid.setWalkable(x, y, true);
        topology.setWall(x, y, false);
    }

    @Test
    void aYardWithOneGateGetsADoorInEachOtherFaceAndNoneBesideTheGate() {
        yard(false);
        List<SallyDoorSurvey.Spec> doors = SallyDoorSurvey.derive(grid, topology, L, T, R, B, 29, 20, (x, y) -> false);
        assertEquals(3, doors.size(), "north, west and east faces each send the holder round; the south has its gate");
        for (SallyDoorSurvey.Spec door : doors) {
            assertEquals(SallyDoorSurvey.WIDTH, door.cells().length, "a one-cell wall, two cells along it");
            for (int[] c : door.cells()) assertTrue(topology.isWall(c[0], c[1]), "a door is cut through wall");
            assertTrue(door.saving() >= SallyDoorSurvey.MIN_SAVING_CELLS);
            assertTrue(door.insideX() > L && door.insideX() < R && door.insideY() > T && door.insideY() < B,
                    "it opens on the yard");
            boolean outside = door.mouthX() < L || door.mouthX() > R || door.mouthY() < T || door.mouthY() > B;
            assertTrue(outside, "its mouth is beyond the wall");
        }
        SallyDoorSurvey.Spec north = doors.get(0);
        assertEquals(T + 1, north.insideY());
        assertEquals(T - 1, north.mouthY());
        assertTrue(Math.abs(north.insideX() - (L + R) / 2) <= 1, "the span nearest the face's middle");
    }

    @Test
    void aYardWithAGateInEveryFaceGetsNoDoor() {
        yard(true);
        assertTrue(SallyDoorSurvey.derive(grid, topology, L, T, R, B, 29, 20, (x, y) -> false).isEmpty());
    }

    @Test
    void anExcludedWallIsNeverCut() {
        yard(false);
        List<SallyDoorSurvey.Spec> doors = SallyDoorSurvey.derive(grid, topology, L, T, R, B, 29, 20,
                (x, y) -> y == T);
        assertEquals(2, doors.size(), "the north wall is excluded");
        for (SallyDoorSurvey.Spec door : doors) for (int[] c : door.cells()) assertTrue(c[1] != T);
    }

    @Test
    void theAnchorNeedNotBeStandable() {
        yard(false);
        wall(29, 20);
        assertEquals(3, SallyDoorSurvey.derive(grid, topology, L, T, R, B, 29, 20, (x, y) -> false).size());
    }

    @Test
    void openGroundWithAWallAtItsEdgeIsNoWalledPlace() {
        yard(false);
        // A rectangle on the open ground west of the yard whose east edge runs along the yard's west wall.
        assertTrue(SallyDoorSurvey.derive(grid, topology, 2, T, L - 1, B, 10, 30, (x, y) -> false).isEmpty());
    }
}
