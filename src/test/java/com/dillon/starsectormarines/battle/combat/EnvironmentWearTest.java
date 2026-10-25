package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestBrush;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestField;
import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.FightingPositionRole;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An arrived round wears what it stopped in, through the map's ordinary destruction channel. */
class EnvironmentWearTest {

    private static final float RIFLE_DAMAGE = 14f;
    private static final float RIFLE_PEN = 7f;
    private static final float AMR_DAMAGE = 42f;
    private static final float AMR_PEN = 18f;
    private static final int WALL_HP = 100;

    private static final class Ground implements AutoCloseable {
        final NavigationGrid grid = new NavigationGrid(8, 8);
        final CellTopology topology = new CellTopology(8, 8);
        final DoodadService props = new DoodadService(grid);
        final NavigationService navigation;
        final MapEditor editor;
        final EnvironmentWear wear;

        Ground() {
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
            }
            navigation = new NavigationService(grid, topology, false);
            editor = new MapEditor(navigation);
            editor.setDoodads(props);
            wear = new EnvironmentWear(grid, topology, props, editor);
        }

        void wall(int x, int y) {
            grid.setWalkable(x, y, false);
            grid.setWallHp(x, y, WALL_HP);
            topology.setWall(x, y, true);
            grid.recomputeCoverAt(x - 1, y);
        }

        @Override public void close() { navigation.close(); }
    }

    @Test
    void rifleFireIntoACrateWearsItAndThenTakesItsCoverAway() {
        try (Ground g = new Ground()) {
            g.props.addDoodad(new Doodad(3, 3, TileRegistry.installed().doodad("doodad.crate")));
            StruckFeature crate = StruckFeature.cell(3, 3);
            assertEquals(EnvironmentMaterial.WOOD, g.wear.materialOf(crate));
            assertTrue(g.props.getDoodadLevelOnCell(3, 3) > 0);
            assertFalse(g.wear.apply(crate, RIFLE_DAMAGE, RIFLE_PEN));
            assertEquals("doodad.crate-damaged", g.props.getDoodads().get(0).definitionId());
            assertTrue(g.props.getDoodadLevelOnCell(3, 3) > 0, "a damaged crate still covers");
            int rounds = 1;
            boolean broke = false;
            while (!broke) {
                rounds++;
                broke = g.wear.apply(crate, RIFLE_DAMAGE, RIFLE_PEN);
                assertTrue(rounds < 20, "the crate never broke");
            }
            assertEquals(8, rounds, "100 structure at 14 a round: eight rounds");
            assertEquals("doodad.crate-destroyed", g.props.getDoodads().get(0).definitionId());
            assertEquals(0, g.props.getDoodadLevelOnCell(3, 3), "the crate's cover is gone");
            assertNull(g.wear.materialOf(crate), "a broken crate is nothing to wear");
            assertFalse(g.wear.wouldWear(crate, RIFLE_DAMAGE, RIFLE_PEN));
        }
    }

    @Test
    void aLaserBurnsATreeDownThatItsPenetrationAloneCouldNotScratch() {
        try (Ground g = new Ground()) {
            List<Doodad> trees = new ArrayList<>();
            ForestBrush.stampTrees(List.of(new ForestField.Tree(2, 2, TreeCanopy.PINE, 0)), g.grid, g.topology,
                    trees, null, false);
            trees.forEach(g.props::addDoodad);
            StruckFeature trunk = StruckFeature.cell(3, 3);
            assertEquals(EnvironmentMaterial.TIMBER, g.wear.materialOf(trunk));
            // The burst laser's round: 16 damage at penetration 3.
            assertFalse(g.wear.wouldWear(trunk, 16f, 3f));
            assertTrue(g.wear.wouldWear(trunk, 16f, 3f, true));
            int rounds = 0;
            while (!g.wear.apply(trunk, 16f, 3f, true)) assertTrue(++rounds < 20, "the tree never fell");
            assertTrue(g.grid.isWalkable(3, 3), "the felled tree's ground is open");
        }
    }

    @Test
    void theSameRoundIntoAWallDoesNothing() {
        try (Ground g = new Ground()) {
            g.wall(4, 3);
            StruckFeature wall = StruckFeature.cell(4, 3);
            assertEquals(EnvironmentMaterial.MASONRY, g.wear.materialOf(wall));
            assertFalse(g.wear.wouldWear(wall, RIFLE_DAMAGE, RIFLE_PEN));
            for (int i = 0; i < 500; i++) assertFalse(g.wear.apply(wall, RIFLE_DAMAGE, RIFLE_PEN));
            assertEquals(WALL_HP, g.grid.getWallHp(4, 3));
            assertFalse(g.grid.isWalkable(4, 3));
            assertEquals(0L, g.wear.strikesWorn());
        }
    }

    @Test
    void anAntiMaterielRoundBreachesTheWall() {
        try (Ground g = new Ground()) {
            g.wall(4, 3);
            StruckFeature wall = StruckFeature.cell(4, 3);
            assertTrue(g.wear.wouldWear(wall, AMR_DAMAGE, AMR_PEN));
            assertFalse(g.wear.apply(wall, AMR_DAMAGE, AMR_PEN));
            assertEquals(WALL_HP - 27, g.grid.getWallHp(4, 3));
            g.wear.apply(wall, AMR_DAMAGE, AMR_PEN);
            g.wear.apply(wall, AMR_DAMAGE, AMR_PEN);
            assertTrue(g.wear.apply(wall, AMR_DAMAGE, AMR_PEN), "the fourth round opens it");
            assertTrue(g.grid.isWalkable(4, 3));
            assertEquals(CellTopology.GroundKind.RUBBLE, g.topology.getGroundKind(4, 3));
            assertEquals(0, g.grid.getCellCoverAtFacing(3, 3, NavigationGrid.FACING_E), "its cover went with it");
            assertTrue(g.navigation.isNavigationTopologyDirty());
        }
    }

    @Test
    void aTrenchIsGroundAndTakesNothing() {
        try (Ground g = new Ground()) {
            DoodadDef trench = new DoodadDef("doodad.test.trench", "sheet", 0, 0,
                    DoodadCover.HEAVY, 0.3f, 1, 1, null, 0, null, FightingPositionRole.TRENCH);
            g.props.addDoodad(new Doodad(3, 3, trench));
            assertNull(g.wear.materialOf(StruckFeature.cell(3, 3)));
            assertFalse(g.wear.apply(StruckFeature.cell(3, 3), AMR_DAMAGE, AMR_PEN));
            assertEquals(3, g.props.occupantCoverAt(3, 3, 0f));
        }
    }

    @Test
    void aWindowEdgeWearsAsMasonry() {
        try (Ground g = new Ground()) {
            g.grid.tryPlaceEdgeBarrier(3, 3, Direction.W, SharedEdgeBarrier.Kind.WINDOW);
            StruckFeature window = StruckFeature.edge(3, 3, Direction.W);
            assertEquals(EnvironmentMaterial.MASONRY, g.wear.materialOf(window));
            assertFalse(g.wear.apply(window, RIFLE_DAMAGE, RIFLE_PEN));
            assertTrue(g.wear.apply(window, AMR_DAMAGE, AMR_PEN) || g.wear.apply(window, AMR_DAMAGE, AMR_PEN));
            assertNull(g.grid.getEdgeBarrier(3, 3, Direction.W), "the window is gone");
        }
    }

    @Test
    void openFloorIsNothingToWear() {
        try (Ground g = new Ground()) {
            assertNull(g.wear.materialOf(StruckFeature.cell(2, 2)));
            assertNull(g.wear.materialOf(StruckFeature.cell(-1, 2)));
        }
    }
}
