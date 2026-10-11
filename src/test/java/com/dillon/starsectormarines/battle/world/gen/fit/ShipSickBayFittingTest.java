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

class ShipSickBayFittingTest {
    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors) implements FurnishableRoom {}
    private record Fixture(GenContext ctx, RoomFloor floor, List<LayoutOp> ops) {}
    private static Fixture fit(RoomPose pose, int doorX, int doorY) {
        RoomShape shape = RoomShape.rectangle(14, 10).posed(pose);
        int w = shape.width() + 8, h = shape.height() + 8;
        NavigationGrid grid = new NavigationGrid(w, h);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) grid.setWalkableFloor(x, y);
        GenContext ctx = new GenContext(grid, new CellTopology(w, h), new Random(7), w, h, 7);
        int[] door = pose.map(doorX, doorY, 14, 10);
        RoomFloor floor = new RoomFloor(ctx, new Room(shape, 4, 4, pose, RoomPurpose.PATIENT_WARD,
                List.of(new Doorway(4 + door[0], 4 + door[1]))), RoomFit.STANDARD);
        List<LayoutOp> ops = new ArrayList<>(); floor.record(ops::add);
        RoomFittings.forShipRoom(RoomPurpose.PATIENT_WARD, RoomShape.rectangle(14, 10), RoomFit.STANDARD).fit(floor);
        return new Fixture(ctx, floor, ops);
    }

    @Test void allPosesKeepTheWardClinicAndStretcherAccess() {
        for (RoomPose pose : RoomPose.all()) {
            Fixture f = fit(pose, -1, 4);
            assertEquals(10, pieces(f, "bed"));
            assertEquals(pieces(f, "bed"), pieces(f, "monitor"));
            assertEquals(1, pieces(f, "station"));
            assertEquals(1, pieces(f, "exam"));
            assertEquals(12, tasks(f, Affordance.TREAT));
            assertEquals(1, tasks(f, Affordance.STOW));
            assertEquals(1, tasks(f, Affordance.WASH));
            verifyReachability(f);
            for (int x = 0; x < 14; x++) for (int y = 3; y < 7; y++) assertWalkable(f, x, y);
            for (int x = 5; x < 14; x += 2) for (int y = 0; y < 10; y++) assertWalkable(f, x, y);
        }
    }

    @Test void wardHatchesNeverLeaveOrphanMonitorsOrLinen() {
        for (RoomPose pose : RoomPose.all()) for (int doorY : new int[]{-1,10}) {
            Fixture f = fit(pose, 6, doorY);
            assertEquals(9, pieces(f, "bed"));
            assertEquals(9, pieces(f, "monitor"));
            assertEquals(9, f.ops.stream().filter(op -> op instanceof LayoutOp.Assembly a
                    && a.layers().stream().anyMatch(l -> l.doodadId().startsWith("sickbay-clinic.linen"))).count());
            verifyReachability(f);
            for (int y = doorY < 0 ? 0 : 7; y < (doorY < 0 ? 3 : 10); y++) assertWalkable(f, 6, y);
        }
    }

    @Test void clinicalHatchesRetainANurseStationAndExaminationCouch() {
        for (RoomPose pose : RoomPose.all()) for (int doorY : new int[]{-1,10}) {
            Fixture f = fit(pose, 1, doorY);
            assertEquals(1, pieces(f, "station"));
            assertEquals(1, pieces(f, "exam"));
            verifyReachability(f);
            for (int y = doorY < 0 ? 0 : 7; y < (doorY < 0 ? 3 : 10); y++) assertWalkable(f, 1, y);
        }
    }

    @Test void linenPaddingAndScreenSideVoidsRemainTransparent() throws Exception {
        var image = ImageIO.read(Path.of("mod/graphics/tilesets/sickbay-clinic.png").toFile());
        for (String id : List.of("linen-high", "linen-low")) {
            var def = TileRegistry.installed().doodad("sickbay-clinic." + id);
            assertEquals(0, def.cover.level()); assertEquals(0f, def.ballisticHalfHeight);
            for (int y = 0; y < 128; y++) for (int x : new int[]{4,8,55,59})
                assertTrue((image.getRGB(def.col * 64 + x, def.row * 64 + y) >>> 24) <= 2);
        }
        var screen = TileRegistry.installed().doodad("sickbay-clinic.screen");
        for (int y = 24; y < 104; y++) for (int x : new int[]{6,10,54,58})
            assertTrue((image.getRGB(screen.col * 64 + x, screen.row * 64 + y) >>> 24) <= 2);
    }

    private static void verifyReachability(Fixture f) {
        assertTrue(f.floor.circulationSurvives());
        assertEquals(0, f.floor.dropUnreachableWork());
        f.floor.seal();
        for (var task : f.ctx.fixtureTasks) {
            assertTrue(f.ctx.grid.isWalkable(task.cellX(), task.cellY()));
            assertTrue(f.ctx.topology.isFixture(task.fixtureX(), task.fixtureY()));
        }
    }
    private static void assertWalkable(Fixture f, int x, int y) {
        int[] cell = f.floor.toLocal(x, y);
        assertTrue(f.ctx.grid.isWalkable(4 + cell[0], 4 + cell[1]));
    }
    private static long pieces(Fixture f, String id) {
        return f.ops.stream().filter(op -> op instanceof LayoutOp.Assembly a
                && a.baseId().equals("sickbay-clinic." + id)).count();
    }
    private static long tasks(Fixture f, Affordance affordance) {
        return f.ctx.fixtureTasks.stream().filter(t -> t.affordance() == affordance).count();
    }
}
