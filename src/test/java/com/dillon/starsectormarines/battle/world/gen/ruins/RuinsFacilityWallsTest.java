package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vehicle.WallCrush;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The ruins facility's walls hold against every wall crush ({@link RuinsDelveStage#fortify}). */
class RuinsFacilityWallsTest {

    private static final int W = 6, H = 3;

    @Test
    void theFacilitysClosedCellsTakeFortressStructureAndNothingElseDoes() {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        wall(grid, topology, 0, 1);              // the facility's wall
        wall(grid, topology, 1, 1);              // a dead door's leaf
        topology.setFixture(1, 1, true);
        wall(grid, topology, 2, 1);              // a prop on the facility's ground
        wall(grid, topology, 4, 1);              // a ruined lot's wall, off the facility
        boolean[] facility = new boolean[W * H];
        for (int x = 0; x < 4; x++) facility[W + x] = true;
        boolean[] props = new boolean[W * H];
        props[W + 2] = true;

        assertEquals(1, RuinsDelveStage.fortify(grid, topology, facility, props, RuinsDelveStage.FACILITY_WALL_HP));
        assertEquals(RuinsDelveStage.FACILITY_WALL_HP, grid.getWallHp(0, 1));
        assertEquals(0, grid.getWallHp(1, 1), "a leaf is a fixture, never a wall");
        assertEquals(0, grid.getWallHp(2, 1), "a prop keeps its own structure");
        assertEquals(0, grid.getWallHp(3, 1), "open floor stays open");
        assertEquals(0, grid.getWallHp(4, 1), "a ruined lot is not the facility");
    }

    @Test
    void noHeavyPushesAFacilityWallOrADeadLeafAndNoRoutePlansThroughThem() {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        wall(grid, topology, 0, 1);
        grid.setWallHp(0, 1, RuinsDelveStage.FACILITY_WALL_HP);
        grid.setWalkable(1, 1, false);           // a dead leaf: closed, a fixture, no wall
        topology.setFixture(1, 1, true);
        wall(grid, topology, 4, 1);
        grid.setWallHp(4, 1, 100);               // an ordinary ruined wall
        assertTrue(RuinsDelveStage.FACILITY_WALL_HP > WallCrush.ULTRA_HEAVY_CEILING);
        assertFalse(WallCrush.canPush(grid, topology, null, 0, 1, WallCrush.ULTRA_HEAVY_CEILING));
        assertFalse(WallCrush.canPush(grid, topology, null, 1, 1, WallCrush.ULTRA_HEAVY_CEILING));
        assertTrue(WallCrush.canPush(grid, topology, null, 4, 1, WallCrush.HEAVY_CEILING));
        NavigationGrid view = WallCrush.breachView(grid, topology, null);
        assertFalse(view.isWalkable(0, 1) || view.hasTag(0, 1, NavigationGrid.CellTag.BREACH));
        assertFalse(view.isWalkable(1, 1) || view.hasTag(1, 1, NavigationGrid.CellTag.BREACH));
        assertTrue(view.hasTag(4, 1, NavigationGrid.CellTag.BREACH));
    }

    private static void wall(NavigationGrid grid, CellTopology topology, int x, int y) {
        grid.setWalkable(x, y, false);
        topology.setWall(x, y, true);
    }
}
