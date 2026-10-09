package com.dillon.starsectormarines.battle.world.gen.ship.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.ship.BayAperture;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckSide;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckZone;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipKeys;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BayApertureStageTest {
    @Test
    void centeredAisleCanCrossAnUncarvedHullLipRatherThanFollowAnOffCenterExposure() {
        GenContext ctx = context();
        DeckGraph.Compartment bay = bay();
        boolean[][] hull = new boolean[ctx.width][ctx.height];
        // Only the extreme left of the outboard wall is already outside.
        // The aisle centre has two cells of uncarved structure beyond its ring.
        for (int x = 0; x < ctx.width; x++) {
            for (int y = 0; y < ctx.height; y++) hull[x][y] = x >= 6 && y >= 3;
        }
        BayAperture door = RoomPlacementStage.doorOnto(ctx, hull, bay, List.of(bay));
        assertNotNull(door);
        assertEquals(16f, door.centerX());
        assertEquals(5.5f, door.centerY());
        assertEquals(8, door.widthCells());
        assertEquals(-1, door.outDy());
    }

    @Test
    void openingIsVacuumAcrossTheWallAndOutwardToTheEdgeWithoutOpeningInfantryFloor() {
        GenContext ctx = context();
        DeckGraph.Compartment bay = bay();
        for (int x = bay.left(); x <= bay.right(); x++) {
            for (int y = bay.top(); y <= bay.bottom(); y++) {
                ctx.grid.setWalkableFloor(x, y);
                ctx.topology.setWall(x, y, false);
            }
        }
        BayAperture door = new BayAperture(bay.id(), 16f, 5.5f, 0, -1, 8);
        ctx.put(ShipKeys.DECK_GRAPH, new DeckGraph(List.of(bay), List.of(), List.of(door)));
        new BayApertureStage().run(ctx);
        for (int x = 12; x < 20; x++) {
            for (int y = 0; y <= 5; y++) {
                assertEquals(GroundKind.VOID, ctx.topology.getGroundKind(x, y));
                assertFalse(ctx.grid.isWalkable(x, y));
                assertTrue(ctx.grid.isSeeThrough(x, y));
                assertFalse(ctx.topology.isWall(x, y));
                assertEquals(0, ctx.grid.getWallHp(x, y));
            }
            assertTrue(ctx.grid.isWalkable(x, 6));
            assertEquals(GroundKind.INDOOR, ctx.topology.getGroundKind(x, 6));
        }
        assertTrue(ctx.topology.isWall(11, 5));
        assertTrue(ctx.topology.isWall(20, 5));
    }

    @Test
    void centeredExitNeverConsumesAnotherCompartmentOrCirculation() {
        GenContext ctx = context();
        DeckGraph.Compartment bay = bay();
        DeckGraph.Compartment neighbor = new DeckGraph.Compartment(2, RoomShape.rectangle(4, 2),
                14, 2, RoomPose.CANONICAL, DeckSide.PORT, DeckZone.MIDSHIPS,
                RoomPurpose.STOCKROOM, List.of());
        boolean[][] hull = new boolean[ctx.width][ctx.height];
        assertNull(RoomPlacementStage.doorOnto(ctx, hull, bay, List.of(bay, neighbor)));
        ctx.grid.setWalkableFloor(16, 3);
        assertNull(RoomPlacementStage.doorOnto(ctx, hull, bay, List.of(bay)));
    }

    private static DeckGraph.Compartment bay() {
        return new DeckGraph.Compartment(1, RoomShape.rectangle(24, 20), 4, 6,
                RoomPose.CANONICAL, DeckSide.PORT, DeckZone.MIDSHIPS, RoomPurpose.HANGAR, List.of());
    }

    private static GenContext context() {
        NavigationGrid grid = new NavigationGrid(40, 32);
        CellTopology topology = new CellTopology(40, 32);
        GenContext ctx = new GenContext(grid, topology, new Random(1), 40, 32, 1);
        for (int x = 0; x < ctx.width; x++) {
            for (int y = 0; y < ctx.height; y++) {
                grid.setWalkable(x, y, false);
                grid.setWallHp(x, y, 100);
                topology.setWall(x, y, true);
                topology.setGroundKind(x, y, GroundKind.INDOOR);
            }
        }
        return ctx;
    }
}
