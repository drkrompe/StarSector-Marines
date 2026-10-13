package com.dillon.starsectormarines.battle.world.gen.ship.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckSide;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckZone;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipKeys;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipRestoration;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipConditionStageTest {
    @Test void sealedGantryDisappearsAndSurvivingBoatWorkKeepsItsBerthAcrossPoses() {
        for (var pose : RoomPose.all()) {
            var room = new DeckGraph.Compartment(1, RoomShape.rectangle(16, 10).posed(pose), 4, 4,
                    pose, DeckSide.PORT, DeckZone.MIDSHIPS, RoomPurpose.VEHICLE_BAY, List.of());
            var ctx = new GenContext(new NavigationGrid(40, 40), new CellTopology(40, 40), new Random(7), 40, 40, 7);
            ctx.put(ShipKeys.DECK_GRAPH, new DeckGraph(List.of(room), List.of()));
            ctx.gantries.add(new Gantry(6, 6, 1, 1, Gantry.Facing.NORTH));
            var boat = new Gantry(30, 30, 1, 1, Gantry.Facing.NORTH, Gantry.Holds.BOAT);
            ctx.gantries.add(boat);
            ctx.fixtureTasks.add(new FixtureTask(6, 6, Affordance.FABRICATE, 6, 6, 0, true));
            ctx.fixtureTasks.add(new FixtureTask(30, 30, Affordance.FABRICATE, 30, 30, 1, true));
            new ShipConditionStage(ShipRestoration.WRECK).run(ctx);
            assertEquals(List.of(boat), ctx.gantries);
            assertEquals(1, ctx.fixtureTasks.size());
            assertEquals(0, ctx.fixtureTasks.get(0).berth());
            assertTrue(ctx.get(ShipKeys.DECK_GRAPH).damaged(1));
            for (var d : ctx.doodads) {
                assertTrue(room.contains(d.cellX, d.cellY), d.definitionId());
                assertTrue(room.contains(d.cellX + d.footprintCellsX - 1, d.cellY + d.footprintCellsY - 1), d.definitionId());
            }
        }
    }
    @Test void faultyDistributionDoesNotDisableWorkingFacilities() {
        var room = new DeckGraph.Compartment(1, RoomShape.rectangle(8, 6), 4, 4,
                RoomPose.CANONICAL, DeckSide.PORT, DeckZone.FORE, RoomPurpose.ARMORY, List.of());
        for (boolean power : new boolean[]{false, true}) {
            var ctx = new GenContext(new NavigationGrid(20, 20), new CellTopology(20, 20), new Random(7), 20, 20, 7);
            ctx.put(ShipKeys.DECK_GRAPH, new DeckGraph(List.of(room), List.of()));
            ctx.fixtureTasks.add(new FixtureTask(6, 6, Affordance.FABRICATE, 6, 6, -1, true));
            new ShipConditionStage(new ShipRestoration(true, false, false, power)).run(ctx);
            assertFalse(ctx.get(ShipKeys.DECK_GRAPH).damaged(1));
            assertEquals(!power, ctx.get(ShipKeys.DECK_GRAPH).emergencyLighting(1));
            assertEquals(1, ctx.fixtureTasks.size());
        }
    }
}
