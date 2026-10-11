package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ShipArmoryFittingTest {
    @BeforeAll static void catalog() throws Exception { DiskRegistries.install(); }
    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors) implements FurnishableRoom {}
    private record Fixture(GenContext ctx, RoomFloor floor, List<LayoutOp> ops) {}
    private static Fixture fit(RoomPose pose, boolean rearDoor) {
        RoomShape shape = RoomShape.rectangle(12, 8).posed(pose);
        int w = shape.width() + 8, h = shape.height() + 8;
        NavigationGrid grid = new NavigationGrid(w, h);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) grid.setWalkableFloor(x, y);
        GenContext ctx = new GenContext(grid, new CellTopology(w, h), new Random(7), w, h, 7);
        int[] door = pose.map(rearDoor ? 0 : 3, rearDoor ? 8 : -1, 12, 8);
        RoomFloor floor = new RoomFloor(ctx, new Room(shape, 4, 4, pose, RoomPurpose.ARMORY,
                List.of(new Doorway(4 + door[0], 4 + door[1]))), RoomFit.STANDARD);
        List<LayoutOp> ops = new ArrayList<>(); floor.record(ops::add);
        RoomFittings.forShipRoom(RoomPurpose.ARMORY, RoomShape.rectangle(12, 8), RoomFit.STANDARD).fit(floor);
        return new Fixture(ctx, floor, ops);
    }
    @Test void aCompleteWorkshopSurvivesEveryRotationAndMirror() {
        for (RoomPose pose : RoomPose.all()) {
            Fixture f = fit(pose, false);
            assertTrue(f.floor.circulationSurvives(), pose.toString());
            assertEquals(0, f.floor.dropUnreachableWork());
            assertEquals(12, f.floor.placedFixtures());
            assertEquals(2, f.ctx.fixtureTasks.stream().filter(t -> t.affordance() == Affordance.ISSUE).count());
            assertEquals(1, f.ctx.fixtureTasks.stream().filter(t -> t.affordance() == Affordance.REPAIR).count());
            assertEquals(6, f.ctx.fixtureTasks.stream().filter(t -> t.affordance() == Affordance.STOW).count());
            assertEquals(21, f.ctx.doodads.size(), "base, weapons and frame remain separate");
            f.floor.seal();
            for (int y = 0; y < 8; y++) for (int x = 0; x < 12; x++) {
                int[] cell = f.floor.toLocal(x, y);
                boolean fixture = y == 2 && (x < 8 || x >= 10)
                        || (y == 4 || y == 5) && (x < 3 || x >= 4 && x < 7 || x >= 10)
                        || y == 7;
                assertEquals(!fixture, f.ctx.grid.isWalkable(4 + cell[0], 4 + cell[1]),
                        "occupied silhouette at " + x + "," + y + " " + pose);
            }
            for (var task : f.ctx.fixtureTasks) {
                assertTrue(f.ctx.grid.isWalkable(task.cellX(), task.cellY()));
                assertTrue(f.ctx.topology.isFixture(task.fixtureX(), task.fixtureY()));
            }
        }
    }
    @Test void fallbackDoorOmitsWholeFixturesAndKeepsAccessToWork() {
        for (RoomPose pose : RoomPose.all()) {
            Fixture f = fit(pose, true);
            assertTrue(f.floor.circulationSurvives());
            assertEquals(0, f.floor.dropUnreachableWork());
            assertEquals(9, f.floor.placedFixtures());
            f.floor.seal();
            for (int y = 0; y < 8; y++) {
                int[] cell = f.floor.toLocal(0, y);
                assertTrue(f.ctx.grid.isWalkable(4 + cell[0], 4 + cell[1]));
            }
        }
    }
}
