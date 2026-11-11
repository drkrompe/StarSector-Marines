package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.appearance.DerelictLegRig;
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
    private static final float[] NO_FEET = new float[0];

    @Test
    void theBodyStripLiesAheadOfTheWayTheWalkerGoes() {
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
    void theBodyBreaksAnOrdinaryWallAheadAtOnceAndAFortifiedOneHolds() {
        NavigationGrid grid = open();
        CellTopology topology = new CellTopology(W, W);
        wall(grid, topology, 8, 7, WallCrush.HEAVY_CEILING);
        wall(grid, topology, 9, 7, 150);
        Map<Integer, Integer> broken = crush(grid, topology, 8.5f, 5.9f, .7f, NO_FEET, 0f);
        assertEquals(WallCrush.HEAVY_CEILING, broken.get(7 * W + 8),
                "the city wall ahead takes its whole structure the tick the body reaches it: no grind");
        assertFalse(broken.containsKey(7 * W + 9), "a fortified wall over the ceiling holds");
    }

    @Test
    void aFootBreaksOnlyWhatItComesDownOnAndAWallBetweenTheLegsStands() {
        NavigationGrid grid = open();
        CellTopology topology = new CellTopology(W, W);
        // The walker at (8.5, 5.5) going north: a foot planted out at its east flank, on a garden wall, and another
        // stretch of the same wall level with the body but between body and foot, where neither collider reaches.
        wall(grid, topology, 11, 5, 80);
        wall(grid, topology, 10, 4, 80);
        float[] feet = {11.4f, 5.5f};
        Map<Integer, Integer> broken = crush(grid, topology, 8.5f, 5.5f, .7f, feet, .35f);
        assertEquals(80, broken.get(5 * W + 11), "the planted foot breaks the wall it came down on");
        assertFalse(broken.containsKey(4 * W + 10), "the stretch between the legs and beside the body stands");
        assertFalse(crush(grid, topology, 8.5f, 5.5f, .7f, NO_FEET, .35f).containsKey(5 * W + 11),
                "with that foot in the air, nothing breaks the wall under it");
    }

    @Test
    void aFootColliderIsADiscOverTheCellsItTouches() {
        NavigationGrid grid = open();
        Set<Integer> touched = new HashSet<>();
        MechWallCrush.forEachFootCell(grid, 5.5f, 5.9f, .35f, (x, y) -> touched.add(y * W + x));
        assertTrue(touched.contains(5 * W + 5) && touched.contains(6 * W + 5), "its own cell and the one it overhangs");
        assertFalse(touched.contains(5 * W + 6), "not a cell a whole half cell off");
        assertFalse(touched.contains(7 * W + 5));
    }

    @Test
    void whatItCanBreakIsFloorToItsFeetAndWhatItCannotIsSolid() {
        NavigationGrid grid = open();
        CellTopology topology = new CellTopology(W, W);
        wall(grid, topology, 3, 3, 80);
        wall(grid, topology, 4, 3, 240);
        assertFalse(MechWallCrush.footHolds(grid, topology, null, 3, 3), "a garden wall it breaks is floor to a foot");
        assertTrue(MechWallCrush.footHolds(grid, topology, null, 4, 3), "a facility wall is gripped, not stepped on");
        assertFalse(MechWallCrush.footHolds(grid, topology, null, 5, 5), "open floor");
        assertTrue(MechWallCrush.footHolds(grid, topology, null, -1, 5), "off the map");
    }

    @Test
    void aDerelictWalkersFeetAreItsRigsPlantedFeet() {
        DerelictLegRig rig = new DerelictLegRig(DerelictLegRig.walkerAt(1f));
        DerelictLegRig.Solid nothing = (x, y) -> false;
        rig.advance(20f, 20f, 0f, 0f, nothing);
        float[] feet = new float[MechWallCrush.MAX_FEET * 2];
        assertEquals(4, MechWallCrush.plantedFeet(rig, feet), "standing, all four feet are down");
        for (int i = 0; i < 4; i++) {
            assertEquals(rig.footX(i), feet[i * 2], 1e-6f);
            assertEquals(rig.footY(i), feet[i * 2 + 1], 1e-6f);
        }
        // Walking north, a leg lifts in its turn; a lifted foot is no collider.
        boolean sawStep = false;
        for (int tick = 1; tick <= 60 && !sawStep; tick++) {
            rig.advance(20f, 20f + tick * .03f, 0f, 1f / 30f, nothing);
            int stepping = 0;
            for (int i = 0; i < rig.legs(); i++) if (rig.stepping(i)) stepping++;
            if (stepping == 0) continue;
            sawStep = true;
            int planted = MechWallCrush.plantedFeet(rig, feet);
            assertEquals(rig.legs() - stepping, planted, "only the feet on the ground count");
            for (int i = 0; i < rig.legs(); i++) {
                if (!rig.stepping(i)) continue;
                for (int f = 0; f < planted; f++) {
                    assertFalse(feet[f * 2] == rig.footX(i) && feet[f * 2 + 1] == rig.footY(i),
                            "the lifted foot is not among the colliders");
                }
            }
        }
        assertTrue(sawStep, "a walking rig lifts a leg within two seconds");
    }

    @Test
    void aCompanyWalkersFeetAreItsGaitsFeetOnTheGround() {
        MechGaitState gait = MechGaitState.create(10f, 10f, 0f, MechVariant.HAULER);
        float[] feet = new float[MechWallCrush.MAX_FEET * 2];
        assertEquals(2, MechWallCrush.plantedFeet(gait, feet), "standing, both feet are down");
        assertEquals(gait.leftFootX(), feet[0], 1e-6f);
        assertEquals(gait.rightFootY(), feet[3], 1e-6f);
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

    /** The structure one tick's crush of a walker going north spends on each cell, by {@code y * W + x}. */
    private static Map<Integer, Integer> crush(NavigationGrid grid, CellTopology topology, float x, float y,
                                               float radius, float[] feet, float footRadius) {
        Map<Integer, Integer> broken = new HashMap<>();
        new MechWallCrush.Bearing().crush(grid, topology, null, x, y, 0f, 1f, radius, feet, feet.length / 2,
                footRadius, new MechWallCrush.Breaker() {
                    @Override public void wall(int cx, int cy, int structure) { broken.put(cy * W + cx, structure); }

                    @Override public void edge(int cx, int cy, Direction direction, int structure) { }
                });
        return broken;
    }
}
