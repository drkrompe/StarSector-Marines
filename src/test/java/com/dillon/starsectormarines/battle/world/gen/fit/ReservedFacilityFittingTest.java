package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The compact fittings Second Wind's refit compartments are furnished with,
 * each at its reserved footprint, in every pose and behind a two-cell hatch
 * at every place on its bulkhead the deck could cut one: the fill keeps its
 * circulation, every place it publishes can be walked to from the hatch, and
 * it publishes the work its room is for.
 */
class ReservedFacilityFittingTest {
    private static final int MARGIN = 3;

    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors) implements FurnishableRoom {}

    private record Fitted(GenContext ctx, RoomFloor floor, Room room) {}

    @Test void theShortRangeKeepsThreeLanesAndItsReadyEnd() {
        RoomShape shape = RoomShape.rectangle(16, 6);
        for (RoomPose pose : RoomPose.all())
            for (Hookup hookup : FiringRangeFitting.shipboard().hookups(shape))
                for (Hookup.DoorSlot slot : hookup.slots()) {
                    Fitted f = fit(16, 6, RoomPurpose.FIRING_RANGE, FiringRangeFitting.shipboard(), pose,
                            slot.cells().toArray(new int[0][]));
                    String where = "range pose " + pose + " hatch " + slot.cells().get(0)[0] + "," + slot.cells().get(0)[1];
                    assertTrue(f.floor.circulationSurvives(), where);
                    assertEquals(0, f.floor.dropUnreachableWork(), where);
                    f.floor.seal();
                    Set<Long> reached = reached(f);
                    long lanes = 0;
                    for (FixtureTask task : f.ctx.fixtureTasks) {
                        if (task.affordance() != Affordance.PRACTICE) continue;
                        lanes++;
                        assertTrue(reached.contains(key(task.cellX(), task.cellY())), where + ": a firing point is walled off");
                        int dx = Integer.signum(task.fixtureX() - task.cellX()), dy = Integer.signum(task.fixtureY() - task.cellY());
                        for (int x = task.cellX() + dx, y = task.cellY() + dy; ; x += dx, y += dy) {
                            assertFalse(f.ctx.grid.isWalkable(x, y), where + ": lane open at " + x + "," + y);
                            assertTrue(f.ctx.grid.isSeeThrough(x, y), where + ": lane blind at " + x + "," + y);
                            if (x == task.fixtureX() && y == task.fixtureY()) break;
                        }
                    }
                    assertEquals(3, lanes, where);
                    // A hatch in the end bulkhead leaves the side walls to the gear;
                    // one in a side wall may take the only run long enough for it.
                    if (slot.cells().get(0)[0] < 0)
                        assertTrue(f.ctx.doodads.stream().anyMatch(d -> d.definitionId().equals("firing-range.control")),
                                where + ": no range control at the ready end");
                }
    }

    @Test void theGymKeepsItsLoopAndItsEquipment() {
        everyHatch(12, 8, RoomPurpose.GYMNASIUM, TrackGymFitting::new, Affordance.EXERCISE, 7);
    }

    @Test void theSickBayKeepsItsBedsBesideTheRehabPool() {
        everyHatch(10, 6, RoomPurpose.PATIENT_WARD, RehabSickBayFitting::new, Affordance.TREAT, 4);
        Fitted f = fit(10, 6, RoomPurpose.PATIENT_WARD, new RehabSickBayFitting(), new RoomPose(0, false), new int[]{-1, 2});
        f.floor.seal();
        int water = 0;
        for (int y = 0; y < f.ctx.height; y++) for (int x = 0; x < f.ctx.width; x++) {
            if (!f.ctx.topology.isWater(x, y)) continue;
            water++;
            assertFalse(f.ctx.grid.isWalkable(x, y));
            assertTrue(f.ctx.grid.isSeeThrough(x, y));
        }
        assertEquals(8, water, "a two-by-four basin");
        for (FixtureTask task : f.ctx.fixtureTasks) assertFalse(f.ctx.topology.isWater(task.cellX(), task.cellY()));
    }

    @Test void theLoungeKeepsItsChapelAtTheQuietEnd() {
        everyHatch(8, 6, RoomPurpose.CREW_LOUNGE, ChapelLoungeFitting::new, Affordance.UNWIND, 6);
        // A hatch at one end puts the pews at the other.
        Fitted west = fit(8, 6, RoomPurpose.CREW_LOUNGE, new ChapelLoungeFitting(), new RoomPose(0, false), new int[]{-1, 3});
        Fitted east = fit(8, 6, RoomPurpose.CREW_LOUNGE, new ChapelLoungeFitting(), new RoomPose(0, false), new int[]{8, 3});
        assertTrue(pewEnd(west) > 4 && pewEnd(east) < 4, "pews at " + pewEnd(west) + " and " + pewEnd(east));
    }

    @Test void theBrigPostsOneGuardOutsideItsCells() {
        everyHatch(6, 6, RoomPurpose.BRIG, BrigFitting::new, Affordance.GUARD, 1);
        Fitted f = fit(6, 6, RoomPurpose.BRIG, new BrigFitting(), new RoomPose(0, false), new int[]{3, 6});
        f.floor.seal();
        assertEquals(1, f.ctx.fixtureTasks.stream().filter(t -> t.affordance() == Affordance.GUARD).count());
        assertTrue(f.ctx.fixtureTasks.stream().noneMatch(t -> t.affordance() == Affordance.REST), "nobody sleeps in a cell");
        // The cells are behind bars and a bulkhead: nothing walks into them.
        assertFalse(f.ctx.grid.isWalkable(MARGIN + 2, MARGIN), "the bulkhead between the cells");
    }

    private static int pewEnd(Fitted f) {
        return (int) f.ctx.doodads.stream().filter(d -> d.definitionId().equals("crew-lounge.sofa-high"))
                .mapToInt(d -> d.cellX - MARGIN).average().orElse(-1);
    }

    private static void everyHatch(int w, int h, RoomPurpose purpose, Supplier<RoomFitting> fitting,
                                   Affordance work, int least) {
        RoomShape shape = RoomShape.rectangle(w, h);
        for (RoomPose pose : RoomPose.all()) {
            for (int[] door : shape.doorways()) {
                Fitted f = fit(w, h, purpose, fitting.get(), pose, door, pair(door, w, h));
                String where = purpose + " pose " + pose + " hatch " + door[0] + "," + door[1];
                assertTrue(f.floor.circulationSurvives(), where + ": circulation severed");
                assertEquals(0, f.floor.dropUnreachableWork(), where + ": work nobody can reach");
                f.floor.seal();
                Set<Long> reached = reached(f);
                long count = 0;
                Set<Long> stands = new HashSet<>();
                for (FixtureTask task : f.ctx.fixtureTasks) {
                    assertTrue(stands.add(key(task.cellX(), task.cellY())), where + ": two places on one cell");
                    assertTrue(reached.contains(key(task.cellX(), task.cellY())),
                            where + ": " + task.affordance() + " at " + task.cellX() + "," + task.cellY() + " is walled off");
                    if (task.affordance() == work) count++;
                }
                assertTrue(count >= least, where + ": only " + count + " " + work);
            }
        }
    }

    /** The second cell of a two-wide hatch, along the bulkhead it is cut in. */
    private static int[] pair(int[] door, int w, int h) {
        boolean side = door[0] < 0 || door[0] >= w;
        int along = side ? door[1] : door[0];
        int limit = side ? h : w;
        int next = along + 1 < limit ? along + 1 : along - 1;
        return side ? new int[]{door[0], next} : new int[]{next, door[1]};
    }

    private static Fitted fit(int w, int h, RoomPurpose purpose, RoomFitting fitting, RoomPose pose, int[]... doors) {
        RoomShape canonical = RoomShape.rectangle(w, h);
        RoomShape shape = canonical.posed(pose);
        int gw = shape.width() + 2 * MARGIN, gh = shape.height() + 2 * MARGIN;
        NavigationGrid grid = new NavigationGrid(gw, gh);
        for (int y = 0; y < shape.height(); y++) for (int x = 0; x < shape.width(); x++)
            if (shape.contains(x, y)) grid.setWalkableFloor(x + MARGIN, y + MARGIN);
        List<Doorway> entrances = new ArrayList<>();
        for (int[] d : doors) {
            int[] cell = pose.map(d[0], d[1], canonical.width(), canonical.height());
            entrances.add(new Doorway(cell[0] + MARGIN, cell[1] + MARGIN));
            grid.setWalkableFloor(cell[0] + MARGIN, cell[1] + MARGIN);
        }
        GenContext ctx = new GenContext(grid, new CellTopology(gw, gh), new Random(7), gw, gh, 7);
        Room room = new Room(shape, MARGIN, MARGIN, pose, purpose, entrances);
        RoomFloor floor = new RoomFloor(ctx, room, RoomFit.STANDARD);
        fitting.fit(floor);
        return new Fitted(ctx, floor, room);
    }

    private static Set<Long> reached(Fitted f) {
        Set<Long> seen = new HashSet<>();
        var queue = new ArrayDeque<int[]>();
        for (Doorway door : f.room.doors()) if (seen.add(key(door.x(), door.y()))) queue.add(new int[]{door.x(), door.y()});
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = c[0] + step[0], y = c[1] + step[1];
                if (x < 0 || y < 0 || x >= f.ctx.width || y >= f.ctx.height) continue;
                if (!f.ctx.grid.isWalkable(x, y) || !seen.add(key(x, y))) continue;
                queue.add(new int[]{x, y});
            }
        }
        return seen;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }
}
