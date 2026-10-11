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
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class MainStoresFittingTest {
    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors) implements FurnishableRoom {}
    private record Fixture(GenContext ctx, RoomFloor floor, List<LayoutOp> ops) {}
    private static Fixture fit(RoomPose pose, int w, int h, int doorX, int doorY) {
        RoomShape shape = RoomShape.rectangle(w, h).posed(pose);
        int width = shape.width() + 8, height = shape.height() + 8;
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        GenContext ctx = new GenContext(grid, new CellTopology(width, height), new Random(7), width, height, 7);
        int[] door = pose.map(doorX, doorY, w, h);
        RoomFloor floor = new RoomFloor(ctx, new Room(shape, 4, 4, pose, RoomPurpose.STOCKROOM,
                List.of(new Doorway(4 + door[0], 4 + door[1]))), RoomFit.STANDARD);
        List<LayoutOp> ops = new ArrayList<>(); floor.record(ops::add);
        RoomFittings.forShipRoom(RoomPurpose.STOCKROOM, RoomShape.rectangle(w, h), RoomFit.STANDARD).fit(floor);
        return new Fixture(ctx, floor, ops);
    }

    @Test void banksPalletsAndWorkFollowAllEightPosesWithClearHandlingAisles() {
        for (RoomPose pose : RoomPose.all()) {
            Fixture f = fit(pose, 18, 12, 14, -1);
            assertEquals(4, pieces(f, "rack"));
            assertEquals(3, pieces(f, "pallet"));
            assertEquals(1, tasks(f, Affordance.READOUT));
            assertEquals(1, tasks(f, Affordance.REPAIR));
            assertEquals(15, tasks(f, Affordance.STOW));
            verify(f);
            for (int y : new int[]{1,2,9,10}) for (int x = 0; x < 16; x++) walkable(f, x, y);
            for (int x : new int[]{2,3,6,7,10,11,14,15})
                for (int y = 3; y < 9; y++) walkable(f, x, y);
            // Empty stock slots remain solid shelves, not a path through the bank.
            int[] empty = f.floor.toLocal(8, 5);
            assertFalse(f.ctx.grid.isWalkable(empty[0] + 4, empty[1] + 4));
        }
    }

    @Test void hatchesYieldWholeAssembliesWithoutStrandedWork() {
        for (RoomPose pose : RoomPose.all())
            for (int[] door : new int[][]{{0,-1},{5,-1},{0,12},{9,12},{-1,4},{-1,8},{18,4},{18,9}}) {
                Fixture f = fit(pose, 18, 12, door[0], door[1]);
                assertTrue(pieces(f, "rack") >= 3);
                assertEquals(1, tasks(f, Affordance.READOUT));
                assertEquals(1, tasks(f, Affordance.REPAIR));
                long frames = f.ctx.doodads.stream().filter(d -> "main-stores.frame".equals(d.definitionId())).count();
                assertEquals(pieces(f, "rack"), frames);
                verify(f);
                int x = Math.max(0, Math.min(17, door[0]));
                int y = Math.max(0, Math.min(11, door[1]));
                walkable(f, x, y);
            }
    }

    @Test void utilityStoresKeepWorkedStockAroundAnyHatchSide() {
        for (RoomPose pose : RoomPose.all())
            for (int[] door : new int[][]{{1,-1},{1,4},{-1,0},{4,0},{-1,2},{4,2}}) {
                Fixture f = fit(pose, 4, 4, door[0], door[1]);
                assertTrue(pieces(f, "pallet") > 0, pose + " door=" + door[0] + "," + door[1]);
                assertTrue(tasks(f, Affordance.STOW) > 0);
                verify(f);
            }
    }

    @Test void frameOpeningsAndStockMarginsPreserveTheLayerContract() throws Exception {
        var image = ImageIO.read(Path.of("mod/graphics/tilesets/main-stores.png").toFile());
        var frame = TileRegistry.installed().doodad("main-stores.frame");
        for (int slot = 0; slot < 3; slot++) for (int y = 35; y < 90; y++)
            for (int x = 30; x < 98; x++)
                assertTrue((image.getRGB(frame.col * 64 + x, frame.row * 64 + slot * 128 + y) >>> 24) <= 2);
        for (String id : List.of("cartons", "cases", "drums", "frame")) {
            var def = TileRegistry.installed().doodad("main-stores." + id);
            assertEquals(0, def.cover.level()); assertEquals(0f, def.ballisticHalfHeight);
            if (id.equals("frame")) continue;
            for (int y = 0; y < 128; y++) for (int x : new int[]{3,7,120,124})
                assertTrue((image.getRGB(def.col * 64 + x, def.row * 64 + y) >>> 24) <= 2);
        }
    }

    private static void verify(Fixture f) {
        assertTrue(f.floor.circulationSurvives());
        assertEquals(0, f.floor.dropUnreachableWork());
        f.floor.seal();
        for (var task : f.ctx.fixtureTasks) {
            assertTrue(f.ctx.grid.isWalkable(task.cellX(), task.cellY()));
            assertTrue(f.ctx.topology.isFixture(task.fixtureX(), task.fixtureY()));
        }
    }
    private static void walkable(Fixture f, int x, int y) {
        int[] cell = f.floor.toLocal(x,y);
        assertTrue(f.ctx.grid.isWalkable(cell[0] + 4, cell[1] + 4), x + "," + y);
    }
    private static long pieces(Fixture f, String id) {
        return f.ops.stream().filter(op -> op instanceof LayoutOp.Assembly a && a.baseId().equals("main-stores." + id)).count();
    }
    private static long tasks(Fixture f, Affordance work) {
        return f.ctx.fixtureTasks.stream().filter(t -> t.affordance() == work).count();
    }
}
