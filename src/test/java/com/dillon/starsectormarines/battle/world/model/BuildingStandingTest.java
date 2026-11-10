package com.dillon.starsectormarines.battle.world.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A building's outline, standing share and demolished state ({@code BuildingStanding}). */
class BuildingStandingTest {

    /** A 2x2 floor at (2..3, 2..3) walled all round on a 6x6 map: a 4x4 ring of twelve wall cells. */
    private static boolean[][] hut() {
        boolean[][] wall = new boolean[6][6];
        for (int x = 1; x <= 4; x++) for (int y = 1; y <= 4; y++) wall[x][y] = x == 1 || x == 4 || y == 1 || y == 4;
        return wall;
    }

    private static final int[] FLOOR_X = {2, 3, 2, 3}, FLOOR_Y = {2, 2, 3, 3};

    @Test
    void theOutlineIsEveryWallTouchingTheFloorOnceAndNothingBeyond() {
        boolean[][] wall = hut();
        wall[5][5] = true; // a wall that touches no floor cell is not the building's
        List<int[]> outline = BuildingStanding.outline(FLOOR_X, FLOOR_Y, (x, y) -> wall[x][y], 6, 6);
        assertEquals(12, outline.size());
        assertTrue(outline.stream().noneMatch(c -> c[0] == 5 && c[1] == 5));
    }

    @Test
    void theStandingShareFallsAsWallsComeDownAndHalfDownIsDemolished() {
        boolean[][] wall = hut();
        List<int[]> outline = BuildingStanding.outline(FLOOR_X, FLOOR_Y, (x, y) -> wall[x][y], 6, 6);
        assertEquals(1f, BuildingStanding.standingShare(outline, (x, y) -> wall[x][y]));
        for (int i = 0; i < 5; i++) wall[outline.get(i)[0]][outline.get(i)[1]] = false;
        float share = BuildingStanding.standingShare(outline, (x, y) -> wall[x][y]);
        assertEquals(7f / 12f, share, 1e-6);
        assertFalse(BuildingStanding.demolished(share));
        wall[outline.get(5)[0]][outline.get(5)[1]] = false;
        share = BuildingStanding.standingShare(outline, (x, y) -> wall[x][y]);
        assertTrue(BuildingStanding.demolished(share), "half the outline down");
        assertEquals(1f, BuildingStanding.progress(share), 1e-6);
        assertEquals(0f, BuildingStanding.progress(1f), 1e-6);
    }

    @Test
    void aBuildingWithNoOutlineStandsAtNothing() {
        assertEquals(0f, BuildingStanding.standingShare(List.of(), (x, y) -> true));
    }
}
