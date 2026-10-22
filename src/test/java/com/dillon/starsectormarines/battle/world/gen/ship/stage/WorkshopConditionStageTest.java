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
            assertEquals(buried, SealedShipHatches.sealed(ctx, door.x(), door.y()));
            if (buried) {
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

    private static boolean has(GenContext ctx, String id) {
        return ctx.doodads.stream().anyMatch(d -> ("workshop-damage." + id).equals(d.definitionId()));
    }
}
