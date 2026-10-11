package com.dillon.starsectormarines.battle.world.gen.forest;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainBrush;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainPlan;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ForestBrushTest {
    @Test void productionCrownsFitGrassySummitsButRefuseBareRockAndCliffFaces() {
        var tree = new ForestField.Tree(10, 10, TreeCanopy.PINE, 0);
        for (var finish : TerrainPlan.Finish.values()) {
            var grid = new NavigationGrid(24, 24); var topology = ground(grid);
            var field = TerrainBrush.stamp(new TerrainPlan(24, 24, finish, 2, 0, 1, List.of(
                    new TerrainPlan.Stroke(TerrainPlan.Mode.RAISE, 8, List.of(new TerrainPlan.Point(12, 12))))), grid, topology);
            List<Doodad> art = new ArrayList<>();
            for (int y = 0; y < 24; y++) for (int x = 0; x < 24; x++) {
                boolean fullCap = finish == TerrainPlan.Finish.GRASS_TOP && field.corner(x, y) >= 2
                        && field.corner(x + 1, y) >= 2 && field.corner(x, y + 1) >= 2 && field.corner(x + 1, y + 1) >= 2;
                assertEquals(fullCap, ForestBrush.isGrassySummit(grid, topology, x, y),
                        "a clipped four-vertex polygon is not necessarily a full cell");
            }
            if (finish == TerrainPlan.Finish.GRASS_TOP) {
                ForestBrush.stampTrees(List.of(tree), grid, topology, art, null, true);
                assertEquals(1, art.size());
                assertFalse(grid.isWalkable(11, 11));
                assertFalse(topology.isWall(11, 11));
            } else assertThrows(IllegalArgumentException.class,
                    () -> ForestBrush.stampTrees(List.of(tree), grid, topology, art, null, true));
            var faceTree = new ForestField.Tree(4, 10, TreeCanopy.PINE, 0);
            assertThrows(IllegalArgumentException.class,
                    () -> ForestBrush.stampTrees(List.of(faceTree), grid, topology, art, null, true));
        }
    }

    static ForestPlan plan() {
        return new ForestPlan(24, 24, ForestPlan.Woodland.MIXED, 7821, List.of(
                new ForestPlan.Stroke(ForestPlan.Mode.PAINT, 8, List.of(new ForestPlan.Point(12, 4), new ForestPlan.Point(12, 20)))));
    }
    @Test void wholeCanopiesBecomeOpaquePermanentObstaclesWithoutWallOrRoofIdentity() {
        var plan = plan(); var grid = new NavigationGrid(24, 24); var topology = ground(grid);
        List<Doodad> art = new ArrayList<>();
        ForestField field = ForestBrush.stamp(plan, grid, topology, art, null);
        assertTrue(art.size() > 8);
        int count = 0;
        for (int y = 0; y < 24; y++) for (int x = 0; x < 24; x++) {
            assertEquals(!field.blocked(x, y), grid.isWalkable(x, y));
            if (!field.blocked(x, y)) continue;
            count++;
            assertFalse(grid.isSeeThrough(x, y));
            assertTrue(grid.hasTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID));
            assertTrue(topology.isFixture(x, y));
            assertFalse(topology.isWall(x, y));
            assertEquals(0, topology.getBuildingId(x, y));
            assertFalse(grid.damageCell(x, y, 100000), "a structural blast must not open invisible paths under intact crowns");
        }
        assertTrue(count > art.size() * 3, "branches occupy several cells beyond each trunk");
        assertEquals(field.trees().size(), art.size());
    }

    @Test void protectedCanopyEdgeRefusesTheWholeStampBeforeMutatingAnything() {
        var plan = plan(); var field = ForestField.build(plan); var tree = field.trees().get(0);
        int px = -1, py = -1;
        for (int y = tree.y(); y < tree.y() + tree.height() && px < 0; y++)
            for (int x = tree.x(); x < tree.x() + tree.width(); x++) if (tree.blocks(x, y)) { px = x; py = y; break; }
        var grid = new NavigationGrid(24, 24); var topology = ground(grid); List<Doodad> art = new ArrayList<>();
        boolean[] reserved = new boolean[24 * 24]; reserved[py * 24 + px] = true;
        assertThrows(IllegalArgumentException.class, () -> ForestBrush.stamp(plan, grid, topology, art, reserved));
        assertTrue(art.isEmpty());
        for (int y = 0; y < 24; y++) for (int x = 0; x < 24; x++) assertTrue(grid.isWalkable(x, y));
        topology.setGroundKind(px, py, CellTopology.GroundKind.STREET);
        assertThrows(IllegalArgumentException.class, () -> ForestBrush.stamp(plan, grid, topology, art, null));
        assertTrue(art.isEmpty());
    }

    private static CellTopology ground(NavigationGrid grid) {
        CellTopology topology = new CellTopology(24, 24);
        for (int y = 0; y < 24; y++) for (int x = 0; x < 24; x++) {
            grid.setWalkableFloor(x, y); topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
        }
        return topology;
    }
}
