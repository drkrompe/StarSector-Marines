package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
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
 * through {@link ZoneGraph}; the tests below pin the three seed-resolution
 * cases (indoor walkable, walkable doorway, unwalkable wall-mount) and the
 * room-bound contract that was the whole point of the refactor.
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

    /** Build the same shape the live sim does — ZoneDetector flood over the current grid state. */
    private static ZoneGraph zonesFor(NavigationGrid grid) {
        ZoneGraph zones = new ZoneGraph(grid);
        zones.rebuild();
        return zones;
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
        // y=7 cells in the antechamber. ZoneDetector puts y≥11 cells in
        // one zone and y≤9 cells in another (the doorway is its own zone);
        // pickCellsNear should draw only from the throne-room zone.
        NavigationGrid grid = openGrid();
        for (int x = 0; x < W; x++) {
            if (x == 10) continue;
            grid.setWalkable(x, 10, false);
        }
        grid.setDoorway(10, 10, true);

        List<int[]> cells = BattleSetup.pickCellsNear(grid, zonesFor(grid),
                10, 12, 5, 50);

        Set<Long> picked = keysOf(cells);
        assertTrue(picked.contains(key(10, 12)), "anchor cell included");
        assertTrue(picked.contains(key(10, 13)), "throne-room neighbor included");
        assertFalse(picked.contains(key(10, 9)),
                "antechamber cell not picked despite Manhattan distance 3 from anchor");
        assertFalse(picked.contains(key(10, 7)),
                "deep antechamber cell not picked");
        assertFalse(picked.contains(key(10, 10)),
                "partition doorway not a spawn position (it's in its own 1-cell zone, not the throne room)");
    }

    @Test
    public void doorwayAnchorEscapesIntoBothRooms() {
        // Anchor at (10, 10) — the gate cell itself (doorway). ZoneDetector
        // makes it a 1-cell doorway zone; the gate-anchor branch in
        // resolveSpawnZones walks portals to the adjacent zones so gate
        // defenders spawn on either side of the partition.
        NavigationGrid grid = openGrid();
        for (int x = 0; x < W; x++) {
            if (x == 10) continue;
            grid.setWalkable(x, 10, false);
        }
        grid.setDoorway(10, 10, true);

        List<int[]> cells = BattleSetup.pickCellsNear(grid, zonesFor(grid),
                10, 10, 5, 50);

        Set<Long> picked = keysOf(cells);
        assertTrue(picked.contains(key(10, 12)),
                "north of gate reached (one of the two rooms)");
        assertTrue(picked.contains(key(10,  8)),
                "south of gate reached (the other room)");
    }

    @Test
    public void outdoorAnchorReachesFullRadius() {
        // No interior walls + no doorways. ZoneDetector puts every cell in
        // one big zone; pickCellsNear with radius 2 returns the Manhattan
        // diamond. Any non-compound caller (HEAVY_TOWER in a kill-zone,
        // FORWARD_BUNKER, GUARDPOST in open terrain) sees no regression.
        NavigationGrid grid = openGrid();

        List<int[]> cells = BattleSetup.pickCellsNear(grid, zonesFor(grid),
                10, 10, 2, 50);

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
    public void wallMountedTowerReachesNearbyFloor() {
        // The case the patch-3 critique surfaced: a HEAVY_TOWER / MG_NEST
        // anchored at the center of a 3×3 wall ring. Old BFS expanded
        // every cell unconditionally and reached floor cells beyond the
        // ring. ZoneDetector returns -1 for wall cells, so resolveSpawnZones
        // walks outward through walls until it hits a zone — picks up the
        // surrounding courtyard zone and draws spawn cells from it.
        NavigationGrid grid = openGrid();
        // 3×3 wall ring at (9..11, 9..11) with the seed at center (10, 10).
        for (int yy = 9; yy <= 11; yy++) {
            for (int xx = 9; xx <= 11; xx++) {
                grid.setWalkable(xx, yy, false);
            }
        }

        List<int[]> cells = BattleSetup.pickCellsNear(grid, zonesFor(grid),
                10, 10, 4, 50);

        Set<Long> picked = keysOf(cells);
        assertFalse(picked.contains(key(10, 10)),
                "wall-mount anchor cell itself not a spawn position");
        assertFalse(picked.contains(key(9, 10)),
                "wall-ring cell not a spawn position");
        assertTrue(picked.contains(key(12, 10)),
                "floor cell just beyond the wall ring reachable as spawn");
        assertTrue(picked.contains(key(10, 12)),
                "floor cell on another side of the wall ring reachable");
        assertTrue(cells.size() > 0,
                "wall-mount garrison must not collapse to empty (the patch-3-critique regression)");
    }

    @Test
    public void wallMountOnPerimeterReachesBothSides() {
        // Wall-mounted tower whose 3×3 ring sits on a perimeter wall with
        // courtyard on one side and kill-zone on the other. Old behavior
        // included cells from both sides (the cover sort then biased to
        // the defender side). With ZoneGraph: walls walked transparently
        // pick up both adjacent zones — same shape.
        NavigationGrid grid = openGrid();
        // Wall stripe at y=10 (with no doorway — fully sealed). 3×3 wall-
        // mount sits at (10, 10) embedded in the wall stripe.
        for (int x = 0; x < W; x++) {
            grid.setWalkable(x, 10, false);
        }
        for (int yy = 9; yy <= 11; yy++) {
            for (int xx = 9; xx <= 11; xx++) {
                grid.setWalkable(xx, yy, false);
            }
        }

        List<int[]> cells = BattleSetup.pickCellsNear(grid, zonesFor(grid),
                10, 10, 5, 50);

        Set<Long> picked = keysOf(cells);
        assertTrue(picked.contains(key(10, 13)),
                "north-side floor (one of the two zones) reachable");
        assertTrue(picked.contains(key(10,  7)),
                "south-side floor (the other zone) reachable");
    }
}
