package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.ship.RoomRecipe;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ShipFiringRangeFittingTest {
    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors) implements FurnishableRoom {}
    private record Fixture(GenContext ctx, RoomFloor floor) {}

    private static Fixture fit(RoomPose pose, List<int[]> doors, boolean ship) {
        RoomShape canonical = RoomRecipe.RANGE.shape(), shape = canonical.posed(pose);
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
                new Room(shape, 4, 4, pose, RoomPurpose.FIRING_RANGE, entrances), RoomFit.STANDARD);
        (ship ? RoomFittings.forShipRoom(RoomPurpose.FIRING_RANGE, canonical, RoomFit.STANDARD)
                : RoomFittings.forPurpose(RoomPurpose.FIRING_RANGE)).fit(floor);
        return new Fixture(ctx, floor);
    }

    @Test void everyPoseAndAuthoredHatchKeepsTwoSafeReachableLiveLanes() {
        for (RoomPose pose : RoomPose.all())
            for (Hookup hookup : FiringRangeFitting.shipboard().hookups(RoomRecipe.RANGE.shape()))
                for (Hookup.DoorSlot slot : hookup.slots()) {
                    verify(fit(pose, slot.cells(), true));
                    for (int[] door : slot.cells()) verify(fit(pose, List.of(door), true));
                }
    }

    @Test void simultaneousReadyEndHatchesDoNotEraseTheEquipmentOrPractice() {
        List<int[]> doors = new ArrayList<>();
        for (Hookup hookup : FiringRangeFitting.shipboard().hookups(RoomRecipe.RANGE.shape()))
            for (Hookup.DoorSlot slot : hookup.slots()) doors.addAll(slot.cells());
        for (RoomPose pose : RoomPose.all()) verify(fit(pose, doors, true));
    }

    @Test void groundRangeRetainsItsFieldFittingAndShipKeepsItsHookups() {
        Fixture ground = fit(RoomPose.CANONICAL, List.of(new int[]{-1, 6}), false);
        assertTrue(ground.ctx.doodads.stream().noneMatch(d -> d.definitionId().startsWith("firing-range.")));
        assertFalse(FiringRangeFitting.shipboard().handed(), "do not alter the deck packer's pose set");
    }

    private static void verify(Fixture f) {
        assertTrue(f.floor.circulationSurvives());
        assertEquals(0, f.floor.dropUnreachableWork());
        assertEquals(2, f.ctx.fixtureTasks.size(), "practice only; storage is scenery");
        f.floor.seal();
        for (var task : f.ctx.fixtureTasks) {
            assertEquals(Affordance.PRACTICE, task.affordance());
            assertTrue(f.ctx.grid.isWalkable(task.cellX(), task.cellY()));
            int sx = Integer.signum(task.fixtureX() - task.cellX());
            int sy = Integer.signum(task.fixtureY() - task.cellY());
            int x = task.cellX() + sx, y = task.cellY() + sy;
            for (int step = 0; step < 19; step++, x += sx, y += sy) {
                assertFalse(f.ctx.grid.isWalkable(x, y), "the beaten zone is shut to walking");
                assertTrue(f.ctx.grid.isSeeThrough(x, y), "a clear line to the live target");
                assertFalse(f.ctx.topology.isWall(x, y));
                for (var doodad : f.ctx.doodads) {
                    if (doodad.definitionId().equals("firing-range-target.plate")) continue;
                    assertFalse(x >= doodad.cellX && x < doodad.cellX + doodad.footprintCellsX
                            && y >= doodad.cellY && y < doodad.cellY + doodad.footprintCellsY,
                            "no fixture overlaps the firing ray, including its target");
                }
            }
            assertFalse(f.ctx.grid.isSeeThrough(x, y), "solid backstop lies beyond the target");
        }
        for (String id : List.of("firing-range.backstop", "firing-range.divider", "firing-range.prep-bench",
                "firing-range.control", "armory-workshop.rack", "armory-workshop.rifles", "armory-workshop.rack-frame"))
            assertTrue(f.ctx.doodads.stream().anyMatch(d -> id.equals(d.definitionId())), id);
        for (int y = 5; y <= 6; y++) for (int x = 0; x < 10; x++) {
            int[] c = f.floor.toLocal(x, y);
            assertTrue(f.ctx.grid.isWalkable(c[0] + 4, c[1] + 4), "two-cell ready aisle");
        }
        assertEquals(2, f.ctx.doodads.stream()
                .filter(d -> d.definitionId().equals("firing-range-target.plate")).count());
        for (var doodad : f.ctx.doodads) {
            if (doodad.definitionId().equals("firing-range-target.plate")) {
                assertTrue(f.ctx.fixtureTasks.stream().anyMatch(t -> t.fixtureX() == doodad.cellX
                        && t.fixtureY() == doodad.cellY), "plate matches the real target focus");
                continue; // symmetric nonblocking paving needs no pose
            }
            assertEquals(f.floor.pose().quarterTurns(), doodad.quarterTurns);
            assertEquals(f.floor.pose().mirrored(), doodad.mirrored);
        }
    }
}
