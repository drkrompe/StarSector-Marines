package com.dillon.starsectormarines.battle.world;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainField;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainPlan;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainSurface;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TreeDamageTest {
    private static final class Ground implements AutoCloseable {
        final NavigationGrid grid = new NavigationGrid(14, 14);
        final CellTopology topology = new CellTopology(14, 14);
        final DoodadService props = new DoodadService(grid);
        final NavigationService navigation;
        final MapEditor editor;
        Ground() {
            for (int y = 0; y < 14; y++) for (int x = 0; x < 14; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
            }
            navigation = new NavigationService(grid, topology, false);
            editor = new MapEditor(navigation); editor.setDoodads(props);
        }
        void add(TreeCanopy canopy, int x, int y, int turns) {
            for (int dy = 0; dy < canopy.height(turns); dy++) for (int dx = 0; dx < canopy.width(turns); dx++)
                if (canopy.blocks(dx, dy, turns)) {
                    grid.setWalkable(x + dx, y + dy, false);
                    grid.setTag(x + dx, y + dy, NavigationGrid.CellTag.NATURAL_SOLID, true);
                    topology.setFixture(x + dx, y + dy, true);
                }
            props.addDoodad(new Doodad(x, y, TileRegistry.installed().doodad(canopy.id), turns));
        }
        public void close() { navigation.close(); }
    }
    @Test void anyCrownCellDamagesTheWholeRotatedTreeAndFellingReleasesItsFootprint() {
        for (TreeCanopy canopy : TreeCanopy.values()) for (int turns = 0; turns < 4; turns++) try (Ground g = new Ground()) {
            g.add(canopy, 3, 3, turns);
            int hx = 3 + canopy.width(turns) / 2, hy = 3;
            while (!canopy.blocks(hx - 3, hy - 3, turns)) hy++;
            assertFalse(g.editor.damageWall(hx, hy, 0));
            assertFalse(g.editor.damageWall(hx, hy, -1));
            assertEquals(canopy.id, g.props.getDoodads().get(0).definitionId());
            assertFalse(g.editor.damageWall(hx, hy, 50));
            assertEquals(canopy.id + "-damaged", g.props.getDoodads().get(0).definitionId());
            assertFalse(g.grid.isWalkable(hx, hy));
            assertTrue(g.grid.blocksLineOfSightAt(g.grid.index(hx, hy)));
            assertTrue(g.editor.damageWall(hx, hy, 150));
            Doodad debris = g.props.getDoodads().get(0);
            assertEquals(canopy.id + "-destroyed", debris.definitionId());
            assertEquals(turns, debris.quarterTurns);
            for (int y = 3; y < 3 + canopy.height(turns); y++) for (int x = 3; x < 3 + canopy.width(turns); x++) {
                assertTrue(g.grid.isWalkable(x, y));
                assertFalse(g.grid.blocksLineOfSightAt(g.grid.index(x, y)));
                assertFalse(g.grid.isDoorway(x, y));
                assertFalse(g.topology.isFixture(x, y));
                assertEquals(CellTopology.GroundKind.GRASS, g.topology.getGroundKind(x, y));
            }
            assertFalse(g.editor.damageWall(hx, hy, 999));
            assertSame(debris, g.props.getDoodads().get(0));
            assertTrue(g.navigation.isNavigationTopologyDirty());
            g.navigation.flushNavigationTopologyIfDirty();
            assertFalse(g.navigation.isNavigationTopologyDirty());
        }
    }
    @Test void oneBlastDoesNotMultiplyDamageByTheNumberOfCanopyCells() {
        try (Ground g = new Ground()) {
            g.add(TreeCanopy.OAK, 3, 3, 0);
            g.editor.damageWallsInRadius(5.5f, 5.5f, 4, 100, null);
            assertEquals("forest.oak-damaged", g.props.getDoodads().get(0).definitionId());
            assertFalse(g.grid.isWalkable(5, 5));
            int[] fallen = {0};
            g.editor.damageWallsInRadius(5.5f, 5.5f, 4, 100, (x, y) -> fallen[0]++);
            assertEquals(1, fallen[0]);
            assertEquals("forest.oak-destroyed", g.props.getDoodads().get(0).definitionId());
        }
    }
    @Test void survivingOverlapKeepsItsObstructionAndCover() {
        try (Ground g = new Ground()) {
            g.add(TreeCanopy.OAK, 2, 3, 0); g.add(TreeCanopy.OAK, 5, 3, 0);
            assertTrue(g.editor.damageWall(3, 5, 200));
            assertTrue(g.grid.isWalkable(3, 5));
            assertFalse(g.grid.isWalkable(5, 5));
            assertTrue(g.grid.blocksLineOfSightAt(g.grid.index(5, 5)));
            assertEquals("forest.oak", g.props.getDoodads().get(1).definitionId());
            assertTrue(g.editor.damageWall(8, 5, 200));
            assertTrue(g.grid.isWalkable(5, 5));
            assertFalse(g.grid.blocksLineOfSightAt(g.grid.index(5, 5)));
        }
    }
    @Test void destroyedSummitTreeDoesNotRemoveTheHill() {
        try (Ground g = new Ground()) {
            TerrainPlan plan = new TerrainPlan(14, 14, TerrainPlan.Finish.GRASS_TOP, 1, 0, 7,
                    List.of(new TerrainPlan.Stroke(TerrainPlan.Mode.RAISE, 5, List.of(new TerrainPlan.Point(6, 6)))));
            TerrainSurface hill = TerrainSurface.build(plan, TerrainField.build(plan), g.topology);
            g.topology.setTerrainSurface(hill);
            g.add(TreeCanopy.PINE, 5, 5, 0);
            assertTrue(g.editor.damageWall(6, 6, 200));
            for (int y = 5; y < 8; y++) for (int x = 5; x < 8; x++) {
                assertNotNull(hill.patch(x, y, 6));
                assertFalse(g.grid.isWalkable(x, y));
                assertTrue(g.grid.hasTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID));
                assertTrue(g.topology.isFixture(x, y));
                assertFalse(g.grid.damageCell(x, y, 9999));
            }
            assertSame(hill, g.topology.terrainSurface());
            assertFalse(g.navigation.isNavigationTopologyDirty());
        }
    }
}
