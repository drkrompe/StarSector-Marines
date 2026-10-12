package com.dillon.starsectormarines.battle.world.gen.ship.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFloor;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.ShipMachineShopFitting;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckSide;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckZone;
import com.dillon.starsectormarines.battle.world.gen.ship.RoomRecipe;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipKeys;
import com.dillon.starsectormarines.battle.world.gen.ship.WorkshopState;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkshopConditionStageTest {
    @Test void pressureDamageAndRepairsStayInsideEveryRoomPose() {
        for (var pose : RoomPose.all()) for (var state : List.of(
                WorkshopState.BURIED, WorkshopState.CLEARED, WorkshopState.REPAIRED, WorkshopState.EQUIPPED)) {
            var shape = RoomRecipe.MACHINE_SHOP.shape().posed(pose);
            int width = shape.width() + 8, height = shape.height() + 8;
            var grid = new NavigationGrid(width, height);
            var topology = new CellTopology(width, height);
            var ctx = new GenContext(grid, topology, new Random(7), width, height, 7);
            int[] entrance = pose.map(3, -1, 14, 10);
            var door = new Doorway(entrance[0] + 4, entrance[1] + 4);
            var room = new DeckGraph.Compartment(1, shape, 4, 4, pose, DeckSide.PORT,
                    DeckZone.MIDSHIPS, RoomPurpose.PARTS_CAGE, List.of(door));
            for (int y = room.top(); y <= room.bottom(); y++)
                for (int x = room.left(); x <= room.right(); x++) grid.setWalkableFloor(x, y);
            grid.setWalkableFloor(door.x(), door.y());
            ctx.put(ShipKeys.DECK_GRAPH, new DeckGraph(List.of(room), List.of()));
            var floor = new RoomFloor(ctx, room, RoomFit.STANDARD);
            new ShipMachineShopFitting().fit(floor);
            floor.seal();
            new WorkshopConditionStage(state).run(ctx);
            new FinalizeStage().run(ctx);
            new ShipMaterialStage().run(ctx);
            WorkshopConditionStage.restrictAccess(ctx, state);
            assertEquals(state != WorkshopState.BURIED, grid.isWalkable(door.x(), door.y()));
            for (var d : ctx.doodads) {
                if (d.definitionId() == null || !d.definitionId().startsWith("workshop-damage.")) continue;
                assertTrue(room.contains(d.cellX, d.cellY));
                assertTrue(room.contains(d.cellX + d.footprintCellsX - 1, d.cellY + d.footprintCellsY - 1));
            }
            boolean buried = state == WorkshopState.BURIED;
            var seals = ctx.doodads.stream().filter(d -> SealedShipHatches.ART.equals(d.definitionId())).toList();
            assertEquals(buried ? 1 : 0, seals.size());
            if (buried) {
                var seal = seals.get(0);
                assertEquals(door.x(), seal.cellX);
                assertEquals(door.y(), seal.cellY);
                assertEquals(pose.quarterTurns() & 1, seal.quarterTurns);
                assertFalse(grid.isSeeThrough(door.x(), door.y()));
                assertFalse(topology.isWall(door.x(), door.y()), "No full-cell wall behind the hatch");
                assertEquals("ship-floor-trim.passage", topology.getSurfaceId(door.x(), door.y()));
                assertTrue(grid.isDoorway(door.x(), door.y()));
                assertTrue(grid.getWallHp(door.x(), door.y()) > 0, "Keep the physical seal");
                assertTrue(grid.blocksStructuralLineOfSightAt(grid.index(door.x(), door.y())));
            }
            assertEquals(buried, has(ctx, "breach"));
            assertEquals(buried, has(ctx, "collapse"));
            assertEquals(!buried, has(ctx, "patch"));
            assertEquals(!state.operational(), has(ctx, "lathe"));
            assertEquals(!state.operational(), has(ctx, "mill"));
            assertEquals(!state.operational(), has(ctx, "scorch"));
            for (int y = room.top(); y <= room.bottom(); y++)
                for (int x = room.left(); x <= room.right(); x++) {
                    assertFalse(topology.isWall(x, y), "Pressure exclusion must not paint bulkheads over the floor");
                    if (buried) assertFalse(grid.isWalkable(x, y), "No crew can spawn on an exposed deck");
                }
            if (!state.operational()) assertTrue(ctx.fixtureTasks.isEmpty());
            else for (var task : ctx.fixtureTasks) assertTrue(grid.isWalkable(task.cellX(), task.cellY()));
        }
    }

    @Test void exportedArtKeepsSpaceOpaqueAndMachineFootprintsAndCoverUnchanged() throws Exception {
        var registry = TileRegistry.installed();
        var image = ImageIO.read(Path.of("mod/graphics/tilesets/workshop-damage.png").toFile());
        var breach = registry.doodad("workshop-damage.breach");
        assertTrue((image.getRGB((breach.col + 3) * 64, (breach.row + 2) * 64) >>> 24) >= 245);
        assertEquals(0, image.getRGB(breach.col * 64, breach.row * 64) >>> 24);
        for (String machine : List.of("lathe", "mill")) {
            var intact = registry.doodad("machine-shop." + machine);
            var broken = registry.doodad("workshop-damage." + machine);
            assertEquals(intact.footprintCellsX, broken.footprintCellsX);
            assertEquals(intact.footprintCellsY, broken.footprintCellsY);
            assertEquals(intact.cover, broken.cover);
            assertEquals(intact.ballisticHalfHeight, broken.ballisticHalfHeight);
        }
    }

    @Test void sealedHatchHasClosedLeavesAndTransparentMargins() throws Exception {
        var image = ImageIO.read(Path.of("mod/graphics/tilesets/ship-sealed-hatches.png").toFile());
        var hatch = TileRegistry.installed().doodad(SealedShipHatches.ART);
        int x = hatch.col * 64, y = hatch.row * 64;
        for (int across : new int[]{4, 16, 32, 48, 59}) {
            assertTrue((image.getRGB(x + across, y + 32) >>> 24) >= 245, "Closed locking-bar silhouette");
            assertTrue((image.getRGB(x + across, y + 4) >>> 24) < 40, "Keep top margin clear");
            assertTrue((image.getRGB(x + across, y + 59) >>> 24) < 40, "Keep bottom margin clear");
        }
    }

    private static boolean has(GenContext ctx, String id) {
        return ctx.doodads.stream().anyMatch(d -> ("workshop-damage." + id).equals(d.definitionId()));
    }
}
