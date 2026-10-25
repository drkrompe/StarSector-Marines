package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.creep.CreepField;
import com.dillon.starsectormarines.battle.creep.CreepGrowth;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingFireTest {

    private static final int W = 12, H = 6;

    /** Building 1 in x 1..4 (walls round an indoor floor), building 2 in x 6..9 likewise, its walls at {@code hp2}. */
    private static BattleFire twoHouses(int hp2, GroundKind floor2) {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topo = new CellTopology(W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            grid.setWalkableFloor(x, y);
            topo.setGroundKind(x, y, GroundKind.GRASS);
        }
        house(grid, topo, 1, 1, 100, GroundKind.INDOOR);
        house(grid, topo, 6, 2, hp2, floor2);
        return new BattleFire(grid, topo, null, null, () -> null, 1L);
    }

    private static void house(NavigationGrid grid, CellTopology topo, int x0, int id, int hp, GroundKind floor) {
        for (int y = 1; y <= 4; y++) for (int x = x0; x <= x0 + 3; x++) {
            topo.setBuildingId(x, y, id);
            boolean wall = y == 1 || y == 4 || x == x0 || x == x0 + 3;
            if (wall) {
                grid.setWalkable(x, y, false);
                grid.setWallHp(x, y, hp);
                topo.setWall(x, y, true);
            } else {
                topo.setGroundKind(x, y, floor);
            }
        }
    }

    private static int idx(int x, int y) { return y * W + x; }

    @Test
    void lightWallsAndFurnishedFloorsBurn() {
        BattleFire fire = twoHouses(100, GroundKind.INDOOR);
        assertEquals(FireFuel.STRUCTURE.bit, fire.fuelMask(idx(1, 2)), "a light wall is structure fuel");
        assertEquals(FireFuel.FURNISHING.bit, fire.fuelMask(idx(2, 2)), "an indoor floor is furnishing");
        assertTrue(fire.blocksHeat(idx(1, 2)), "a standing wall stops heat passing over it");
        assertFalse(fire.blocksHeat(idx(2, 2)));
    }

    @Test
    void aFortifiedStructureBurnsNowhere() {
        BattleFire fire = twoHouses(150, GroundKind.INDOOR);
        assertEquals(0, fire.fuelMask(idx(6, 2)), "a fortified wall does not burn");
        assertEquals(0, fire.fuelMask(idx(7, 2)), "nor does a floor in a fortified structure");
        assertEquals(FireFuel.FURNISHING.bit, fire.fuelMask(idx(2, 2)), "the light house beside it still does");
    }

    @Test
    void aMilitaryFloorMakesTheStructureFortified() {
        BattleFire fire = twoHouses(100, GroundKind.STRIPED);
        assertEquals(0, fire.fuelMask(idx(7, 2)));
        assertEquals(0, fire.fuelMask(idx(6, 2)));
    }

    @Test
    void creepBurnsOverGrassButIsAFilmOverClearedGround() {
        // A row of grass, a dirt ride and a road, all overgrown by one source.
        NavigationGrid grid = new NavigationGrid(W, 1);
        CellTopology topo = new CellTopology(W, 1);
        for (int x = 0; x < W; x++) {
            grid.setWalkableFloor(x, 0);
            topo.setGroundKind(x, 0, x < 4 ? GroundKind.GRASS : x < 8 ? GroundKind.DIRT : GroundKind.STREET);
        }
        CreepField creep = new CreepField(grid, topo, 1L);
        creep.putSource(1L, 6, 0, new CreepGrowth(6f, 12f));
        creep.mature();
        BattleFire fire = new BattleFire(grid, topo, null, null, () -> creep, 1L);
        assertTrue(creep.coverageAt(2) >= CreepField.CREEPED && creep.coverageAt(10) >= CreepField.CREEPED, "the creep covers the row");
        assertEquals(FireFuel.GRASS.bit | FireFuel.CREEP.bit, fire.fuelMask(2), "creep over grass is fuel");
        assertEquals(0, fire.fuelMask(5), "creep over a bare ride does not burn");
        assertEquals(0, fire.fuelMask(10), "nor over a road");
    }

    @Test
    void aWoodenPropBurnsAndIsLeftAsItsDestroyedArt() {
        NavigationGrid grid = new NavigationGrid(W, 1);
        CellTopology topo = new CellTopology(W, 1);
        for (int x = 0; x < W; x++) {
            grid.setWalkableFloor(x, 0);
            topo.setGroundKind(x, 0, GroundKind.DIRT);
        }
        DoodadService props = new DoodadService(grid);
        props.addDoodad(new Doodad(3, 0, TileRegistry.installed().doodad("doodad.crate"), 0, false));
        props.addDoodad(new Doodad(6, 0, TileRegistry.installed().doodad("doodad.fort-sandbag-n"), 0, false));
        try (NavigationService navigation = new NavigationService(grid, topo, false)) {
            MapEditor editor = new MapEditor(navigation);
            editor.setDoodads(props);
            BattleFire fire = new BattleFire(grid, topo, props, editor, () -> null, 1L);
            assertEquals(FireFuel.DEBRIS.bit, fire.fuelMask(3), "a crate is debris fuel");
            assertEquals(0, fire.fuelMask(6), "a sandbag is not");
            assertEquals(0, fire.fuelMask(5), "nor is bare dirt");
            fire.burntOut(3, FireFuel.DEBRIS.bit);
            assertEquals("doodad.crate-destroyed", props.getDoodads().get(0).definitionId(), "burnt out, the crate is wreckage");
            assertEquals(0, fire.fuelMask(3), "and wreckage does not burn again");
            fire.burntOut(3, FireFuel.DEBRIS.bit);
            assertEquals(GroundKind.DIRT, topo.getGroundKind(3, 0), "a cell with nothing standing is never breached");
        }
    }

    @Test
    void heatDoesNotPassOverAStandingWall() {
        // Dead creep either side of a one-cell wall: a fire on the west must not reach the east. It carries a fire
        // as far as it stands, so the front reaches the wall from the far edge.
        int w = 20, h = 5, wallX = 10;
        FireTerrain walled = new FireTerrain() {
            @Override public int fuelMask(int i) { return i % w == wallX ? 0 : FireFuel.DEAD_CREEP.bit; }
            @Override public boolean blocksHeat(int i) { return i % w == wallX; }
        };
        FireTerrain open = i -> i % w == wallX ? 0 : FireFuel.DEAD_CREEP.bit;
        assertTrue(crossed(new FireField(w, h, 3L, open), wallX), "control: without the wall the fire jumps the gap");
        assertFalse(crossed(new FireField(w, h, 3L, walled), wallX), "heat stops at the wall");
    }

    private static boolean crossed(FireField f, int wallX) {
        for (int y = 0; y < 5; y++) f.addHeat(0, y, 5000);
        for (int t = 0; t < 30 * 40; t++) f.tick(t);
        for (int y = 0; y < 5; y++) if (f.stateAt(y * 20 + wallX + 1) != FireField.UNBURNT) return true;
        return false;
    }

    @Test
    void aTreeCrownBurnsRoundNotAsItsSquareFootprint() {
        int[] fourByFour = {10, 20, 4, 4};
        assertTrue(BattleFire.inCanopy(11, 21, fourByFour), "the middle of the crown");
        assertTrue(BattleFire.inCanopy(10, 21, fourByFour), "the middle of its edge");
        assertFalse(BattleFire.inCanopy(10, 20, fourByFour), "not its corner");
        assertFalse(BattleFire.inCanopy(13, 23, fourByFour), "nor the opposite one");
    }
}
