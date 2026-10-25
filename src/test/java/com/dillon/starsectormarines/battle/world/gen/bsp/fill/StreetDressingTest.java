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
 * arm laid out the way it points; a door canopy hangs over each run of doors, centred, one per span.
 */
class StreetDressingTest {

    private static final int SIZE = 12;
    private TileRegistry previous;

    @BeforeEach
    void install() throws Exception {
        previous = TileRegistry.installed();
        TileRegistry registry = new TileRegistry();
        registry.ingestSheet(new JSONObject(Files.readString(Path.of("mod/data/tilesets/industrial-street-kit.tileset.json"))));
        registry.ingestSheet(new JSONObject(Files.readString(Path.of("mod/data/tilesets/street-dressing.tileset.json"))));
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

    @Test
    void aCanopyHangsOverEachRunOfDoorsInFrontOfTheWall() {
        NavigationGrid grid = floor();
        // A three-wide loading door at x 2..4 and two single doors at x 7 and 8, close enough to share one canopy.
        for (int x : new int[]{2, 3, 4, 7, 8}) grid.setDoorway(x, 6, true);
        List<Doodad> doodads = new ArrayList<>();
        StreetDressing.doorCanopies(grid, doodads, 1, 10, x -> new int[]{x, 6}, 0, 1);
        assertEquals(2, doodads.size());
        Doodad first = doodads.get(0), second = doodads.get(1);
        assertEquals(StreetDressing.CANOPY, first.definitionId());
        assertEquals(2, first.cellX, "centred on the door's middle");
        assertEquals(5, first.cellY, "on the row in front of the wall");
        assertEquals(StreetDressing.AWNING_SPAN, first.footprintCellsX);
        assertEquals(6, second.cellX);
    }

    private static NavigationGrid floor() {
        NavigationGrid grid = new NavigationGrid(SIZE, SIZE);
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) grid.setWalkableFloor(x, y);
        return grid;
    }
}
