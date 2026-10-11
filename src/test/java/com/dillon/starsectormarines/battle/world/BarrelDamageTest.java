package com.dillon.starsectormarines.battle.world;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.world.model.*;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BarrelDamageTest {
    private static final class Ground implements AutoCloseable {
        final NavigationGrid grid = new NavigationGrid(10, 10);
        final CellTopology topology = new CellTopology(10, 10);
        final DoodadService props = new DoodadService(grid);
        final NavigationService navigation;
        final MapEditor editor;
        Ground() {
            for (int y = 0; y < 10; y++) for (int x = 0; x < 10; x++) {
                grid.setWalkableFloor(x, y); topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
            }
            navigation = new NavigationService(grid, topology, false);
            editor = new MapEditor(navigation); editor.setDoodads(props);
        }
        void barrel(String id, int x, int y, boolean blocks, int turn, boolean mirror) {
            Doodad d = new Doodad(x, y, TileRegistry.installed().doodad(id), turn, mirror);
            if (blocks) for (int yy = y; yy < y + d.footprintCellsY; yy++) for (int xx = x; xx < x + d.footprintCellsX; xx++) {
                grid.setWalkable(xx, yy, false); grid.setSeeThrough(xx, yy, true); topology.setFixture(xx, yy, true);
            }
            props.addDoodad(d);
        }
        public void close() { navigation.close(); }
    }

    @Test void everyStyleAndOrientationDentsThenCollapsesWithoutChangingGroundOrUnderlyingStorage() {
        for (String id : BarrelDamage.IDS) for (boolean blocked : new boolean[]{false, true})
            for (int turn = 0; turn < 4; turn++) for (boolean mirror : new boolean[]{false, true})
                try (Ground g = new Ground()) {
                    g.barrel(id, 3, 3, blocked, turn, mirror);
                    var original = g.props.getDoodads().get(0);
                    int hx = 3 + original.footprintCellsX - 1, hy = 3 + original.footprintCellsY - 1;
                    g.topology.setSurface(hx, hy, g.topology.surfaceIndex("floors.stone"));
                    assertFalse(g.editor.damageWall(hx, hy, 0)); assertFalse(g.editor.damageWall(hx, hy, -5));
                    assertSame(original, g.props.getDoodads().get(0));
                    assertFalse(g.editor.damageWall(hx, hy, 99));
                    assertEquals(id + "-damaged", g.props.getDoodads().get(0).definitionId());
                    assertEquals(!blocked, g.grid.isWalkable(hx, hy));
                    assertTrue(g.editor.damageWall(hx, hy, 1));
                    var debris = g.props.getDoodads().get(0);
                    assertEquals(id + "-destroyed", debris.definitionId());
                    assertEquals(turn, debris.quarterTurns); assertEquals(mirror, debris.mirrored);
                    assertEquals(original.footprintCellsX, debris.footprintCellsX); assertEquals(0, debris.cover);
                    boolean opens = blocked && original.cover > 0;
                    for (int y = 3; y <= hy; y++) for (int x = 3; x <= hx; x++) {
                        assertEquals(!blocked || opens, g.grid.isWalkable(x, y));
                        assertEquals(blocked && !opens, g.topology.isFixture(x, y));
                        assertFalse(g.grid.isDoorway(x, y));
                        assertEquals(CellTopology.GroundKind.GRASS, g.topology.getGroundKind(x, y));
                    }
                    assertEquals("floors.stone", g.topology.getSurfaceId(hx, hy));
                    assertEquals(opens, g.navigation.isNavigationTopologyDirty());
                    assertFalse(g.editor.damageWall(hx, hy, 999)); assertSame(debris, g.props.getDoodads().get(0));
                }
    }

    @Test void destroyingStockRetainsItsPalletCoverAndObstruction() {
        try (Ground g = new Ground()) {
            for (int y = 3; y < 5; y++) for (int x = 3; x < 5; x++) {
                g.grid.setWalkable(x, y, false); g.topology.setFixture(x, y, true);
            }
            Doodad pallet = new Doodad(3, 3, TileRegistry.installed().doodad("main-stores.pallet"), 0);
            g.props.addDoodad(pallet);
            g.barrel("main-stores.drums", 3, 3, true, 0, false);
            int cover = g.grid.getCellCoverAtFacing(2, 3, NavigationGrid.FACING_E);
            assertEquals(2, cover);
            assertTrue(g.editor.damageWall(4, 4, 100));
            assertSame(pallet, g.props.getDoodads().get(0));
            assertEquals(cover, g.grid.getCellCoverAtFacing(2, 3, NavigationGrid.FACING_E));
            assertFalse(g.editor.damageWall(3, 3, 1000));
            assertFalse(g.grid.isWalkable(3, 3)); assertTrue(g.topology.isFixture(3, 3));
            assertFalse(g.navigation.isNavigationTopologyDirty());
        }
    }

    @Test void aBlastSpendsTheWholeFourCellGroupOnlyOnce() {
        try (Ground g = new Ground()) {
            g.barrel("main-stores.drums", 3, 3, false, 1, true);
            g.editor.damageWallsInRadius(4, 4, 3, 60, null);
            assertEquals("main-stores.drums-damaged", g.props.getDoodads().get(0).definitionId());
            int[] events = {0};
            g.editor.damageWallsInRadius(4, 4, 3, 40, (x,y) -> events[0]++);
            assertEquals(1, events[0]);
            assertEquals("main-stores.drums-destroyed", g.props.getDoodads().get(0).definitionId());
            assertFalse(g.navigation.isNavigationTopologyDirty());
        }
    }

    @Test void physicalCoverDisappearsButStaticPropsAndNearbyBarrelsSurvive() {
        try (Ground g = new Ground()) {
            g.barrel("doodad.industrial-drum-cluster", 3, 3, false, 0, false);
            g.barrel("doodad.industrial-drum-cluster", 4, 3, false, 0, false);
            g.props.addStaticCover(3, 3, 1, .6f);
            assertEquals(2, g.props.getDoodadLevelOnCell(3, 3, .4f));
            assertEquals(1, g.props.getDoodadLevelOnCell(3, 3, .5f));
            assertTrue(g.editor.damageWall(3, 3, 100));
            assertEquals(1, g.props.getDoodadLevelOnCell(3, 3, .4f));
            assertEquals(2, g.props.getDoodadCoverAtFacing(3, 3, NavigationGrid.FACING_E));
            assertEquals(2, g.props.getDoodadLevelOnCell(4, 3));
        }
    }

    @Test void breachRestoresDamagedNeighborsAndNeverExcavatesNaturalTerrain() {
        try (Ground g = new Ground()) {
            g.barrel("doodad.industrial-drum-cluster", 3, 3, true, 0, false);
            g.barrel("doodad.industrial-drum-cluster", 4, 3, true, 0, false);
            g.editor.damageWall(4, 3, 1);
            assertTrue(g.editor.damageWall(3, 3, 100));
            assertEquals(2, g.grid.getCellCoverAtFacing(3, 3, NavigationGrid.FACING_E));
            assertTrue(g.editor.damageWall(4, 3, 99));
            assertEquals(0, g.grid.getCellCoverAtFacing(3, 3, NavigationGrid.FACING_E));
            g.grid.setTag(6, 6, NavigationGrid.CellTag.NATURAL_SOLID, true);
            g.barrel("doodad.industrial-drum-cluster", 6, 6, true, 0, false);
            g.editor.damageWall(6, 6, 100);
            assertFalse(g.grid.isWalkable(6, 6));
            assertTrue(g.grid.hasTag(6, 6, NavigationGrid.CellTag.NATURAL_SOLID));
        }
    }
}
