package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tracks' crush view, and a hull crushing what it is driven into. */
class HeavyTracksTest {

    private static final int SIZE = 16;

    private static final class Ground implements AutoCloseable {
        final NavigationGrid grid = new NavigationGrid(SIZE, SIZE);
        final CellTopology topology = new CellTopology(SIZE, SIZE);
        final DoodadService props = new DoodadService(grid);
        final NavigationService navigation;
        final MapEditor editor;

        Ground() {
            for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
            }
            navigation = new NavigationService(grid, topology, false);
            editor = new MapEditor(navigation);
            editor.setDoodads(props);
        }

        void solid(int x, int y, boolean crushable) {
            grid.setWalkable(x, y, false);
            grid.setTag(x, y, CellTag.NATURAL_SOLID, true);
            grid.setTag(x, y, CellTag.CRUSHABLE, crushable);
        }

        void pine(int x, int y) {
            for (int dy = 0; dy < 3; dy++) for (int dx = 0; dx < 3; dx++) {
                if (!TreeCanopy.PINE.blocks(dx, dy, 0)) continue;
                grid.setWalkable(x + dx, y + dy, false);
                grid.setTag(x + dx, y + dy, CellTag.NATURAL_SOLID, true);
                topology.setFixture(x + dx, y + dy, true);
            }
            props.addDoodad(new Doodad(x, y, TileRegistry.installed().doodad(TreeCanopy.PINE.id), 0));
        }

        @Override public void close() { navigation.close(); }
    }

    @Test
    void theViewOpensOnlyCrushableCellsAndCatchesUpWithoutBeingRebuilt() {
        try (Ground g = new Ground()) {
            g.solid(3, 3, true);
            g.solid(5, 5, false);
            HeavyTracks tracks = new HeavyTracks(g.navigation, null);
            NavigationGrid view = tracks.motionGrid();
            assertTrue(view.isWalkable(3, 3), "undergrowth gives");
            assertFalse(view.isWalkable(5, 5), "a natural solid that is not crushable stands");
            assertFalse(g.grid.isWalkable(3, 3), "the live grid is untouched");

            g.grid.setTag(5, 5, CellTag.CRUSHABLE, true);
            g.grid.setWalkable(9, 9, false);
            assertSame(view, tracks.motionGrid(), "kept, not copied afresh");
            assertTrue(view.isWalkable(5, 5), "newly dead undergrowth gives");
            assertFalse(view.isWalkable(9, 9), "a new obstruction closes");
        }
    }

    @Test
    void aTankCrushesTheUndergrowthUnderItsFootprintAndIsSlowedByIt() {
        try (Ground g = new Ground()) {
            g.pine(7, 6);
            HeavyTracks tracks = new HeavyTracks(g.navigation, g.editor);
            VehicleType type = VehicleType.BREAKTHROUGH_TANK;
            GroundBody body = type.createBody();
            body.x = 8.5f;
            body.y = 4.5f;
            body.facingDegrees = 0f;
            body.speed = type.maxSpeed;

            assertTrue(tracks.crush(body, type) > 0);
            assertEquals("forest.pine-destroyed", g.props.getDoodads().get(0).definitionId());
            assertTrue(g.grid.isWalkable(8, 7));
            assertTrue(body.speed <= type.maxSpeed * HeavyTracks.CRUSH_SPEED_FRACTION + 1e-6f);

            body.speed = type.maxSpeed;
            assertEquals(0, tracks.crush(body, type), "open ground");
            assertEquals(type.maxSpeed, body.speed, "costs nothing");
        }
    }

    @Test
    void crushableGroundIsPricedAsRubble() {
        try (Ground g = new Ground()) {
            g.solid(4, 4, true);
            TerrainCostField cost = HeavyTracks.costField(g.grid, TerrainCostField.from(g.topology));
            assertEquals(TerrainCostField.COST_RUBBLE, cost.costAt(4, 4));
            assertEquals(TerrainCostField.COST_OPEN_GROUND, cost.costAt(5, 4));
        }
    }
}
