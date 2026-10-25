package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Street dressing's placement rules: a gantry blocks only at its columns, and a boom gate only at its post, with the
 * arm laid out the way it points.
 */
class StreetDressingTest {

    private static final int SIZE = 12;
    private TileRegistry previous;

    @BeforeEach
    void install() throws Exception {
        previous = TileRegistry.installed();
        TileRegistry registry = new TileRegistry();
        registry.ingestSheet(new JSONObject(Files.readString(Path.of("mod/data/tilesets/industrial-street-kit.tileset.json"))));
        TileRegistry.install(registry);
    }

    @AfterEach
    void restore() {
        TileRegistry.install(previous);
    }

    @Test
    void aGantryBlocksOnlyAtItsColumns() {
        NavigationGrid grid = floor();
        CellTopology topology = new CellTopology(SIZE, SIZE);
        List<Doodad> doodads = new ArrayList<>();
        StreetDressing.pipeRun(grid, topology, doodads, 1, 5, 9, 5, 4);
        for (int x = 1; x <= 9; x++) {
            boolean column = x == 1 || x == 5 || x == 9;
            assertEquals(!column, grid.isWalkable(x, 5), "x=" + x);
            if (column) assertTrue(grid.isSeeThrough(x, 5), "a column never closes a sightline, x=" + x);
        }
        assertEquals(9 + 3, doodads.size(), "a rack cell on every cell, and a column under each beam");
    }

    @Test
    void aBoomGateBlocksAtItsPostAndLaysItsArmTheWayItPoints() {
        NavigationGrid grid = floor();
        CellTopology topology = new CellTopology(SIZE, SIZE);
        List<Doodad> doodads = new ArrayList<>();
        StreetDressing.boomGate(grid, topology, doodads, 6, 6, 0, -1);
        assertFalse(grid.isWalkable(6, 6));
        for (int y = 3; y <= 5; y++) assertTrue(grid.isWalkable(6, y), "a figure walks under the arm, y=" + y);
        Doodad arm = doodads.get(1);
        assertEquals(6, arm.cellX);
        assertEquals(3, arm.cellY);
        assertEquals(1, arm.footprintCellsX);
        assertEquals(StreetDressing.BOOM_REACH, arm.footprintCellsY);
    }

    private static NavigationGrid floor() {
        NavigationGrid grid = new NavigationGrid(SIZE, SIZE);
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) grid.setWalkableFloor(x, y);
        return grid;
    }
}
