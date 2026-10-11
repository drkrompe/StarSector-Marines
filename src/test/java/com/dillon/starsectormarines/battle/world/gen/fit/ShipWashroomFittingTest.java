package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ShipWashroomFittingTest {
    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors) implements FurnishableRoom {}
    private record Fixture(GenContext ctx, RoomFloor floor, List<LayoutOp> ops) {}

    private static Fixture fit(RoomShape canonical, RoomPose pose, List<int[]> doors) {
        RoomShape shape = canonical.posed(pose);
        int w = shape.width() + 8, h = shape.height() + 8;
        NavigationGrid grid = new NavigationGrid(w, h);
        for (int y = 0; y < shape.height(); y++) for (int x = 0; x < shape.width(); x++)
            if (shape.contains(x, y)) grid.setWalkableFloor(x + 4, y + 4);
        List<Doorway> entrances = new ArrayList<>();
        for (int[] d : doors) {
            int[] cell = pose.map(d[0], d[1], canonical.width(), canonical.height());
            entrances.add(new Doorway(cell[0] + 4, cell[1] + 4));
            grid.setWalkableFloor(cell[0] + 4, cell[1] + 4);
        }
        GenContext ctx = new GenContext(grid, new CellTopology(w, h), new Random(7), w, h, 7);
        RoomFloor floor = new RoomFloor(ctx,
                new Room(shape, 4, 4, pose, RoomPurpose.WASHROOM, entrances), RoomFit.STANDARD);
        List<LayoutOp> ops = new ArrayList<>(); floor.record(ops::add);
        RoomFittings.forShipRoom(RoomPurpose.WASHROOM, canonical, RoomFit.STANDARD).fit(floor);
        return new Fixture(ctx, floor, ops);
    }

    @Test void everyPoseAndPerimeterHatchRetainsAllThreeKindsOfFixture() {
        RoomShape shape = RoomShape.rectangle(6, 5);
        for (RoomPose pose : RoomPose.all()) for (int[] door : shape.doorways())
            verify(fit(shape, pose, List.of(door)));
    }

    @Test void wideAndSimultaneousHatchesKeepTheirEntireApproaches() {
        for (RoomPose pose : RoomPose.all()) {
            verify(fit(RoomShape.rectangle(6, 5), pose,
                    List.of(new int[]{2, -1}, new int[]{3, -1}, new int[]{2, 5}, new int[]{3, 5})));
            verify(fit(RoomShape.rectangle(6, 5), pose,
                    List.of(new int[]{2, -1}, new int[]{3, 5}, new int[]{-1, 2}, new int[]{6, 2})));
        }
    }

    @Test void smallAndGroundWashroomsKeepTheirExistingFitting() {
        assertInstanceOf(WashroomFitting.class, RoomFittings.forPurpose(RoomPurpose.WASHROOM));
        Fixture small = fit(RoomShape.rectangle(3, 4), RoomPose.CANONICAL, List.of(new int[]{-1, 2}));
        assertTrue(small.ctx.doodads.stream().noneMatch(d -> d.definitionId().startsWith("ship-washrooms.")));
    }

    private static void verify(Fixture f) {
        for (String id : List.of("sink", "toilet", "shower"))
            assertTrue(f.ctx.doodads.stream().anyMatch(d -> d.definitionId().equals("ship-washrooms." + id)), id);
        long fixtures = f.ops.stream().filter(op -> op instanceof LayoutOp.Assembly).count();
        assertTrue(fixtures >= 8);
        assertEquals(fixtures, f.ctx.fixtureTasks.size(), "one exclusive wash position per fixture");
        assertTrue(f.floor.circulationSurvives());
        assertEquals(0, f.floor.dropUnreachableWork());
        f.floor.seal();
        for (int y = 1; y <= 2; y++) for (int x = 0; x < 6; x++) {
            int[] c = f.floor.toLocal(x, y);
            assertTrue(f.ctx.grid.isWalkable(c[0] + 4, c[1] + 4), "two-person aisle");
        }
        for (Doorway door : f.floor.localDoors()) {
            int[] d = f.floor.toCanonical(door.x(), door.y());
            int x = Math.max(0, Math.min(5, d[0]));
            int y = Math.max(0, Math.min(4, d[1]));
            int[] c = f.floor.toLocal(x, y);
            assertTrue(f.ctx.grid.isWalkable(c[0] + 4, c[1] + 4), "whole hatch approach");
        }
        HashSet<String> stands = new HashSet<>();
        for (var task : f.ctx.fixtureTasks) {
            assertTrue(stands.add(task.cellX() + "," + task.cellY()));
            assertEquals(Affordance.WASH, task.affordance());
            assertTrue(f.ctx.grid.isWalkable(task.cellX(), task.cellY()));
            assertFalse(f.ctx.grid.isWalkable(task.fixtureX(), task.fixtureY()));
            assertEquals(1, Math.abs(task.cellX() - task.fixtureX()) + Math.abs(task.cellY() - task.fixtureY()));
        }
        for (int y = 0; y < 5; y++) for (int x = 0; x < 6; x++) {
            int[] c = f.floor.toLocal(x, y);
            assertEquals("ship-washrooms.deck", f.ctx.topology.getSurfaceId(c[0] + 4, c[1] + 4));
        }
        for (var d : f.ctx.doodads) {
            assertEquals(f.floor.pose().quarterTurns(), d.quarterTurns);
            assertEquals(f.floor.pose().mirrored(), d.mirrored);
        }
    }
}
