package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ShipMessFittingTest {
    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors) implements FurnishableRoom {}
    private record Fixture(GenContext ctx, RoomFloor floor, List<LayoutOp> ops) {}

    private static Fixture fit(RoomPose pose, int width, int height, int doorX, int doorY) {
        RoomShape canonical = RoomShape.rectangle(width, height);
        RoomShape shape = canonical.posed(pose);
        int w = shape.width() + 8, h = shape.height() + 8;
        NavigationGrid grid = new NavigationGrid(w, h);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) grid.setWalkableFloor(x, y);
        GenContext ctx = new GenContext(grid, new CellTopology(w, h), new Random(7), w, h, 7);
        int[] door = pose.map(doorX, doorY, width, height);
        RoomFloor floor = new RoomFloor(ctx, new Room(shape, 4, 4, pose, RoomPurpose.MESS_HALL,
                List.of(new Doorway(4 + door[0], 4 + door[1]))), RoomFit.STANDARD);
        List<LayoutOp> ops = new ArrayList<>(); floor.record(ops::add);
        RoomFittings.forShipRoom(RoomPurpose.MESS_HALL, canonical, RoomFit.STANDARD).fit(floor);
        return new Fixture(ctx, floor, ops);
    }

    @Test void everyPoseKeepsSeatsAndGalleyWorkReachableAndSeparate() {
        for (RoomPose pose : RoomPose.all()) {
            Fixture f = fit(pose, 22, 12, 20, -1);
            assertTrue(f.floor.circulationSurvives(), pose.toString());
            assertEquals(0, f.floor.dropUnreachableWork());
            long tables = f.ops.stream().filter(op -> op instanceof LayoutOp.Assembly a
                    && a.baseId().equals("mess-galley.table")).count();
            assertEquals(9, tables);
            assertEquals(6 * tables, count(f, Affordance.MESS));
            assertTrue(count(f, Affordance.COOK) > 0);
            assertTrue(count(f, Affordance.STOW) > 0);
            assertEquals(count(f, Affordance.MESS), f.ops.stream().filter(op -> op instanceof LayoutOp.Paving p
                    && p.doodadId().equals("mess-galley.stool")).count());
            f.floor.seal();
            var occupied = new HashSet<String>();
            for (var task : f.ctx.fixtureTasks) {
                assertTrue(occupied.add(task.cellX() + "," + task.cellY()), "one occupant per activity cell");
                assertTrue(f.ctx.grid.isWalkable(task.cellX(), task.cellY()));
                assertTrue(f.ctx.topology.isFixture(task.fixtureX(), task.fixtureY()));
                int[] canonical = f.floor.toCanonical(task.cellX() - 4, task.cellY() - 4);
                if (task.affordance() == Affordance.MESS) {
                    assertTrue(canonical[0] >= 9);
                    assertEquals(1, Math.abs(task.cellX() - task.fixtureX())
                            + Math.abs(task.cellY() - task.fixtureY()), "diners face their own place at table");
                } else assertTrue(canonical[0] < 6, "galley work stays behind the servery");
            }
            // The aligned staff opening connects both working aisles to the public apron.
            for (int x = 1; x <= 7; x++) for (int y = 5; y <= 6; y++) {
                int[] cell = f.floor.toLocal(x, y);
                assertTrue(f.ctx.grid.isWalkable(cell[0] + 4, cell[1] + 4));
            }
        }
    }

    @Test void fallbackHatchesOmitWholeAppliancesWithoutStrandingWork() {
        for (RoomPose pose : RoomPose.all()) for (int[] door : new int[][]{{-1,6},{4,-1},{4,12},{22,6}}) {
            Fixture f = fit(pose, 22, 12, door[0], door[1]);
            assertTrue(f.floor.circulationSurvives());
            assertEquals(0, f.floor.dropUnreachableWork());
            assertTrue(count(f, Affordance.MESS) > 0);
            assertTrue(count(f, Affordance.COOK) > 0);
            f.floor.seal();
            for (var task : f.ctx.fixtureTasks)
                assertTrue(f.ctx.grid.isWalkable(task.cellX(), task.cellY()));
            int[] approach = f.floor.toLocal(Math.max(0, Math.min(21, door[0])),
                    Math.max(0, Math.min(11, door[1])));
            assertTrue(f.ctx.grid.isWalkable(approach[0] + 4, approach[1] + 4));
        }
    }

    @Test void smallDiningRoomDoesNotInventAGalleyPosting() {
        for (RoomPose pose : RoomPose.all()) {
            Fixture f = fit(pose, 9, 8, 8, -1);
            assertTrue(f.floor.circulationSurvives());
            assertEquals(0, f.floor.dropUnreachableWork());
            assertTrue(count(f, Affordance.MESS) > 0);
            assertEquals(0, count(f, Affordance.COOK));
            assertEquals(0, count(f, Affordance.STOW));
        }
    }

    @Test void mealOverlayKeepsTheMiddleOfTheTableBare() throws Exception {
        var image = ImageIO.read(Path.of("mod/graphics/tilesets/mess-galley.png").toFile());
        var meals = TileRegistry.installed().doodad("mess-galley.meals");
        for (int y = 4; y < 60; y++) for (int x = 84; x < 112; x++)
            assertTrue((image.getRGB(meals.col * 64 + x, meals.row * 64 + y) >>> 24) <= 2);
        for (String id : List.of("meals", "stool")) {
            var def = TileRegistry.installed().doodad("mess-galley." + id);
            assertEquals(0, def.cover.level());
            assertEquals(0f, def.ballisticHalfHeight);
        }
    }

    private static long count(Fixture f, Affordance affordance) {
        return f.ctx.fixtureTasks.stream().filter(t -> t.affordance() == affordance).count();
    }
}
