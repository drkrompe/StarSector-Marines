package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A shed the size of a motor pool is entered from more than one place, and the
 * further ways in cost it nothing.
 *
 * <p>A shed is entered from one face and cleared by holding one doorway, and
 * that is what the bay's further doors are for. Where they may go is the whole
 * question. A door is free on a stores aisle, because the gap between two bays
 * already runs a file from the outer bulkhead in to the service lane; anywhere
 * else along that wall is a bay, and a door into a bay is a hatch that costs a
 * berth.
 *
 * <p>Which makes this a claim about two pieces of arithmetic agreeing — where
 * the doors are stated, and where the fill opens its aisles — written in
 * different methods and read at different moments. Nothing about a room whose
 * doors had drifted off its aisles would look wrong: it would generate, it
 * would be walkable, and its side doors would open onto the flank of a gantry.
 *
 * <p>Asked of the fitting on a synthetic floor, because it is a fact about the
 * arrangement and not about any deck or yard that happens to hold one.
 */
class AMechShedHasSeveralWaysOutTest {

    /** The fortress shed, sized off the bay module exactly as the program sizes it. */
    private static final int SHED_BAYS = 3;
    private static final int SHED_LENGTH = VehicleBayFitting.VESTIBULE
            + SHED_BAYS * VehicleBayFitting.BAY_WIDTH
            + (SHED_BAYS - 1) * VehicleBayFitting.BAY_GAP
            + VehicleBayFitting.SHOP_WIDTH;
    private static final int SHED_DEPTH =
            2 * VehicleBayFitting.BAY_DEPTH + VehicleBayFitting.SERVICE_LANE;

    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors)
            implements FurnishableRoom { }

    /**
     * A bay's own circulation: every cell it reserved as lane, less the berths.
     *
     * <p>Berths are reserved too — a machine has to stand on clear deck — so
     * counting them as circulation would let a door onto the flank of a gantry
     * pass for a door onto the aisle, which is the one thing being asked about.
     */
    private record Circulation(boolean[][] open, int length, int depth) {

        boolean isOpen(int along, int across) {
            return along >= 0 && across >= 0 && along < length && across < depth
                    && open[along][across];
        }

        /** Every open cell reachable from {@code (along, across)} without leaving the lane. */
        boolean[][] reachedFrom(int along, int across) {
            boolean[][] seen = new boolean[length][depth];
            if (!isOpen(along, across)) return seen;
            Deque<int[]> frontier = new ArrayDeque<>();
            seen[along][across] = true;
            frontier.add(new int[]{ along, across });
            while (!frontier.isEmpty()) {
                int[] cell = frontier.poll();
                for (int[] step : new int[][]{ {1, 0}, {-1, 0}, {0, 1}, {0, -1} }) {
                    int nx = cell[0] + step[0];
                    int ny = cell[1] + step[1];
                    if (!isOpen(nx, ny) || seen[nx][ny]) continue;
                    seen[nx][ny] = true;
                    frontier.add(new int[]{ nx, ny });
                }
            }
            return seen;
        }
    }

    /**
     * A shed states a door on every aisle its own fill opens, on both flanks.
     *
     * <p>The count is the cheap half and it is here to name the shape: a
     * personnel door each side of every gap between two bays. The machine doors
     * at the vestibule are hookups rather than further doors — they are how the
     * room is entered, not somewhere a door may be added.
     */
    @Test
    void theShedAuthorsADoorOnEveryStoresAisle() {
        List<Hookup.DoorSlot> slots = furtherDoors(SHED_LENGTH, SHED_DEPTH);

        assertEquals(2 * (SHED_BAYS - 1), slots.size(),
                "a " + SHED_LENGTH + "-by-" + SHED_DEPTH + " shed with " + SHED_BAYS
                        + " bays to a rank states " + slots.size() + " further doors");
    }

    /**
     * Every door the shed states opens onto the shed's own circulation, and all
     * of them open onto the <em>same</em> circulation.
     *
     * <p>This is the claim the count cannot make. A door authored a cell off its
     * aisle lands on the flank of a gantry frame, which is not lane at all; one
     * authored onto a berth lands on deck that is reserved but is the machine's,
     * and leads nowhere. Both leave the room generating and walkable, and both
     * are a door onto the side of a bay.
     */
    @Test
    void everyAuthoredDoorOpensOntoTheSameCirculation() {
        assertDoorsAreServed(SHED_LENGTH, SHED_DEPTH);
    }

    /**
     * And on a deck's bay, which is longer and carries another rank of stores.
     *
     * <p>Two footprints rather than one because the aisles are derived from the
     * length: a shed and a ship's bay open a different number of them, and an
     * agreement that held only at the size it was written for would be an
     * agreement about one constant.
     */
    @Test
    void andOnALongerBayWithMoreAisles() {
        assertDoorsAreServed(40, 16);

        assertTrue(furtherDoors(40, 16).size()
                        > furtherDoors(SHED_LENGTH, SHED_DEPTH).size(),
                "a longer bay has no more ways in than a shorter one");
    }

    /**
     * The further doors cost no berth.
     *
     * <p>The whole reason they go on the aisles. A shed that answered "more ways
     * in" by cutting one through a rank would trade the thing the building is
     * for against the thing it was missing.
     */
    @Test
    void theFurtherDoorsCostNoBerth() {
        GenContext ctx = fit(SHED_LENGTH, SHED_DEPTH, null);

        assertEquals(2 * SHED_BAYS, ctx.gantries.size(),
                "the shed berths " + ctx.gantries.size() + " machines, not " + 2 * SHED_BAYS);
    }

    /** Fit the bay and assert each authored door sits on shared, open lane. */
    private static void assertDoorsAreServed(int length, int depth) {
        List<Hookup.DoorSlot> slots = furtherDoors(length, depth);
        List<int[]> thresholds = new ArrayList<>();
        for (Hookup.DoorSlot slot : slots) {
            for (int[] cell : slot.cells()) thresholds.add(inboard(cell, depth));
        }

        Circulation lanes = circulation(length, depth);
        for (int[] threshold : thresholds) {
            assertTrue(lanes.isOpen(threshold[0], threshold[1]),
                    "the door at " + threshold[0] + "," + threshold[1]
                            + " opens onto deck the fill did not leave clear");
        }

        int[] first = thresholds.get(0);
        boolean[][] reached = lanes.reachedFrom(first[0], first[1]);
        for (int[] threshold : thresholds) {
            assertTrue(reached[threshold[0]][threshold[1]],
                    "the door at " + threshold[0] + "," + threshold[1]
                            + " opens onto a pocket the rest of the bay cannot reach");
        }
    }

    /** The floor cell one step inside a doorway authored on the ring. */
    private static int[] inboard(int[] ringCell, int depth) {
        int across = ringCell[1] < 0 ? 0 : ringCell[1] >= depth ? depth - 1 : ringCell[1];
        return new int[]{ ringCell[0], across };
    }

    /** The doors the bay states may be added, beyond the ones it is entered by. */
    private static List<Hookup.DoorSlot> furtherDoors(int length, int depth) {
        return new VehicleBayFitting().furtherDoors(RoomShape.rectangle(length, depth));
    }

    /** The lane this bay reserves, less its berths. */
    private static Circulation circulation(int length, int depth) {
        boolean[][] open = new boolean[length][depth];
        fit(length, depth, op -> {
            if (op instanceof LayoutOp.Lane lane) {
                paint(open, lane.x(), lane.y(), lane.spanX(), lane.spanY(), true);
            } else if (op instanceof LayoutOp.Berth berth) {
                paint(open, berth.x(), berth.y(), berth.spanX(), berth.spanY(), false);
            }
        });
        return new Circulation(open, length, depth);
    }

    private static void paint(boolean[][] grid, int x, int y, int spanX, int spanY,
                              boolean value) {
        for (int dx = 0; dx < spanX; dx++) {
            for (int dy = 0; dy < spanY; dy++) {
                int cx = x + dx;
                int cy = y + dy;
                if (cx < 0 || cy < 0 || cx >= grid.length || cy >= grid[0].length) continue;
                grid[cx][cy] = value;
            }
        }
    }

    private static GenContext fit(int length, int depth, RoomFloor.Listener listener) {
        int mapWidth = length + 8;
        int mapHeight = depth + 8;
        NavigationGrid grid = new NavigationGrid(mapWidth, mapHeight);
        CellTopology topology = new CellTopology(mapWidth, mapHeight);
        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) grid.setWalkableFloor(x, y);
        }
        GenContext ctx = new GenContext(grid, topology, new Random(7L),
                mapWidth, mapHeight, 7L);
        Room room = new Room(RoomShape.rectangle(length, depth), 4, 4,
                RoomPose.CANONICAL, RoomPurpose.VEHICLE_BAY,
                List.of(new Doorway(4, 4 + depth / 2)));
        RoomFloor floor = new RoomFloor(ctx, room, RoomFit.STANDARD);
        if (listener != null) floor.record(listener);
        new VehicleBayFitting().fit(floor);
        return ctx;
    }
}
