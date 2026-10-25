package com.dillon.starsectormarines.battle.world;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a heavy tank may crush, decided where natural props keep their pools, and what crushing leaves. */
class UndergrowthCrushTest {

    private static final int SIZE = 14;

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

        void add(TreeCanopy canopy, int x, int y) {
            for (int dy = 0; dy < canopy.height; dy++) for (int dx = 0; dx < canopy.width; dx++) {
                if (!canopy.blocks(dx, dy, 0)) continue;
                grid.setWalkable(x + dx, y + dy, false);
                grid.setTag(x + dx, y + dy, CellTag.NATURAL_SOLID, true);
                topology.setFixture(x + dx, y + dy, true);
            }
            props.addDoodad(new Doodad(x, y, TileRegistry.installed().doodad(canopy.id), 0));
        }

        boolean crushable(int x, int y) { return grid.hasTag(x, y, CellTag.CRUSHABLE); }

        @Override public void close() { navigation.close(); }
    }

    @Test
    void aYoungTreeIsCrushableAndOneCrushFellsItWhole() {
        try (Ground g = new Ground()) {
            g.add(TreeCanopy.PINE, 4, 4);
            for (int y = 4; y < 7; y++) for (int x = 4; x < 7; x++) {
                assertEquals(TreeCanopy.PINE.blocks(x - 4, y - 4, 0), g.crushable(x, y), x + "," + y);
            }
            assertTrue(g.editor.crush(5, 5));
            assertEquals("forest.pine-destroyed", g.props.getDoodads().get(0).definitionId());
            for (int y = 4; y < 7; y++) for (int x = 4; x < 7; x++) {
                assertTrue(g.grid.isWalkable(x, y));
                assertFalse(g.crushable(x, y));
            }
            assertFalse(g.editor.crush(5, 5), "nothing is left to crush");
        }
    }

    @Test
    void aMatureTreeStandsUntilTheCreepKillsItThenFallsTrunkAndAll() {
        try (Ground g = new Ground()) {
            g.add(TreeCanopy.OAK, 4, 4);
            assertFalse(g.crushable(6, 6));
            assertFalse(g.editor.crush(6, 6));
            assertEquals("forest.oak", g.props.getDoodads().get(0).definitionId());

            g.editor.witherTree(4, 4);
            for (int y = 4; y < 9; y++) for (int x = 4; x < 9; x++) {
                if (TreeCanopy.OAK.blocks(x - 4, y - 4, 0)) assertTrue(g.crushable(x, y), x + "," + y);
            }
            assertTrue(g.editor.crush(6, 6), "the trunk gives with the rest");
            assertEquals("forest.oak-destroyed", g.props.getDoodads().get(0).definitionId());
            assertTrue(g.grid.isWalkable(6, 6));
        }
    }

    @Test
    void aDeadCrownUnderALivingOneHoldsUntilTheLivingTreeFalls() {
        try (Ground g = new Ground()) {
            g.add(TreeCanopy.OAK, 2, 4);
            g.add(TreeCanopy.OAK, 5, 4);
            g.editor.witherTree(2, 4);
            assertTrue(g.crushable(2, 6), "the dead tree's own crown");
            assertFalse(g.crushable(5, 6), "shared with the living oak");
            assertFalse(g.editor.crush(5, 6));

            assertTrue(g.editor.damageWall(8, 6, 1000), "the living oak is shot down");
            assertTrue(g.crushable(5, 6), "only the dead oak holds the shared cell now");
        }
    }

    @Test
    void permanentGroundIsNeverCrushable() {
        try (Ground g = new Ground()) {
            int summit = g.grid.index(5, 5);
            g.props.setPermanentNatural(cell -> cell == summit);
            g.add(TreeCanopy.PINE, 4, 4);
            assertFalse(g.crushable(5, 5));
            assertTrue(g.crushable(4, 5));
        }
    }

    @Test
    void aCrushableBoulderIsGroundToRubbleAndAnUnmarkedOneStands() {
        try (Ground g = new Ground()) {
            boulder(g, 3, 3, true);
            boulder(g, 8, 8, false);
            assertFalse(g.editor.crush(8, 8));
            assertFalse(g.grid.isWalkable(8, 8));

            assertTrue(g.editor.crush(3, 3));
            assertTrue(g.grid.isWalkable(3, 3));
            assertFalse(g.crushable(3, 3));
            assertFalse(g.topology.isFixture(3, 3));
        }
    }

    /** A lone large rock as nature zones place it: a see-through fixture, crushable where its tile says so. */
    private static void boulder(Ground g, int x, int y, boolean crushable) {
        g.topology.setNatureOverlayIndex(x, y, TileRegistry.installed().tile("nature.rock-large-1").index);
        g.grid.setWalkable(x, y, false);
        g.grid.setSeeThrough(x, y, true);
        g.topology.setFixture(x, y, true);
        g.grid.setTag(x, y, CellTag.CRUSHABLE, crushable);
    }
}
