package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.AuthoredFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.RoomLayout;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.RoomLayoutCheck;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.RoomLayoutJson;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ShipMechLabFittingTest {
    @BeforeAll static void catalog() throws Exception { DiskRegistries.install(); }

    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors) implements FurnishableRoom {}
    private record Fixture(GenContext ctx, RoomFloor floor, List<LayoutOp> ops) {}

    private static Fixture empty(int width, int height, RoomPose pose, boolean storageDoor) {
        RoomShape shape = RoomShape.rectangle(width, height).posed(pose);
        int mapWidth = shape.width() + 8, mapHeight = shape.height() + 8;
        NavigationGrid grid = new NavigationGrid(mapWidth, mapHeight);
        for (int y = 0; y < mapHeight; y++) for (int x = 0; x < mapWidth; x++) grid.setWalkableFloor(x, y);
        GenContext ctx = new GenContext(grid, new CellTopology(mapWidth, mapHeight),
                new Random(7), mapWidth, mapHeight, 7);
        List<Doorway> doors = new ArrayList<>();
        for (int[] canonical : new int[][]{{0, -1}, {0, height}, {9, -1}}) {
            int[] cell = pose.map(canonical[0], canonical[1], width, height);
            doors.add(new Doorway(4 + cell[0], 4 + cell[1]));
        }
        if (storageDoor) {
            int[] cell = pose.map(width - 3, -1, width, height);
            doors.add(new Doorway(4 + cell[0], 4 + cell[1]));
        }
        RoomFloor floor = new RoomFloor(ctx, new Room(shape, 4, 4, pose, RoomPurpose.VEHICLE_BAY, doors), RoomFit.STANDARD);
        List<LayoutOp> ops = new ArrayList<>();
        floor.record(ops::add);
        return new Fixture(ctx, floor, ops);
    }

    @Test void racksAndPartitionsPreserveBerthsCirculationAndWorkInEveryPose() {
        for (int width : new int[]{24, 40}) for (RoomPose pose : RoomPose.all()) {
            Fixture f = empty(width, 16, pose, false);
            RoomFittings.forShipRoom(RoomPurpose.VEHICLE_BAY, RoomShape.rectangle(width, 16), RoomFit.STANDARD).fit(f.floor);
            assertTrue(f.floor.circulationSurvives(), pose.toString());
            assertEquals(0, f.floor.dropUnreachableWork(), "all published work must remain reachable: " + pose);
            assertEquals(width == 24 ? 4 : 8, f.ctx.gantries.size());
            assertEquals(4, f.ops.stream().filter(op -> op instanceof LayoutOp.Assembly).count());
            assertEquals(12, f.ctx.fixtureTasks.stream().filter(t -> t.affordance() == Affordance.STOW).count());
            assertTrue(f.ops.stream().anyMatch(op -> op instanceof LayoutOp.Partition));
            for (LayoutOp op : f.ops) if (op instanceof LayoutOp.Assembly rack) {
                int[] rect = f.floor.toLocalRect(rack.x(), rack.y(), 2, 6);
                assertTrue(f.ctx.grid.isWalkable(4 + rect[0], 4 + rect[1]), "blocking waits for acceptance");
            }
            f.floor.seal();
            for (LayoutOp op : f.ops) {
                if (op instanceof LayoutOp.Assembly rack) {
                    int[] rect = f.floor.toLocalRect(rack.x(), rack.y(), 2, 6);
                    for (int dy = 0; dy < rect[3]; dy++) for (int dx = 0; dx < rect[2]; dx++) {
                        int x = 4 + rect[0] + dx, y = 4 + rect[1] + dy;
                        assertFalse(f.ctx.grid.isWalkable(x, y));
                        assertTrue(f.ctx.topology.isFixture(x, y));
                        assertFalse(f.ctx.topology.isWall(x, y));
                    }
                } else if (op instanceof LayoutOp.Partition wall) {
                    int[] rect = f.floor.toLocalRect(wall.x(), wall.y(), wall.spanX(), wall.spanY());
                    assertTrue(f.ctx.topology.isWall(4 + rect[0], 4 + rect[1]));
                    assertFalse(f.ctx.grid.isWalkable(4 + rect[0], 4 + rect[1]));
                }
            }
            for (var task : f.ctx.fixtureTasks) assertTrue(f.ctx.grid.isWalkable(task.cellX(), task.cellY()));
        }
    }

    @Test void aStorageDoorKeepsItsApproachInsteadOfPlacingHalfARack() {
        for (RoomPose pose : RoomPose.all()) {
            Fixture f = empty(24, 16, pose, true);
            VehicleBayFitting.shipboard().fit(f.floor);
            assertTrue(f.floor.circulationSurvives());
            assertEquals(3, f.ops.stream().filter(op -> op instanceof LayoutOp.Assembly).count());
            assertEquals(0, f.floor.dropUnreachableWork());
        }
    }

    @Test void assemblyLayersTurnTogetherAndRefusalIsAtomic() {
        List<LayoutOp.Layer> layers = List.of(new LayoutOp.Layer(0, 4, "warehouse-racks-muted.engine"),
                new LayoutOp.Layer(0, 0, "warehouse-racks-muted.frame"));
        for (RoomPose pose : RoomPose.all()) {
            Fixture f = empty(24, 16, pose, false);
            assertTrue(f.floor.placeAssembly(17, 1, "warehouse-racks-muted.base", layers));
            assertEquals(3, f.ctx.doodads.size());
            for (int i = 0; i < 3; i++) {
                var doodad = f.ctx.doodads.get(i);
                int[] rect = f.floor.toLocalRect(17, i == 1 ? 5 : 1, 2, i == 1 ? 2 : 6);
                assertEquals(4 + rect[0], doodad.cellX);
                assertEquals(4 + rect[1], doodad.cellY);
                assertEquals(rect[2], doodad.footprintCellsX);
                assertEquals(rect[3], doodad.footprintCellsY);
                assertEquals(pose.quarterTurns(), doodad.quarterTurns);
                assertEquals(pose.mirrored(), doodad.mirrored);
            }
            assertFalse(f.floor.placeAssembly(17, 5, "warehouse-racks-muted.base", layers));
            assertFalse(f.floor.placeAssembly(1, 1, "warehouse-racks-muted.base",
                    List.of(new LayoutOp.Layer(0, 0, "missing.layer"))));
            assertEquals(3, f.ctx.doodads.size());
            assertEquals(1, f.floor.placedFixtures());
        }
    }

    @Test void layeredFittingRoundTripsAndReplaysWithoutLosingStructure() throws Exception {
        Fixture f = empty(24, 16, RoomPose.CANONICAL, false);
        VehicleBayFitting.shipboard().fit(f.floor);
        RoomLayout original = new RoomLayout(RoomPurpose.VEHICLE_BAY, RoomFit.STANDARD,
                RoomShape.rectangle(24, 16), f.ops, VehicleBayFitting.shipboard().hookups(RoomShape.rectangle(24, 16)), true);
        RoomLayout read = RoomLayoutJson.parse(RoomLayoutJson.write(original));
        assertEquals(original.ops(), read.ops());
        assertEquals(8, read.posesThatFit().size());
        assertTrue(RoomLayoutCheck.replay(read).clean());
        Fixture replay = empty(24, 16, RoomPose.CANONICAL, false);
        new AuthoredFitting(read).fit(replay.floor);
        assertEquals(f.floor.placedFixtures(), replay.floor.placedFixtures());
        assertEquals(f.ctx.doodads.size(), replay.ctx.doodads.size());
        assertEquals(f.ctx.gantries.size(), replay.ctx.gantries.size());
        assertEquals(f.ops.stream().filter(op -> op instanceof LayoutOp.Assembly || op instanceof LayoutOp.Partition).toList(),
                replay.ops.stream().filter(op -> op instanceof LayoutOp.Assembly || op instanceof LayoutOp.Partition).toList());
    }
}
