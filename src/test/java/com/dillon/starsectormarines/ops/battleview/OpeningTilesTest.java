package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpeningTilesTest {
    @Test
    void cargoRunsSuppressIntermediateFramesOnBothAxes() {
        for (Direction along : new Direction[]{Direction.N, Direction.E}) {
            NavigationGrid grid = new NavigationGrid(7, 7);
            for (int i = 0; i < 3; i++) grid.setDoorway(2 + along.dx * i, 2 + along.dy * i, true);
            for (int i = 0; i < 3; i++) assertTrue(OpeningTiles.wideDoorway(grid, 2 + along.dx * i, 2 + along.dy * i));
            grid.setDoorway(6, 6, true);
            assertFalse(OpeningTiles.wideDoorway(grid, 6, 6));
            assertFalse(OpeningTiles.wideDoorway(grid, 1, 2));
        }
    }

    @Test
    void canonicalEdgesRecoverAllFourOwnerFacings() {
        for (Direction direction : Direction.CARDINALS) {
            NavigationGrid grid = new NavigationGrid(3, 3);
            for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) grid.setWalkableFloor(x, y);
            grid.placeEdgeBarrier(1, 1, direction, SharedEdgeBarrier.Kind.WINDOW);
            assertEquals(direction, OpeningTiles.facing(grid.getEdgeBarriers().iterator().next()));
        }
    }

    @Test
    void jambMaterialsBeatAccentFloorsAndFloorSurvivesLossOfBothJambs() {
        TileRegistry registry = TileRegistry.installed();
        CellTopology topology = new CellTopology(3, 3);
        topology.setSurface(1, 1, topology.surfaceIndex("colony.terracotta"));
        topology.setSurface(0, 1, topology.surfaceIndex("colony.masonry-wall"));
        topology.setWall(0, 1, true);
        assertEquals("colony.masonry-window", OpeningTiles.skin(registry, topology, 1, 1, Direction.N, true).id);
        assertEquals("colony.masonry-door", OpeningTiles.skin(registry, topology, 1, 1, Direction.S, false).id);
        topology.setWall(0, 1, false);
        assertEquals("colony.ceramic-window", OpeningTiles.skin(registry, topology, 1, 1, Direction.N, true).id);
    }

    @Test
    void doorwayFacingPersistsAfterSideWallsAreBreached() {
        CellTopology topology = new CellTopology(3, 3);
        topology.setWall(1, 0, true);
        topology.setWall(1, 2, true);
        assertEquals(Direction.E, OpeningTiles.doorFacing(topology, 1, 1));
        topology.setWallDirMask(1, 1, CellTopology.WALL_DIR_W);
        topology.setWall(1, 0, false);
        topology.setWall(1, 2, false);
        assertEquals(Direction.W, OpeningTiles.doorFacing(topology, 1, 1));
    }
}
