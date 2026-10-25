package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.bsp.District;
import com.dillon.starsectormarines.battle.world.gen.bsp.DistrictProgram;
import com.dillon.starsectormarines.battle.world.gen.bsp.DistrictStreets;
import com.dillon.starsectormarines.battle.world.gen.bsp.QuarterClaim;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cordoned facility's plan ({@code set-piece-districts.md}) on a two-by-two block plan with five-wide
 * streets and road all round: the gatehouses narrow every way in to a lane and keep it, and the roles go
 * round the quarter.
 */
class CordonedFacilityProgramTest {

    static final int W = 40, H = 40;

    final BlockLeaf a = new BlockLeaf(3, 3, 12, 12, false), b = new BlockLeaf(18, 3, 27, 12, false);
    final BlockLeaf c = new BlockLeaf(3, 18, 12, 27, false), d = new BlockLeaf(18, 18, 27, 27, false);

    static boolean[][] road(List<BlockLeaf> leaves) {
        boolean[][] road = new boolean[W][H];
        for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) road[x][y] = true;
        for (BlockLeaf leaf : leaves) {
            for (int x = leaf.left; x <= leaf.right; x++) for (int y = leaf.top; y <= leaf.bottom; y++) road[x][y] = false;
        }
        return road;
    }

    QuarterClaim.Quarter quarter(List<BlockLeaf> leaves, boolean[][] road) {
        QuarterClaim.Quarter q = QuarterClaim.find(leaves, road, null, leaf -> true, leaf -> 1,
                CordonedFacilityProgram.SPEC);
        assertNotNull(q);
        return q;
    }

    @Test
    void everyGatehouseNarrowsItsStreetToALaneAndKeepsTheWayIn() {
                List<BlockLeaf> leaves = List.of(a, b, c, d);
        boolean[][] road = road(leaves);
        QuarterClaim.Quarter q = quarter(leaves, road);
        DistrictProgram.Plan plan = new CordonedFacilityProgram().plan(q, road, null);
        assertNotNull(plan);
        boolean[][] after = plan.streets();

        assertEquals(4, CordonedFacilityProgram.gates(q, road).size(), "both ends of the main street, one per row");
        // The main street's west mouth, y 13..17: the middle three kept through the gatehouse, the kerbs closed.
        for (int x = 3; x < 3 + CordonedFacilityProgram.GATEHOUSE_DEPTH; x++) {
            assertFalse(after[x][13] || after[x][17], "the gatehouse takes the kerbs");
            for (int y = 14; y <= 16; y++) assertTrue(after[x][y], "the lane stays street");
        }
        for (int y = 13; y <= 17; y++) assertTrue(after[3 + CordonedFacilityProgram.GATEHOUSE_DEPTH][y], "inside, whole");
        assertTrue(after[2][13], "the street outside is not the facility's");
        assertTrue(DistrictStreets.keepsEntries(q, road, after), "every mouth keeps a way in, joined as before");
        assertEquals(CordonedFacilityProgram.gates(q, road), CordonedFacilityProgram.gates(q, after),
                "the gatehouses read the same off the rewritten streets");
    }

    @Test
    void aStreetEndingAgainstALotIsClosedAndTheRestKeepTheirWays() {
                BlockLeaf lot = new BlockLeaf(13, 28, 17, 34, false);
        List<BlockLeaf> leaves = List.of(a, b, c, d, lot);
        boolean[][] road = road(leaves);
        QuarterClaim.Quarter q = quarter(List.of(a, b, c, d), road);
        boolean[][] after = CordonedFacilityProgram.gated(q, road);
        assertEquals(3, CordonedFacilityProgram.gates(q, road).size());
        for (int x = 13; x <= 17; x++) assertFalse(after[x][27], "the wall runs across a street with no street beyond");
        assertTrue(after[15][26]);
        assertTrue(DistrictStreets.keepsEntries(q, road, after));
    }

    @Test
    void aStreetNoWiderThanTheLaneIsItsOwnLane() {
        BlockLeaf b3 = new BlockLeaf(16, 3, 27, 12, false), d3 = new BlockLeaf(16, 18, 27, 27, false);
        List<BlockLeaf> leaves = List.of(a, b3, c, d3);
        boolean[][] road = road(leaves);
        QuarterClaim.Quarter q = quarter(leaves, road);
        boolean[][] after = CordonedFacilityProgram.gated(q, road);
        for (int x = 13; x <= 15; x++) assertTrue(after[x][3], "a three-wide cross street keeps its whole width");
        assertTrue(DistrictStreets.keepsEntries(q, road, after));
    }

    @Test
    void rolesGoRoundTheQuarter() {
        BlockLeaf wide = new BlockLeaf(3, 18, 14, 27, false), square = new BlockLeaf(18, 18, 27, 27, false);
        List<BlockLeaf> leaves = List.of(a, b, wide, square);
        QuarterClaim.Quarter q = quarter(leaves, road(leaves));
        Map<BlockLeaf, District.Role> roles = CordonedFacilityProgram.roles(q);
        assertEquals(District.Role.COURTYARD, roles.get(square), "nearest the middle, and the smaller of two as near");
        assertEquals(District.Role.ADMIN, roles.get(wide), "the largest of the rest");
        assertEquals(District.Role.DEPOT, roles.get(a), "the next largest, the first of two as large");
        assertEquals(District.Role.LAB, roles.get(b));
        assertEquals(wide, new CordonedFacilityProgram().plan(q, road(leaves), null).seed(), "the admin block seeds it");
    }
}
