package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

/** The cruise-ship gymnasium in every pose and behind hatches on every side: track, floor and pool all hold. */
class GrandGymFittingTest {
    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors) implements FurnishableRoom {}
    private record Fitted(GenContext ctx, RoomFloor floor) {}

    private static Fitted fit(RoomPose pose, List<int[]> doors) {
        RoomShape canonical = RoomShape.rectangle(GrandGymFitting.WIDTH, GrandGymFitting.HEIGHT);
        RoomShape shape = canonical.posed(pose);
        int w = shape.width() + 8, h = shape.height() + 8;
        NavigationGrid grid = new NavigationGrid(w, h);
        for (int y = 0; y < shape.height(); y++) for (int x = 0; x < shape.width(); x++) grid.setWalkableFloor(x + 4, y + 4);
        List<Doorway> entrances = new ArrayList<>();
        for (int[] d : doors) {
            int[] cell = pose.map(d[0], d[1], canonical.width(), canonical.height());
            entrances.add(new Doorway(cell[0] + 4, cell[1] + 4));
            grid.setWalkableFloor(cell[0] + 4, cell[1] + 4);
        }
        GenContext ctx = new GenContext(grid, new CellTopology(w, h), new Random(7), w, h, 7);
        RoomFloor floor = new RoomFloor(ctx, new Room(shape, 4, 4, pose, RoomPurpose.GYMNASIUM, entrances), RoomFit.STANDARD);
        RoomFittings.forShipRoom(RoomPurpose.GYMNASIUM, canonical, RoomFit.STANDARD).fit(floor);
        return new Fitted(ctx, floor);
    }

    @Test void everyPoseAndHatchKeepsTheTrackTheFloorAndThePool() {
        for (RoomPose pose : RoomPose.all()) {
            verify(fit(pose, List.of(new int[]{14, -1})));
            verify(fit(pose, List.of(new int[]{-1, 7}, new int[]{28, 7}, new int[]{6, 14})));
        }
    }

    private static void verify(Fitted f) {
        long exercise = f.ctx.fixtureTasks.stream().filter(t -> t.affordance() == Affordance.EXERCISE).count();
        long unwind = f.ctx.fixtureTasks.stream().filter(t -> t.affordance() == Affordance.UNWIND).count();
        long laps = f.ctx.fixtureTasks.stream().filter(t -> t.affordance() == Affordance.LAP).count();
        assertEquals(16, exercise);
        assertEquals(3, unwind);
        assertEquals(12, laps, "a lap marker at each corner of the track and along its straights");
        assertTrue(f.floor.circulationSurvives());
        assertEquals(0, f.floor.dropUnreachableWork());
        f.floor.seal();
        int water = 0;
        for (int y = 0; y < GrandGymFitting.HEIGHT; y++) for (int x = 0; x < GrandGymFitting.WIDTH; x++) {
            int[] c = f.floor.toLocal(x, y);
            int gx = c[0] + 4, gy = c[1] + 4;
            boolean ring = x < 2 || y < 2 || x >= GrandGymFitting.WIDTH - 2 || y >= GrandGymFitting.HEIGHT - 2;
            if (ring) {
                assertEquals("ship-gym-track.track", f.ctx.topology.getSurfaceId(gx, gy), x + "," + y);
                assertTrue(f.ctx.grid.isWalkable(gx, gy), "track " + x + "," + y);
            }
            if (f.ctx.topology.isWater(gx, gy)) {
                water++;
                assertFalse(f.ctx.grid.isWalkable(gx, gy));
                assertTrue(f.ctx.grid.isSeeThrough(gx, gy));
            }
        }
        assertEquals(40, water, "a ten by four basin");
        HashSet<String> stands = new HashSet<>();
        for (var task : f.ctx.fixtureTasks) {
            // The track is run, not stood on: its markers are on it and nothing else is.
            int[] c = canonical(f.floor, task.cellX() - 4, task.cellY() - 4);
            boolean ring = c[0] < 2 || c[1] < 2 || c[0] >= GrandGymFitting.WIDTH - 2 || c[1] >= GrandGymFitting.HEIGHT - 2;
            assertEquals(task.affordance() == Affordance.LAP, ring, task.affordance() + " at " + c[0] + "," + c[1]);
            assertTrue(stands.add(task.cellX() + "," + task.cellY()));
            assertTrue(f.ctx.grid.isWalkable(task.cellX(), task.cellY()));
            assertFalse(f.ctx.topology.isWater(task.cellX(), task.cellY()));
        }
    }

    /** The canonical cell a local one is, found by asking the floor's own mapping the other way. */
    private static int[] canonical(RoomFloor floor, int localX, int localY) {
        for (int y = 0; y < GrandGymFitting.HEIGHT; y++) for (int x = 0; x < GrandGymFitting.WIDTH; x++) {
            int[] c = floor.toLocal(x, y);
            if (c[0] == localX && c[1] == localY) return new int[]{x, y};
        }
        throw new AssertionError("outside the room: " + localX + "," + localY);
    }
}
