package com.dillon.starsectormarines.battle.world.gen.ship.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFloor;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
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
    @Test void individualBerthsLoseCapacityAndWorkWithoutClosingTheirSharedLane() {
        for (var pose : RoomPose.all()) for (int working = 1; working <= 4; working++) {
            var room = new DeckGraph.Compartment(1, RoomShape.rectangle(24, 16).posed(pose), 4, 4,
                    pose, DeckSide.PORT, DeckZone.MIDSHIPS, RoomPurpose.VEHICLE_BAY, List.of());
            var ctx = new GenContext(new NavigationGrid(40, 40), new CellTopology(40, 40), new Random(7), 40, 40, 7);
            ctx.put(ShipKeys.DECK_GRAPH, new DeckGraph(List.of(room), List.of()));
            var floor = new RoomFloor(ctx, room, RoomFit.STANDARD);
            for (int y = room.top(); y <= room.bottom(); y++) for (int x = room.left(); x <= room.right(); x++)
                ctx.grid.setWalkableFloor(x, y);
            for (int i = 0; i < 4; i++) {
                int[] rect = floor.toLocalRect(4 + (i / 2) * 8, i % 2 == 0 ? 1 : 9, 3, 6);
                int[] heading = pose.mapDirection(0, i % 2 == 0 ? 1 : -1);
                var berth = Gantry.covering(room.left() + rect[0], room.top() + rect[1], rect[2], rect[3],
                        Gantry.Facing.of(heading[0], heading[1]), Gantry.Holds.MACHINE);
                ctx.gantries.add(berth);
                ctx.fixtureTasks.add(FixtureTask.servingBerth(berth.centerX, berth.centerY, i, berth.centerX, berth.centerY));
            }
            var original = List.copyOf(ctx.gantries);
            var boat = new Gantry(35, 35, 1, 1, Gantry.Facing.NORTH, Gantry.Holds.BOAT);
            ctx.gantries.add(boat);
            ctx.fixtureTasks.add(FixtureTask.servingBerth(35, 35, 4, 35, 35));
            var state = new ShipRestoration(true, true, false, false, false, working - 1);
            new ShipConditionStage(state).run(ctx);
            ShipConditionStage.restrictAccess(ctx, state);
            assertEquals(original.subList(0, working), ctx.gantries.subList(0, working));
            assertEquals(boat, ctx.gantries.get(working));
            assertEquals(working + 1, ctx.fixtureTasks.size());
            assertEquals(working, ctx.fixtureTasks.get(working).berth());
            assertFalse(ctx.get(ShipKeys.DECK_GRAPH).damaged(room.id()), "The room remains open");
            for (int i = 0; i < 4; i++) assertEquals(i < working, ctx.grid.isWalkable(original.get(i).centerX, original.get(i).centerY));
            for (int x = 0; x < 24; x++) for (int y : new int[]{7, 8}) {
                int[] cell = floor.toLocal(x, y);
                assertTrue(ctx.grid.isWalkable(room.left() + cell[0], room.top() + cell[1]));
            }
            for (var d : ctx.doodads) {
                assertTrue(room.contains(d.cellX, d.cellY));
                assertTrue(room.contains(d.cellX + d.footprintCellsX - 1, d.cellY + d.footprintCellsY - 1));
            }
        }
    }
    @Test void sealedMessRemovesTasksAndBlocksCrewUntilItsOwnRepair() {
        for (var pose : RoomPose.all()) for (boolean restored : new boolean[]{false, true}) {
            var room = new DeckGraph.Compartment(1, RoomShape.rectangle(22, 12).posed(pose), 4, 4,
                    pose, DeckSide.PORT, DeckZone.MIDSHIPS, RoomPurpose.MESS_HALL, List.of());
            var ctx = new GenContext(new NavigationGrid(40, 40), new CellTopology(40, 40), new Random(7), 40, 40, 7);
            ctx.put(ShipKeys.DECK_GRAPH, new DeckGraph(List.of(room), List.of()));
            for (int y = room.top(); y <= room.bottom(); y++) for (int x = room.left(); x <= room.right(); x++)
                if (room.contains(x, y)) ctx.grid.setWalkableFloor(x, y);
            ctx.fixtureTasks.add(new FixtureTask(6, 6, Affordance.FABRICATE, 6, 6, -1, true));
            var state = new ShipRestoration(true, true, true, true, restored);
            new ShipConditionStage(state).run(ctx);
            ShipConditionStage.restrictAccess(ctx, state);
            assertEquals(!restored, ctx.get(ShipKeys.DECK_GRAPH).damaged(1));
            assertEquals(restored ? 1 : 0, ctx.fixtureTasks.size());
            assertEquals(restored, ctx.grid.isWalkable(6, 6));
            for (var d : ctx.doodads) {
                assertTrue(room.contains(d.cellX, d.cellY), d.definitionId());
                assertTrue(room.contains(d.cellX + d.footprintCellsX - 1, d.cellY + d.footprintCellsY - 1), d.definitionId());
            }
        }
    }

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
