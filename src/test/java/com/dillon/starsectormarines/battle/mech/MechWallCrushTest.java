package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vehicle.WallCrush;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechWallCrushTest {

    private static final int W = 16;

    @Test
    void theStripLiesAheadOfTheWayTheWalkerGoes() {
        NavigationGrid grid = new NavigationGrid(10, 10);
        Set<Integer> north = new HashSet<>();
        MechWallCrush.forEachBearingCell(grid, 5.5f, 5.5f, 0f, 1f, .6f, (x, y) -> north.add(y * 10 + x));
        assertTrue(north.contains(6 * 10 + 5), "the cell just north of a walker going north is borne on");
        assertFalse(north.contains(4 * 10 + 5), "nothing behind it is");
        for (int cell : north) assertTrue(cell / 10 >= 5, "no cell lies behind the walker's own row");

        Set<Integer> east = new HashSet<>();
        MechWallCrush.forEachBearingCell(grid, 5.5f, 5.5f, 1f, 0f, .6f, (x, y) -> east.add(y * 10 + x));
        assertTrue(east.contains(5 * 10 + 6), "turned east, it bears on the cell to its east");
        assertFalse(east.contains(5 * 10 + 4));
    }

    @Test
    void anOrdinaryWallAheadFallsAtOnceAndAFortifiedOneHolds() {
        NavigationGrid grid = open();
        CellTopology topology = new CellTopology(W, W);
        wall(grid, topology, 8, 7, WallCrush.HEAVY_CEILING);
        wall(grid, topology, 9, 7, 150);
        Map<Integer, Integer> broken = crush(grid, topology, 8.5f, 5.9f, 0f, 1f, .7f, 0f);
        assertEquals(WallCrush.HEAVY_CEILING, broken.get(7 * W + 8),
                "the city wall ahead takes its whole structure the tick the walker reaches it: no grind");
        assertFalse(broken.containsKey(7 * W + 9), "a fortified wall over the ceiling is never borne on");
    }

    @Test
    void itsLegsBreakWhatTheyStandOverBesideItsBody() {
        NavigationGrid grid = open();
        CellTopology topology = new CellTopology(W, W);
        // Level with the walker, a cell off its side: no strip ahead reaches it, its splayed legs do.
        wall(grid, topology, 9, 5, 80);
        Set<Integer> strip = new HashSet<>();
        MechWallCrush.forEachBearingCell(grid, 8.5f, 5.5f, 0f, 1f, .7f, (x, y) -> strip.add(y * W + x));
        assertFalse(strip.contains(5 * W + 9), "the strip ahead of the body does not reach the cell beside it");

        float legs = MechWallCrush.legReach(MechVariant.SECURITY_WALKER);
        assertTrue(legs > MechVariant.SECURITY_WALKER.radius, "an MG-40's legs reach past its body");
        assertEquals(80, crush(grid, topology, 8.5f, 5.5f, 0f, 1f, .7f, legs).get(5 * W + 9),
                "a leg over a garden wall beside the walker brings it down");
        assertFalse(crush(grid, topology, 8.5f, 5.5f, 0f, 1f, .7f, 0f).containsKey(5 * W + 9),
                "without its legs the torso alone would leave it standing");
    }

    @Test
    void aBiggerChassisReachesFurtherWithItsLegs() {
        assertTrue(MechWallCrush.legReach(MechVariant.SECURITY_HALL) > MechWallCrush.legReach(MechVariant.SECURITY_WALKER),
                "the MG-70's legs reach further than the MG-40's");
    }

    private static NavigationGrid open() {
        NavigationGrid grid = new NavigationGrid(W, W);
        for (int y = 0; y < W; y++) for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        return grid;
    }

    private static void wall(NavigationGrid grid, CellTopology topology, int x, int y, int structure) {
        grid.setWalkable(x, y, false);
        grid.setWallHp(x, y, structure);
        topology.setTag(x, y, CellTopology.Tag.WALL, true);
    }

    /** The structure the crush spends on each cell, by {@code y * W + x}, for one tick of one walker. */
    private static Map<Integer, Integer> crush(NavigationGrid grid, CellTopology topology, float x, float y, float dx,
                                               float dy, float radius, float legs) {
        Map<Integer, Integer> broken = new HashMap<>();
        new MechWallCrush.Bearing().crush(grid, topology, null, x, y, dx, dy, radius, legs,
                new MechWallCrush.Breaker() {
                    @Override public void wall(int cx, int cy, int structure) { broken.put(cy * W + cx, structure); }

                    @Override public void edge(int cx, int cy, Direction direction, int structure) { }
                });
        return broken;
    }
}
