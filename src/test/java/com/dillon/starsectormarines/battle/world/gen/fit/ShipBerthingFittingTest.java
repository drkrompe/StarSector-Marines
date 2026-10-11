package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class ShipBerthingFittingTest {
    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors) implements FurnishableRoom {}
    private record Fixture(GenContext ctx, RoomFloor floor) {}
    private static Fixture fit(RoomPose pose, RoomPurpose purpose, boolean ship, boolean rearDoor) {
        RoomShape shape = RoomShape.rectangle(8, 6).posed(pose);
        int w = shape.width() + 8, h = shape.height() + 8;
        NavigationGrid grid = new NavigationGrid(w, h);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) grid.setWalkableFloor(x, y);
        GenContext ctx = new GenContext(grid, new CellTopology(w, h), new Random(7), w, h, 7);
        List<Doorway> doors = new ArrayList<>();
        for (int x : new int[]{2, 3, 5, 6}) {
            int[] cell = pose.map(x, rearDoor ? 6 : -1, 8, 6);
            doors.add(new Doorway(4 + cell[0], 4 + cell[1]));
        }
        RoomFloor floor = new RoomFloor(ctx, new Room(shape, 4, 4, pose, purpose, doors), RoomFit.STANDARD);
        if (ship) RoomFittings.forShipRoom(purpose, RoomShape.rectangle(8, 6), RoomFit.STANDARD).fit(floor);
        else new BerthingFitting(purpose, "doodad.chest-1").fit(floor);
        return new Fixture(ctx, floor);
    }
    @Test void bunksKeepBerthCapacityAndReachableRestAcrossAllPoses() {
        for (RoomPurpose purpose : new RoomPurpose[]{RoomPurpose.BARRACKS, RoomPurpose.CREW_QUARTERS})
            for (RoomPose pose : RoomPose.all()) {
                Fixture f = fit(pose, purpose, true, false);
                Fixture prior = fit(pose, purpose, false, false);
                assertTrue(f.floor.circulationSurvives());
                assertEquals(0, f.floor.dropUnreachableWork());
                long rests = f.ctx.fixtureTasks.stream().filter(t -> t.affordance() == Affordance.REST).count();
                assertEquals(9, rests);
                assertEquals(prior.ctx.fixtureTasks.stream().filter(t -> t.affordance() == Affordance.REST).count(), rests);
                assertEquals(3, f.ctx.fixtureTasks.stream().filter(t -> t.affordance() == Affordance.STOW).count());
                assertEquals(30, f.ctx.doodads.size(), "nine three-layer bunks and three locker banks");
                f.floor.seal();
                for (var task : f.ctx.fixtureTasks) {
                    assertTrue(f.ctx.grid.isWalkable(task.cellX(), task.cellY()));
                    assertTrue(f.ctx.topology.isFixture(task.fixtureX(), task.fixtureY()));
                }
                for (int y : new int[]{2, 3}) for (int x = 0; x < 8; x++) {
                    int[] cell = f.floor.toLocal(x, y);
                    assertTrue(f.ctx.grid.isWalkable(4 + cell[0], 4 + cell[1]));
                }
            }
    }
    @Test void rearHatchesRemainClearWithoutOrphanBedding() {
        for (RoomPose pose : RoomPose.all()) {
            Fixture f = fit(pose, RoomPurpose.BARRACKS, true, true);
            assertTrue(f.floor.circulationSurvives());
            assertEquals(0, f.floor.dropUnreachableWork());
            f.floor.seal();
            for (int x : new int[]{2, 3, 5, 6}) for (int y : new int[]{4, 5}) {
                int[] cell = f.floor.toLocal(x, y);
                assertTrue(f.ctx.grid.isWalkable(4 + cell[0], 4 + cell[1]));
            }
        }
    }
    @Test void beddingRemainsVisibleThroughTheRetainingFrame() throws Exception {
        var image = ImageIO.read(Path.of("mod/graphics/tilesets/barracks-bunks.png").toFile());
        var frame = TileRegistry.installed().doodad("barracks-bunks.frame");
        for (int y = 20; y < 108; y++) for (int x = 15; x < 49; x++)
            assertTrue((image.getRGB(frame.col * 64 + x, frame.row * 64 + y) >>> 24) <= 2);
        for (String id : new String[]{"frame", "bedding-low", "bedding-high"}) {
            var def = TileRegistry.installed().doodad("barracks-bunks." + id);
            assertEquals(0, def.cover.level()); assertEquals(0f, def.ballisticHalfHeight);
        }
    }
}
