package com.dillon.starsectormarines.battle.world.gen.forest;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Undergrowth closes its silhouette's cells, stops sight only where dense, and a heavy tank flattens it whole. */
class UndergrowthBrushTest {

    private static final int SIZE = 10;

    private static NavigationGrid grid;
    private static CellTopology topology;

    private static void ground() {
        grid = new NavigationGrid(SIZE, SIZE);
        topology = new CellTopology(SIZE, SIZE);
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            grid.setWalkableFloor(x, y);
            topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
        }
    }

    @Test
    void closesTheSilhouetteAndOnlyDenseGrowthStopsSight() {
        ground();
        List<Doodad> props = new ArrayList<>();
        var bramble = new UndergrowthBrush.Placement(1, 1, Undergrowth.BRAMBLE, 1);
        var thicket = new UndergrowthBrush.Placement(2, 2, Undergrowth.THICKET, 0);
        UndergrowthBrush.stamp(List.of(bramble, thicket), grid, topology, props, null);
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            boolean closed = bramble.blocks(x, y) || thicket.blocks(x, y);
            assertEquals(!closed, grid.isWalkable(x, y), x + "," + y);
            if (closed) assertEquals(!thicket.blocks(x, y), grid.isSeeThrough(x, y), "dense growth wins at " + x + "," + y);
        }
        assertEquals(2, props.size());
    }

    @Test
    void refusesGroundSomethingElseHolds() {
        ground();
        topology.setFixture(3, 3, true);
        var placement = new UndergrowthBrush.Placement(2, 2, Undergrowth.THICKET_SMALL, 0);
        assertFalse(UndergrowthBrush.fits(placement, grid, topology, null));
        assertThrows(IllegalArgumentException.class,
                () -> UndergrowthBrush.stamp(List.of(placement), grid, topology, new ArrayList<>(), null));
        assertTrue(grid.isWalkable(2, 2), "refusal is atomic");
    }

    @Test
    void everyCellIsCrushableAndOneCrushFlattensTheWholePatch() {
        ground();
        List<Doodad> placed = new ArrayList<>();
        var bracken = new UndergrowthBrush.Placement(3, 4, Undergrowth.BRACKEN, 0);
        UndergrowthBrush.stamp(List.of(bracken), grid, topology, placed, null);
        DoodadService props = new DoodadService(grid);
        placed.forEach(props::addDoodad);
        try (NavigationService navigation = new NavigationService(grid, topology, false)) {
            MapEditor editor = new MapEditor(navigation);
            editor.setDoodads(props);
            for (int x = 3; x < 7; x++) for (int y = 4; y < 6; y++)
                assertEquals(bracken.blocks(x, y), grid.hasTag(x, y, CellTag.CRUSHABLE), x + "," + y);
            assertTrue(editor.crush(4, 4));
            assertEquals("forest.bracken-destroyed", props.getDoodads().get(0).definitionId());
            for (int x = 3; x < 7; x++) for (int y = 4; y < 6; y++) {
                assertTrue(grid.isWalkable(x, y), x + "," + y);
                assertFalse(grid.hasTag(x, y, CellTag.CRUSHABLE));
            }
        }
    }
}
