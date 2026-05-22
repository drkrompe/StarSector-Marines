package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioral coverage for {@link BattleSetup#pickCellsNear} — the spawn-
 * pool picker used by the defender allocator and the mid-battle squad
 * fallback. Patch 3 of the slice-6 central-keep fix sequence routes this
 * through {@link com.dillon.starsectormarines.battle.nav.RoomFinder}; the
 * tests below pin the room-bound contract that was the whole point of the
 * refactor.
 */
public class BattleSetupPickCellsNearTest {

    private static final int W = 20;
    private static final int H = 20;

    private static NavigationGrid openGrid() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    private static Set<Long> keysOf(List<int[]> cells) {
        Set<Long> set = new HashSet<>();
        for (int[] c : cells) set.add(((long) c[0] << 32) | (c[1] & 0xFFFFFFFFL));
        return set;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    @Test
    public void roomBoundedSpawnPoolForPartitionedBuilding() {
        // Two-room building partitioned at y=10 with a doorway at x=10.
        // Anchor at (10, 12) — the "throne room" side. Spawn pool radius 5
        // is large enough that, without the room bound, the BFS would reach
        // y=7 cells in the antechamber. Room-bound contract: only y ≥ 11
        // cells appear; antechamber cells are excluded.
        NavigationGrid grid = openGrid();
        // Wall row at y=10 with doorway at x=10. Mirrors BuildingShellCore.
        for (int x = 0; x < W; x++) {
            if (x == 10) continue;
            grid.setWalkable(x, 10, false);
        }
        grid.setDoorway(10, 10, true);

        List<int[]> cells = BattleSetup.pickCellsNear(grid, 10, 12, 5, 50);

        Set<Long> picked = keysOf(cells);
        assertTrue(picked.contains(key(10, 12)), "anchor cell included");
        assertTrue(picked.contains(key(10, 13)), "throne-room neighbor included");
        // Antechamber cells (y ≤ 9) are within Manhattan-distance-5 from
        // (10, 12) only via the doorway, which the room bound forbids.
        assertFalse(picked.contains(key(10, 9)),
                "antechamber cell not picked despite Manhattan distance 3 from anchor");
        assertFalse(picked.contains(key(10, 7)),
                "deep antechamber cell not picked");
        // Partition doorway itself is a boundary, not floor — should not
        // appear as a spawn position.
        assertFalse(picked.contains(key(10, 10)),
                "partition doorway not a spawn position");
    }

    @Test
    public void doorwayAnchorEscapesIntoBothRooms() {
        // Anchor at (10, 10) — the gate cell itself (doorway). Seed
        // exemption lets the flood escape both sides; gate defenders spawn
        // on either side of the partition.
        NavigationGrid grid = openGrid();
        for (int x = 0; x < W; x++) {
            if (x == 10) continue;
            grid.setWalkable(x, 10, false);
        }
        grid.setDoorway(10, 10, true);

        List<int[]> cells = BattleSetup.pickCellsNear(grid, 10, 10, 5, 50);

        Set<Long> picked = keysOf(cells);
        assertTrue(picked.contains(key(10, 12)),
                "north of gate reached (one of the two rooms)");
        assertTrue(picked.contains(key(10,  8)),
                "south of gate reached (the other room)");
    }

    @Test
    public void outdoorAnchorBehavesAsBeforeWhenNoDoorwaysInRadius() {
        // No interior walls + no doorways. The room-aware flood degenerates
        // to a plain Manhattan-distance BFS — same behavior the pre-patch
        // BFS gave, so any non-compound caller (HEAVY_TOWER in a kill-zone,
        // FORWARD_BUNKER, GUARDPOST in open terrain) sees no regression.
        NavigationGrid grid = openGrid();

        List<int[]> cells = BattleSetup.pickCellsNear(grid, 10, 10, 2, 50);

        // Manhattan diamond of radius 2 = 13 cells (1 center + 4 d=1 + 8 d=2).
        assertTrue(cells.size() == 13,
                "open-grid radius-2 pool has 13 cells (size was " + cells.size() + ")");
        Set<Long> picked = keysOf(cells);
        assertTrue(picked.contains(key(10, 10)), "anchor included");
        assertTrue(picked.contains(key(12, 10)), "Manhattan-2 cell included");
        assertFalse(picked.contains(key(13, 10)),
                "Manhattan-3 cell excluded by radius (unchanged behavior)");
    }

    @Test
    public void unwalkableAnchorReachesNearbyFloor() {
        // Turret-mount pattern: anchor cell is a wall (unwalkable). The
        // flood escapes into adjacent walkable cells but excludes the wall
        // itself. Matches the historical behavior — wall-mounted HEAVY_TOWER
        // garrisons need to spawn around their tower, not on it.
        NavigationGrid grid = openGrid();
        grid.setWalkable(10, 10, false);

        List<int[]> cells = BattleSetup.pickCellsNear(grid, 10, 10, 2, 50);

        Set<Long> picked = keysOf(cells);
        assertFalse(picked.contains(key(10, 10)),
                "unwalkable anchor cell not a spawn position");
        assertTrue(picked.contains(key(11, 10)),
                "walkable neighbor of unwalkable anchor included");
        assertTrue(picked.contains(key(10, 11)),
                "walkable neighbor in another direction included");
    }
}
