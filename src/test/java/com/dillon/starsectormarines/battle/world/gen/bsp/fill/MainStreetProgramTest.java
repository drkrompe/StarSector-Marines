package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.bsp.District;
import com.dillon.starsectormarines.battle.world.gen.bsp.DistrictProgram;
import com.dillon.starsectormarines.battle.world.gen.bsp.DistrictStreets;
import com.dillon.starsectormarines.battle.world.gen.bsp.SpineClaim;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The main street program's plan ({@code set-piece-districts.md}) on a small block plan: a street along x with
 * three leaves above it and two below, a three-wide cross street between each pair above and a four-wide one
 * below. The plaza takes the larger end leaf, the hall the leaf at the other end, and the rewrite narrows only
 * the wider cross street while keeping every way in. Those leaves are too shallow for the market square; on a
 * plan with deeper leaves, the two at one end of the longer side become the square's lot instead.
 */
class MainStreetProgramTest {

    @Test
    void plansAPlazaAtOneEndAHallAtTheOtherAndNarrowsTheCrossStreets() {
        BlockLeaf a1 = new BlockLeaf(1, 3, 8, 11, false), a2 = new BlockLeaf(12, 3, 19, 11, false);
        BlockLeaf a3 = new BlockLeaf(23, 3, 30, 11, false), b1 = new BlockLeaf(1, 16, 10, 24, false);
        BlockLeaf b2 = new BlockLeaf(15, 16, 30, 24, false);
        List<BlockLeaf> leaves = List.of(a1, a2, a3, b1, b2);
        boolean[][] road = new boolean[36][28];
        for (int x = 0; x < 36; x++) for (int y = 0; y < 28; y++) road[x][y] = true;
        for (BlockLeaf leaf : leaves) {
            for (int x = leaf.left; x <= leaf.right; x++) for (int y = leaf.top; y <= leaf.bottom; y++) road[x][y] = false;
        }
        SpineClaim.Spine spine = SpineClaim.find(leaves, road, leaf -> true, leaf -> 1, MainStreetProgram.SPEC);
        assertNotNull(spine);

        DistrictProgram.Plan plan = new MainStreetProgram().plan(spine, road, null);
        assertNotNull(plan);
        assertFalse(plan.roles().containsValue(District.Role.MARKET_SQUARE), "nine-deep leaves hold no square");
        assertEquals(District.Role.PLAZA, plan.roles().get(b2), "the larger end leaf is the square");
        assertEquals(District.Role.LANDMARK, plan.roles().get(b1), "the hall closes the other end");
        assertEquals(b1, plan.seed());
        for (BlockLeaf leaf : List.of(a1, a2, a3)) assertEquals(District.Role.SHOPFRONT, plan.roles().get(leaf));

        for (int y = 16; y <= 24; y++) {
            assertTrue(plan.streets()[13][y], "the cross street below keeps three cells");
            assertFalse(plan.streets()[14][y], "and gives its fourth to the pavement");
        }
        for (int y = 3; y <= 11; y++) assertTrue(plan.streets()[10][y], "a three-wide cross street is left alone");
        assertTrue(DistrictStreets.keepsEntries(spine, road, plan.streets()));
    }

    @Test
    void givesTheMarketSquareTheEndPairOfTheLongerSideFacingTheStreet() {
        BlockLeaf a1 = new BlockLeaf(2, 6, 13, 19, false), a2 = new BlockLeaf(17, 6, 30, 19, false);
        BlockLeaf a3 = new BlockLeaf(35, 6, 46, 19, false), b1 = new BlockLeaf(2, 24, 20, 36, false);
        BlockLeaf b2 = new BlockLeaf(24, 24, 46, 36, false);
        List<BlockLeaf> leaves = List.of(a1, a2, a3, b1, b2);
        boolean[][] road = roadAround(leaves, 52, 40);
        SpineClaim.Spine spine = SpineClaim.find(leaves, road, leaf -> true, leaf -> 1, MainStreetProgram.SPEC);
        assertNotNull(spine);

        DistrictProgram.Plan plan = new MainStreetProgram().plan(spine, road, null);
        assertNotNull(plan);
        assertEquals(District.Role.MARKET_SQUARE, plan.roles().get(a2));
        assertEquals(District.Role.MARKET_SQUARE, plan.roles().get(a3), "the pair at the plaza end, on the side of three");
        assertEquals(District.Role.LANDMARK, plan.roles().get(b1), "the hall closes the other end");
        assertEquals(b1, plan.seed());
        assertEquals(District.Role.SHOPFRONT, plan.roles().get(a1));
        assertEquals(District.Role.SHOPFRONT, plan.roles().get(b2));
        assertFalse(plan.roles().containsValue(District.Role.PLAZA));
        for (int x = 31; x <= 34; x++) assertTrue(plan.streets()[x][10], "the street through the square keeps its width");

        MarketSquare.Placement square = MainStreetLayout.chooseSquare(spine, List.of(a2, a3), plan.streets(), null,
                52, 40, List.of());
        assertNotNull(square);
        for (int x = 0; x < MarketSquare.WIDTH; x++) {
            int[] out = square.pose().cell(x, -1);
            assertTrue(out[1] >= spine.lo && out[1] <= spine.hi, "the square's front is on the main street");
        }
        for (int x = 0; x < MarketSquare.WIDTH; x++) {
            int world = square.pose().cell(x, 0)[0];
            if (world >= 31 && world <= 34) {
                assertTrue(x >= 6 && x < MarketSquare.WIDTH - 6, "the street through runs between the side rows");
            }
        }
    }

    private static boolean[][] roadAround(List<BlockLeaf> leaves, int width, int height) {
        boolean[][] road = new boolean[width][height];
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) road[x][y] = true;
        for (BlockLeaf leaf : leaves) {
            for (int x = leaf.left; x <= leaf.right; x++) for (int y = leaf.top; y <= leaf.bottom; y++) road[x][y] = false;
        }
        return road;
    }
}
