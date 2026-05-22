package com.dillon.starsectormarines.battle.nav;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct unit coverage for {@link RoomFinder}. Pins the flood primitive's
 * core contracts: doorway-as-boundary, seed exemption, radius bound, bbox
 * bound, non-walkable seed (turret mount), and the membership helper.
 */
public class RoomFinderTest {

    private static final int W = 20;
    private static final int H = 20;

    private static NavigationGrid openGrid() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    /** Pack a flood result list into a set of {@code key(x, y)} for membership assertions. */
    private static Set<Long> keysOf(List<int[]> cells) {
        Set<Long> set = new HashSet<>();
        for (int[] c : cells) set.add(RoomFinder.key(c[0], c[1]));
        return set;
    }

    @Test
    public void singleOpenRoomFloodReachesEntireRadius() {
        // No walls, no doorways. Flood with radius 2 from (10, 10) reaches
        // the diamond of cells within Manhattan distance 2 — 13 cells total
        // (1 center + 4 at d=1 + 8 at d=2).
        NavigationGrid grid = openGrid();
        List<int[]> result = RoomFinder.flood(grid, 10, 10, 2, null);

        assertEquals(13, result.size(),
                "open-grid radius-2 Manhattan diamond has 13 cells");
        Set<Long> reached = keysOf(result);
        assertTrue(reached.contains(RoomFinder.key(10, 10)), "seed included");
        assertTrue(reached.contains(RoomFinder.key(12, 10)), "cells at d=2 reached");
        assertFalse(reached.contains(RoomFinder.key(13, 10)),
                "cells at d=3 excluded by radius bound");
    }

    @Test
    public void doorwayActsAsRoomBoundary() {
        // Wall stripe across y=10 with doorway at x=10. Seed at (10, 12)
        // (above the wall). The flood should reach all walkable cells with
        // y > 10 but stop at the partition — no cells with y < 10, even
        // though they're walkable and within Manhattan distance.
        NavigationGrid grid = openGrid();
        for (int x = 0; x < W; x++) {
            if (x == 10) continue;
            grid.setWalkable(x, 10, false);
        }
        grid.setDoorway(10, 10, true);

        List<int[]> result = RoomFinder.flood(grid, 10, 12,
                Integer.MAX_VALUE, null);
        Set<Long> reached = keysOf(result);

        // Throne-room side (y ≥ 11): all walkable cells reached.
        assertTrue(reached.contains(RoomFinder.key(10, 12)), "seed reached");
        assertTrue(reached.contains(RoomFinder.key(10, 19)), "far-top reached (no boundary)");
        assertTrue(reached.contains(RoomFinder.key( 0, 11)), "wide spread within room");
        // Partition row + below: nothing reached.
        assertFalse(reached.contains(RoomFinder.key(10, 10)),
                "partition doorway excluded — boundary, not floor");
        assertFalse(reached.contains(RoomFinder.key(10,  9)),
                "across-doorway cell unreached even though walkable");
        assertFalse(reached.contains(RoomFinder.key( 0,  5)),
                "far-bottom unreached");
    }

    @Test
    public void doorwaySeedEscapesIntoBothAdjacentRooms() {
        // Same wall + doorway as the prior test, but the SEED is on the
        // doorway cell. Seed exemption: doorway boundary doesn't apply to
        // the seed itself, so the flood escapes north AND south. Models the
        // GATE-defender pattern — gate at a wall gap, defenders on both
        // sides.
        NavigationGrid grid = openGrid();
        for (int x = 0; x < W; x++) {
            if (x == 10) continue;
            grid.setWalkable(x, 10, false);
        }
        grid.setDoorway(10, 10, true);

        List<int[]> result = RoomFinder.flood(grid, 10, 10,
                Integer.MAX_VALUE, null);
        Set<Long> reached = keysOf(result);

        assertTrue(reached.contains(RoomFinder.key(10, 10)),
                "seed doorway IS in the pool (exempt from boundary rule)");
        assertTrue(reached.contains(RoomFinder.key(10, 12)),
                "north side reached");
        assertTrue(reached.contains(RoomFinder.key(10,  8)),
                "south side reached");
    }

    @Test
    public void unwalkableSeedStillFloodsNeighbors() {
        // Turret mount pattern — anchor is on an unwalkable cell (e.g. a
        // tower's gun mount). Seed itself doesn't appear in the pool, but
        // walkable neighbors do.
        NavigationGrid grid = openGrid();
        grid.setWalkable(10, 10, false);  // turret mount cell

        List<int[]> result = RoomFinder.flood(grid, 10, 10, 2, null);
        Set<Long> reached = keysOf(result);

        assertFalse(reached.contains(RoomFinder.key(10, 10)),
                "unwalkable seed excluded from pool");
        assertTrue(reached.contains(RoomFinder.key(11, 10)),
                "walkable neighbor reached");
        assertTrue(reached.contains(RoomFinder.key(10, 11)), "neighbor in all 4 directions");
        assertTrue(reached.contains(RoomFinder.key(11, 11)),
                "diagonally-reached walkable cell (via 2-hop path)");
    }

    @Test
    public void bboxClampPreventsLeakingPastPerimeterDoorway() {
        // Mimics KeepEntryChamberStamper's use case: bound the flood to a
        // building's leaf bbox so the flood can't leak out through the
        // building's perimeter doorway and pollute the bbox-relative
        // membership test.
        NavigationGrid grid = openGrid();
        // Pretend bbox is (5,5)-(8,8). Mark cells outside the bbox as
        // walkable (open grid already is) — without the bbox bound, the
        // flood would visit them.
        int[] bbox = {5, 5, 8, 8};

        List<int[]> result = RoomFinder.flood(grid, 6, 6,
                Integer.MAX_VALUE, bbox);
        Set<Long> reached = keysOf(result);

        // Bbox-interior cells reached.
        assertTrue(reached.contains(RoomFinder.key(5, 5)), "bbox corner reached");
        assertTrue(reached.contains(RoomFinder.key(8, 8)), "opposite corner reached");
        // Out-of-bbox cells skipped.
        assertFalse(reached.contains(RoomFinder.key(4, 6)),
                "left of bbox skipped");
        assertFalse(reached.contains(RoomFinder.key(9, 6)),
                "right of bbox skipped");
        assertFalse(reached.contains(RoomFinder.key(6, 4)),
                "above bbox skipped");
    }

    @Test
    public void emptyResultOnNullGrid() {
        // Defensive: caller hands us a null grid. Return empty rather than
        // NPE — same shape as other defensive nav helpers.
        List<int[]> result = RoomFinder.flood(null, 0, 0, 5, null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void toMembershipBuildsLookupSet() {
        // Membership helper — convert a flood result into an O(1)-lookup
        // Set<Long> for "is this cell in the room" tests.
        NavigationGrid grid = openGrid();
        List<int[]> result = RoomFinder.flood(grid, 10, 10, 1, null);
        Set<Long> members = RoomFinder.toMembership(result);

        assertEquals(5, members.size(),
                "radius-1 diamond from (10,10) has 5 cells");
        assertTrue(members.contains(RoomFinder.key(10, 10)));
        assertTrue(members.contains(RoomFinder.key(11, 10)));
        assertFalse(members.contains(RoomFinder.key(12, 10)),
                "membership matches the flood — cells outside the flood are not members");
    }
}
