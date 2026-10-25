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
 * the wider cross street while keeping every way in.
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
}
