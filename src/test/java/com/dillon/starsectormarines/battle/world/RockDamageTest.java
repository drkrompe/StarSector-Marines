package com.dillon.starsectormarines.battle.world;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.battle.world.model.RockDamage;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RockDamageTest {
    private static final class Ground implements AutoCloseable {
        final NavigationGrid grid = new NavigationGrid(8, 8);
        final CellTopology topology = new CellTopology(8, 8);
        final DoodadService props = new DoodadService(grid);
        final NavigationService navigation;
        final MapEditor editor;
        Ground() {
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
            }
            navigation = new NavigationService(grid, topology, false);
            editor = new MapEditor(navigation); editor.setDoodads(props);
        }
        void rock(String id, int x, int y) {
            var def = TileRegistry.installed().tile(id);
            topology.setNatureOverlayIndex(x, y, def.index);
            if (!def.passable) {
                grid.setWalkable(x, y, false); grid.setSeeThrough(x, y, true);
                topology.setFixture(x, y, true);
            }
            props.addNatureOverlayCover(topology, TileRegistry.installed());
        }
        public void close() { navigation.close(); }
    }

    @Test void everyRockCracksThenShattersWithoutReplacingItsGround() {
        for (String id : RockDamage.IDS) try (Ground g = new Ground()) {
            g.rock(id, 3, 3);
            boolean large = id.contains("large");
            int hp = large ? 200 : id.contains("medium") ? 100 : 50;
            int overlay = g.topology.getNatureOverlayIndex(3, 3);
            assertFalse(g.editor.damageWall(3, 3, 0));
            assertFalse(g.editor.damageWall(3, 3, -1));
            assertEquals(overlay, g.topology.getNatureOverlayIndex(3, 3));
            assertTrue(g.props.getDoodads().isEmpty());
            assertFalse(g.editor.damageWall(3, 3, hp - 1));
            assertEquals(id + "-damaged", g.props.getDoodads().get(0).definitionId());
            assertEquals(-1, g.topology.getNatureOverlayIndex(3, 3));
            assertEquals(!large, g.grid.isWalkable(3, 3));
            assertTrue(g.editor.damageWall(3, 3, 1));
            var rubble = g.props.getDoodads().get(0);
            assertEquals(id + "-destroyed", rubble.definitionId());
            assertEquals(0, rubble.cover);
            assertTrue(g.grid.isWalkable(3, 3));
            assertFalse(g.grid.isDoorway(3, 3));
            assertFalse(g.topology.isFixture(3, 3));
            assertEquals(CellTopology.GroundKind.GRASS, g.topology.getGroundKind(3, 3));
            assertFalse(g.editor.damageWall(3, 3, 999));
            assertSame(rubble, g.props.getDoodads().get(0));
            assertEquals(large, g.navigation.isNavigationTopologyDirty());
        }
    }

    @Test void shatteredMediumRockReleasesPhysicalAndNeighborCoverButRetainsOverlappingProps() {
        try (Ground g = new Ground()) {
            g.rock("nature.rock-medium-1", 3, 3);
            assertEquals(1, g.props.getDoodadLevelOnCell(3, 3, .17f));
            assertEquals(0, g.props.getDoodadLevelOnCell(3, 3, .19f));
            assertEquals(1, g.props.getDoodadCoverAtFacing(3, 2, NavigationGrid.FACING_S));
            g.editor.damageWall(3, 3, 1);
            assertEquals(1, g.props.getDoodadLevelOnCell(3, 3));
            g.editor.damageWall(3, 3, 99);
            assertEquals(0, g.props.getDoodadLevelOnCell(3, 3));
            assertEquals(0, g.props.getDoodadCoverAt(3, 2));
            g.rock("nature.rock-medium-2", 5, 3);
            g.props.addStaticCover(5, 3, 2, .1f);
            g.editor.damageWall(5, 3, 100);
            assertEquals(2, g.props.getDoodadLevelOnCell(5, 3, .05f));
            assertEquals(0, g.props.getDoodadLevelOnCell(5, 3, .17f));
            assertEquals(2, g.props.getDoodadCoverAtFacing(4, 3, NavigationGrid.FACING_E));
        }
    }

    @Test void boulderBreachRetainsAdjacentDamagedBoulderCover() {
        try (Ground g = new Ground()) {
            g.rock("nature.rock-large-1", 3, 3);
            g.rock("nature.rock-large-2", 4, 3);
            g.editor.damageWall(4, 3, 1);
            assertTrue(g.editor.damageWall(3, 3, 200));
            assertEquals(3, g.grid.getCoverAtFacing(3, 3, NavigationGrid.FACING_E));
            assertEquals(.6f, g.grid.getCoverCatchHalfHeightAtFacing(3, 3, NavigationGrid.FACING_E), .001f);
            assertEquals(0, g.props.getDoodadLevelOnCell(4, 3));
            assertFalse(g.grid.isWalkable(4, 3));
            assertTrue(g.editor.damageWall(4, 3, 199));
            assertEquals(0, g.grid.getCoverAtFacing(3, 3, NavigationGrid.FACING_E));
        }
    }

    @Test void blastDamagesEachRockOnceAndCliffsRemainPermanent() {
        try (Ground g = new Ground()) {
            g.rock("nature.rock-large-1", 3, 3);
            g.rock("nature.rock-medium-1", 4, 3);
            g.editor.damageWallsInRadius(4, 3.5f, 2, 100, null);
            assertFalse(g.grid.isWalkable(3, 3));
            assertEquals("nature.rock-large-1-damaged", g.props.getDoodads().get(0).definitionId());
            assertEquals("nature.rock-medium-1-destroyed", g.props.getDoodads().get(1).definitionId());
            g.rock("nature.rock-large-3", 6, 6);
            g.grid.setTag(6, 6, NavigationGrid.CellTag.NATURAL_SOLID, true);
            assertFalse(g.editor.damageWall(6, 6, 9999));
            assertFalse(g.grid.isWalkable(6, 6));
            assertTrue(g.topology.getNatureOverlayIndex(6, 6) >= 0);
        }
    }
}
