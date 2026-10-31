package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a redoubt holds its front: its wall teams on the face ({@link CordonGarrisons#frontTeams}) and a gun at each
 * corner ({@link CordonGarrisons#cornerCells}, {@link CordonGarrisons#keepsWay}).
 */
class CordonRedoubtFrontTest {

    private static NavigationGrid open(int w, int h) {
        NavigationGrid grid = new NavigationGrid(w, h);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) grid.setWalkableFloor(x, y);
        return grid;
    }

    @Test
    void twoTeamsHoldTheFrontSideBySideAndTheThirdStandsBehind() {
        // Front north on row 20: four posts on the face, the rest down both flanks.
        List<int[]> posts = new ArrayList<>();
        for (int x : new int[]{12, 14, 16, 18}) posts.add(new int[]{x, 20});
        for (int y = 19; y >= 15; y--) {
            posts.add(new int[]{10, y});
            posts.add(new int[]{20, y});
        }
        List<List<int[]>> teams = CordonGarrisons.frontTeams(posts.subList(0, 12), 20, 3);
        assertEquals(3, teams.size());
        for (List<int[]> t : teams) assertEquals(4, t.size());
        // Every face post is on one of the two front teams; the west one holds the west end, the east one the east.
        int onFront = 0;
        for (int k = 0; k < 2; k++) for (int[] c : teams.get(k)) if (c[1] == 20) onFront++;
        assertEquals(4, onFront);
        for (int[] c : teams.get(0)) for (int[] d : teams.get(1)) assertTrue(c[0] <= d[0], "west team, then east");
        for (int[] c : teams.get(2)) assertTrue(c[1] <= 18, "the third stands behind the front teams");
    }

    @Test
    void aCornerGunStandsNearestEachCornerOffThePosts() {
        NavigationGrid grid = open(30, 30);
        List<int[]> corners = CordonGarrisons.cornerCells(grid, 5, 5, 15, 15, List.of(new int[]{5, 15}));
        assertEquals(4, corners.size());
        // West front corner (5, 15) is a post: the next cell out along the ring takes its gun.
        int[] westFront = corners.get(0);
        assertEquals(1, Math.max(Math.abs(westFront[0] - 5), Math.abs(westFront[1] - 15)));
        assertArrayEquals(new int[]{15, 15}, corners.get(1));
        assertArrayEquals(new int[]{5, 5}, corners.get(2));
        assertArrayEquals(new int[]{15, 5}, corners.get(3));
    }

    @Test
    void aCornerWithNoOpenGroundNearItHasNoGun() {
        NavigationGrid grid = new NavigationGrid(30, 30);
        for (int y = 10; y <= 15; y++) for (int x = 5; x <= 15; x++) grid.setWalkableFloor(x, y);
        // The south half of the ground is built over: only the two north corners take guns.
        List<int[]> corners = CordonGarrisons.cornerCells(grid, 5, 0, 15, 15, List.of());
        assertEquals(2, corners.size());
        for (int[] c : corners) assertEquals(15, c[1]);
    }

    @Test
    void aGunNeverCutsTheGroundRoundItInTwo() {
        // A corridor one cell wide running east-west: closing a cell in it splits west from east.
        CordonGarrisons.Open corridor = (x, y) -> y == 0;
        assertFalse(CordonGarrisons.keepsWay(corridor, 5, 0));
        // An open field, or a dead end, keeps its way.
        assertTrue(CordonGarrisons.keepsWay((x, y) -> true, 5, 5));
        assertTrue(CordonGarrisons.keepsWay((x, y) -> x <= 5 && y == 0 || x == 4 && y == 1, 5, 0));
        // A corner of a room: the two sides meet through the diagonal.
        assertTrue(CordonGarrisons.keepsWay((x, y) -> x >= 0 && y >= 0, 0, 0));
    }
}
