package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoldPositionPickerTest {
    @Test
    void mergedExteriorDoesNotTurnIntoTheHoldFootprint() {
        NavigationGrid grid = floor(80, 60);
        ZoneGraph zones = zones(grid);
        TacticalNode node = node(21, 21, 20, 20, 23, 23);
        int zone = zones.zoneIdAt(21, 21);
        TickInnerProfile previous = TickInnerProfile.currentIfBound();
        TickInnerProfile profile = new TickInnerProfile();
        int[][] posts;
        try {
            TickInnerProfile.setCurrent(profile);
            posts = HoldPositionPicker.pick(node, zone, 8, grid, zones);
            assertEquals(16, profile.countOf(TickInnerProfile.Bucket.HOLD_POSITION_CELL),
                    "inspect the 4x4 objective, not the 4,800-cell exterior zone");
            assertEquals(0, profile.countOf(TickInnerProfile.Bucket.HOLD_POSITION_FALLBACK));
        } finally {
            TickInnerProfile.setCurrent(previous);
        }
        assertEquals(8, posts[0].length);
        assertPosts(posts, grid, zones, zone, 20, 20, 23, 23);
        assertArrayEquals(new int[]{21, 21}, new int[]{posts[0][0], posts[1][0]});
    }

    @Test
    void multipleRoomsRemainBoundToExactCaptureZoneAndCountIsCapped() {
        NavigationGrid grid = floor(9, 5);
        for (int y = 0; y < 5; y++) grid.setWalkable(4, y, false);
        ZoneGraph zones = zones(grid);
        TacticalNode node = node(2, 2, 0, 0, 8, 4);
        int zone = zones.zoneIdAt(2, 2);
        int[][] posts = HoldPositionPicker.pick(node, zone, 50, grid, zones);
        assertEquals(20, posts[0].length);
        assertPosts(posts, grid, zones, zone, 0, 0, 3, 4);
    }

    @Test
    void compoundBoundsNotJustNodeBuildingDefineTheLocalArea() {
        NavigationGrid grid = floor(10, 10);
        ZoneGraph zones = zones(grid);
        TacticalNode node = node(4, 4, 4, 4, 4, 4);
        node.setCompoundBounds(3, 3, 5, 5);
        int[][] posts = HoldPositionPicker.pick(node, 0, 20, grid, zones);
        assertEquals(9, posts[0].length);
        assertPosts(posts, grid, zones, 0, 3, 3, 5, 5);
    }

    @Test
    void breachAndRebuildUseCurrentGeometryWithoutStaleCachedPosts() {
        NavigationGrid grid = floor(9, 5);
        for (int y = 0; y < 5; y++) grid.setWalkable(4, y, false);
        ZoneGraph zones = zones(grid);
        TacticalNode node = node(3, 2, 2, 1, 5, 3);
        int[][] before = HoldPositionPicker.pick(node, zones.zoneIdAt(3, 2), 20, grid, zones);
        assertEquals(6, before[0].length);
        grid.setWalkableFloor(4, 2);
        zones.applyCellsOpened(new int[]{2 * 9 + 4});
        int zone = zones.zoneIdAt(3, 2);
        int[][] after = HoldPositionPicker.pick(node, zone, 20, grid, zones);
        assertEquals(10, after[0].length);
        assertPosts(after, grid, zones, zone, 2, 1, 5, 3);
        grid.setWalkable(3, 2, false);
        zones.rebuild();
        int[][] rebuilt = HoldPositionPicker.pick(node, zones.zoneIdAt(2, 2), 20, grid, zones);
        assertPosts(rebuilt, grid, zones, zones.zoneIdAt(2, 2), 2, 1, 5, 3);
    }

    @Test
    void noLocalIntersectionFallsBackToOneNearestLegalCellNotAnotherRoom() {
        NavigationGrid grid = floor(9, 5);
        for (int y = 0; y < 5; y++) grid.setWalkable(4, y, false);
        ZoneGraph zones = zones(grid);
        TacticalNode node = node(2, 2, 0, 0, 3, 4);
        int[][] posts = HoldPositionPicker.pick(node, zones.zoneIdAt(6, 2), 8, grid, zones);
        assertArrayEquals(new int[]{5}, posts[0]);
        assertArrayEquals(new int[]{2}, posts[1]);
    }

    @Test
    void absentZoneUsesAnchorAndNonpositiveCountStillSelectsOnePost() {
        NavigationGrid grid = floor(5, 5);
        ZoneGraph zones = zones(grid);
        TacticalNode node = node(2, 2, -2, -2, 6, 6);
        int[][] absent = HoldPositionPicker.pick(node, -1, 8, grid, zones);
        assertArrayEquals(new int[]{2}, absent[0]);
        assertArrayEquals(new int[]{2}, absent[1]);
        assertEquals(1, HoldPositionPicker.pick(node, 0, 0, grid, zones)[0].length);
    }

    @Test
    void overflowAndUnassignedMembersShareLegalPostInsteadOfOutOfZoneAnchor() {
        HoldZone hold = new HoldZone(1, node(2, 2, 0, 0, 3, 4), new int[]{5}, new int[]{2});
        for (int slot : new int[]{-1, 0, 9}) {
            assertEquals(5, hold.postX(slot));
            assertEquals(2, hold.postY(slot));
        }
    }

    @Test
    void incrementalSpreadExactlyMatchesNestedDistanceOracleIncludingTies() {
        Random random = new Random(1729);
        for (int run = 0; run < 100; run++) {
            int[] candidates = random.ints(0, 144).distinct().limit(40).toArray();
            int ax = random.nextInt(12), ay = random.nextInt(12);
            int[][] actual = HoldPositionPicker.spread(candidates, candidates.length, 12, ax, ay, 12);
            int[][] expected = oracle(candidates, 12, ax, ay, 12);
            assertArrayEquals(expected[0], actual[0]);
            assertArrayEquals(expected[1], actual[1]);
            int[][] prefix = HoldPositionPicker.spread(candidates, candidates.length, 12, ax, ay, 4);
            for (int i = 0; i < 4; i++) {
                assertEquals(actual[0][i], prefix[0][i]);
                assertEquals(actual[1][i], prefix[1][i]);
            }
        }
    }

    private static int[][] oracle(int[] cells, int width, int ax, int ay, int n) {
        int[][] out = new int[2][n];
        float seedDistance = Float.MAX_VALUE;
        for (int cell : cells) {
            float d = distance(cell % width, cell / width, ax, ay);
            if (d < seedDistance) {
                seedDistance = d; out[0][0] = cell % width; out[1][0] = cell / width;
            }
        }
        for (int p = 1; p < n; p++) {
            float farthest = 0;
            for (int cell : cells) {
                float nearest = Float.MAX_VALUE;
                for (int i = 0; i < p; i++) {
                    nearest = Math.min(nearest, distance(cell % width, cell / width, out[0][i], out[1][i]));
                }
                if (nearest > farthest) {
                    farthest = nearest; out[0][p] = cell % width; out[1][p] = cell / width;
                }
            }
        }
        return out;
    }

    private static float distance(int x, int y, int ax, int ay) {
        float dx = x - ax, dy = y - ay;
        return dx * dx + dy * dy;
    }

    private static NavigationGrid floor(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        return grid;
    }

    private static ZoneGraph zones(NavigationGrid grid) {
        ZoneGraph zones = new ZoneGraph(grid);
        zones.rebuild();
        return zones;
    }

    private static TacticalNode node(int x, int y, int left, int top, int right, int bottom) {
        return new TacticalNode(TacticalNode.Kind.ARMORY, x, y, left, top, right, bottom,
                Faction.DEFENDER, 80, 4);
    }

    private static void assertPosts(int[][] posts, NavigationGrid grid, ZoneGraph zones, int zone,
                                    int left, int top, int right, int bottom) {
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < posts[0].length; i++) {
            int x = posts[0][i], y = posts[1][i];
            assertTrue(x >= left && x <= right && y >= top && y <= bottom);
            assertTrue(grid.isWalkable(x, y));
            assertEquals(zone, zones.zoneIdAt(x, y));
            assertTrue(seen.add(y * grid.getWidth() + x));
        }
    }
}
