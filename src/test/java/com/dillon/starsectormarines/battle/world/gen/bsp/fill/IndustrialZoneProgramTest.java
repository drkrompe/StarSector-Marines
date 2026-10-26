package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;
import com.dillon.starsectormarines.battle.world.gen.bsp.District;
import com.dillon.starsectormarines.battle.world.gen.bsp.DistrictProgram;
import com.dillon.starsectormarines.battle.world.gen.bsp.DistrictStreets;
import com.dillon.starsectormarines.battle.world.gen.bsp.SpineClaim;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The industrial zone program ({@code set-piece-districts.md}) on a small block plan: a street four wide along x,
 * three working lots and a guard post above it, the freight yard's parcel and one more lot below, with a
 * four-wide cross street between the first two lots above and another beside the yard.
 */
class IndustrialZoneProgramTest {

    final BlockLeaf a1 = leaf(1, 6, 8, 11, BlockKind.BUILDING_INDUSTRIAL);
    final BlockLeaf a2 = leaf(13, 3, 20, 11, BlockKind.INDUSTRIAL_YARD);
    final BlockLeaf a3 = leaf(24, 3, 31, 11, BlockKind.FORTIFIED_POST);
    final BlockLeaf yard = leaf(1, 16, 14, 35, BlockKind.FREIGHT_YARD);
    final BlockLeaf b2 = leaf(19, 16, 31, 24, BlockKind.BUILDING_INDUSTRIAL);
    final List<BlockLeaf> leaves = List.of(a1, a2, a3, yard, b2);

    static BlockLeaf leaf(int left, int top, int right, int bottom, BlockKind kind) {
        BlockLeaf leaf = new BlockLeaf(left, top, right, bottom, false);
        leaf.kind = kind;
        return leaf;
    }

    boolean[][] road() {
        boolean[][] road = new boolean[36][40];
        for (int x = 0; x < 36; x++) for (int y = 0; y < 40; y++) road[x][y] = true;
        for (BlockLeaf leaf : leaves) {
            for (int x = leaf.left; x <= leaf.right; x++) for (int y = leaf.top; y <= leaf.bottom; y++) road[x][y] = false;
        }
        return road;
    }

    SpineClaim.Spine spine(boolean[][] road) {
        SpineClaim.Spine spine = SpineClaim.find(leaves, road, leaf -> true, leaf -> 1, IndustrialZoneProgram.SPEC);
        assertNotNull(spine);
        return spine;
    }

    @Test
    void hostsTheYardKeepsThePostAndMakesTheSmallestLotADepot() {
        boolean[][] road = road();
        SpineClaim.Spine spine = spine(road);
        DistrictProgram.Plan plan = new IndustrialZoneProgram().plan(spine, road, null);
        assertNotNull(plan);
        assertEquals(District.Role.FREIGHT_YARD, plan.roles().get(yard), "the yard's parcel keeps its set piece");
        assertEquals(yard, plan.seed(), "and carries the zone's seed");
        assertEquals(District.Role.SECURITY_POST, plan.roles().get(a3), "a guard post keeps its hardpoint");
        assertEquals(District.Role.TANK_FARM, plan.roles().get(a1), "the smallest lot is the depot");
        assertEquals(District.Role.WAREHOUSE, plan.roles().get(a2));
        assertEquals(District.Role.WAREHOUSE, plan.roles().get(b2));
    }

    @Test
    void narrowsOnlyTheCrossStreetBetweenPlainLotsAndKeepsEveryWayIn() {
        boolean[][] road = road();
        SpineClaim.Spine spine = spine(road);
        DistrictProgram.Plan plan = new IndustrialZoneProgram().plan(spine, road, null);
        for (int y = 6; y <= 11; y++) {
            assertTrue(plan.streets()[11][y], "the cross street between the depot and the warehouse keeps three cells");
            assertFalse(plan.streets()[12][y], "and gives up its fourth");
        }
        for (int y = 16; y <= 24; y++) {
            for (int x = 15; x <= 18; x++) assertTrue(plan.streets()[x][y], "the yard's cross street stays whole");
        }
        assertTrue(DistrictStreets.keepsEntries(spine, road, plan.streets()));
    }

    @Test
    void takesWorkingGroundHomesAndTheSetPiecesButNeverACompoundSeed() {
        assertTrue(IndustrialZoneProgram.eligible(leaf(0, 0, 9, 9, BlockKind.WASTELAND_RUBBLE)));
        assertTrue(IndustrialZoneProgram.eligible(leaf(0, 0, 9, 9, BlockKind.BUILDING_RESIDENTIAL)));
        assertTrue(IndustrialZoneProgram.eligible(leaf(0, 0, 10, 10, BlockKind.FACTORY_ROW)),
                "a row seed as deep as the shallow row may join, alone or with a neighbour");
        assertFalse(IndustrialZoneProgram.eligible(leaf(0, 0, 9, 9, BlockKind.FACTORY_ROW)),
                "a smaller one claims its neighbour instead");
        assertFalse(IndustrialZoneProgram.eligible(leaf(0, 0, 9, 9, BlockKind.MILITARY_BASE)));
        assertFalse(IndustrialZoneProgram.eligible(leaf(0, 0, 9, 9, BlockKind.BUILDING_CIVIC)));
        assertEquals(2 + IndustrialZoneProgram.SET_PIECE,
                IndustrialZoneProgram.pocket(BlockKind.FREIGHT_YARD, MapDistrictTheme.INDUSTRIAL));
        assertEquals(0, IndustrialZoneProgram.pocket(BlockKind.BUILDING_RESIDENTIAL, MapDistrictTheme.RESIDENTIAL));
    }

    @Test
    void aRowSeedTooSmallAloneJoinsItsNeighbourAcrossTheCrossStreet() {
        BlockLeaf seed = leaf(1, 0, 8, 13, BlockKind.FACTORY_ROW);
        BlockLeaf next = leaf(12, 0, 19, 13, BlockKind.BUILDING_INDUSTRIAL);
        BlockLeaf last = leaf(23, 0, 30, 13, BlockKind.INDUSTRIAL_YARD);
        BlockLeaf across = leaf(1, 18, 30, 26, BlockKind.BUILDING_INDUSTRIAL);
        BlockLeaf elsewhere = leaf(33, 18, 38, 26, BlockKind.FREIGHT_YARD);
        List<BlockLeaf> plan4 = List.of(seed, next, last, across);
        boolean[][] road = new boolean[40][30];
        for (boolean[] col : road) Arrays.fill(col, true);
        for (BlockLeaf leaf : List.of(seed, next, last, across, elsewhere)) {
            for (int x = leaf.left; x <= leaf.right; x++) for (int y = leaf.top; y <= leaf.bottom; y++) road[x][y] = false;
        }
        SpineClaim.Spine spine = SpineClaim.find(plan4, road, leaf -> true, leaf -> 1, IndustrialZoneProgram.SPEC);
        assertNotNull(spine);
        DistrictProgram.Plan plan = new IndustrialZoneProgram().plan(spine, road, null);
        assertNotNull(plan);
        assertEquals(District.Role.FACTORY_ROW, plan.roles().get(seed));
        assertEquals(District.Role.FACTORY_ROW, plan.roles().get(next), "the neighbour widens the row's ground");
        for (int y = 0; y <= 13; y++) {
            for (int x = 9; x <= 11; x++) assertFalse(plan.streets()[x][y], "the cross street between them is the row's");
        }
        assertTrue(DistrictStreets.keepsEntries(spine, road, plan.streets()));
        BlockLeaf outsideRow = leaf(33, 0, 38, 13, BlockKind.FACTORY_ROW);
        new IndustrialZoneProgram().committed(spine, plan, List.of(seed, next, outsideRow, elsewhere));
        assertEquals(BlockKind.BUILDING_INDUSTRIAL, outsideRow.kind, "a row planned elsewhere stands down");
        assertEquals(BlockKind.FREIGHT_YARD, elsewhere.kind, "a yard the zone does not host stays");
    }

    @Test
    void theRowTakesTheLargestGroundFirstWhereOnlyThatHostsBoth() {
        // The large lot holds either; the row will not stand on the smaller, so yard first would host one.
        BlockLeaf large = leaf(1, 0, 20, 19, BlockKind.BUILDING_INDUSTRIAL);
        BlockLeaf small = leaf(25, 2, 38, 19, BlockKind.BUILDING_INDUSTRIAL);
        BlockLeaf across = leaf(1, 24, 38, 30, BlockKind.BUILDING_INDUSTRIAL);
        List<BlockLeaf> lots = List.of(large, small, across);
        boolean[][] road = new boolean[40][32];
        for (boolean[] col : road) Arrays.fill(col, true);
        for (BlockLeaf leaf : lots) {
            for (int x = leaf.left; x <= leaf.right; x++) for (int y = leaf.top; y <= leaf.bottom; y++) road[x][y] = false;
        }
        SpineClaim.Spine spine = SpineClaim.find(lots, road, leaf -> true, leaf -> 1, IndustrialZoneProgram.SPEC);
        assertNotNull(spine);
        Map<BlockLeaf, District.Role> roles = IndustrialZoneProgram.roles(spine, false,
                (ground, yard) -> yard || !ground.contains(small));
        assertNotNull(roles);
        assertEquals(District.Role.FACTORY_ROW, roles.get(large));
        assertEquals(District.Role.FREIGHT_YARD, roles.get(small));
    }

    @Test
    void whereNoRowStandsTheRowIsLaidAlongTheStreetBetweenItsWorkshops() {
        // A belt eight deep: no ground holds even the shallow row, so the row takes the street itself.
        BlockLeaf w1 = leaf(0, 0, 7, 7, BlockKind.BUILDING_INDUSTRIAL);
        BlockLeaf w2 = leaf(12, 0, 19, 7, BlockKind.INDUSTRIAL_YARD);
        BlockLeaf w3 = leaf(24, 0, 31, 7, BlockKind.BUILDING_INDUSTRIAL);
        BlockLeaf opposite = leaf(12, 12, 19, 19, BlockKind.BUILDING_INDUSTRIAL);
        BlockLeaf shallow = leaf(24, 12, 31, 16, BlockKind.BUILDING_INDUSTRIAL);
        List<BlockLeaf> lots = List.of(w1, w2, w3, opposite, shallow);
        boolean[][] road = new boolean[40][24];
        for (boolean[] col : road) Arrays.fill(col, true);
        for (BlockLeaf leaf : lots) {
            for (int x = leaf.left; x <= leaf.right; x++) for (int y = leaf.top; y <= leaf.bottom; y++) road[x][y] = false;
        }
        SpineClaim.Spine spine = SpineClaim.find(lots, road, leaf -> true, leaf -> 1, IndustrialZoneProgram.SPEC);
        assertNotNull(spine);
        Map<BlockLeaf, District.Role> roles = IndustrialZoneProgram.roles(spine, false, (ground, yard) -> true);
        assertNotNull(roles);
        assertFalse(roles.containsValue(District.Role.FACTORY_ROW));
        for (BlockLeaf leaf : List.of(w1, w2, w3)) assertEquals(District.Role.WORKSHOP, roles.get(leaf));
        assertEquals(District.Role.WORKSHOP, roles.get(opposite), "a deep lot across the lane is a workshop too");
        assertFalse(roles.get(shallow) == District.Role.WORKSHOP, "one too shallow for a hall is not");
    }

    @Test
    void theWorksStreetNeverRunsLongerThanALaneOrShorterThanTheShortestRow() {
        BlockLeaf a = leaf(0, 0, 8, 7, BlockKind.BUILDING_INDUSTRIAL);
        BlockLeaf b = leaf(14, 0, 22, 7, BlockKind.BUILDING_INDUSTRIAL);
        BlockLeaf across = leaf(0, 12, 22, 16, BlockKind.BUILDING_INDUSTRIAL);
        List<BlockLeaf> lots = List.of(a, b, across);
        boolean[][] road = new boolean[26][20];
        for (boolean[] col : road) Arrays.fill(col, true);
        for (BlockLeaf leaf : lots) {
            for (int x = leaf.left; x <= leaf.right; x++) for (int y = leaf.top; y <= leaf.bottom; y++) road[x][y] = false;
        }
        SpineClaim.Spine spine = SpineClaim.find(lots, road, leaf -> true, leaf -> 1, IndustrialZoneProgram.SPEC);
        assertNotNull(spine);
        Map<BlockLeaf, District.Role> none = new IdentityHashMap<>();
        List<BlockLeaf> works = IndustrialZoneProgram.worksStreet(spine, none);
        assertEquals(List.of(a, b), works, "the two deep lots front a lane of 23");
        none.put(b, District.Role.FREIGHT_YARD);
        assertTrue(IndustrialZoneProgram.worksStreet(spine, none).isEmpty(), "one nine-cell lot is no lane");
    }

    @Test
    void aRowSeedThatHoldsNoRowEvenWithANeighbourKeepsItsOwnClaim() {
        a1.kind = BlockKind.FACTORY_ROW;
        boolean[][] road = road();
        assertEquals(null, new IndustrialZoneProgram().plan(spine(road), road, null));
    }

    @Test
    void aWarehouseKeepsAStockYardOnlyBehindTheDeepestHall() {
        assertEquals(IndustrialZoneLayout.APRON_SHALLOW, IndustrialZoneLayout.apron(8));
        assertEquals(6, IndustrialZoneLayout.hallDepth(8));
        assertEquals(IndustrialZoneLayout.APRON, IndustrialZoneLayout.apron(12));
        assertEquals(9, IndustrialZoneLayout.hallDepth(12), "no room for a stock yard: the hall takes the depth");
        assertEquals(IndustrialZoneLayout.HALL_MAX, IndustrialZoneLayout.hallDepth(20));
        assertEquals(1, IndustrialZoneLayout.kerbRun(new boolean[]{true, false, false, false, true}, 3, 0, 2));
    }
}
