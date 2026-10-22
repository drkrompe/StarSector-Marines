package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
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
    void heatDoesNotPassOverAStandingWall() {
        // Brush either side of a one-cell wall: a fire on the west must not reach the east.
        int w = 20, h = 5, wallX = 10;
        FireTerrain walled = new FireTerrain() {
            @Override public int fuelMask(int i) { return i % w == wallX ? 0 : FireFuel.BRUSH.bit; }
            @Override public boolean blocksHeat(int i) { return i % w == wallX; }
        };
        FireTerrain open = i -> i % w == wallX ? 0 : FireFuel.BRUSH.bit;
        assertTrue(crossed(new FireField(w, h, 3L, open), wallX), "control: without the wall the fire jumps the gap");
        assertFalse(crossed(new FireField(w, h, 3L, walled), wallX), "heat stops at the wall");
    }

    private static boolean crossed(FireField f, int wallX) {
        for (int y = 0; y < 5; y++) f.addHeat(0, y, 5000);
        for (int t = 0; t < 30 * 40; t++) f.tick(t);
        for (int y = 0; y < 5; y++) if (f.stateAt(y * 20 + wallX + 1) != FireField.UNBURNT) return true;
        return false;
    }
}
