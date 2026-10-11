package com.dillon.starsectormarines.battle.world;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.world.model.*;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrateDamageTest {
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
        void crate(String id, int x, int y, boolean blocks, int turn, boolean mirror) {
            if (blocks) {
                grid.setWalkable(x, y, false); grid.setSeeThrough(x, y, true);
                topology.setFixture(x, y, true);
            }
            props.addDoodad(new Doodad(x, y, TileRegistry.installed().doodad(id), turn, mirror));
        }
        public void close() { navigation.close(); }
    }

    @Test void cratesBreakInEveryOrientationForBothWalkableAndBlockingPlacements() {
        for (String id : CoverPropDamage.CRATES) for (boolean blocked : new boolean[]{false, true})
            for (int turn = 0; turn < 4; turn++) for (boolean mirror : new boolean[]{false, true})
                try (Ground g = new Ground()) {
                    g.crate(id, 3, 3, blocked, turn, mirror);
                    g.topology.setSurface(3, 3, g.topology.surfaceIndex("floors.stone"));
                    int hp = id.equals("doodad.box-dam") ? 50 : 100;
                    int cover = TileRegistry.installed().doodad(id).cover.level();
                    assertFalse(g.editor.damageWall(3, 3, 0));
                    assertFalse(g.editor.damageWall(3, 3, -1));
                    assertEquals(id, g.props.getDoodads().get(0).definitionId());
                    assertFalse(g.editor.damageWall(3, 3, hp - 1));
                    assertEquals(id + "-damaged", g.props.getDoodads().get(0).definitionId());
                    assertEquals(!blocked, g.grid.isWalkable(3, 3));
                    assertEquals(cover, blocked ? g.grid.getCellCoverAtFacing(2, 3, NavigationGrid.FACING_E)
                            : g.props.getDoodadLevelOnCell(3, 3));
                    assertTrue(g.editor.damageWall(3, 3, 1));
                    var debris = g.props.getDoodads().get(0);
                    assertEquals(id + "-destroyed", debris.definitionId());
                    assertEquals(turn, debris.quarterTurns); assertEquals(mirror, debris.mirrored);
                    assertEquals(0, debris.cover);
                    assertEquals(0, g.props.getDoodadLevelOnCell(3, 3));
                    assertEquals(0, g.props.getDoodadCoverAt(2, 3));
                    assertEquals(0, g.grid.getCellCoverAtFacing(2, 3, NavigationGrid.FACING_E));
                    assertTrue(g.grid.isWalkable(3, 3));
                    assertFalse(g.grid.isDoorway(3, 3)); assertFalse(g.topology.isFixture(3, 3));
                    assertEquals(CellTopology.GroundKind.GRASS, g.topology.getGroundKind(3, 3));
                    assertEquals("floors.stone", g.topology.getSurfaceId(3, 3));
                    assertEquals(blocked, g.navigation.isNavigationTopologyDirty());
                    assertFalse(g.editor.damageWall(3, 3, 999));
                    assertSame(debris, g.props.getDoodads().get(0));
                }
    }

    @Test void removingCrateCoverPreservesStaticPropsAndNeighboringCrates() {
        try (Ground g = new Ground()) {
            g.crate("doodad.crate", 3, 3, false, 0, false);
            g.crate("doodad.industrial-crate-stack", 4, 3, false, 0, false);
            g.props.addStaticCover(3, 3, 1, .7f);
            assertEquals(2, g.props.getDoodadLevelOnCell(3, 3, .3f));
            assertEquals(1, g.props.getDoodadLevelOnCell(3, 3, .5f));
            assertTrue(g.editor.damageWall(3, 3, 100));
            assertEquals(1, g.props.getDoodadLevelOnCell(3, 3, .3f));
            assertEquals(3, g.props.getDoodadCoverAtFacing(3, 3, NavigationGrid.FACING_E));
            assertEquals(3, g.props.getDoodadLevelOnCell(4, 3, .5f));
        }
    }

    @Test void oneBlastBreaksCratesAndReleasesBlockersWithoutLosingNeighborCover() {
        try (Ground g = new Ground()) {
            g.crate("doodad.industrial-crate-stack", 3, 3, true, 0, false);
            g.crate("doodad.crate", 4, 3, true, 0, false);
            g.editor.damageWallsInRadius(3.5f, 3.5f, .7f, 40, null);
            assertEquals("doodad.industrial-crate-stack-damaged", g.props.getDoodads().get(0).definitionId());
            g.editor.damageWallsInRadius(3.5f, 3.5f, .7f, 60, null);
            assertTrue(g.grid.isWalkable(3, 3)); assertFalse(g.grid.isWalkable(4, 3));
            assertEquals(2, g.grid.getCellCoverAtFacing(3, 3, NavigationGrid.FACING_E));
            assertEquals("doodad.crate", g.props.getDoodads().get(1).definitionId());
        }
    }

    @Test void coLocatedWalkableCratesRetainTheSurvivingProfile() {
        try (Ground g = new Ground()) {
            g.crate("doodad.box-dam", 3, 3, false, 0, false);
            g.crate("doodad.industrial-crate-stack", 3, 3, false, 0, false);
            assertFalse(g.editor.damageWall(3, 3, 50));
            assertEquals("doodad.box-dam-destroyed", g.props.getDoodads().get(0).definitionId());
            assertEquals(3, g.props.getDoodadLevelOnCell(3, 3, .5f));
            assertTrue(g.editor.damageWall(3, 3, 50));
            assertEquals(0, g.props.getDoodadLevelOnCell(3, 3));
            assertFalse(g.navigation.isNavigationTopologyDirty());
        }
    }
}
