package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The airbase lot: berths on the apron, sheds behind them, a fence with a way
 * in, and a runway along the front.
 *
 * <p>Both traversal axes on every test, because the lot has a front and a back
 * and every piece of it is placed relative to those. A mirrored layout that
 * puts a shed's mouth against its own fence, or a berth outside the gate, is
 * exactly the class of error that survives looking at one orientation.
 */
class AirbaseLotTest {

    private static final int MARGIN = 6;

    /** A blank map with the lot authored into the middle of it. */
    private record Lot(NavigationGrid grid, CellTopology topology, GenContext ctx,
                       int left, int bottom, int right, int top) {}

    private static Lot author(TraversalAxis axis) {
        int spanX = AirbaseLot.spanX(axis);
        int spanY = AirbaseLot.spanY(axis);
        int w = spanX + MARGIN * 2;
        int h = spanY + MARGIN * 2;
        NavigationGrid grid = new NavigationGrid(w, h);
        CellTopology topology = new CellTopology(w, h);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.DIRT);
                topology.setRoomPurpose(x, y, RoomPurpose.GENERIC);
            }
        }
        GenContext ctx = new GenContext(grid, topology, new Random(1L), w, h, 1L);
        int left = MARGIN;
        int bottom = MARGIN;
        new AirbaseLot(left, bottom, left + spanX - 1, bottom + spanY - 1, axis)
                .author(ctx, new Random(1L));
        return new Lot(grid, topology, ctx, left, bottom,
                left + spanX - 1, bottom + spanY - 1);
    }

    private static List<LandingPad> berths(Lot lot) {
        return lot.ctx().landingPads.stream()
                .filter(pad -> pad.purpose == LandingPad.Purpose.GARRISON_AIRFIELD)
                .toList();
    }

    /**
     * Every berth is clear, and every berth can be walked to from off the lot.
     *
     * <p>The two halves catch different things. Clear is the berth law —
     * something has to be able to land there. Reachable is the one that has
     * actually broken: a shed set flush against the edge of its own paving
     * opens onto the far side of the fence, and a crew then has to leave the
     * base and come back in to board an aircraft parked in the middle of it.
     */
    @ParameterizedTest
    @EnumSource(value = TraversalAxis.class, names = { "SOUTH_TO_NORTH", "WEST_TO_EAST" })
    void everyBerthIsClearAndReachableFromOutside(TraversalAxis axis) {
        Lot lot = author(axis);
        List<LandingPad> berths = berths(lot);
        assertEquals(3, berths.size(), axis + ": three berths on the apron");

        boolean[][] reached = flood(lot.grid(), 0, 0);
        for (LandingPad pad : berths) {
            for (int x = pad.left(); x <= pad.right(); x++) {
                for (int y = pad.bottom(); y <= pad.top(); y++) {
                    assertTrue(lot.grid().isWalkable(x, y), axis + ": berth cell "
                            + x + "," + y + " is not clear");
                }
            }
            assertTrue(reached[pad.centerX][pad.centerY], axis + ": the berth at "
                    + pad.centerX + "," + pad.centerY + " cannot be walked to from off the lot");
        }
    }

    /**
     * The berths are on the apron, not inside the sheds.
     *
     * <p>A hangar is where an aircraft is worked on; a berth is where one waits
     * to fly. Putting a berth indoors makes its crew walk through a building to
     * board, and hides the aircraft from the fight the airfield is supposed to
     * be part of.
     */
    @ParameterizedTest
    @EnumSource(value = TraversalAxis.class, names = { "SOUTH_TO_NORTH", "WEST_TO_EAST" })
    void noBerthIsInsideAShed(TraversalAxis axis) {
        Lot lot = author(axis);
        for (LandingPad pad : berths(lot)) {
            assertTrue(openToTheSky(lot, pad.centerX, pad.centerY), axis
                    + ": the berth at " + pad.centerX + "," + pad.centerY
                    + " is walled in — berths belong on the apron");
        }
    }

    /** Whether a straight run in some direction leaves the lot without meeting a wall. */
    private static boolean openToTheSky(Lot lot, int cx, int cy) {
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] step : steps) {
            boolean clear = true;
            for (int i = 1; i <= AirbaseLot.DEPTH; i++) {
                int x = cx + step[0] * i;
                int y = cy + step[1] * i;
                if (x < lot.left() || x > lot.right() || y < lot.bottom() || y > lot.top()) break;
                if (lot.topology().isWall(x, y)) { clear = false; break; }
            }
            if (clear) return true;
        }
        return false;
    }

    /**
     * The sheds are buildings: walled, and open only where they are meant to be.
     *
     * <p>Without walls a hangar is differently-coloured paint. The count is the
     * cheapest honest assertion that they exist as structures at all.
     */
    @ParameterizedTest
    @EnumSource(value = TraversalAxis.class, names = { "SOUTH_TO_NORTH", "WEST_TO_EAST" })
    void theShedsAreWalledAndWorked(TraversalAxis axis) {
        Lot lot = author(axis);
        int walls = 0;
        for (int x = lot.left(); x <= lot.right(); x++) {
            for (int y = lot.bottom(); y <= lot.top(); y++) {
                if (lot.topology().isWall(x, y)) walls++;
            }
        }
        assertTrue(walls > 40, axis + ": two sheds should carry a wall ring each, found "
                + walls + " wall cells");

        // And they have kit in them. A shed with nothing in it is a box.
        long inside = lot.ctx().doodads.stream()
                .filter(d -> insideAShed(lot, d.cellX, d.cellY))
                .count();
        assertTrue(inside >= 4, axis + ": the sheds should be worked, found "
                + inside + " pieces of kit inside them");
    }

    /** Whether this cell sits in a walled pocket rather than out on the apron. */
    private static boolean insideAShed(Lot lot, int x, int y) {
        return !openToTheSky(lot, x, y);
    }

    /**
     * The fence encloses the lot and still lets people in.
     *
     * <p>A ring with no gate is a pocket. The flood from outside reaching a
     * berth already proves a way in exists; this pins that the ring is
     * otherwise closed, so the gate is a gate rather than a gap in a line of
     * posts.
     */
    @ParameterizedTest
    @EnumSource(value = TraversalAxis.class, names = { "SOUTH_TO_NORTH", "WEST_TO_EAST" })
    void theFenceEnclosesTheLotAndHasAWayIn(TraversalAxis axis) {
        Lot lot = author(axis);
        int posts = 0;
        int open = 0;
        for (int x = lot.left(); x <= lot.right(); x++) {
            for (int y = lot.bottom(); y <= lot.top(); y++) {
                boolean ring = x == lot.left() || x == lot.right()
                        || y == lot.bottom() || y == lot.top();
                if (!ring) continue;
                if (lot.grid().isWalkable(x, y)) open++;
                else posts++;
            }
        }
        assertTrue(posts > open * 4, axis + ": the perimeter should be mostly fence — "
                + posts + " posts against " + open + " open cells");
        assertTrue(open >= 4, axis + ": a fence with no gate makes the lot a pocket");
    }

    /** Cells reachable on foot from {@code (x, y)}. */
    private static boolean[][] flood(NavigationGrid grid, int x, int y) {
        boolean[][] seen = new boolean[grid.getWidth()][grid.getHeight()];
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{x, y});
        seen[x][y] = true;
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            for (int[] step : steps) {
                int nx = cell[0] + step[0];
                int ny = cell[1] + step[1];
                if (nx < 0 || ny < 0 || nx >= grid.getWidth() || ny >= grid.getHeight()) continue;
                if (seen[nx][ny] || !grid.isWalkable(nx, ny)) continue;
                seen[nx][ny] = true;
                queue.add(new int[]{nx, ny});
            }
        }
        return seen;
    }
}
