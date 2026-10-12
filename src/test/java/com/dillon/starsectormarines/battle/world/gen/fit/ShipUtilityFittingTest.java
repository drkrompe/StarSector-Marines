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

class ShipUtilityFittingTest {
    private static final List<RoomPurpose> PURPOSES = List.of(
            RoomPurpose.SERVER_ROOM, RoomPurpose.PARTS_CAGE, RoomPurpose.GENERIC);
    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors) implements FurnishableRoom {}
    private record Fixture(GenContext ctx, RoomFloor floor, List<LayoutOp> ops) {}

    private static RoomShape shape(RoomPurpose purpose) {
        return purpose == RoomPurpose.SERVER_ROOM ? RoomShape.rectangle(5, 4)
                : purpose == RoomPurpose.PARTS_CAGE ? RoomShape.rectangle(6, 4) : RoomShape.rectangle(3, 3);
    }

    private static Fixture fit(RoomPurpose purpose, RoomPose pose, List<int[]> doors) {
        RoomShape canonical = shape(purpose), posed = canonical.posed(pose);
        int w = posed.width() + 8, h = posed.height() + 8;
        NavigationGrid grid = new NavigationGrid(w, h);
        for (int y = 0; y < posed.height(); y++) for (int x = 0; x < posed.width(); x++)
            if (posed.contains(x, y)) grid.setWalkableFloor(x + 4, y + 4);
        List<Doorway> entrances = new ArrayList<>();
        for (int[] d : doors) {
            int[] cell = pose.map(d[0], d[1], canonical.width(), canonical.height());
            entrances.add(new Doorway(cell[0] + 4, cell[1] + 4));
            grid.setWalkableFloor(cell[0] + 4, cell[1] + 4);
        }
        GenContext ctx = new GenContext(grid, new CellTopology(w, h), new Random(7), w, h, 7);
        RoomFloor floor = new RoomFloor(ctx,
                new Room(posed, 4, 4, pose, purpose, entrances), RoomFit.STANDARD);
        List<LayoutOp> ops = new ArrayList<>(); floor.record(ops::add);
        RoomFittings.forShipRoom(purpose, canonical, RoomFit.STANDARD).fit(floor);
        return new Fixture(ctx, floor, ops);
    }

    @Test void everyPerimeterHatchAndPoseRetainsTheRoomIdentityAndReachableWork() {
        for (RoomPurpose purpose : PURPOSES) for (RoomPose pose : RoomPose.all())
            for (int[] door : shape(purpose).doorways()) verify(fit(purpose, pose, List.of(door)), true);
    }

    @Test void wideAndSimultaneousHatchesRemainCompletelyClear() {
        for (RoomPurpose purpose : PURPOSES) for (RoomPose pose : RoomPose.all()) {
            RoomShape shape = shape(purpose);
            for (int x = 0; x + 1 < shape.width(); x++) {
                verify(fit(purpose, pose, List.of(new int[]{x, -1}, new int[]{x + 1, -1})), true);
                verify(fit(purpose, pose, List.of(new int[]{x, shape.height()},
                        new int[]{x + 1, shape.height()})), true);
            }
            verify(fit(purpose, pose, List.of(new int[]{0, -1}, new int[]{1, -1},
                    new int[]{1, shape.height()}, new int[]{2, shape.height()},
                    new int[]{-1, 1}, new int[]{shape.width(), 1})), false);
        }
    }

    @Test void groundDispatchAndFullMachineShopRemainSeparate() {
        assertInstanceOf(PartsCageFitting.class, RoomFittings.forPurpose(RoomPurpose.PARTS_CAGE));
        assertInstanceOf(AisleFitting.class, RoomFittings.forPurpose(RoomPurpose.SERVER_ROOM));
        assertNull(RoomFittings.forPurpose(RoomPurpose.GENERIC));
        assertInstanceOf(ShipMachineShopFitting.class, RoomFittings.forShipRoom(RoomPurpose.PARTS_CAGE,
                RoomShape.rectangle(14, 10), RoomFit.STANDARD));
    }

    private static void verify(Fixture f, boolean fullIdentity) {
        RoomPurpose purpose = f.floor.room().purpose();
        assertTrue(f.floor.circulationSurvives(), purpose.toString());
        assertEquals(0, f.floor.dropUnreachableWork());
        assertFalse(f.ctx.doodads.isEmpty());
        assertTrue(f.ctx.doodads.stream().allMatch(d -> d.definitionId().startsWith("ship-utility.")));
        if (purpose == RoomPurpose.GENERIC) {
            assertTrue(f.ctx.fixtureTasks.isEmpty(), "a closet must not invent a watch posting");
            if (fullIdentity) for (String id : List.of("pump", "electrical", "supply-locker"))
                assertTrue(f.ctx.doodads.stream().anyMatch(d -> d.definitionId().equals("ship-utility." + id)), id);
        } else {
            assertEquals(1, f.ctx.fixtureTasks.stream().filter(t -> t.affordance() == Affordance.READOUT).count());
            if (purpose == RoomPurpose.PARTS_CAGE) {
                assertEquals(1, f.ctx.fixtureTasks.stream().filter(t -> t.affordance() == Affordance.REPAIR).count());
                assertTrue(f.ctx.fixtureTasks.stream().anyMatch(t -> t.affordance() == Affordance.STOW));
                assertTrue(f.ops.stream().anyMatch(op -> op instanceof LayoutOp.Assembly a
                        && a.baseId().equals("ship-utility.shelf") && a.layers().size() == 2));
            } else assertTrue(f.ctx.doodads.stream().anyMatch(d -> d.definitionId().equals("ship-utility.server")));
        }
        f.floor.seal();
        int w = f.floor.canonicalWidth(), h = f.floor.canonicalHeight();
        for (int y = 1; y < h - 1; y++) for (int x = 0; x < w; x++) {
            int[] c = f.floor.toLocal(x, y);
            assertTrue(f.ctx.grid.isWalkable(c[0] + 4, c[1] + 4), "full service aisle");
        }
        for (Doorway d : f.floor.localDoors()) {
            int[] canonical = f.floor.toCanonical(d.x(), d.y());
            int[] c = f.floor.toLocal(Math.max(0, Math.min(w - 1, canonical[0])),
                    Math.max(0, Math.min(h - 1, canonical[1])));
            assertTrue(f.ctx.grid.isWalkable(c[0] + 4, c[1] + 4), "whole hatch approach");
        }
        HashSet<String> standing = new HashSet<>();
        for (var task : f.ctx.fixtureTasks) {
            assertTrue(standing.add(task.cellX() + "," + task.cellY()));
            assertTrue(f.ctx.grid.isWalkable(task.cellX(), task.cellY()));
            assertFalse(f.ctx.grid.isWalkable(task.fixtureX(), task.fixtureY()));
            assertEquals(1, Math.abs(task.cellX() - task.fixtureX()) + Math.abs(task.cellY() - task.fixtureY()));
        }
        for (var d : f.ctx.doodads) {
            assertEquals(f.floor.pose().quarterTurns(), d.quarterTurns);
            assertEquals(f.floor.pose().mirrored(), d.mirrored);
            for (int y = 0; y < d.footprintCellsY; y++) for (int x = 0; x < d.footprintCellsX; x++)
                assertFalse(f.ctx.grid.isWalkable(d.cellX + x, d.cellY + y), "complete physical footprint");
        }
    }
}
